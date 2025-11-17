package com.example.demo.Config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.project-id}")
    private String projectId;

    @Value("${firebase.bucket}")
    public String bucketId;

    public static String BUCKET;

    @Bean
    public GoogleCredentials googleCredentials(
            @Value("${firebase.credentials:classpath:firebase/service-account.json}") String location)
            throws Exception {

        org.springframework.core.io.Resource res =
                new org.springframework.core.io.DefaultResourceLoader().getResource(location);

        if (!res.exists()) {
            throw new IllegalStateException("the service account not found at the following location: " + location);
        }
        try (InputStream in = res.getInputStream()) {
            return GoogleCredentials.fromStream(in);
        }
    }
    @Bean
    public FirebaseApp firebaseApp(GoogleCredentials creds) {
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(creds)
                .setProjectId(projectId)
                .setStorageBucket(bucketId)
                .build();
        FirebaseApp app = FirebaseApp.getApps().isEmpty()
                ? FirebaseApp.initializeApp(options)
                : FirebaseApp.getInstance();
        BUCKET = bucketId;
        return app;
    }

    @Bean
    public Firestore firestore(FirebaseApp app) {
        return FirestoreClient.getFirestore(app);
    }

    @Bean
    public Storage storage(GoogleCredentials creds) {
        return StorageOptions.newBuilder()
                .setProjectId(projectId)
                .setCredentials(creds)
                .build()
                .getService();
    }

    @Bean
    public org.springframework.boot.CommandLineRunner storageCheck(Storage storage) {
        return args -> {
            Bucket b = storage.get(bucketId);
            if (b == null) {
                System.err.println("[Startup] Bucket NOT FOUND: " + bucketId +
                        " — enable Firebase Storage or set firebase.bucket correctly.");
            } else {
                System.out.println("[Startup] Bucket OK: " + b.getName() + " (location=" + b.getLocation() + ")");
            }
        };
    }
}

