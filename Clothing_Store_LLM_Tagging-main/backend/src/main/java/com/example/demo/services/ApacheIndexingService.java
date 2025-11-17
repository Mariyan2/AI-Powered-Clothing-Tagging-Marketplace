package com.example.demo.services;

import com.example.demo.Entity.Post;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;

import org.springframework.stereotype.Service;

// Apaches' search indexing is synced to the Post object, to be avalable for searching later.
@Service
public class ApacheIndexingService {


    private volatile IndexWriter writer;

    public ApacheIndexingService( IndexWriter writer) {

        this.writer = writer;
    }

    private static String ns(String s) { return s == null ? "" : s; }


//Post is converted to a document and stored in "d" variable
    private Document toDoc(Post p) {
        Document d = new Document();
        //each field is stored in the new document
        d.add(new StringField("postID", ns(p.getPostID()), Field.Store.YES));
        d.add(new TextField("title", ns(p.getTitle()), Field.Store.YES));
        d.add(new TextField("llmTags", ns(p.getLlmTags()), Field.Store.YES));
        d.add(new TextField("altText", ns(p.getAltText()), Field.Store.YES));
        d.add(new StoredField("imageURL", ns(p.getImageURL())));
        d.add(new StoredField("date", ns(p.getDate())));
        // finally the document is returned
        return d;
    }
// index is updated with the new post.
    public synchronized void upsert(Post p) throws Exception {
        writer.updateDocument(new Term("postID", ns(p.getPostID())), toDoc(p));
        writer.commit();
    }

}
