package com.example.demo.Entity;

public class Post {
    private String postID;
    private String title;
    private String imageURL;
    private String date;

    // ChatGPT (LLM generated tags)
    private String llmTags;

    // Alt-text (azure computer vision)
    private String altText;

    // getters and setters
    public String getPostID() {
        return postID;
    }

    public void setPostID(String postID) {
        this.postID = postID;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImageURL() {
        return imageURL;
    }

    public void setImageURL(String imageURL) {
        this.imageURL = imageURL;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getLlmTags() {
        return llmTags;
    }

    public void setLlmTags(String llmTags) {
        this.llmTags = llmTags;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    // constructors
    public Post(String postID, String title, String imageURL, String date, String llmTags, String altText) {
        this.postID = postID;
        this.title = title;
        this.imageURL = imageURL;
        this.date = date;
        this.llmTags = llmTags;
        this.altText = altText;
    }

    public Post() {}
}
