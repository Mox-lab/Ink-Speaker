package com.ink.speaker.auth.service.impl;

import com.ink.speaker.auth.domain.dto.LoginRequest;
import com.ink.speaker.auth.domain.dto.LoginResponse;
import com.ink.speaker.auth.domain.dto.RefreshTokenRequest;
import com.ink.speaker.auth.service.AuthService;
import com.ink.speaker.common.BusinessException;
import com.ink.speaker.common.ResultCode;
import com.ink.speaker.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 鉴权业务实现。
 * <p>登录走 Spring Security {@link AuthenticationManager} 校验密码,通过后签发 JWT。</p>
 * <p>刷新走 {@link JwtUtil} 校验 refresh token,重签 access token。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Override
    public LoginResponse login(LoginRequest req) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password())
        );

        // 收集角色(去掉 ROLE_ 前缀,JWT 内统一存原始名)
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).filter(Objects::nonNull)
                .map(a -> a.startsWith("ROLE_") ? a : "ROLE_" + a)
                .toList();

        String access = jwtUtil.generateAccessToken(authentication.getName(), roles);
        String refresh = jwtUtil.generateRefreshToken(authentication.getName());

        log.info("[Auth] 登录成功: user={}, roles={}", authentication.getName(), roles);
        return new LoginResponse(access, refresh, jwtUtil.getAccessTokenTtlSeconds());
    }

    @Override
    public LoginResponse refresh(RefreshTokenRequest req) {
        String refreshToken = req.refreshToken();
        if (!jwtUtil.isValid(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 无效或非 refresh 类型");
        }

        // refresh token 中无角色,需要重新查库 → 此处简化为不带角色,
        // 若需带角色,可在 JwtUtil 增加 subject → 角色查询的辅助方法,或登录时把角色塞入 refresh。
        // 当前实现:仅按 subject 重新签发,客户端刷新后用新 access 即可,
        // 角色信息以 access token 内嵌为准(用户角色变更需重新登录)。
        String username = jwtUtil.parse(refreshToken).getSubject();
        List<String> roles = jwtUtil.extractRoles(refreshToken);

        String access = jwtUtil.generateAccessToken(username, roles);
        log.info("[Auth] 刷新 Token: user={}", username);
        return new LoginResponse(access, refreshToken, jwtUtil.getAccessTokenTtlSeconds());
    }
}
