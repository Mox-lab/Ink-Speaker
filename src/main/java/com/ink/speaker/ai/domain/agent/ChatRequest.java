package com.ink.speaker.ai.domain.agent;

import jakarta.validation.constraints.NotBlank;

/**
 * 单轮对话请求 DTO(/api/chat)。
 */
public record ChatRequest(
        @NotBlank String message) {
}
