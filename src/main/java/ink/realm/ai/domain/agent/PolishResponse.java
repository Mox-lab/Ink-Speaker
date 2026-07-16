package ink.realm.ai.domain.agent;

import lombok.Builder;

/**
 * 润色响应 DTO。
 */
@Builder
public record PolishResponse(
        String draft,
        String polished) {
}
