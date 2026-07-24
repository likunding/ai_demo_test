package com.dezhou.springai.controller;

import com.dezhou.springai.config.AiConfig;
import com.dezhou.springai.dto.ApiResponse;
import com.dezhou.springai.service.RagService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;
    private final AiConfig aiConfig;

    @PostMapping("/load")
    public ApiResponse<String> loadDocument(@RequestBody LoadDocRequest req) {
        ragService.loadPlainText(req.getContent(), req.getSourceName());
        return ApiResponse.ok("已加载文档到向量库", aiConfig.getChatProvider());
    }

    @PostMapping("/split-preview")
    public ApiResponse<RagService.ChunkInfo> splitPreview(@RequestBody LoadDocRequest req) {
        return ApiResponse.ok(ragService.splitAndPreview(req.getContent()), aiConfig.getChatProvider());
    }

    @PostMapping("/load-classpath")
    public ApiResponse<Integer> loadFromClasspath(@RequestParam(defaultValue = "classpath*:docs/**/*.txt") String pattern) {
        return ApiResponse.ok(ragService.loadDocsFromClasspath(pattern), aiConfig.getChatProvider());
    }

    @PostMapping("/search")
    public ApiResponse<List<String>> search(@RequestBody QueryRequest req) {
        return ApiResponse.ok(ragService.searchDocuments(req.getQuery()), aiConfig.getChatProvider());
    }

    @PostMapping("/query")
    public ApiResponse<RagService.RagAnswer> query(@RequestBody QueryRequest req) {
        return ApiResponse.ok(ragService.ragQuery(req.getQuery()), aiConfig.getChatProvider());
    }

    @Data
    public static class LoadDocRequest {
        @NotBlank
        private String content;
        private String sourceName = "manual";
    }

    @Data
    public static class QueryRequest {
        @NotBlank
        private String query;
    }

}
