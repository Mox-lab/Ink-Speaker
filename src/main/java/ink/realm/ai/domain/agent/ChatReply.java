package ink.realm.ai.domain.agent;

import lombok.Builder;

/**
 * 单轮对话响应 DTO。
 */
@Builder
public record ChatReply(String reply) {
}
