package com.ink.speaker.ai.agent;

import com.ink.speaker.ai.core.tool.ToolRegistry;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 写作助手 Agent 工厂:按 Skill 的 toolWhitelist 动态构建 WritingAssistantAgent。
 *
 * @see ChapterAgentFactory
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WritingAssistantAgentFactory {

    private static final String DEFAULT_KEY = "__all__";

    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;
    private final ChatMemoryProvider chatMemoryProvider;

    private final Map<String, WritingAssistantAgent> cache = new ConcurrentHashMap<>();

    public WritingAssistantAgent get() {
        return get(null);
    }

    public WritingAssistantAgent get(List<String> whitelist) {
        String key = cacheKey(whitelist);
        return cache.computeIfAbsent(key, k -> {
            Object[] tools = toolRegistry.asArray(whitelist);
            log.info("[WritingAssistantAgentFactory] 构建 WritingAssistantAgent,key={}, tools={}",
                    k, whitelist == null ? "(all)" : whitelist);
            return AiServices.builder(WritingAssistantAgent.class)
                    .chatModel(chatModel)
                    .tools(tools)
                    .chatMemoryProvider(chatMemoryProvider)
                    .build();
        });
    }

    private String cacheKey(List<String> whitelist) {
        if (whitelist == null || whitelist.isEmpty()) {
            return DEFAULT_KEY;
        }
        List<String> sorted = new java.util.ArrayList<>(whitelist);
        Collections.sort(sorted);
        return String.join(",", sorted);
    }
}
