package com.example.demo.controllers;

import com.example.demo.Config.FirebaseConfig;
import com.example.demo.Entity.Post;
import com.example.demo.services.AltTextService;
import com.example.demo.services.ChatGPTService;
import com.example.demo.services.PostService;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

@RestController
@RequestMapping("/images")
@CrossOrigin(origins = "http://localhost:5173")
public class ImageController {

    private final Storage storage;
    private final PostService posts;
    private final ChatGPTService chat;
    private final AltTextService altText;
//bulk upload folder path
    private final Path imgDir = Paths.get("src/main/resources/images").toAbsolutePath().normalize();

    public ImageController(Storage storage, PostService posts, ChatGPTService chat, AltTextService altText) {
        this.storage = storage;
        this.posts = posts;
        this.chat = chat;
        this.altText = altText;
        try { Files.createDirectories(imgDir); } catch (Exception ignored) {}
    }

    //removes the file extension
    private static String stripExt(String filename) {
        int i = filename.lastIndexOf('.');
        return (i > 0) ? filename.substring(0, i) : filename;
    }

    //titles that resemble file names are rejected
    private static boolean looksLikeFilename(String s) {
        if (s == null) return true;
        String t = s.trim();
        if (t.isEmpty()) return true;
        if (t.matches(".*\\.[A-Za-z0-9]{2,4}.*")) return true;
        long digits = t.chars().filter(Character::isDigit).count();
        long dashes = t.chars().filter(c -> c=='-' || c=='_').count();
        if (digits >= 6 || dashes >= 3) return true;
        return t.length() < 4 || t.length() > 120;
    }

//choose ai title
    private static String chooseTitle(String aiTitle, String originalFilenameWithoutExt) {
        String candidate = (aiTitle == null) ? "" : aiTitle.trim();
        if (looksLikeFilename(candidate) || candidate.isEmpty()) return originalFilenameWithoutExt;
        return Character.toUpperCase(candidate.charAt(0)) + candidate.substring(1);
    }
//rate limit checker
    private static boolean isRateLimit(String msg) {
        if (msg == null) return false;
        String m = msg.toLowerCase(Locale.ROOT);
        return m.contains("rate limit") || m.contains("rate_limit_exceeded") || m.contains("429");
    }

//retry wrapper
    private String callWithRetryStopOn429(
            Supplier<String> fn, String label, int maxAttempts, long startDelayMs, long maxDelayMs
    ) throws RateLimitHit {
        long delay = startDelayMs;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String out = fn.get();
                if (out != null && !out.isBlank()) return out;
            } catch (Exception ex) {
                String msg = String.valueOf(ex.getMessage());
                if (isRateLimit(msg)) throw new RateLimitHit("429/rate limit during " + label + ": " + msg);
            }
            try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
            delay = Math.min(maxDelayMs, delay * 2);
        }
        return "";
    }
    private static class RateLimitHit extends Exception {
        public RateLimitHit(String msg) { super(msg); }
    }

    //singlular image upload that stores the image then tags it and finally saves the post
    @PostMapping(value = "/upload", consumes = "multipart/form-data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        String step = "start";
        try {
            // error handler if bucket does not exist
            String bucket = FirebaseConfig.BUCKET; step = "bucket";
            if (storage.get(bucket) == null) {
                return ResponseEntity.internalServerError().body(Map.of(
                        "error", "MissingBucket",
                        "message", "Bucket '" + bucket + "' does not exist.",
                        "step", step
                ));
            }

            // content type and names
            String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("image");
            String originalBase = originalName.contains(".") ? originalName.substring(0, originalName.lastIndexOf('.')) : originalName;
            String objectName  = "clothing/" + UUID.randomUUID() + "-" + originalName; step = "objectName";
            String contentType = Optional.ofNullable(file.getContentType()).orElse("image/jpeg");

            // upload data to firebase storage
            String token = UUID.randomUUID().toString(); step = "token";
            BlobId blobId = BlobId.of(bucket, objectName); step = "blobId";
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType)
                    .setMetadata(Map.of("firebaseStorageDownloadTokens", token))
                    .build();
            storage.create(blobInfo, file.getBytes()); step = "create";

            // public URL (for app) + signed URL (for AI fetch)
            String encoded  = URLEncoder.encode(objectName, StandardCharsets.UTF_8);
            String publicUrl = "https://firebasestorage.googleapis.com/v0/b/" + bucket + "/o/" + encoded + "?alt=media&token=" + token;
            URL signedUrl = storage.signUrl(blobInfo, 1, TimeUnit.DAYS, Storage.SignUrlOption.withV4Signature());

            // ai generated image title
            step = "title";
            String aiTitle = Optional.ofNullable(chat.generateTitleForImage(signedUrl.toString())).orElse("").trim();
            String finalTitle = chooseTitle(aiTitle, originalBase);

            // ai and alt tags
            step = "tags";
            String llmTags = Optional.ofNullable(chat.generateTagsForImage(signedUrl.toString())).orElse("").trim();
            step = "alt";
            String alt = Optional.ofNullable(altText.describe(signedUrl.toString())).orElse("").trim();

            if (alt.isBlank() && !llmTags.isBlank()) {
                step = "altFromTags";
                alt = Arrays.stream(llmTags.split(",")).map(String::trim).filter(s -> !s.isBlank()).limit(6).reduce((a, b) -> a + " " + b).orElse("");
            }

            // if enrichment is unseucesful then fails
            if (llmTags.isBlank() || alt.isBlank()) {
                return ResponseEntity.status(502).body(Map.of(
                        "error", "EnrichmentFailed",
                        "message", (llmTags.isBlank() ? "llmTags is empty (vision tagging failed). " : "")
                                + (alt.isBlank() ? "altText is empty (vision captioning failed)." : ""),
                        "step", step,
                        "imageURL", publicUrl,
                        "title", finalTitle
                ));
            }

            // saves the post
            Post p = new Post(null, finalTitle, publicUrl, LocalDate.now().toString(), llmTags, alt);
            String id = posts.savePost(p); step = "save";
            return ResponseEntity.ok(Map.of(
                    "id", id,
                    "title", p.getTitle(),
                    "imageURL", p.getImageURL(),
                    "llmTags", p.getLlmTags(),
                    "altText", p.getAltText() ));

        } catch (Exception e) {
            System.err.println("[/images/upload] step=" + step + " err=" + e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getClass().getSimpleName(),
                    "message", String.valueOf(e.getMessage()),
                    "step", step )); }
    }

