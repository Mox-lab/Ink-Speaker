package com.ink.speaker.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求 DTO。
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}
