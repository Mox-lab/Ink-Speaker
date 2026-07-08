package com.ink.speaker.novel.domain.dto;

import lombok.Builder;

/**
 * 大纲激活版本简版(供续生时取上一版本尾段)。
 */
@Builder
public record OutlineActive(
        Long id,
        String title,
        String content,
        Integer chapters,
        Integer version) {
}
