package com.dezhou.springai.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class AiConfig {

    @Value("${spring.ai.model.provider:ollama}")
    private String provider;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.options.model:deepseek-coder:6.7b}")
    private String ollamaChatModel;

    @Value("${spring.ai.ollama.embedding.options.model:nomic-embed-text}")
    private String ollamaEmbedModel;

    @Value("${spring.ai.openai.base-url:http://localhost:8000/v1}")
    private String openaiBaseUrl;

    @Value("${spring.ai.openai.api-key:dummy}")
    private String openaiApiKey;

    @Value("${spring.ai.openai.chat.options.model:deepseek-coder:6.7b-instruct}")
    private String openaiChatModel;

    @Value("${spring.ai.openai.embedding.options.model:nomic-embed-text}")
    private String openaiEmbedModel;

    @Value("${spring.ai.ollama.chat.options.temperature:0.2}")
    private Double defaultTemperature;

    @Value("${spring.ai.ollama.chat.options.max-tokens:2048}")
    private Integer defaultMaxTokens;

    private static final RetryTemplate RETRY = RetryTemplate.defaultInstance();
    private static final ObservationRegistry OBS = ObservationRegistry.NOOP;
    private static final ModelManagementOptions MODEL_MGMT = ModelManagementOptions.defaults();
    private static final ToolCallingManager TOOL_MGR = DefaultToolCallingManager.builder().build();

    private boolean isOpenAiLike() {
        return "vllm".equalsIgnoreCase(provider) || "openai".equalsIgnoreCase(provider);
    }

    @Bean
    @Primary
    public ChatModel chatModel() {
        if (isOpenAiLike()) {
            logInit("CHAT", openaiBaseUrl, openaiChatModel);
            OpenAiApi api = new OpenAiApi(openaiBaseUrl, openaiApiKey);
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(openaiChatModel)
                    .temperature(defaultTemperature)
                    .maxTokens(defaultMaxTokens)
                    .build();
            return new OpenAiChatModel(api, options, TOOL_MGR, RETRY, OBS);
        } else {
            logInit("CHAT", ollamaBaseUrl, ollamaChatModel);
            OllamaApi api = new OllamaApi(ollamaBaseUrl);
            OllamaOptions options = OllamaOptions.builder()
                    .model(ollamaChatModel)
                    .temperature(defaultTemperature)
                    .numPredict(defaultMaxTokens)
                    .build();
            return new OllamaChatModel(api, options, TOOL_MGR, OBS, MODEL_MGMT);
        }
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        if (isOpenAiLike()) {
            logInit("EMBED", openaiBaseUrl, openaiEmbedModel);
            OpenAiApi api = new OpenAiApi(openaiBaseUrl, openaiApiKey);
            OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                    .model(openaiEmbedModel)
                    .build();
            return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options, RETRY);
        } else {
            logInit("EMBED", ollamaBaseUrl, ollamaEmbedModel);
            OllamaApi api = new OllamaApi(ollamaBaseUrl);
            OllamaOptions options = OllamaOptions.builder()
                    .model(ollamaEmbedModel)
                    .build();
            return new OllamaEmbeddingModel(api, options, OBS, MODEL_MGMT);
        }
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是专业后端开发工程师，只输出完整可运行代码，不要额外解释、不要markdown标题。")
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    public String getProvider() {
        return provider;
    }

    private void logInit(String kind, String baseUrl, String model) {
        System.out.println("[AI-INIT] " + kind + " provider=" + provider + " base-url=" + baseUrl + " model=" + model);
    }

}
