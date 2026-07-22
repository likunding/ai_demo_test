package com.dezhou.springai.service;

import com.dezhou.springai.config.AiConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KvCacheTestService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final AiConfig aiConfig;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KvRoundResult {
        private int round;
        private int messageCount;
        private int ctxChars;
        private long elapsedMs;
        private String preview;
        private String error;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KvTestResult {
        private String modelProvider;
        private int totalRounds;
        private List<KvRoundResult> rounds;
    }

    public KvTestResult runKvCacheTest(String conversationId, int rounds, int maxTokens) {
        log.info("[runKvCacheTest] provider={}, convId={}, rounds={}", aiConfig.getProvider(), conversationId, rounds);
        List<KvRoundResult> results = new ArrayList<>();

        int runningCtxChars = 0;
        OpenAiChatOptions roundOptions = OpenAiChatOptions.builder()
                .maxTokens(maxTokens)
                .build();

        for (int i = 0; i < rounds; i++) {
            String userPrompt = String.format(
                    "第%d轮：写一个极简 Python 工具函数，处理 JSON 参数（函数名 json_tool_%d）。", i, i
            );
            runningCtxChars += userPrompt.length();

            long t0 = System.currentTimeMillis();
            String content = "";
            String error = null;
            try {
                content = chatClient.prompt()
                        .advisors(a -> a.param(MessageChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
                        .user(userPrompt)
                        .options(roundOptions)
                        .call()
                        .content();
            } catch (Exception e) {
                error = e.getMessage();
                log.warn("[runKvCacheTest] round {} 异常: {}", i, error);
            }
            long elapsed = System.currentTimeMillis() - t0;
            runningCtxChars += content.length();

            String preview = (content == null ? "" : content).replace("\n", " ");
            preview = preview.substring(0, Math.min(80, preview.length())) + "...";

            int msgCount = chatMemory.get(conversationId, Integer.MAX_VALUE).size();

            results.add(KvRoundResult.builder()
                    .round(i)
                    .messageCount(msgCount)
                    .ctxChars(runningCtxChars)
                    .elapsedMs(elapsed)
                    .preview(preview)
                    .error(error)
                    .build());
        }

        return KvTestResult.builder()
                .modelProvider(aiConfig.getProvider())
                .totalRounds(rounds)
                .rounds(results)
                .build();
    }

}
