# SecurityConfig JWT 重构方案

> 本文档仅描述 `SecurityConfig.java` 的目标形态与重构步骤,**不修改任何 Java 源代码**。
> 实施时由人工按本文档操作。

---

## 1. 现状评估(2026-07-05)

文件:`src/main/java/com/novel/forge/config/SecurityConfig.java`

| 行号 | 现状 | 问题 |
|------|------|------|
| 36-41 | 表单登录 `loginPage("/login")` + `defaultSuccessUrl("/home")` | 项目是 REST API + SSE 架构(无前端页面),`/login`、`/home` 路径不存在 |
| 51 | `.httpBasic(withDefaults())` | 与表单登录共存,语义混乱;`withDefaults()` 静态导入在新版建议显式写 `Customizer` |
| 53-55 | CSRF 仅对 `/api/**` 禁用 | 改 JWT 无状态后应整体禁用 CSRF(无 session 即无 CSRF 风险) |
| 63-77 | `InMemoryUserDetailsManager` + `User.withDefaultPasswordEncoder()` | `withDefaultPasswordEncoder()` 已 `@Deprecated`;密码硬编码 |
| 65-74 | admin/admin123、user/user123 | 弱密码、硬编码、无法动态管理 |
| 84 | `BCryptPasswordEncoder` Bean | 已注入但 `withDefaultPasswordEncoder()` 未使用,存在矛盾 |
| 全文 | 未配置 `SessionCreationPolicy.STATELESS` | REST API 应无状态 |
| 全文 | 未引用 `ink.jwt.*` 配置项 | `application.yml` 中 `ink.jwt.secret/access-token-ttl/refresh-token-ttl` 已定义但悬空 |
| 全文 | 未放行 `/actuator/health`、`/swagger-ui/**`、`/v3/api-docs/**` | K8s 探针和 Swagger UI 无法直接访问 |
| 全文 | 未放行登录端点 `/api/auth/login` | 应用没有登录端点,无法签发 JWT |

---

## 2. 目标架构

```
HTTP 请求
   ↓
┌─────────────────────────────────────────────┐
│ SecurityFilterChain                          │
│  ┌────────────────────────────────────────┐ │
│  │ 1. CORS Filter(可选)                   │ │
│  │ 2. JwtAuthenticationFilter(自定义)     │ │
│  │    - 从 Authorization: Bearer xxx 解析 │ │
│  │    - 校验签名/过期 → 构建 Authentication │ │
│  │    - 放入 SecurityContextHolder         │ │
│  │ 3. AuthorizationFilter(URL 鉴权)       │ │
│  └────────────────────────────────────────┘ │
└─────────────────────────────────────────────┘
   ↓
Controller(@PreAuthorize 注解可选,做方法级细控)
```

**核心原则**:
- **无状态**:`SessionCreationPolicy.STATELESS`,不创建 `HttpSession`
- **JWT 自包含**:用户身份、角色、过期时间全部写在 Token 里
- **双重放行**:公开路径在 `permitAll()` 列出;其他路径 `authenticated()`

---

## 3. 需要新增的类(3 个)

### 3.1 `JwtUtil`(工具类,放在 `config/security/` 包)

职责:签发 / 解析 / 校验 JWT。

```java
@Component
public class JwtUtil {
    private final SecretKey key;
    private final long accessTtlMs;
    private final long refreshTtlMs;
    private final String issuer;

    // @Value 注入 ink.jwt.* 配置
    public JwtUtil(
        @Value("${ink.jwt.secret}") String secret,
        @Value("${ink.jwt.access-token-ttl}") Duration accessTtl,
        @Value("${ink.jwt.refresh-token-ttl}") Duration refreshTtl,
        @Value("${ink.jwt.issuer}") String issuer
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMs = accessTtl.toMillis();
        this.refreshTtlMs = refreshTtl.toMillis();
        this.issuer = issuer;
    }

    public String generateAccessToken(String subject, Collection<String> roles) { ... }
    public String generateRefreshToken(String subject) { ... }
    public Claims parse(String token) { ... }              // 抛 JwtException 表示无效
    public boolean isValid(String token) { ... }
}
```

依赖:`io.jsonwebtoken:jjwt-api/impl/jackson`(pom.xml 已引入 0.12.6)。

### 3.2 `JwtAuthenticationFilter`(OncePerRequestFilter)

职责:每个请求提取 Bearer Token → 校验 → 写入 SecurityContext。

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            HttpServletRequest req,
            HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {

        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtil.parse(token);
                String username = claims.getSubject();
                List<String> roles = claims.get("roles", List.class);

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    username, null,
                    roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException e) {
                // 不抛出,让后续匿名访问;受保护资源会被 AuthorizationFilter 拦截
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}
```

### 3.3 `AuthController`(登录端点)

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtUtil jwtUtil;

    public record LoginRequest(String username, String password) {}
    public record LoginResponse(String accessToken, String refreshToken, long expiresIn) {}

    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest req) {
        Authentication auth = authManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.username(), req.password())
        );
        List<String> roles = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
            .toList();
        String access = jwtUtil.generateAccessToken(auth.getName(), roles);
        String refresh = jwtUtil.generateRefreshToken(auth.getName());
        return new LoginResponse(access, refresh, 7200);
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        Claims claims = jwtUtil.parse(refreshToken);
        // 仅签发新 access,refresh 不滚动(可选策略)
        String access = jwtUtil.generateAccessToken(claims.getSubject(), List.of());
        return new LoginResponse(access, refreshToken, 7200);
    }
}
```

