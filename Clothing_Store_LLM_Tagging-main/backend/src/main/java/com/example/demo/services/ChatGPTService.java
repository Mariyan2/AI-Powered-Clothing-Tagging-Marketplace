package com.example.demo.services;

import com.example.demo.util.JSON_Util;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ChatGPTService {

    private final ChatClient chatClient;

    @Value("${spring.ai.openai.api-key}")
    private String chatGPT_API_KEY;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static final int MaxRetries = 6; // total trries including the initial try
    private static final long StartMiliseconds = 500;
    private static final long MaximumMiliseconds = 6_000;
    private static final long SleepMiliseconds = 60_000; //60s when the retry-after header absent

    public ChatGPTService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String chatTest() {
        return chatClient
                .prompt()
                .user("Calculate 4 times 5")
                .options(OpenAiChatOptions.builder()
                        .model("gpt-4o-mini")
                        .temperature(0.4)
                        .build())
                .call()
                .content();
    }

    // RETRY send function to circumvent rate limtation.
    private HttpResponse<String> sendWithRetry(HttpRequest req, String label) throws Exception {
        long backoff = StartMiliseconds;
        for (int attempt = 1; attempt <= MaxRetries; attempt++) {
            try {
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int code = res.statusCode();
                String body = res.body();
                //if succesful
                if (code / 100 == 2) return res;
                //rate limit handling
                boolean looks429 = (code == 429) || containsRateLimitCode(body);
                if (looks429) {
                    long sleepMs = retryAfterToMillis(res.headers()).orElse(SleepMiliseconds);
                    System.err.printf("[OpenAI/%s] 429 (attempt %d/%d). Sleeping %ds. Body=%s%n",
                            label, attempt, MaxRetries, sleepMs/1000, truncate(body, 400));
                    safe_sleep(sleepMs);
                    continue;
                }

                if (code / 100 == 5) {
                    System.err.printf("[OpenAI/%s] %d server error (attempt %d/%d). Backoff %dms. Body=%s%n",
                            label, code, attempt, MaxRetries, backoff, truncate(body, 400));
                    safe_sleep(backoff);
                    backoff = Math.min(MaximumMiliseconds, backoff * 2);
                    continue;
                }

                System.err.printf("[OpenAI/%s] Non-retriable %d. Body=%s%n", label, code, truncate(body, 800));
                return res;

            } catch (Exception io) {
                System.err.printf("[OpenAI/%s] Transport error (attempt %d/%d): %s (%s)%n",
                        label, attempt, MaxRetries, io.getClass().getSimpleName(), io.getMessage());
                safe_sleep(backoff);
                backoff = Math.min(MaximumMiliseconds, backoff * 2);
            }
        }
        throw new RuntimeException("Exhausted retries for " + label);
    }

    private static Optional<Long> retryAfterToMillis(HttpHeaders headers) {
        try {
            Optional<String> ra = headers.firstValue("retry-after");
            if (ra.isEmpty()) return Optional.empty();
            String v = ra.get().trim();

            if (v.matches("\\d+")) return Optional.of(TimeUnit.SECONDS.toMillis(Long.parseLong(v)));

            return Optional.empty();
        } catch (Exception ignored) { return Optional.empty(); }
    }

    private boolean containsRateLimitCode(String body) {
        try {
            if (body == null || body.isBlank()) return false;
            JsonNode root = mapper.readTree(body);
            String code = root.path("error").path("code").asText("");
            String msg  = root.path("error").path("message").asText("");
            String type = root.path("error").path("type").asText("");
            String all = (code + " " + type + " " + msg).toLowerCase();
            return all.contains("rate_limit") || all.contains("rate limit");
        } catch (Exception e) {
            String low = String.valueOf(body).toLowerCase();
            return low.contains("rate_limit") || low.contains("rate limit");
        }
    }

    private static void safe_sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    // generate between 6 to 12 tags from chatgpt4o mini model
    public String generateTagsForImage(String imageUrl) {
        try {
            if (chatGPT_API_KEY == null || chatGPT_API_KEY.isBlank()) {
                System.err.println("OpenAI key missing (spring.ai.openai.api-key).");
                return "";
            }

            // prompt
            String prompt =
                    "You are an image tagging assistant for a clothing store, that MUST analyze the image and return ONLY a single line containing 6-12 short tags. " +
                            "ALL lowercase, WITHOUT hashtags, ONLY using commas as separators. Include the colors, types of garment, and the styles if they are relevant. " +
                            "Example of tags : blue, jeans, louis vuitton, streetwear, y2k, summer";

            String body = """
            {
              "model": "gpt-4o-mini",
              "temperature": 0.3,
              "max_output_tokens": 128,
              "input": [
                {
                  "role": "user",
                  "content": [
                    { "type": "input_text", "text": %s },
                    { "type": "input_image", "image_url": %s }
                  ]
                }
              ]
            }
            """.formatted(JSON_Util.escape(prompt), JSON_Util.escape(imageUrl));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/responses"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + chatGPT_API_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> res = sendWithRetry(req, "vision-tags");
            if (res.statusCode() / 100 != 2) {
                System.err.println("[vision-tags] HTTP " + res.statusCode() + " -> " + truncate(res.body(), 600));
                return "";
            }


            JsonNode root = mapper.readTree(res.body());
            String raw = root.path("output_text").path(0).asText("");

            if (raw.isBlank()) {

                JsonNode contentArr = root.path("output").path(0).path("content");
                if (contentArr.isArray()) {
                    for (JsonNode c : contentArr) {
                        if ("output_text".equals(c.path("type").asText(""))) {
                            raw = c.path("text").asText("");
                            if (!raw.isBlank()) break;
                        }
                    }
                }
            }
            if (raw.isBlank()) {
                raw = root.path("choices").path(0).path("message").path("content").asText("");
            }

            return postProcessTags(raw);

        } catch (Exception e) {
            System.err.println("vision-tags - >" + e.getMessage());
            return "";
        }
    }

    // Format tags to be usable in the post
    private static String postProcessTags(String raw) {
        return Arrays.stream((raw == null ? "" : raw).split("[,\\n]"))
                .map(String::trim)
                .map(s -> s.replaceAll("[^a-zA-Z0-9\\s\\-/]", ""))
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .limit(12)
                .collect(Collectors.joining(", "));
    }

    // generate a title (3 to 6 words)
    public String generateTitleForImage(String imageUrlReadableByModel) {
        try {
            if (chatGPT_API_KEY == null || chatGPT_API_KEY.isBlank()) {
                System.err.println("The api key is NOT PRESENT (spring.ai.openai.api-key).");
                return "";
            }

            String prompt =
                    "You are naming a clothing product image for a storefront. " +
                            "Output ONLY a concise 3–6 word title in Title Case. " +
                            "DO NOT generate emojis, hashtags or raw filenames.";

            String body = """
            {
              "model": "gpt-4o-mini",
              "temperature": 0.3,
              "max_tokens": 20,
              "messages": [
                {
                  "role": "user",
                  "content": [
                    { "type": "text", "text": %s },
                    { "type": "image_url", "image_url": { "url": %s } }
                  ]
                }
              ]
            }
            """.formatted(JSON_Util.escape(prompt), JSON_Util.escape(imageUrlReadableByModel));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + chatGPT_API_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> res = sendWithRetry(req, "vision-title");
            if (res.statusCode() / 100 != 2) {
                System.err.println("[vision-title] HTTP " + res.statusCode() + " -> " + truncate(res.body(), 500));
                return "";
            }

            String title = mapper.readTree(res.body())
                    .path("choices").path(0).path("message").path("content").asText("");

            return normalizeTitle(title);

        } catch (Exception e) {
            System.err.println("[vision-title] " + e.getMessage());
            return "";
        }
    }
//removes line breaks , strings that are too long and would not fit the post alongside additional punctuation that is not needed.
    private static String normalizeTitle(String s) {
        if (s == null) return "";
        s = s.replaceAll("[\\r\\n]+", " ").trim();
        s = s.replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "");
        if (s.length() > 60) s = s.substring(0, 60).trim();

        String low = s.toLowerCase();
        if (low.contains("pexels") || low.matches(".*\\b[0-9a-f]{6,}\\b.*") || s.contains("_")) {
            return "";
        }
        return s;
    }
}

