package com.ink.speaker.auth.service;

import com.ink.speaker.auth.domain.dto.LoginRequest;
import com.ink.speaker.auth.domain.dto.LoginResponse;
import com.ink.speaker.auth.domain.dto.RefreshTokenRequest;

/**
 * 鉴权业务接口。
 * <p>承担登录、Token 刷新的业务逻辑,Controller 仅做参数校验与结果封装。</p>
 */
public interface AuthService {

    /**
     * 登录:校验用户名/密码 → 签发 access + refresh token。
     */
    LoginResponse login(LoginRequest request);

    /**
     * 刷新:用 refresh token 换新的 access token。
     */
    LoginResponse refresh(RefreshTokenRequest request);
}