---

## 4. 重构后的 SecurityConfig.java(伪代码示意)

> 不直接写入 Java 文件,仅做目标形态描述。

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())                    // 无 session → 无 CSRF
            .cors(Customizer.withDefaults())                 // 配合 CorsConfigurationSource Bean
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/actuator/health",                      // 健康探针公开
                    "/actuator/info",
                    "/v3/api-docs/**",                       // OpenAPI schema
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")  // 其他 actuator 仅 ADMIN
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .authenticationManager(authenticationManager())   // 自定义或 DaoAuthenticationProvider
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable());
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(provider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
```

**关键差异**:
- 不再使用 `User.withDefaultPasswordEncoder()`(已 Deprecated)
- 用 `PasswordEncoderFactories.createDelegatingPasswordEncoder()` 支持 `{bcrypt}`/`{argon2}` 等多算法前缀,便于未来升级
- `UserDetailsService` 替换为基于 `novel_user` 表的 JPA 实现(后续可加)

---

## 5. 与 application.yml 的对接

| `application.yml` 配置项 | 在 Java 中的消费点 |
|--------------------------|-------------------|
| `ink.jwt.secret` | `JwtUtil` 构造函数 `@Value` 注入,生成 HMAC-SHA256 密钥 |
| `ink.jwt.access-token-ttl` (PT2H) | `JwtUtil` 控制 access token 过期(2 小时 = 7200 秒) |
| `ink.jwt.refresh-token-ttl` (P7D) | `JwtUtil` 控制 refresh token 过期(7 天) |
| `ink.jwt.issuer` (ink-realm) | `JwtUtil` 写入 `claims.setIssuer()` |

**注意**:HS256 要求 secret 字节长度 ≥ 32。当前 `application.yml` 中的默认值
`c2VjcmV0LWtleS1mb3Itbm92ZWwtZm9yZ2UtZG8tbm90LXVzZS1pbi1wcm9kdWN0aW9u`
(Base64) 解码后约 47 字节,**满足要求**;但仅用于 dev,生产请用环境变量注入强随机密钥。

---

## 6. 实施步骤(推荐顺序)

1. **新建包** `ink.realm.config.security`
2. **写 JwtUtil**:签发 / 解析 / 校验工具类,单元测试覆盖率 ≥ 90%
3. **写 JwtAuthenticationFilter**:继承 `OncePerRequestFilter`,先不改 SecurityConfig
4. **写 AuthController**:`/api/auth/login` + `/api/auth/refresh`
5. **写 UserDetailsService JPA 实现**:查 `novel_user` 表(需先在 V2__add_user_table.sql 加表)
6. **改 SecurityConfig**:按本文档第 4 节伪代码重写
7. **集成测试**:`MockMvc` 模拟登录 → 拿 Token → 带 Token 访问 `/api/chat`

---

## 7. 测试要点

| 测试场景 | 期望 |
|---------|------|
| 不带 Token 访问 `/api/chat` | 401 Unauthorized |
| 带过期 Token | 401,jwtFilter 清空 SecurityContext |
| 带合法 Token,角色 USER 访问 `/api/admin/**` | 403 Forbidden |
| 带合法 Token,角色 ADMIN 访问 `/api/admin/**` | 200 OK |
| `/api/auth/login` 错误密码 | 401 |
| `/api/auth/login` 正确密码 | 200 + accessToken/refreshToken |
| `/actuator/health` 不带 Token | 200(permitAll) |
| `/actuator/env` 不带 Token | 401 |
| `/actuator/env` 带 ADMIN Token | 200 |

---

## 8. 风险与兼容性

| 风险 | 缓解 |
|------|------|
| 现有 `AgentController` 全部走 `/api/**`,改造后默认需 Token | 提供 dev profile,可临时 `.anyRequest().permitAll()` |
| `AgentController` 没有任何鉴权注解 | 暂时统一在 Filter 链做 URL 级鉴权,后续按需加 `@PreAuthorize` |
| Swagger UI 调试需要 Token | Swagger 配置 `securitySchemes` Bearer JWT,UI 右上角 Authorize |
| JJWT 0.12.6 API 变更 | `Jwts.builder().signWith(key)` 取代旧 `signWith(SignatureAlgorithm.HS256, secret)` |
| 测试用例失效 | 原 SecurityConfig 是表单登录,测试需重写为 JWT Mock |

---

## 9. 与现有功能的关系

- **`/api/chat`、`/api/writing` 等接口**:重构后必须带 JWT
- **`/actuator/health`**:K8s liveness/readiness 探针,**必须** `permitAll`
- **`/swagger-ui.html`**:开发环境开放,生产环境通过 Profile 关闭或加 ADMIN 鉴权
- **SSE `/api/chat/stream`**:JWT 通过 `EventSource` 的 `Authorization` header 传递(浏览器原生 EventSource 不支持自定义 header,需用 `event-source-polyfill` 或改用 query param `?token=xxx`)

---

## 10. 后续可选增强(不在本次范围)

- Refresh Token 滚动续签(7 天内有访问自动续)
- Token 黑名单(Redis 存已登出 Token,解决 JWT 无法主动失效的问题)
- 多端互踢(同用户最多 N 个有效 Token)
- OAuth2 登录(微信/GitHub)
- IP 风控 + 设备指纹
