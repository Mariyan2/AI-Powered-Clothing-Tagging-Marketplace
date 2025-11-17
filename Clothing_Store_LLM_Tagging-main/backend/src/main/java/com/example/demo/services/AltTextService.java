package com.example.demo.services;

import com.example.demo.util.JSON_Util;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class AltTextService {

    @Value("${azure.vision.endpoint}") // azure url
    private String endpoint;
    @Value("${azure.vision.key}") // api key
    private String apiKey;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)) //wait for 15 seconds
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    private static final int MaxRetries = 6;
    private static final long StartMiliseconds = 500;
    private static final long MaximumMiliseconds = 6_000;
    private static final long SleepMiliseconds = 60_000;


// describes using the azure computer vision
    public String describe(String imageUrl) {
        try {
            // Azure computer vision is called with the help of the "caption" request
            String apiUrl = endpoint
                    + "/computervision/imageanalysis:analyze"
                    + "?api-version=2024-02-01"
                    + "&features=caption"
                    + "&language=en"
                    + "&genderNeutralCaption=true";

            String body = """
                { "url": %s }
            """.formatted(JSON_Util.escape(imageUrl));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Ocp-Apim-Subscription-Key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> res = sendWithRetry(req, "azure-caption");

            //error handler returns empty string if an error is encountered
            if (res.statusCode() / 100 != 2) {
                System.err.println("Computer Vision error: " + res.statusCode() + " -> " + res.body());
                return "";
            }

            JsonNode root = mapper.readTree(res.body());
            String caption = root.path("captionResult").path("text").asText("");
            if (caption != null && !caption.isBlank()) {
                return caption.trim();
            }


            String denseCaption_API = endpoint
                    + "/computervision/imageanalysis:analyze"
                    + "?api-version=2024-02-01"
                    + "&features=denseCaptions"
                    + "&language=en";

            HttpRequest denseCaptionsRequest = HttpRequest.newBuilder()
                    .uri(URI.create(denseCaption_API))
                    .timeout(Duration.ofSeconds(60))
                    .header("Ocp-Apim-Subscription-Key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> dcRes = sendWithRetry(denseCaptionsRequest, "azure-denseCaptions");
            if (dcRes.statusCode() / 100 != 2) {
                System.err.println("Azure Vision denseCaptions error: " + dcRes.statusCode() + " -> " + dcRes.body());
                return "";
            }

            JsonNode denseCaptionsRoot = mapper.readTree(dcRes.body());
            //The caption is taken from densecapitons
            JsonNode items = denseCaptionsRoot.path("denseCaptionsResult").path("values");
            if (items.isArray() && items.size() > 0) {
                String best = items.get(0).path("text").asText("");
                return best == null ? "" : best.trim();
            }

            return "";
        } catch (Exception e) {
            System.err.println("Azure Vision exception: " + e.getMessage());
            return "";
        }
    }

    // Send request with retry in case of rate limiting

    private HttpResponse<String> sendWithRetry(HttpRequest req, String label) throws Exception {
        long backoff = StartMiliseconds;

        for (int attempt = 1; attempt <= MaxRetries; attempt++) {
            try {
                HttpResponse<String> res =
                        http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                int code = res.statusCode();
                String body = res.body();

//if statement, returned if success
                if (code / 100 == 2) return res;

                // A 429 error code means rate limitation, this will trigger retry.
                if (code == 429 || containsRateLimit(body)) {
                    long sleepMs = retryAfterToMillis(res.headers()).orElse(SleepMiliseconds);
                    System.err.println("[Azure/" + label + "] 429 rate limit. Sleeping ~" + (sleepMs / 1000) + "s (attempt " + attempt + "/" + MaxRetries + "). Body: " + truncate(body, 300));
                    safeSleep(sleepMs);
                    continue;
                }

                // 5xx — transient server error: exponential backoff
                if (code / 100 == 5) {
                    System.err.println("[Azure/" + label + "] " + code + " server error. Backing off " + backoff + "ms (attempt " + attempt + "/" + MaxRetries + "). Body: " + truncate(body, 300));
                    safeSleep(backoff);
                    backoff = Math.min(MaximumMiliseconds, backoff * 2);
                    continue;
                }

                // Non-retriavlable if image size is too big or other 4xx error
                if (body != null && body.contains("InvalidImageSize")) {
                    System.err.println("[Azure - " + label + " InvalidImageSize – the image is too large for this current model.");
                } else {
                    System.err.println("Azure - " + label + " non-retriable " + code + ": " + truncate(body, 500));
                }
                return res;

            } catch (Exception io) {
                System.err.println("[Azure/" + label + "] transport error: " + io.getMessage() +
                        " | backing off " + backoff + "ms (attempt " + attempt + "/" + MaxRetries + ")");
                safeSleep(backoff);
                backoff = Math.min(MaximumMiliseconds, backoff * 2);
            }
        }
        throw new RuntimeException("[Azure/" + label + "] exhausted retries.");
    }



//Converts Retry-after header to miliseconds
    private static Optional<Long> retryAfterToMillis(HttpHeaders headers) {
        try {
            Optional<String> ra = headers.firstValue("retry-after");
            if (ra.isEmpty()) return Optional.empty();
            String v = ra.get().trim();
            //If the number returned is not in miliseconds then convert it to miliseconds.
            if (v.matches("\\d+")) {
                return Optional.of(TimeUnit.SECONDS.toMillis(Long.parseLong(v)));
            }
            //
        } catch (Exception ignored) {}
        //return empty if there is neither an error present or a header statiting the time required.
        return Optional.empty();
    }

    //check if rate limit text is present in responce body
    private static boolean containsRateLimit(String body) {
        if (body == null) return false;
        String lc = body.toLowerCase();
        return lc.contains("rate limit") || lc.contains("rate_limit");
    }
//The thread is paused for a select number of seconds.
    private static void safeSleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    //Make sure strings are shorter for improved readability in case error messages are too long.
    private static String truncate(String s, int max) {
        if (s == null) return "";
        return (s.length() <= max) ? s : s.substring(0, max) + "…";
    }
}
