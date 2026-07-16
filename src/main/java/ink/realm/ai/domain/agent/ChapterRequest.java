package ink.realm.ai.domain.agent;

import jakarta.validation.constraints.NotBlank;

/**
 * 章节生成请求 DTO(/api/chapter)。
 */
public record ChapterRequest(
        String sessionId,
        @NotBlank String outline,
        @NotBlank String setting,
        Integer wordCount,
        String skillId) {
}
