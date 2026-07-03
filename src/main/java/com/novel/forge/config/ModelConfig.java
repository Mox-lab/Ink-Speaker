package com.novel.forge.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
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
 * 模型客户端配置。
 * <p>同时声明 OpenAI 兼容与 Ollama 两套 Bean,通过 spring.profiles.active 切换。</p>
 */
@Configuration
public class ModelConfig {

    /**
     * OpenAI 兼容 ChatModel(DeepSeek/通义/Moonshot 等)。
     * <p>由 spring.profiles.active=openai 激活。</p>
     *
     * @param apiKey      从配置读取的 API Key(支持 ${OPENAI_API_KEY} 环境变量)
     * @param baseUrl     OpenAI 兼容端点
     * @param modelName   模型名称
     * @param temperature 采样温度,越高越发散
     * @param maxTokens   单次最大生成 token 数
     * @param timeout     超时字符串(ISO-8601 duration 格式,如 PT60S)
     * @return ChatModel 同步模型实例
     */
    @Bean
    @Primary
    @org.springframework.context.annotation.Profile("openai")
    public ChatModel openAiChatModel(
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName,
            @Value("${langchain4j.open-ai.chat-model.temperature:0.7}") double temperature,
            @Value("${langchain4j.open-ai.chat-model.max-tokens:2000}") int maxTokens,
            @Value("${langchain4j.open-ai.chat-model.timeout:PT60S}") String timeout) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)                       // 鉴权 key
                .baseUrl(baseUrl)                     // 接口地址
                .modelName(modelName)                 // 模型名
                .temperature(temperature)             // 采样温度
                .maxTokens(maxTokens)                 // 单次生成上限
                .timeout(Duration.parse(timeout))     // 超时(Duration.parse 解析 ISO-8601)
                .logRequests(true)                    // 打印请求体,便于调试
                .logResponses(true)                   // 打印响应体
                .build();
    }

    /**
     * OpenAI 兼容流式 ChatModel。
     * <p>流式输出不设 maxTokens,由模型/服务端控制。</p>
     *
     * @param apiKey      API Key
     * @param baseUrl     端点
     * @param modelName   模型名
     * @param temperature 采样温度
     * @param timeout     超时
     * @return StreamingChatModel 流式模型实例
     */
    @Bean
    @Primary
    @org.springframework.context.annotation.Profile("openai")
    public StreamingChatModel openAiStreamingChatModel(
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
     * Ollama ChatModel(本地开源模型)。
     * <p>由 spring.profiles.active=ollama 激活。前置:安装 Ollama 并 ollama pull 模型。</p>
     *
     * @param baseUrl     Ollama 服务地址(默认 http://localhost:11434)
     * @param modelName   模型名(如 qwen2.5:7b)
     * @param temperature 采样温度
     * @param timeout     超时
     * @return ChatModel 实例
     */
    @Bean
    @Primary
    @org.springframework.context.annotation.Profile("ollama")
    public ChatModel ollamaChatModel(
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
     * Ollama 流式 ChatModel。
     *
     * @param baseUrl     Ollama 服务地址
     * @param modelName   模型名
     * @param temperature 采样温度
     * @param timeout     超时
     * @return StreamingChatModel 流式模型实例
     */
    @Bean
    @Primary
    @org.springframework.context.annotation.Profile("ollama")
    public StreamingChatModel ollamaStreamingChatModel(
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
