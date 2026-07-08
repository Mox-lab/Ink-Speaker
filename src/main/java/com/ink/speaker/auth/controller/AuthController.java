package com.ink.speaker.auth.controller;

import com.ink.speaker.auth.domain.dto.LoginRequest;
import com.ink.speaker.auth.domain.dto.LoginResponse;
import com.ink.speaker.auth.domain.dto.RefreshTokenRequest;
import com.ink.speaker.auth.service.AuthService;
import com.ink.speaker.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴权接口。
 * <p>提供登录(签发 JWT)和刷新(换发新 access token)端点。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "鉴权接口:登录/刷新 Token")
public class AuthController {

    private final AuthService authService;

    /**
     * 登录:校验用户名/密码 → 签发 access + refresh token。
     */
    @Operation(summary = "登录", description = "校验用户名密码,返回 access/refresh token")
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest req) {
        return Result.success(authService.login(req));
    }

    /**
     * 刷新:用 refresh token 换新的 access token。
     */
    @Operation(summary = "刷新 Token", description = "用 refresh token 换新的 access token")
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody @Valid RefreshTokenRequest req) {
        return Result.success(authService.refresh(req));
    }
}
