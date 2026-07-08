package com.ink.speaker.ai.domain.agent;

import jakarta.validation.constraints.NotBlank;

/**
 * 构思阶段请求 DTO(/api/concept)。
 */
public record ConceptRequest(
        @NotBlank String inspiration,
        String genre) {
}
