package com.ink.speaker.novel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 章节保存请求 DTO。
 * <p>对应 POST /api/data/chapter/save 请求体。</p>
 *
 * @param novelId   小说 ID(可空,缺省 1)
 * @param chapterNo 章节序号(必填,正整数)
 * @param title     章节标题
 * @param content   章节正文(必填,非空)
 * @param sessionId 会话 ID
 * @param outlineId 关联大纲 ID(可空)
 */
public record ChapterSaveRequest(
        Long novelId,
        @NotNull @Positive Integer chapterNo,
        String title,
        @NotBlank String content,
        String sessionId,
        Long outlineId) {
}
