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
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryAndParserService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final AiConfig aiConfig;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeResp {
        private String code;
        private String explain;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatTurn {
        private String role;
        private String content;
    }

    public CodeResp parseCodeToBean(String requirement) {
        BeanOutputConverter<CodeResp> parser = new BeanOutputConverter<>(CodeResp.class);
        String format = parser.getFormat();
        String template = """
                你是Python开发工程师，严格遵守下面格式要求输出，禁止输出任何中文解释、markdown、代码块标记、多余文字，只返回单层JSON：
                {format}
                需求：{req}
                """;
        log.info("[parseCodeToBean] provider={}, req={}", aiConfig.getChatProvider(), requirement);
        String raw = chatClient.prompt()
                .user(usr -> usr.text(template).param("format", format).param("req", requirement))
                .advisors(a -> a.param(MessageChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY, 0))
                .call()
                .content();
        log.info("[parseCodeToBean] raw response={}", raw);
        return parser.convert(raw);
    }

    public List<String> parseToList(String requirement) {
        ListOutputConverter parser = new ListOutputConverter(new DefaultConversionService());
        String format = parser.getFormat();
        String template = """
                列出满足需求的条目，严格按格式输出：
                {format}
                需求：{req}
                """;
        String raw = chatClient.prompt()
                .user(usr -> usr.text(template).param("format", format).param("req", requirement))
                .advisors(a -> a.param(MessageChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY, 0))
                .call()
                .content();
        return parser.convert(raw);
    }

    public Map<String, Object> parseToMap(String requirement) {
        MapOutputConverter parser = new MapOutputConverter();
        String format = parser.getFormat();
        String template = """
                按格式输出需求的关键信息：
                {format}
                需求：{req}
                """;
        String raw = chatClient.prompt()
                .user(usr -> usr.text(template).param("format", format).param("req", requirement))
                .advisors(a -> a.param(MessageChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY, 0))
                .call()
                .content();
        return parser.convert(raw);
    }

    public String chatWithMemory(String conversationId, String userMessage) {
        log.info("[chatWithMemory] convId={}, user={}", conversationId, userMessage);
        return chatClient.prompt()
                .advisors(a -> a.param(MessageChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
                .user(userMessage)
                .call()
                .content();
    }

    public List<ChatTurn> getConversationHistory(String conversationId) {
        List<Message> messages = chatMemory.get(conversationId, Integer.MAX_VALUE);
        List<ChatTurn> result = new ArrayList<>();
        for (Message msg : messages) {
            ChatTurn turn = new ChatTurn();
            turn.setRole(msg.getMessageType() != null ? msg.getMessageType().name() : "UNKNOWN");
            turn.setContent(msg.getText() != null ? msg.getText() : "");
            result.add(turn);
        }
        return result;
    }

    public void clearConversation(String conversationId) {
        chatMemory.clear(conversationId);
        log.info("[clearConversation] convId={} cleared", conversationId);
    }

}
