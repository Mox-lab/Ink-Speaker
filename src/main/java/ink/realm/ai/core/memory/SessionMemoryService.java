package ink.realm.ai.core.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 单会话短期记忆管理。
 * <p>当前实现委托 LangChain4j {@code ChatMemoryProvider} 维护窗口,
 * 本类负责"观察":统计消息数、判断是否触发压缩、对外暴露查询接口。</p>
 *
 * <p>未来可扩展为 Redis 持久化(分布式会话)或 Caffeine 缓存(性能优化)。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionMemoryService {

    /**
     * 估算当前会话已积累的字符数(粗略,1 中文字符 ≈ 1.5 token)。
     * <p>用于判断是否需要触发 {@link ContextCompactor}。</p>
     *
     * @param messages 会话消息列表(每条为 "role:content" 格式)
     * @return 总字符数
     */
    public int estimateChars(java.util.List<String> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        return messages.stream().mapToInt(String::length).sum();
    }
}
