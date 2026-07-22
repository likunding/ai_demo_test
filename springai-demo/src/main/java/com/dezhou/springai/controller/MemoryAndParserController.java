package com.dezhou.springai.controller;

import com.dezhou.springai.config.AiConfig;
import com.dezhou.springai.dto.ApiResponse;
import com.dezhou.springai.service.MemoryAndParserService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/advanced")
@RequiredArgsConstructor
public class MemoryAndParserController {

    private final MemoryAndParserService memoryAndParserService;
    private final AiConfig aiConfig;

    @PostMapping("/parse/bean")
    public ApiResponse<MemoryAndParserService.CodeResp> parseBean(@RequestBody ParseBeanRequest req) {
        return ApiResponse.ok(memoryAndParserService.parseCodeToBean(req.getRequirement()), aiConfig.getProvider());
    }

    @PostMapping("/parse/list")
    public ApiResponse<List<String>> parseList(@RequestBody ParseBeanRequest req) {
        return ApiResponse.ok(memoryAndParserService.parseToList(req.getRequirement()), aiConfig.getProvider());
    }

    @PostMapping("/parse/map")
    public ApiResponse<Map<String, Object>> parseMap(@RequestBody ParseBeanRequest req) {
        return ApiResponse.ok(memoryAndParserService.parseToMap(req.getRequirement()), aiConfig.getProvider());
    }

    @PostMapping("/memory/chat")
    public ApiResponse<String> chatWithMemory(@RequestBody MemoryChatRequest req) {
        return ApiResponse.ok(
                memoryAndParserService.chatWithMemory(req.getConversationId(), req.getMessage()),
                aiConfig.getProvider()
        );
    }

    @GetMapping("/memory/{conversationId}")
    public ApiResponse<List<MemoryAndParserService.ChatTurn>> getHistory(@PathVariable String conversationId) {
        return ApiResponse.ok(memoryAndParserService.getConversationHistory(conversationId), aiConfig.getProvider());
    }

    @DeleteMapping("/memory/{conversationId}")
    public ApiResponse<Void> clearHistory(@PathVariable String conversationId) {
        memoryAndParserService.clearConversation(conversationId);
        return ApiResponse.ok(null, aiConfig.getProvider());
    }

    @Data
    public static class ParseBeanRequest {
        @NotBlank
        private String requirement = "MySQL分页查询工具函数";
    }

    @Data
    public static class MemoryChatRequest {
        @NotBlank
        private String conversationId = "conv-1";
        @NotBlank
        private String message;
    }

}
