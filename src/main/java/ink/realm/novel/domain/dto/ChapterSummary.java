package ink.realm.novel.domain.dto;

import lombok.Builder;

/**
 * 章节摘要 DTO(列表场景使用,不含正文)。
 */
@Builder
public record ChapterSummary(
        Long id,
        Long novelId,
        Long outlineId,
        Integer chapterNo,
        String title,
        Integer wordCount,
        String sessionId,
        String contentPreview,
        java.time.LocalDateTime createdAt) {
}
