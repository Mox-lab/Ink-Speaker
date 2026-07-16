package ink.realm.ai.domain.agent;

import lombok.Builder;

/**
 * 大纲生成响应 DTO。
 */
@Builder
public record OutlineResponse(
        Integer chapters,
        String outline,
        Integer startChapter,
        Boolean continued,
        String error) {
}
