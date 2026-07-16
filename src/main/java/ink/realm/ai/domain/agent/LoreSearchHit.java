package ink.realm.ai.domain.agent;

import lombok.Builder;

/**
 * 知识库检索命中条目。
 */
@Builder
public record LoreSearchHit(
        double score,
        String text) {
}
