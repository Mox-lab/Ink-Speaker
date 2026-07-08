package com.ink.speaker.novel.domain.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 大纲激活请求 DTO。
 *
 * @param id      大纲 ID(必填)
 * @param novelId 小说 ID(可空,缺省 1)
 */
public record OutlineActivateRequest(
        @NotNull Long id,
        Long novelId) {
}
