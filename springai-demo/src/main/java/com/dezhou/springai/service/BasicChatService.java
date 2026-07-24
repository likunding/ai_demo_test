package com.dezhou.springai.service;

import com.dezhou.springai.config.AiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BasicChatService {

    private final ChatClient chatClient;
    private final AiConfig aiConfig;

    public String simpleChat(String prompt) {
        log.info("[simpleChat] provider={}, prompt={}", aiConfig.getChatProvider(), prompt);
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    public Flux<String> streamChat(String prompt) {
        log.info("[streamChat] provider={}, prompt={}", aiConfig.getChatProvider(), prompt);
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content();
    }

    public String promptTemplateCall(String requirement) {
        String template = """
                你是专业后端开发工程师，只输出完整可运行代码，不要额外解释、不要markdown标题。
                业务需求：{requirement}
                直接返回代码：
                """;
        PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("requirement", requirement));
        Prompt prompt = promptTemplate.create();
        log.info("[promptTemplateCall] provider={}, requirement={}", aiConfig.getChatProvider(), requirement);
        return chatClient.prompt(prompt).call().content();
    }

    public Map<String, String> temperatureTest(String prompt, List<Double> temperatures) {
        Map<String, String> results = new HashMap<>();
        for (Double temp : temperatures) {
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .temperature(temp)
                    .maxTokens(300)
                    .build();
            String content = chatClient.prompt()
                    .user(prompt)
                    .options(options)
                    .call()
                    .content();
            results.put("temperature=" + temp, content);
            log.info("[temperatureTest] temp={}, content length={}", temp, content.length());
        }
        return results;
    }

    public String formatPromptPreview(String requirement) {
        String template = """
                你是专业后端开发，只输出可运行代码，不额外解释。
                需求：{requirement}
                仅返回完整代码：
                """;
        PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("requirement", requirement));
        return promptTemplate.create().getContents();
    }

}
