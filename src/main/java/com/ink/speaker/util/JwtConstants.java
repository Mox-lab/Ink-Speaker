package com.ink.speaker.util;

/**
 * JWT 相关常量。
 * <p>抽取自 {@link JwtUtil},供 JwtUtil / JwtAuthenticationFilter / 业务模块共享。</p>
 */
public final class JwtConstants {

    /** Token 类型声明的 claim key */
    public static final String TOKEN_TYPE_CLAIM = "type";

    /** 角色列表的 claim key */
    public static final String ROLES_CLAIM = "roles";

    /** Access Token 类型标识(用于业务接口鉴权) */
    public static final String TYPE_ACCESS = "access";

    /** Refresh Token 类型标识(仅用于换 Access Token) */
    public static final String TYPE_REFRESH = "refresh";

    private JwtConstants() {
    }
}
