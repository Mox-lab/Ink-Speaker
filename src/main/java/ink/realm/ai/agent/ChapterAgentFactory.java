package ink.realm.ai.agent;

import ink.realm.ai.core.tool.ToolRegistry;
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
 * 章节Agent 工厂:按 Skill 的 toolWhitelist 动态构建 ChapterAgent。
 *
 * <p>背景:LangChain4j {@code AiServices} 在构建期通过反射固化 {@code tools(...)},
 * 运行时无法切换。要支持 Skill 维度的工具过滤,需要在 build 时传入不同的工具集。</p>
 *
 * <p>策略:</p>
 * <ol>
 *   <li>无 whitelist:返回固定单例(默认全工具)</li>
 *   <li>有 whitelist:按 whitelist 内容计算 cache key,命中则复用,否则构建并缓存</li>
 * </ol>
 *
 * <p>缓存大小由 Skill 数量决定(每 Skill 至多一个 whitelist),无需 TTL 或上限管理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChapterAgentFactory {

    /** 默认 key:无白名单时使用。 */
    private static final String DEFAULT_KEY = "__all__";

    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;
    private final ChatMemoryProvider chatMemoryProvider;

    /** 按 whitelist 构建并缓存的 Agent 实例。 */
    private final Map<String, ChapterAgent> cache = new ConcurrentHashMap<>();

    /**
     * 获取默认 ChapterAgent(暴露全部工具)。
     */
    public ChapterAgent get() {
        return get(null);
    }

    /**
     * 按 whitelist 获取 ChapterAgent。
     *
     * @param whitelist 工具名白名单;null/空 表示使用全部工具
     * @return ChapterAgent 实例(缓存命中或新建)
     */
    public ChapterAgent get(List<String> whitelist) {
        String key = cacheKey(whitelist);
        return cache.computeIfAbsent(key, k -> {
            Object[] tools = toolRegistry.asArray(whitelist);
            log.info("[ChapterAgentFactory] 构建 ChapterAgent,key={}, tools={}",
                    k, whitelist == null ? "(all)" : whitelist);
            return AiServices.builder(ChapterAgent.class)
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
        // 排序 + 拼接,保证不同顺序的相同集合复用同一 key
        List<String> sorted = new java.util.ArrayList<>(whitelist);
        Collections.sort(sorted);
        return String.join(",", sorted);
    }
}
