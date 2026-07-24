package com.dezhou.springai.service;

import com.dezhou.springai.config.AiConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final AiConfig aiConfig;

    @Value("${app.rag.chunk-size:500}")
    private int chunkSize;

    @Value("${app.rag.chunk-overlap:50}")
    private int chunkOverlap;

    @Value("${app.rag.top-k:3}")
    private int topK;

    @Data
    public static class ChunkInfo {
        private int totalChunks;
        private List<String> samples;
    }

    @Data
    public static class RagAnswer {
        private String answer;
        private List<String> references;
    }

    private TokenTextSplitter newSplitter() {
        return new TokenTextSplitter(chunkSize, chunkOverlap, 300, 10000, false);
    }

    public void loadPlainText(String content, String sourceName) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("source", sourceName);
        Document doc = new Document(content, meta);
        List<Document> chunks = newSplitter().split(List.of(doc));
        log.info("[loadPlainText] source={}, chunks={}", sourceName, chunks.size());
        vectorStore.add(chunks);
    }

    public ChunkInfo splitAndPreview(String content) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("source", "preview");
        Document doc = new Document(content, meta);
        List<Document> chunks = newSplitter().split(List.of(doc));
        ChunkInfo info = new ChunkInfo();
        info.setTotalChunks(chunks.size());
        info.setSamples(chunks.stream().limit(3).map(Document::getText).collect(Collectors.toList()));
        return info;
    }

    public List<String> searchDocuments(String query) {
        SearchRequest req = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        List<Document> docs = vectorStore.similaritySearch(req);
        return docs.stream().map(d -> {
            Object src = d.getMetadata().getOrDefault("source", "unknown");
            return "[src=" + src + "] " + d.getText();
        }).collect(Collectors.toList());
    }

    public RagAnswer ragQuery(String question) {
        SearchRequest req = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .build();
        List<Document> docs = vectorStore.similaritySearch(req);
        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
        List<String> references = docs.stream()
                .map(d -> String.valueOf(d.getMetadata().getOrDefault("source", "unknown"))
                        + ": " + truncate(d.getText(), 120))
                .collect(Collectors.toList());

        String template = """
                基于以下上下文回答问题，如果上下文没有相关信息请说明无法从文档中找到答案：
                {context}

                问题: {question}
                """;
        log.info("[ragQuery] provider={}, question={}, contextChunks={}", aiConfig.getChatProvider(), question, docs.size());
        String answer = chatClient.prompt()
                .user(usr -> usr.text(template).param("context", context).param("question", question))
                .call()
                .content();
        RagAnswer ra = new RagAnswer();
        ra.setAnswer(answer);
        ra.setReferences(references);
        return ra;
    }

    public int loadDocsFromClasspath(String pattern) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(pattern);
            List<Document> allChunks = new ArrayList<>();
            TokenTextSplitter splitter = newSplitter();
            for (Resource r : resources) {
                String content;
                try (InputStream is = r.getInputStream()) {
                    content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                String name = r.getFilename() == null ? "unknown" : r.getFilename();
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("source", name);
                Document doc = new Document(content, meta);
                allChunks.addAll(splitter.split(List.of(doc)));
            }
            vectorStore.add(allChunks);
            log.info("[loadDocsFromClasspath] loaded {} chunks from {}", allChunks.size(), pattern);
            return allChunks.size();
        } catch (IOException e) {
            throw new RuntimeException("加载文档失败", e);
        }
    }

    private String truncate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }

}
