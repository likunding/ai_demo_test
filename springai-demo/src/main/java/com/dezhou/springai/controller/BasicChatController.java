package com.dezhou.springai.controller;

import com.dezhou.springai.config.AiConfig;
import com.dezhou.springai.dto.ApiResponse;
import com.dezhou.springai.service.BasicChatService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/basic")
@RequiredArgsConstructor
public class BasicChatController {

    private final BasicChatService basicChatService;
    private final AiConfig aiConfig;

    @PostMapping("/chat")
    public ApiResponse<String> chat(@RequestBody ChatRequest req) {
        return ApiResponse.ok(basicChatService.simpleChat(req.getPrompt()), aiConfig.getChatProvider());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatRequest req) {
        return basicChatService.streamChat(req.getPrompt());
    }

    @PostMapping("/prompt-template")
    public ApiResponse<String> promptTemplateCall(@RequestBody PromptTemplateRequest req) {
        return ApiResponse.ok(basicChatService.promptTemplateCall(req.getRequirement()), aiConfig.getChatProvider());
    }

    @PostMapping("/prompt-preview")
    public ApiResponse<String> promptPreview(@RequestBody PromptTemplateRequest req) {
        return ApiResponse.ok(basicChatService.formatPromptPreview(req.getRequirement()), aiConfig.getChatProvider());
    }

    @PostMapping("/temperature")
    public ApiResponse<Map<String, String>> temperatureTest(@RequestBody TemperatureRequest req) {
        return ApiResponse.ok(
                basicChatService.temperatureTest(req.getPrompt(), req.getTemperatures()),
                aiConfig.getChatProvider()
        );
    }

    @Data
    public static class ChatRequest {
        @NotBlank
        private String prompt;
    }

    @Data
    public static class PromptTemplateRequest {
        @NotBlank
        private String requirement;
    }

    @Data
    public static class TemperatureRequest {
        @NotBlank
        private String prompt = "写冒泡排序Python代码";
        @NotEmpty
        private List<Double> temperatures = List.of(0.1, 0.5, 0.8);
    }

}
