package com.ink.speaker.ai.domain.director.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 手动触发审查请求 DTO。
 */
public record ReviewTriggerRequest(@NotBlank String content) {
}
