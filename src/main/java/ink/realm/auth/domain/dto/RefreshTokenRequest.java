package ink.realm.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新 Token 请求 DTO。
 */
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
