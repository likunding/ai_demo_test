package com.dezhou.springai.controller;

import com.dezhou.springai.config.AiConfig;
import com.dezhou.springai.dto.ApiResponse;
import com.dezhou.springai.service.KvCacheTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class IndexController {

    private final AiConfig aiConfig;
    private final KvCacheTestService kvCacheTestService;

    @GetMapping
    public ApiResponse<Map<String, Object>> index() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "Spring AI Demo - Ollama & vLLM Samples");
        data.put("provider", aiConfig.getProvider());
        data.put("timestamp", Instant.now());

        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("POST /api/basic/chat", "基础聊天调用");
        endpoints.put("POST /api/basic/stream", "流式输出 (SSE)");
        endpoints.put("POST /api/basic/prompt-template", "Prompt模板代码生成");
        endpoints.put("POST /api/basic/prompt-preview", "Prompt模板格式化预览");
        endpoints.put("POST /api/basic/temperature", "不同temperature输出对比");

        endpoints.put("POST /api/advanced/parse/bean", "结构化Bean输出解析（CodeResp）");
        endpoints.put("POST /api/advanced/parse/list", "结构化List输出解析");
        endpoints.put("POST /api/advanced/parse/map", "结构化Map输出解析");
        endpoints.put("POST /api/advanced/memory/chat", "带记忆多轮对话");
        endpoints.put("GET /api/advanced/memory/{convId}", "获取对话历史");
        endpoints.put("DELETE /api/advanced/memory/{convId}", "清空对话历史");

        endpoints.put("POST /api/rag/load", "加载纯文本到向量库");
        endpoints.put("POST /api/rag/split-preview", "文本切分预览");
        endpoints.put("POST /api/rag/load-classpath", "加载classpath下docs/**/*.txt");
        endpoints.put("POST /api/rag/search", "仅检索相似文档");
        endpoints.put("POST /api/rag/query", "RAG检索增强问答");

        endpoints.put("POST /api/agent/run", "Agent+Tool调用（生成代码+自动校验）");
        endpoints.put("POST /api/agent/tools/calculate", "直接调用计算器工具");
        endpoints.put("POST /api/agent/tools/check-python", "直接调用Python语法校验");
        endpoints.put("POST /api/agent/tools/check-java", "直接调用Java语法校验");

        endpoints.put("POST /api/orchestration/workflow/code-fix", "工作流：生成-校验-重试循环");
        endpoints.put("POST /api/orchestration/planner/split", "Planner：仅拆分任务计划");
        endpoints.put("POST /api/orchestration/planner/run", "Planner：拆分并按顺序执行");

        endpoints.put("POST /api/kv-cache/run", "KV Cache多轮上下文累积测试");

        data.put("endpoints", endpoints);
        return ApiResponse.ok(data, aiConfig.getProvider());
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("provider", aiConfig.getProvider());
        data.put("timestamp", Instant.now());
        return ApiResponse.ok(data, aiConfig.getProvider());
    }

    @PostMapping("/kv-cache/run")
    public ApiResponse<KvCacheTestService.KvTestResult> runKvTest(
            @RequestParam(defaultValue = "5") int rounds,
            @RequestParam(defaultValue = "256") int maxTokens
    ) {
        String convId = "kv-test-" + UUID.randomUUID().toString().substring(0, 8);
        return ApiResponse.ok(kvCacheTestService.runKvCacheTest(convId, rounds, maxTokens), aiConfig.getProvider());
    }

}
