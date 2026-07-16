package ink.realm.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JWT 工具类。
 * <p>使用 HMAC-SHA256 算法签发 / 解析 / 校验 JWT。</p>
 * <p>配置项来自 application.yml 的 ink.jwt.* :</p>
 * <ul>
 *   <li>secret           : HMAC 密钥(要求 ≥ 32 字节)</li>
 *   <li>issuer           : 签发者(ink-realm)</li>
 *   <li>access-token-ttl : 访问令牌 TTL(默认 PT2H)</li>
 *   <li>refresh-token-ttl: 刷新令牌 TTL(默认 P7D)</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;
    private final String issuer;
    private final long accessTtlMs;
    private final long refreshTtlMs;

    public JwtUtil(
            @Value("${ink.jwt.secret}") String secret,
            @Value("${ink.jwt.issuer:ink-realm}") String issuer,
            @Value("${ink.jwt.access-token-ttl:PT2H}") Duration accessTtl,
            @Value("${ink.jwt.refresh-token-ttl:P7D}") Duration refreshTtl) {
        // 启动时强制校验:禁止空密钥与短密钥(HMAC-SHA256 要求 ≥ 32 字节)
        // 这是从 application.yml 移除兜底值后的失败快速通道
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "ink.jwt.secret 未配置:请通过环境变量 JWT_SECRET 注入(≥ 32 字符)");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "ink.jwt.secret 长度不足:HMAC-SHA256 要求密钥 ≥ 32 字节,当前 "
                            + secret.getBytes(StandardCharsets.UTF_8).length + " 字节");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTtlMs = accessTtl.toMillis();
        this.refreshTtlMs = refreshTtl.toMillis();
    }

    /**
     * 签发 Access Token。
     *
     * <p>注:JJWT 0.12.x 的 {@code JwtBuilder} 仅接受 {@link Date} 类型的时间参数,
     * 内部已使用 {@link Instant} 计算时间,仅在传给 builder 时通过 {@link Date#from(Instant)}
     * 转换 —— 这是 JJWT API 的硬约束,故对此方法抑制 S2143。</p>
     *
     * @param subject  用户名(或用户 ID)
     * @param userId   用户数据库主键(写入 {@code uid} claim,供行级隔离使用)
     * @param roles    角色列表(如 ["ADMIN","USER"])
     * @param nickname 用户昵称(写入 {@code nickname} claim,作为小说作者名展示;可空)
     * @return JWT 字符串
     */
    public String generateAccessToken(String subject, Long userId, Collection<String> roles, String nickname) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessTtlMs);
        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(JwtConstants.USER_ID_CLAIM, userId)
                .claim(JwtConstants.ROLES_CLAIM, roles)
                .claim(JwtConstants.NICKNAME_CLAIM, nickname)
                .claim(JwtConstants.TOKEN_TYPE_CLAIM, JwtConstants.TYPE_ACCESS)
                .signWith(key)
                .compact();
    }

    /**
     * 签发 Refresh Token(仅用于换 Access Token,不含角色)。
     *
     * <p>同 {@link #generateAccessToken},Date 类型由 JJWT API 强制要求。</p>
     *
     * @param subject 用户名
     * @param userId  用户数据库主键(写入 {@code uid} claim,刷新后回传)
     * @return JWT 字符串
     */
    public String generateRefreshToken(String subject, Long userId) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(refreshTtlMs);
        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(JwtConstants.USER_ID_CLAIM, userId)
                .claim(JwtConstants.TOKEN_TYPE_CLAIM, JwtConstants.TYPE_REFRESH)
                .signWith(key)
                .compact();
    }

    /**
     * 提取 Token 中的用户 ID(数据库 users.id)。
     *
     * @param token JWT 字符串
     * @return userId,缺失或解析失败时返回 null
     */
    public Long extractUserId(String token) {
        try {
            Object raw = parse(token).get(JwtConstants.USER_ID_CLAIM);
            if (raw instanceof Number n) {
                return n.longValue();
            }
            if (raw instanceof String s && !s.isBlank()) {
                return Long.parseLong(s);
            }
            return null;
        } catch (JwtException | NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析 Token。
     *
     * @param token JWT 字符串
     * @return Claims(主体信息)
     * @throws JwtException 解析或验签失败时抛出
     */
    public Claims parse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 校验 Token 是否有效(签名 + 过期时间 + 签发者)。
     *
     * @param token JWT 字符串
     * @return true 表示有效
     */
    public boolean isValid(String token) {
        try {
            Claims c = parse(token);
            return c.getExpiration().toInstant().isAfter(Instant.now());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 校验失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 提取 Token 中的角色列表。
     *
     * @param token JWT 字符串
     * @return 角色列表(无角色时返回空列表)
     */
    public List<String> extractRoles(String token) {
        try {
            Object roles = parse(token).get(JwtConstants.ROLES_CLAIM);
            if (roles instanceof Collection<?> c) {
                return c.stream().map(Object::toString).toList();
            }
            return List.of();
        } catch (JwtException e) {
            return List.of();
        }
    }

    /**
     * 提取 Token 类型(access / refresh)。
     *
     * @param token JWT 字符串
     * @return 类型字符串,无法解析时返回 null
     */
    public String extractTokenType(String token) {
        try {
            Object t = parse(token).get(JwtConstants.TOKEN_TYPE_CLAIM);
            return t == null ? null : t.toString();
        } catch (JwtException e) {
            return null;
        }
    }

    /**
     * Access Token 有效期(秒),用于登录响应返回前端。
     */
    public long getAccessTokenTtlSeconds() {
        return accessTtlMs / 1000;
    }

    /**
     * 是否为 refresh 类型 Token。
     */
    public boolean isRefreshToken(String token) {
        return JwtConstants.TYPE_REFRESH.equals(extractTokenType(token));
    }

    /**
     * 是否为 access 类型 Token。
     */
    public boolean isAccessToken(String token) {
        return JwtConstants.TYPE_ACCESS.equals(extractTokenType(token));
    }

    /**
     * 提取所有 claims(用于诊断/调试)。
     */
    public Map<String, Object> extractAll(String token) {
        return parse(token);
    }
}
