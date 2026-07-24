package com.dezhou.springai.service;

import com.dezhou.springai.config.AiConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlannerService {

    private final ChatClient chatClient;
    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanStep {
        private int step;
        @JsonProperty("function")
        private String functionName;
        @JsonProperty("input_from")
        private String inputFrom;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanResult {
        private String goal;
        private List<PlanStep> steps;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlannerExecutionResult {
        private PlanResult plan;
        private Map<String, String> outputs;
        private List<String> executionTrace;
    }

    private static final Map<String, ToolMeta> AVAILABLE_TOOLS = Map.of(
            "CodeGenerator", new ToolMeta("根据业务需求生成业务源代码", "task",
                    """
                            你是后端工程师，根据需求生成干净可运行的%s代码。
                            需求：{input}
                            只输出代码，不要额外说明。
                            """),
            "TestGenerator", new ToolMeta("接收源代码，生成单元测试代码", "CodeGenerator",
                    """
                            基于下面代码生成完整的单元测试（JUnit5或pytest，按代码语言选择）：
                            {input}
                            仅输出测试代码。
                            """),
            "DocGenerator", new ToolMeta("接收源代码，生成接口文档", "CodeGenerator",
                    """
                            为下面代码生成简洁Markdown接口文档：
                            {input}
                            """)
    );

    public PlanResult splitPlan(String complexTask, String language) {
        log.info("[splitPlan] provider={}, task={}", aiConfig.getChatProvider(), complexTask);
        StringBuilder toolCatalog = new StringBuilder();
        AVAILABLE_TOOLS.forEach((k, v) -> toolCatalog.append("- ").append(k).append(": ").append(v.description()).append("\n"));

        BeanOutputConverter<PlanResult> parser = new BeanOutputConverter<>(PlanResult.class);
        String format = parser.getFormat();

        String plannerPrompt = """
                你是任务规划器。根据用户任务，从可用工具中选出必要函数，排成有序执行计划。

                可用工具：
                %s

                规则：
                1. 只输出合法 JSON，不要其它说明
                2. 严格按以下格式输出：
                %s
                3. function 必须是可用工具之一
                4. 第一步通常 input_from 为 "task"；后续步骤可引用前面工具名作为输入来源
                5. 不要编造不存在的工具

                用户任务：
                {task}
                """.formatted(toolCatalog, format);

        String raw = chatClient.prompt()
                .user(usr -> usr.text(plannerPrompt).param("task", complexTask))
                .call()
                .content();
        log.info("[splitPlan] raw={}", raw);
        try {
            return extractPlan(raw);
        } catch (Exception e) {
            log.warn("[splitPlan] 解析失败，回退默认计划：{}", e.getMessage());
            return fallbackPlan(complexTask);
        }
    }

    public PlannerExecutionResult runPlanner(String complexTask, String language) {
        PlanResult plan = splitPlan(complexTask, language);
        Map<String, String> outputs = new LinkedHashMap<>();
        outputs.put("task", complexTask);
        List<String> trace = new ArrayList<>();

        for (PlanStep step : plan.getSteps()) {
            ToolMeta meta = AVAILABLE_TOOLS.get(step.getFunctionName());
            if (meta == null) {
                trace.add("SKIP step " + step.getStep() + ": 未知函数 " + step.getFunctionName());
                continue;
            }
            String src = step.getInputFrom() == null ? "task" : step.getInputFrom();
            if (!outputs.containsKey(src)) {
                trace.add("SKIP step " + step.getStep() + ": 找不到输入来源 " + src);
                continue;
            }
            String input = outputs.get(src);
            String prompt = meta.promptTemplate().formatted(language).replace("{input}", input);
            trace.add("EXEC step " + step.getStep() + " -> " + step.getFunctionName() + " (from=" + src + ", reason=" + step.getReason() + ")");
            String result = chatClient.prompt().user(prompt).call().content();
            outputs.put(step.getFunctionName(), result);
        }

        return PlannerExecutionResult.builder()
                .plan(plan)
                .outputs(outputs)
                .executionTrace(trace)
                .build();
    }

    private PlanResult fallbackPlan(String task) {
        return PlanResult.builder()
                .goal(task)
                .steps(List.of(
                        PlanStep.builder().step(1).functionName("CodeGenerator").inputFrom("task").reason("先生成源代码").build(),
                        PlanStep.builder().step(2).functionName("TestGenerator").inputFrom("CodeGenerator").reason("基于代码生成测试").build(),
                        PlanStep.builder().step(3).functionName("DocGenerator").inputFrom("CodeGenerator").reason("基于代码生成文档").build()
                ))
                .build();
    }

    private PlanResult extractPlan(String raw) throws Exception {
        try {
            BeanOutputConverter<PlanResult> parser = new BeanOutputConverter<>(PlanResult.class);
            return parser.convert(raw);
        } catch (Exception e) {
            String json = extractJson(raw);
            return objectMapper.readValue(json, PlanResult.class);
        }
    }

    private static String extractJson(String text) {
        text = text.trim();
        Matcher fence = Pattern.compile("```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.DOTALL).matcher(text);
        if (fence.find()) return fence.group(1);
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) return text.substring(start, end + 1);
        throw new IllegalArgumentException("未找到JSON内容");
    }

    private record ToolMeta(String description, String defaultInputFrom, String promptTemplate) {}

}
