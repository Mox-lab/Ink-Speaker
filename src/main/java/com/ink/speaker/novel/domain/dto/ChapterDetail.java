package com.ink.speaker.novel.domain.dto;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 章节详情 DTO(含正文全文)。
 */
@Builder
public record ChapterDetail(
        Long id,
        Long novelId,
        Long outlineId,
        Integer chapterNo,
        String title,
        String content,
        Integer wordCount,
        String sessionId,
        LocalDateTime createdAt) {
}
