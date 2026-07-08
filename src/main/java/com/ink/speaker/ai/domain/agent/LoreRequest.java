package com.ink.speaker.ai.domain.agent;

import jakarta.validation.constraints.NotBlank;

/**
 * 设定问答请求 DTO(/api/lore)。
 */
public record LoreRequest(
        String sessionId,
        @NotBlank String question) {
}
