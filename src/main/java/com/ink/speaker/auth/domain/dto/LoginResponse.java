package com.ink.speaker.auth.domain.dto;

/**
 * 登录响应 DTO。
 *
 * @param accessToken  访问令牌(2 小时)
 * @param refreshToken 刷新令牌(7 天)
 * @param expiresIn    access token 过期秒数
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn) {
}
