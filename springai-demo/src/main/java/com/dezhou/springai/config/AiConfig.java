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

    @Value("${spring.ai.model.chat-provider:ollama}")
    private String chatProvider;

    @Value("${spring.ai.model.vision-provider:ollama}")
    private String visionProvider;

    @Value("${spring.ai.model.code-provider:ollama}")
    private String codeProvider;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.options.model:deepseek-coder:6.7b}")
    private String ollamaChatModel;

    @Value("${spring.ai.ollama.vision.options.model:qwen2.5vl:7b}")
    private String ollamaVisionModel;

    @Value("${spring.ai.ollama.code.options.model:deepseek-coder:6.7b}")
    private String ollamaCodeModel;

    @Value("${spring.ai.ollama.vision.options.temperature:0.1}")
    private Double visionTemperature;

    @Value("${spring.ai.ollama.vision.options.max-tokens:2048}")
    private Integer visionMaxTokens;

    @Value("${spring.ai.ollama.embedding.options.model:nomic-embed-text}")
    private String ollamaEmbedModel;

    @Value("${spring.ai.openai.base-url:http://localhost:8000/v1}")
    private String openaiBaseUrl;

    @Value("${spring.ai.openai.api-key:dummy}")
    private String openaiApiKey;

    @Value("${spring.ai.openai.chat.options.model:deepseek-coder:6.7b-instruct}")
    private String openaiChatModel;

    @Value("${spring.ai.openai.vision.options.model:deepseek-coder:6.7b-instruct}")
    private String openaiVisionModel;

    @Value("${spring.ai.openai.code.options.model:deepseek-coder:6.7b-instruct}")
    private String openaiCodeModel;

    @Value("${spring.ai.openai.embedding.options.model:nomic-embed-text}")
    private String openaiEmbedModel;

    @Value("${spring.ai.doubao.api-key:}")
    private String doubaoApiKey;

    @Value("${spring.ai.doubao.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String doubaoBaseUrl;

    @Value("${spring.ai.doubao.chat.options.model:doubao-seed-2-1-pro-260628}")
    private String doubaoChatModel;

    @Value("${spring.ai.doubao.vision.options.model:doubao-seed-2-1-pro-260628}")
    private String doubaoVisionModel;

    @Value("${spring.ai.doubao.code.options.model:doubao-seed-2-1-pro-260628}")
    private String doubaoCodeModel;

    @Value("${spring.ai.ollama.chat.options.temperature:0.2}")
    private Double defaultTemperature;

    @Value("${spring.ai.ollama.chat.options.max-tokens:2048}")
    private Integer defaultMaxTokens;

    private static final RetryTemplate RETRY = RetryTemplate.defaultInstance();
    private static final ObservationRegistry OBS = ObservationRegistry.NOOP;
    private static final ModelManagementOptions MODEL_MGMT = ModelManagementOptions.defaults();
    private static final ToolCallingManager TOOL_MGR = DefaultToolCallingManager.builder().build();

    private boolean isOpenAiLike(String provider) {
        return "vllm".equalsIgnoreCase(provider) || "openai".equalsIgnoreCase(provider) || "doubao".equalsIgnoreCase(provider);
    }

    private String getApiKey(String provider) {
        if ("doubao".equalsIgnoreCase(provider)) {
            return doubaoApiKey;
        }
        return openaiApiKey;
    }

    private String getBaseUrl(String provider) {
        if ("doubao".equalsIgnoreCase(provider)) {
            return doubaoBaseUrl;
        }
        return openaiBaseUrl;
    }

    private String getChatModel(String provider) {
        if ("doubao".equalsIgnoreCase(provider)) {
            return doubaoChatModel;
        }
        if (isOpenAiLike(provider)) {
            return openaiChatModel;
        }
        return ollamaChatModel;
    }

    private String getVisionModel(String provider) {
        if ("doubao".equalsIgnoreCase(provider)) {
            return doubaoVisionModel;
        }
        if (isOpenAiLike(provider)) {
            return openaiVisionModel;
        }
        return ollamaVisionModel;
    }

    private String getCodeModel(String provider) {
        if ("doubao".equalsIgnoreCase(provider)) {
            return doubaoCodeModel;
        }
        if (isOpenAiLike(provider)) {
            return openaiCodeModel;
        }
        return ollamaCodeModel;
    }

    private String getEmbedModel(String provider) {
        if (isOpenAiLike(provider)) {
            return openaiEmbedModel;
        }
        return ollamaEmbedModel;
    }

    @Bean
    @Primary
    public ChatModel chatModel() {
        if (isOpenAiLike(chatProvider)) {
            String baseUrl = getBaseUrl(chatProvider);
            String model = getChatModel(chatProvider);
            logInit("CHAT", chatProvider, baseUrl, model);
            OpenAiApi api = new OpenAiApi(baseUrl, getApiKey(chatProvider));
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(model)
                    .temperature(defaultTemperature)
                    .maxTokens(defaultMaxTokens)
                    .build();
            return new OpenAiChatModel(api, options, TOOL_MGR, RETRY, OBS);
        } else {
            logInit("CHAT", chatProvider, ollamaBaseUrl, ollamaChatModel);
            OllamaApi api = new OllamaApi(ollamaBaseUrl);
            OllamaOptions options = OllamaOptions.builder()
                    .model(ollamaChatModel)
                    .temperature(defaultTemperature)
                    .numPredict(defaultMaxTokens)
                    .build();
            return new OllamaChatModel(api, options, TOOL_MGR, OBS, MODEL_MGMT);
        }
    }

    @Bean(name = "codeChatModel")
    public ChatModel codeChatModel() {
        if (isOpenAiLike(codeProvider)) {
            String baseUrl = getBaseUrl(codeProvider);
            String model = getCodeModel(codeProvider);
            logInit("CODE", codeProvider, baseUrl, model);
            OpenAiApi api = new OpenAiApi(baseUrl, getApiKey(codeProvider));
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(model)
                    .temperature(defaultTemperature)
                    .maxTokens(defaultMaxTokens)
                    .build();
            return new OpenAiChatModel(api, options, TOOL_MGR, RETRY, OBS);
        } else {
            logInit("CODE", codeProvider, ollamaBaseUrl, ollamaCodeModel);
            OllamaApi api = new OllamaApi(ollamaBaseUrl);
            OllamaOptions options = OllamaOptions.builder()
                    .model(ollamaCodeModel)
                    .temperature(defaultTemperature)
                    .numPredict(defaultMaxTokens)
                    .build();
            return new OllamaChatModel(api, options, TOOL_MGR, OBS, MODEL_MGMT);
        }
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        if (isOpenAiLike(chatProvider)) {
            logInit("EMBED", chatProvider, openaiBaseUrl, openaiEmbedModel);
            OpenAiApi api = new OpenAiApi(openaiBaseUrl, openaiApiKey);
            OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                    .model(openaiEmbedModel)
                    .build();
            return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options, RETRY);
        } else {
            logInit("EMBED", chatProvider, ollamaBaseUrl, ollamaEmbedModel);
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

    @Bean(name = "visionChatModel")
    public ChatModel visionChatModel() {
        if (isOpenAiLike(visionProvider)) {
            String baseUrl = getBaseUrl(visionProvider);
            String model = getVisionModel(visionProvider);
            logInit("VISION", visionProvider, baseUrl, model);
            OpenAiApi api = new OpenAiApi(baseUrl, getApiKey(visionProvider));
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(model)
                    .temperature(visionTemperature)
                    .maxTokens(visionMaxTokens)
                    .build();
            return new OpenAiChatModel(api, options, TOOL_MGR, RETRY, OBS);
        } else {
            logInit("VISION", visionProvider, ollamaBaseUrl, ollamaVisionModel);
            OllamaApi api = new OllamaApi(ollamaBaseUrl);
            OllamaOptions options = OllamaOptions.builder()
                    .model(ollamaVisionModel)
                    .temperature(visionTemperature)
                    .numPredict(visionMaxTokens)
                    .build();
            return new OllamaChatModel(api, options, TOOL_MGR, OBS, MODEL_MGMT);
        }
    }

    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    public String getChatProvider() {
        return chatProvider;
    }

    public String getVisionProvider() {
        return visionProvider;
    }

    public String getCodeProvider() {
        return codeProvider;
    }

    private void logInit(String kind, String provider, String baseUrl, String model) {
        System.out.println("[AI-INIT] " + kind + " provider=" + provider + " base-url=" + baseUrl + " model=" + model);
    }

}
