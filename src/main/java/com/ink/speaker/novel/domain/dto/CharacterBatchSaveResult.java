package com.ink.speaker.novel.domain.dto;

import lombok.Builder;

/**
 * 人物批量保存结果。
 */
@Builder
public record CharacterBatchSaveResult(int saved) {
}
