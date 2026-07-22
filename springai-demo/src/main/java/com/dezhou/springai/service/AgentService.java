package com.dezhou.springai.service;

import com.dezhou.springai.config.AiConfig;
import com.dezhou.springai.tools.CodeTools;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ChatClient chatClient;
    private final CodeTools codeTools;
    private final AiConfig aiConfig;

    @Data
    public static class AgentStep {
        private int stepNo;
        private String type;
        private String content;
    }

    @Data
    public static class AgentResult {
        private String finalAnswer;
        private List<AgentStep> trace;
    }

    public AgentResult agentRun(String userTask, String language) {
        log.info("[agentRun] provider={}, lang={}, task={}", aiConfig.getProvider(), language, userTask);
        List<AgentStep> trace = new ArrayList<>();
        trace.add(newStep(1, "user_input", userTask));

        String systemPrompt = switch (language.toLowerCase()) {
            case "java" -> """
                    你是代码工程师助手。当用户让你写Java代码时，先输出代码，然后必须调用 codeCheckJava 工具校验语法。
                    如果校验发现错误，请根据错误信息修正代码并再次校验，最多循环3次。
                    最终用自然语言总结最终代码以及校验结论。
                    """;
            case "python" -> """
                    你是代码工程师助手。当用户让你写Python代码时，先输出代码，然后必须调用 codeCheckPython 工具校验语法。
                    如果校验发现错误，请根据错误信息修正代码并再次校验，最多循环3次。
                    最终用自然语言总结最终代码以及校验结论。
                    """;
            default -> """
                    你是助手。如果需要做数值计算，务必调用 calculate 工具。
                    如果涉及代码生成，结合相应工具校验。
                    """;
        };

        ChatResponse resp = chatClient.prompt()
                .system(systemPrompt)
                .tools(codeTools)
                .user(userTask)
                .call()
                .chatResponse();
        AssistantMessage assistant = resp.getResult().getOutput();

        trace.add(newStep(2, "assistant_final", assistant.getText() == null ? "" : assistant.getText()));

        AgentResult result = new AgentResult();
        result.setFinalAnswer(assistant.getText() == null ? "" : assistant.getText());
        result.setTrace(trace);
        return result;
    }

    public String directToolCalculate(String expression) {
        return codeTools.calculate(expression);
    }

    public String directToolCheckPython(String code) {
        return codeTools.codeCheckPython(code);
    }

    public String directToolCheckJava(String code) {
        return codeTools.codeCheckJava(code);
    }

    private AgentStep newStep(int n, String type, String content) {
        AgentStep s = new AgentStep();
        s.setStepNo(n);
        s.setType(type);
        String c = content == null ? "" : content;
        s.setContent(c.length() > 2000 ? c.substring(0, 2000) + "..." : c);
        return s;
    }

}