//bulk image upload from the "images " folder in resources
    @PostMapping("/resumableBulk")
    public ResponseEntity<String> resumableBulk(@RequestParam(name = "enrich", defaultValue = "false") boolean enrich) {
        if (!Files.isDirectory(imgDir)) {
            return ResponseEntity.badRequest().body("Image folder not found: " + imgDir.toAbsolutePath()); }

        Set<String> exts = Set.of(".jpg", ".jpeg", ".png");
        int success = 0, failed = 0, skipped = 0;
        String lastStoppedReason = "";

        try (Stream<Path> paths = Files.list(imgDir)) {
            outer:
            for (Path img : (Iterable<Path>) paths.sorted()::iterator) {
                String fileName = img.getFileName().toString();
                String lcName = fileName.toLowerCase(Locale.ROOT);
                boolean supported = exts.stream().anyMatch(lcName::endsWith);
                if (!supported) { skipped++; continue; }

                try {
                    // file is uplaoded to firebase storage
                    byte[] data = Files.readAllBytes(img);
                    String bucket = FirebaseConfig.BUCKET;
                    String cloudName = "clothing/" + UUID.randomUUID() + "-" + fileName;
                    String token = UUID.randomUUID().toString();

                    BlobId blobId = BlobId.of(bucket, cloudName);
                    String contentType = Optional.ofNullable(Files.probeContentType(img)).orElse("image/jpeg");
                    BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                            .setContentType(contentType)
                            .setMetadata(Map.of("firebaseStorageDownloadTokens", token))
                            .build();
                    storage.create(blobInfo, data);
                    String encoded = URLEncoder.encode(cloudName, StandardCharsets.UTF_8);
                    String publicUrl = "https://firebasestorage.googleapis.com/v0/b/" + bucket + "/o/" + encoded + "?alt=media&token=" + token;
                    URL signedUrl = storage.signUrl(blobInfo, 1, TimeUnit.DAYS, Storage.SignUrlOption.withV4Signature());
                    String title;
                    String llmTags = "";
                    String altTextValue = "";
                    if (enrich) {
                        try {
                            String t = callWithRetryStopOn429(() -> chat.generateTitleForImage(signedUrl.toString()), "title", 5, 500, 6000);
                            title = chooseTitle(t, stripExt(fileName));
                            llmTags = callWithRetryStopOn429(() -> chat.generateTagsForImage(signedUrl.toString()), "tags", 5, 500, 6000);
                            try {
                                altTextValue = callWithRetryStopOn429(() -> altText.describe(signedUrl.toString()), "alt", 3, 400, 4000);
                            } catch (RateLimitHit rl) { lastStoppedReason = rl.getMessage(); break outer; }
                            if (altTextValue.isBlank() && !llmTags.isBlank()) {
                                altTextValue = Arrays.stream(llmTags.split(",")).map(String::trim).filter(s -> !s.isBlank()).limit(6).reduce((a, b) -> a + " " + b).orElse("");
                            }
                            if (llmTags.isBlank() || altTextValue.isBlank()) {
                                failed++;
                                System.err.println(" Enrichment failed => " + fileName + " the tags or alt are blank.");
                                continue;
                            }
                        } catch (RateLimitHit rl) {
                            lastStoppedReason = rl.getMessage();
                            break;}}
                    else {
                        title = stripExt(fileName);
                    }
                    // ssave the post
                    Post p = new Post(null, (enrich ? (title == null || title.isBlank() ? stripExt(fileName) : title) : stripExt(fileName)),
                            publicUrl, LocalDate.now().toString(), llmTags, altTextValue);
                    posts.savePost(p);
                    try { Files.delete(img); } catch (Exception del) {
                        System.err.println(" Saved but has not deleted the file succesfuly: " + img + " -> " + del.getMessage());
                    }
                    success++;
                    try { Thread.sleep(enrich ? 400 : 100); } catch (InterruptedException ignored) {}
                } catch (Exception inner) {
                    failed++;
                    System.err.println(" error =>" + fileName + " / " + inner.getMessage());}
            }
        }
        catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error scanning images: " + e.getMessage());
        }

        // Result summary builder
        StringBuilder sb = new StringBuilder();
        sb.append("Resumable bulk finished. ")
                .append("Success=").append(success)
                .append(" Failed=").append(failed)
                .append(" Skipped=").append(skipped);
        if (!lastStoppedReason.isBlank()) sb.append(" | Not succesful => ").append(lastStoppedReason);
        else sb.append(" | COMPLETED! ");

        return ResponseEntity.ok(sb.toString());
    }
}
