package com.ink.speaker.ai.domain.agent;

import jakarta.validation.constraints.NotBlank;

/**
 * 人物抽取请求 DTO(/api/character)。
 */
public record CharacterExtractRequest(
        @NotBlank String text) {
}
