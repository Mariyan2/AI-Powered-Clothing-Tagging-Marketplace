package com.example.demo.controllers;

import com.example.demo.Config.FirebaseConfig;
import com.example.demo.services.ChatGPTService;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// This controller was built prior to the proper implementation of the APIs', its' function is to verify that they are properly functioning and connected to the backend.
@RestController
public class TestController {

    //FIELDS
    private final ChatGPTService chatGPTService;
    private final Firestore firestore;
    private final Storage storage;

    //CONSTRUCTOR
    public TestController(ChatGPTService chatGPTService, Firestore firestore, Storage storage) {
        this.chatGPTService = chatGPTService;
        this.firestore = firestore;
        this.storage = storage;
    }

    //Chatgpt test
    @GetMapping("/chatgptTest")
    public ResponseEntity<String> chatgptTest() {
        return ResponseEntity.ok(chatGPTService.chatTest());
    }

    //writes specifically to the real posts collection to verify that it exists and we can read and write to it.
    @GetMapping("/firebaseTestPosts")
    public ResponseEntity<String> firebaseTest() {
        try {
            String id = UUID.randomUUID().toString();
            ApiFuture<WriteResult> write = firestore.collection("posts")
                    .document(id)
                    .set(Map.of("hello", "world", "id", id));

            String updateTime = write.get().getUpdateTime().toString();
            DocumentSnapshot snap = firestore.collection("posts").document(id).get().get();
            String hello = snap.getString("hello");
            return ResponseEntity.ok("Wrote doc " + id + " at " + updateTime + " | read: " + hello);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Firestore error: " + e.getMessage());
        }
    }

    //tests the image upload functionality
    @PostMapping(value = "/imageUploadTest", consumes = "multipart/form-data")
    public ResponseEntity<String> imageUploadTest(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "filename", required = false) String filename,
            @RequestParam(value = "bucket", required = false) String bucketOverride ) {
        try { String bucket = (bucketOverride != null && !bucketOverride.isBlank())
                    ? bucketOverride
                    : FirebaseConfig.BUCKET;
            String name = (filename == null || filename.isBlank())
                    ? "Clothing site images/" + UUID.randomUUID() + "-" + file.getOriginalFilename()
                    : filename;

            BlobId blobId = BlobId.of(bucket, name);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(file.getContentType())
                    .build();

            storage.create(blobInfo, file.getBytes());
            String gsUri = "gs://" + bucket + "/" + name;
            URL signedUrl = storage.signUrl(blobInfo, 1, TimeUnit.DAYS, Storage.SignUrlOption.withV4Signature());
            String resp = "Uploaded: " + gsUri + " | Signed (1d): " + signedUrl;
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Upload error: " + e.getMessage());
        }
    }



}
