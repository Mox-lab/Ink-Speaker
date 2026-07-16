package ink.realm.ai.core.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 {@link TokenBudgetEstimator} 的 LangChain4j {@link TokenCountEstimator} 适配器。
 *
 * <p>用途:把项目内的中英文混合 token 估算逻辑接入 {@code TokenWindowChatMemory},
 * 让 Memory 窗口按 token 数(而非消息条数)淘汰,精准控制上下文大小。</p>
 *
 * <p>估算策略:1 中文字符 ≈ 1.5 token,1 英文字符 ≈ 0.25 token(与 cc-haha 一致)。</p>
 *
 * <p>注:LangChain4j 1.17 的 {@link TokenCountEstimator} 要求实现三个方法,
 * 这里通过提取每种消息的文本内容统一走 {@link TokenBudgetEstimator#estimate(String)}。</p>
 */
public class L4jTokenCountEstimator implements TokenCountEstimator {

    /** overhead per message:格式标记与角色头部的固定开销,与 OpenAI tokenizer 一致(经验值)。 */
    public static final int MESSAGE_OVERHEAD_TOKENS = 4;

    private final TokenBudgetEstimator delegate;

    public L4jTokenCountEstimator(TokenBudgetEstimator delegate) {
        this.delegate = delegate;
    }

    @Override
    public int estimateTokenCountInText(String text) {
        return delegate.estimate(text);
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        if (message == null) {
            return 0;
        }
        String text = extractText(message);
        return delegate.estimate(text) + MESSAGE_OVERHEAD_TOKENS;
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        if (messages == null) {
            return 0;
        }
        List<ChatMessage> list = new ArrayList<>();
        messages.forEach(list::add);
        int sum = 0;
        for (ChatMessage m : list) {
            sum += estimateTokenCountInMessage(m);
        }
        // trailing overhead for priming (与 OpenAI 一致)
        return sum;
    }

    /**
     * 从不同类型的 ChatMessage 中提取文本部分。
     *
     * <p>支持 {@link UserMessage} / {@link AiMessage} / {@link SystemMessage};
     * 其他类型(ToolExecutionResultMessage 等)走 toString 兜底。</p>
     */
    private String extractText(ChatMessage message) {
        if (message instanceof UserMessage um) {
            return um.singleText();
        }
        if (message instanceof AiMessage am) {
            return am.text() == null ? "" : am.text();
        }
        if (message instanceof SystemMessage sm) {
            return sm.text();
        }
        return message.toString();
    }
}
