package ink.realm.ai.core.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.RequiredArgsConstructor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 记忆清洗装饰器。
 * <p>在消息存取时保证对话历史不含会让 OpenAI 兼容端点(DeepSeek/通义/Moonshot 等)拒绝的非法消息,
 * 从根上规避 {@code Invalid assistant message: content or tool_calls must be set} 这类 400 报错。</p>
 *
 * <p>主要处理三类问题:</p>
 * <ul>
 *   <li><b>空 assistant 消息</b>:content 与 tool_calls 皆为空(模型偶发空响应所致)→ 直接丢弃,
 *       否则下一轮请求带上它会被端点判定为非法 assistant 消息。</li>
 *   <li><b>content 为 null 的工具消息</b>:含 tool_calls 但 content 为 null 的 assistant 消息 →
 *       把 content 置为 ""(部分兼容端点要求 content 字段非空字符串)。</li>
 *   <li><b>孤儿 tool 消息</b>:没有被前置 assistant(tool_calls) 配对的 ToolExecutionResultMessage →
 *       丢弃,避免 "tool message must follow assistant with tool_calls" 类错误。</li>
 * </ul>
 *
 * <p>用法:把任意 {@link ChatMemory} 实例包一层即可,内部记忆窗口/淘汰策略保持不变。</p>
 */
@RequiredArgsConstructor
public class SanitizingChatMemory implements ChatMemory {

    /** 被装饰的底层记忆(如 TokenWindowChatMemory)。 */
    private final ChatMemory delegate;

    @Override
    public Object id() {
        return delegate.id();
    }

    @Override
    public void add(ChatMessage message) {
        // 写入前就拦截空 assistant 消息,避免污染历史
        if (message == null || isEmptyAssistant(message)) {
            return;
        }
        delegate.add(message);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public List<ChatMessage> messages() {
        return sanitize(delegate.messages());
    }

    /**
     * 是否为"既无文本也无工具调用"的空 assistant 消息。
     */
    private boolean isEmptyAssistant(ChatMessage message) {
        if (!(message instanceof AiMessage ai)) {
            return false;
        }
        boolean hasText = ai.text() != null && !ai.text().isBlank();
        boolean hasTools = ai.hasToolExecutionRequests();
        return !hasText && !hasTools;
    }

    /**
     * 清洗整段历史:
     * <ol>
     *   <li>含 tool_calls 的 assistant 消息,确保其 content 不为 null;</li>
     *   <li>只有被后续 ToolExecutionResultMessage 配对的 assistant(tool_calls) 才保留,
     *       孤立的工具调用/工具结果一律丢弃,保证工具调用序列完整。</li>
     * </ol>
     */
    private List<ChatMessage> sanitize(List<ChatMessage> input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        List<ChatMessage> result = new ArrayList<>(input.size());
        // 暂存"待配对"的 assistant(tool_calls) 消息,遇到 tool 结果再 flush
        Deque<ChatMessage> pendingToolCalls = new ArrayDeque<>();

        for (ChatMessage message : input) {
            if (message instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                // 工具调用 assistant:先暂存,等待后续 tool 结果配对
                pendingToolCalls.addLast(fixContent(ai));
            } else if (message instanceof ToolExecutionResultMessage) {
                // 仅当存在前置 assistant(tool_calls) 时才接受 tool 结果
                if (!pendingToolCalls.isEmpty()) {
                    while (!pendingToolCalls.isEmpty()) {
                        result.add(pendingToolCalls.pollFirst());
                    }
                    result.add(message);
                }
                // 否则为孤儿 tool 消息,直接丢弃
            } else {
                // 非工具消息:先丢弃尚未配对的 assistant(tool_calls)(孤立工具调用)
                pendingToolCalls.clear();
                if (isEmptyAssistant(message)) {
                    continue;
                }
                result.add(message);
            }
        }
        // 遍历结束仍有未配对的 assistant(tool_calls) → 丢弃(避免 "tool_calls must be followed by tool")
        pendingToolCalls.clear();
        return result;
    }

    /**
     * 把 content 为 null 但含 tool_calls 的 assistant 消息 content 置为 "",满足兼容端点校验。
     */
    private AiMessage fixContent(AiMessage ai) {
        if (ai.text() != null) {
            return ai;
        }
        return AiMessage.aiMessage("", ai.toolExecutionRequests());
    }
}
