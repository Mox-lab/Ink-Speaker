package ink.realm.auth.service;

import ink.realm.auth.domain.dto.LoginRequest;
import ink.realm.auth.domain.dto.LoginResponse;
import ink.realm.auth.domain.dto.RefreshTokenRequest;
import ink.realm.auth.domain.dto.RegisterRequest;
import ink.realm.auth.domain.dto.UpdateNicknameRequest;

/**
 * 鉴权业务接口。
 * <p>承担登录、注册、Token 刷新的业务逻辑,Controller 仅做参数校验与结果封装。</p>
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

    /**
     * 注册:校验用户名唯一性 + 加盐哈希密码 → 写入用户并绑定默认角色 ROLE_USER → 直接签发 token。
     */
    LoginResponse register(RegisterRequest request);

    /**
     * 更新昵称:校验唯一性(排除自身) → 写入用户 → 重新签发 token(携带最新昵称 claim)。
     */
    LoginResponse updateNickname(UpdateNicknameRequest request);
}

