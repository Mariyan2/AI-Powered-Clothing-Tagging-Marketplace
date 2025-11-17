package com.example.demo.controllers;

import com.example.demo.services.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/search")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "llm") String mode,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            return ResponseEntity.ok(searchService.search(q, mode, limit));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    java.util.Map.of("error", e.getClass().getSimpleName(), "message", e.getMessage()));
        }
    }




}
