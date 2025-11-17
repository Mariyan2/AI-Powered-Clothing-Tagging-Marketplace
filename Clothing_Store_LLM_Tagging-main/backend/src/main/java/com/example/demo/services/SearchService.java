
package com.example.demo.services;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class SearchService {
    private final Directory directory;
    private final Analyzer analyzer;

    public SearchService(Directory directory, Analyzer analyzer) {
        this.directory = directory;
        this.analyzer = analyzer;
    }

    public List<SearchHit> search(String q, String mode, int limit) throws Exception {
        DirectoryReader reader;
        try {
            reader = DirectoryReader.open(directory);
        } catch (IndexNotFoundException e) {
            return Collections.emptyList();
        }

        try (reader) {
            String[] fields = switch (mode == null ? "llm" : mode.toLowerCase()) {
                case "alt" -> new String[]{"altText"};
                case "llm" -> new String[]{"llmTags", "title"};
                default    -> new String[]{"llmTags", "altText", "title"};
            };

            IndexSearcher searcher = new IndexSearcher(reader);
            Query query;
            String raw = (q == null) ? "" : q.trim();
            if (raw.isEmpty() || raw.equals("*")) query = new MatchAllDocsQuery();
            else {
                try {
                    query = new MultiFieldQueryParser(fields, analyzer).parse(raw);
                } catch (ParseException pe) {
                    return Collections.emptyList();
                }
            }

            TopDocs top = searcher.search(query, Math.max(1, limit));
            List<SearchHit> out = new ArrayList<>();
            for (ScoreDoc sd : top.scoreDocs) {
                Document d = searcher.storedFields().document(sd.doc);
                out.add(new SearchHit(
                        d.get("postID"),
                        d.get("title"),
                        d.get("imageURL"),
                        d.get("llmTags"),
                        d.get("altText"),
                        d.get("date"),
                        sd.score
                ));
            }
            return out;
        }
    }

    public record SearchHit(
            String postID, String title, String imageURL,
            String llmTags, String altText, String date, float score) {}
}
