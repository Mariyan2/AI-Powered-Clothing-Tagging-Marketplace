package com.example.demo.services;

import com.example.demo.Entity.Post;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PostService {

    private final Firestore db;
    private final ApacheIndexingService indexer;


    public PostService(Firestore db, ApacheIndexingService indexer) {
        this.db = db;
        this.indexer = indexer;
    }

    public String savePost(Post post) throws Exception {
        String id = (post.getPostID() == null ||  post.getPostID().isBlank())
                ? UUID.randomUUID().toString()
                : post.getPostID();

        post.setPostID(id);
        ApiFuture<WriteResult> write = db.collection("posts").document(id).set(post);
        write.get();
        indexer.upsert(post);

        return id;
    }

}
