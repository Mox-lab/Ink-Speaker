package com.ink.speaker.ai.domain.agent;

import jakarta.validation.constraints.NotBlank;

/**
 * 知识库检索调试请求 DTO(/api/lore/search)。
 */
public record LoreSearchRequest(
        @NotBlank String query) {
}
