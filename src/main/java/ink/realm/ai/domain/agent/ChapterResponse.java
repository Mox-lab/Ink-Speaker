package ink.realm.ai.domain.agent;

import lombok.Builder;

/**
 * 章节生成响应 DTO。
 */
@Builder
public record ChapterResponse(
        String sessionId,
        String content,
        String skillId,
        String skillName,
        String error) {
}
