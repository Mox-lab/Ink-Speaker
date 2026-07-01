package com.novel.agent.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * 模型客户端配置
 * <p>
 * 同时声明 OpenAI 兼容与 Ollama 两套 ChatModel Bean,通过 @Primary 控制默认使用哪一个。
 * 这样做的好处是:
 *   1. 切换 Provider 只需改 application.yml 的 spring.profiles.active,无需改代码;
 *   2. 同一应用可以同时调用多个模型(例如:OpenAI 做主模型,Ollama 做本地备用)。
 * </p>
 * <p>
 * 重要概念:
 *   - ChatModel: 同步调用,等待模型完整返回再继续;
 *   - StreamingChatModel: 流式调用,边生成边返回(类 ChatGPT 的打字机效果);
 *   - ChatLanguageModel 在 0.36 版本已重命名为 ChatModel,功能一致。
 * </p>
 */
@Configuration
public class ModelConfig {

    /**
     * OpenAI 兼容 ChatModel(DeepSeek/通义/Moonshot 等)
     * 通过 spring.profiles.active=openai 激活
     */
    @Bean
    @Primary
    @org.springframework.context.annotation.Profile("openai")
    public ChatLanguageModel openAiChatModel(
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName,
            @Value("${langchain4j.open-ai.chat-model.temperature:0.7}") double temperature,
            @Value("${langchain4j.open-ai.chat-model.max-tokens:2000}") int maxTokens,
            @Value("${langchain4j.open-ai.chat-model.timeout:PT60S}") String timeout) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.parse(timeout))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * OpenAI 兼容流式 ChatModel
     */
    @Bean
    @Primary
    @org.springframework.context.annotation.Profile("openai")
    public StreamingChatLanguageModel openAiStreamingChatModel(
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName,
            @Value("${langchain4j.open-ai.chat-model.temperature:0.7}") double temperature,
            @Value("${langchain4j.open-ai.chat-model.timeout:PT60S}") String timeout) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.parse(timeout))
                .build();
    }

    /**
     * Ollama ChatModel(本地开源模型)
     * 通过 spring.profiles.active=ollama 激活
     * 前置条件:
     *   1. 安装 Ollama: https://ollama.com
     *   2. 拉取模型:    ollama pull qwen2.5:7b
     *   3. 启动 Ollama 服务(默认 11434 端口)
     */
    @Bean
    @Primary
    @org.springframework.context.annotation.Profile("ollama")
    public ChatLanguageModel ollamaChatModel(
            @Value("${langchain4j.ollama.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.ollama.chat-model.model-name}") String modelName,
            @Value("${langchain4j.ollama.chat-model.temperature:0.7}") double temperature,
            @Value("${langchain4j.ollama.chat-model.timeout:PT60S}") String timeout) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.parse(timeout))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * Ollama 流式 ChatModel
     */
    @Bean
    @Primary
    @org.springframework.context.annotation.Profile("ollama")
    public StreamingChatLanguageModel ollamaStreamingChatModel(
            @Value("${langchain4j.ollama.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.ollama.chat-model.model-name}") String modelName,
            @Value("${langchain4j.ollama.chat-model.temperature:0.7}") double temperature,
            @Value("${langchain4j.ollama.chat-model.timeout:PT60S}") String timeout) {
        return OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.parse(timeout))
                .build();
    }
}
