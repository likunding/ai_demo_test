package com.dezhou.springai.service;

import com.dezhou.springai.config.AiConfig;
import com.dezhou.springai.tools.CodeTools;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final ChatClient chatClient;
    private final CodeTools codeTools;
    private final AiConfig aiConfig;

    @Value("${app.workflow.max-retry-loops:3}")
    private int maxRetryLoops;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowState {
        private String requirement;
        private String language;
        private String code;
        private String error;
        private int loopTimes;
        private List<String> nodeTrace;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowResult {
        private String finalCode;
        private boolean success;
        private int totalLoops;
        private List<WorkflowState> stateHistory;
    }

    public WorkflowState genCodeNode(WorkflowState state) {
        state.getNodeTrace().add("GEN_CODE@loop" + state.getLoopTimes());
        String prompt;
        if (state.getError() == null || state.getError().isBlank()) {
            prompt = String.format("""
                    根据需求生成%s代码。只输出代码，不要额外说明。
                    需求：%s
                    """, state.getLanguage(), state.getRequirement());
        } else {
            prompt = String.format("""
                    修复%s代码中的语法错误，只输出修复后的完整代码，不要额外说明。
                    上一轮报错：%s
                    原始需求：%s
                    """, state.getLanguage(), state.getError(), state.getRequirement());
        }
        String code = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        state.setCode(code);
        state.setLoopTimes(state.getLoopTimes() + 1);
        log.info("[genCodeNode] loop={}, codeLen={}", state.getLoopTimes(), code.length());
        return state;
    }

    public WorkflowState checkCodeNode(WorkflowState state) {
        state.getNodeTrace().add("CHECK_CODE@loop" + state.getLoopTimes());
        String error;
        switch (state.getLanguage().toLowerCase()) {
            case "python" -> error = normalizeError(codeTools.codeCheckPython(state.getCode()));
            case "java" -> error = normalizeError(codeTools.codeCheckJava(state.getCode()));
            default -> {
                try {
                    Class.forName("javax.script.ScriptEngineManager");
                    error = "";
                } catch (Exception e) {
                    error = "未知语言，跳过校验";
                }
            }
        }
        state.setError(error);
        log.info("[checkCodeNode] loop={}, errorEmpty={}", state.getLoopTimes(), error.isBlank());
        return state;
    }

    public String routeNode(WorkflowState state) {
        if ((state.getError() != null && !state.getError().isBlank()) && state.getLoopTimes() < maxRetryLoops) {
            return "GEN_CODE";
        }
        return "END";
    }

    public WorkflowResult runCodeFixWorkflow(String requirement, String language) {
        log.info("[runCodeFixWorkflow] provider={}, lang={}, req={}", aiConfig.getCodeProvider(), language, requirement);
        List<WorkflowState> history = new ArrayList<>();
        WorkflowState state = WorkflowState.builder()
                .requirement(requirement)
                .language(language)
                .code("")
                .error("")
                .loopTimes(0)
                .nodeTrace(new ArrayList<>())
                .build();
        history.add(snapshot(state));

        state = genCodeNode(state);
        history.add(snapshot(state));

        while (true) {
            state = checkCodeNode(state);
            history.add(snapshot(state));
            String next = routeNode(state);
            if ("END".equals(next)) {
                break;
            }
            state = genCodeNode(state);
            history.add(snapshot(state));
        }

        return WorkflowResult.builder()
                .finalCode(state.getCode())
                .success(state.getError() == null || state.getError().isBlank())
                .totalLoops(state.getLoopTimes())
                .stateHistory(history)
                .build();
    }

    private WorkflowState snapshot(WorkflowState s) {
        return WorkflowState.builder()
                .requirement(s.getRequirement())
                .language(s.getLanguage())
                .code(s.getCode() == null ? null : s.getCode().length() > 500 ? s.getCode().substring(0, 500) + "..." : s.getCode())
                .error(s.getError())
                .loopTimes(s.getLoopTimes())
                .nodeTrace(new ArrayList<>(s.getNodeTrace()))
                .build();
    }

    private String normalizeError(String raw) {
        if (raw == null) return "";
        if (raw.contains("无错误") || raw.contains("成功") || raw.trim().endsWith("代码语法无错误")) return "";
        return raw;
    }

}
