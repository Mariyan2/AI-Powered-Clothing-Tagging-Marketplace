package com.example.demo.Config;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class ApacheLuceneConfig {

    @Bean
    public Analyzer analyzer() { return new StandardAnalyzer(); }

    @Bean
    public IndexWriter IndexWriter(Directory dir, Analyzer analyzer) throws Exception { IndexWriterConfig cfg = new IndexWriterConfig(analyzer);

        return new IndexWriter(dir, cfg);

    }
    @Bean
    public Directory luceneDirectory() throws Exception {

        Path path = Paths.get("lucene-index");
        return FSDirectory.open(path);
    }
}
