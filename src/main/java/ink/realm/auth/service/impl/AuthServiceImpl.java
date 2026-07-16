package ink.realm.auth.service.impl;

import ink.realm.auth.domain.dto.LoginRequest;
import ink.realm.auth.domain.dto.LoginResponse;
import ink.realm.auth.domain.dto.RefreshTokenRequest;
import ink.realm.auth.domain.dto.RegisterRequest;
import ink.realm.auth.domain.dto.UpdateNicknameRequest;
import ink.realm.auth.domain.entity.User;
import ink.realm.auth.mapper.UserMapper;
import ink.realm.auth.service.AuthService;
import ink.realm.common.exception.BusinessException;
import ink.realm.common.result.ResultCode;
import ink.realm.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 鉴权业务实现。
 * <p>登录走手写校验链(userMapper 查用户 + PasswordEncoder 比对密码),
 * 通过后签发 JWT。错误语义具体化:账号不存在 / 账号已禁用 / 密码错误,
 * 全部以 {@link BusinessException} 形式抛出,由 GlobalExceptionHandler 统一转 Result。</p>
 * <p>刷新走 {@link JwtUtil} 校验 refresh token,重签 access token。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginResponse login(LoginRequest req) {
        User user = userMapper.findByUsernameWithRoles(req.username())
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "账号不存在"));

        if (!user.isEnabled()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号已被禁用,请联系管理员");
        }

        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            log.warn("[Auth] 密码错误: user={}", req.username());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "密码错误");
        }

        // 收集角色(去掉 ROLE_ 前缀,JWT 内统一存原始名)
        List<String> roles = user.getRoles().stream()
                .map(r -> r == null ? null : r.getName())
                .filter(Objects::nonNull)
                .map(a -> a.startsWith("ROLE_") ? a : "ROLE_" + a)
                .toList();

        String access = jwtUtil.generateAccessToken(user.getUsername(), user.getId(), roles, user.getNickname());
        String refresh = jwtUtil.generateRefreshToken(user.getUsername(), user.getId());

        log.info("[Auth] 登录成功: user={}, userId={}, roles={}", user.getUsername(), user.getId(), roles);
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
        Long userId = jwtUtil.extractUserId(refreshToken);
        List<String> roles = jwtUtil.extractRoles(refreshToken);

        // 刷新时同步最新昵称,避免旧 token 缺失 nickname 导致前端反复弹窗
        User refreshUser = userMapper.selectById(userId);
        String nickname = refreshUser != null ? refreshUser.getNickname() : null;

        String access = jwtUtil.generateAccessToken(username, userId, roles, nickname);
        log.info("[Auth] 刷新 Token: user={}, userId={}", username, userId);
        return new LoginResponse(access, refreshToken, jwtUtil.getAccessTokenTtlSeconds());
    }

    @Override
    public LoginResponse updateNickname(UpdateNicknameRequest req) {
        Long userId = currentUserId();
        String nickname = req.nickname().trim();

        // 唯一性校验(排除自身)
        if (userMapper.countByNicknameExcluding(nickname, userId) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "昵称已被占用");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        user.setNickname(nickname);
        userMapper.updateById(user);

        // 角色直接取自当前 SecurityContext(与登录时写入的 ROLE_ 前缀保持一致)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<String> roles = auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .toList();

        log.info("[Auth] 昵称更新成功: userId={}, nickname={}", userId, nickname);

        // 重新签发 token,将最新昵称写入 claim,前端无需重新登录
        String access = jwtUtil.generateAccessToken(user.getUsername(), userId, roles, nickname);
        String refresh = jwtUtil.generateRefreshToken(user.getUsername(), userId);
        return new LoginResponse(access, refresh, jwtUtil.getAccessTokenTtlSeconds());
    }

    /**
     * 从当前 SecurityContext 解析登录用户 ID(principal 由 JwtAuthenticationFilter 写入 userId)。
     */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long id)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录,无法更新昵称");
        }
        return id;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest req) {
        String username = req.username().trim();
        String password = req.password();

        if (userMapper.countByUsername(username) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已被占用");
        }

        User entity = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .enabled(true)
                .roles(Set.of())
                .build();
        userMapper.insert(entity);

        // 绑定默认角色 ROLE_USER(收敛后仅两类:ROLE_ADMIN / ROLE_USER)
        userMapper.bindDefaultUserRole(entity.getId());

        log.info("[Auth] 注册成功: user={}, userId={}", username, entity.getId());

        // 注册成功后直接签发 token,前端无需二次登录
        List<String> roles = List.of("ROLE_USER");
        // 注册阶段尚未填写昵称,nickname 为 null,由前端弹窗引导补充
        String access = jwtUtil.generateAccessToken(username, entity.getId(), roles, entity.getNickname());
        String refresh = jwtUtil.generateRefreshToken(username, entity.getId());
        return new LoginResponse(access, refresh, jwtUtil.getAccessTokenTtlSeconds());
    }
}
