package ink.realm.auth.controller;

import ink.realm.auth.domain.dto.LoginRequest;
import ink.realm.auth.domain.dto.LoginResponse;
import ink.realm.auth.domain.dto.RefreshTokenRequest;
import ink.realm.auth.domain.dto.RegisterRequest;
import ink.realm.auth.domain.dto.UpdateNicknameRequest;
import ink.realm.auth.service.AuthService;
import ink.realm.common.exception.BusinessException;
import ink.realm.common.result.Result;
import ink.realm.common.result.ResultCode;
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
 * <p>提供登录(签发 JWT)、注册、刷新(换发新 access token)端点。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "鉴权接口:登录/注册/刷新 Token")
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
     * 注册:校验用户名唯一性 + 加盐哈希密码 → 写入用户并绑定默认角色 ROLE_USER → 直接签发 token。
     */
    @Operation(summary = "注册", description = "用户名/密码/确认密码,成功后直接返回 token,前端无需二次登录")
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody @Valid RegisterRequest req) {
        if (!req.password().equals(req.confirmPassword())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "两次输入的密码不一致");
        }
        return Result.success(authService.register(req));
    }

    /**
     * 刷新:用 refresh token 换新的 access token。
     */
    @Operation(summary = "刷新 Token", description = "用 refresh token 换新的 access token")
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody @Valid RefreshTokenRequest req) {
        return Result.success(authService.refresh(req));
    }

    /**
     * 更新昵称:校验唯一性后写入并重新签发 token(携带最新昵称 claim)。
     */
    @Operation(summary = "更新昵称", description = "注册后补充昵称,全局唯一;成功后返回新 token")
    @PostMapping("/nickname")
    public Result<LoginResponse> updateNickname(@RequestBody @Valid UpdateNicknameRequest req) {
        return Result.success(authService.updateNickname(req));
    }
}

