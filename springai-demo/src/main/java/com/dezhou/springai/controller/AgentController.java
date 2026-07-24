package com.dezhou.springai.controller;

import com.dezhou.springai.config.AiConfig;
import com.dezhou.springai.dto.ApiResponse;
import com.dezhou.springai.service.AgentService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final AiConfig aiConfig;

    @PostMapping("/run")
    public ApiResponse<AgentService.AgentResult> agentRun(@RequestBody AgentRunRequest req) {
        return ApiResponse.ok(
                agentService.agentRun(req.getTask(), req.getLanguage()),
                aiConfig.getChatProvider()
        );
    }

    @PostMapping("/tools/calculate")
    public ApiResponse<String> calc(@RequestBody CalcRequest req) {
        return ApiResponse.ok(agentService.directToolCalculate(req.getExpression()), aiConfig.getChatProvider());
    }

    @PostMapping("/tools/check-python")
    public ApiResponse<String> checkPython(@RequestBody CodeCheckRequest req) {
        return ApiResponse.ok(agentService.directToolCheckPython(req.getCode()), aiConfig.getChatProvider());
    }

    @PostMapping("/tools/check-java")
    public ApiResponse<String> checkJava(@RequestBody CodeCheckRequest req) {
        return ApiResponse.ok(agentService.directToolCheckJava(req.getCode()), aiConfig.getChatProvider());
    }

    @Data
    public static class AgentRunRequest {
        @NotBlank
        private String task = "写一段有语法错误的Python代码并自动修复";
        private String language = "python";
    }

    @Data
    public static class CalcRequest {
        @NotBlank
        private String expression;
    }

    @Data
    public static class CodeCheckRequest {
        @NotBlank
        private String code;
    }

}
