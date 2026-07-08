package com.ink.speaker.novel.domain.dto;

import lombok.Builder;

/**
 * 大纲摘要 DTO(列表场景)。
 */
@Builder
public record OutlineSummary(
        Long id,
        Long novelId,
        String title,
        String theme,
        Integer chapters,
        Integer version,
        Boolean isActive,
        String contentPreview,
        Integer contentLength,
        java.time.LocalDateTime createdAt) {
}
