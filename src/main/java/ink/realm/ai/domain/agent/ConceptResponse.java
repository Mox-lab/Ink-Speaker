package ink.realm.ai.domain.agent;

import lombok.Builder;

/**
 * 构思阶段响应 DTO。
 */
@Builder
public record ConceptResponse(
        String inspiration,
        String blueprint) {
}
