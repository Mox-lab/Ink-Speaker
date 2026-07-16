package ink.realm.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 配置。
 * <p>采用 JWT 无状态鉴权,不创建 HttpSession,适用于 REST API + SSE 架构。</p>
 * <p>放行路径:</p>
 * <ul>
 *   <li>/api/auth/login, /api/auth/refresh - 鉴权端点本身</li>
 *   <li>/actuator/health, /actuator/info   - 健康探针(K8s 友好)</li>
 *   <li>/swagger-ui/**, /v3/api-docs/**     - API 文档</li>
 *   <li>/error                              - Spring 错误页</li>
 * </ul>
 * <p>保护路径:其余 /api/** 全部需 access token。</p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    /**
     * 安全过滤器链。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF:无 session,无需 CSRF 防护
                .csrf(csrf -> csrf.disable())
                // CORS:允许前端跨域(Swagger UI/本地开发)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 无状态会话
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // URL 鉴权规则
                .authorizeHttpRequests(auth -> auth
                        // 公开路径
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/refresh",
                                "/api/track",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/error"
                        ).permitAll()
                        // 管理端点:仅 ADMIN
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // 管理接口:仅 ADMIN
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 其他 /api/** :需登录
                        .requestMatchers("/api/**").authenticated()
                        // 其他请求:允许(静态资源等)
                        .anyRequest().permitAll()
                )
                // 注入 JWT 过滤器:在 UsernamePasswordAuthenticationFilter 之前
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 关闭表单登录与 HTTP Basic(纯 JWT)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                // 关闭默认登出页(可由前端自行实现:删除本地 Token)
                .logout(logout -> logout.disable());

        return http.build();
    }

    /**
     * AuthenticationManager:对外暴露给 AuthController 使用。
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * DaoAuthenticationProvider:用 UserDetailsService + PasswordEncoder 校验密码。
     * <p>Spring Security 6.x 移除了无参构造与 setter,构造时直接注入 UserDetailsService。</p>
     * <p>关闭 hideUserNotFoundExceptions:让 {@code UsernameNotFoundException}
     * 透传到 Service 层,由业务代码区分"账号不存在"与"密码错误"两种语义并返回给前端。</p>
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        provider.setHideUserNotFoundExceptions(false);
        return provider;
    }

    /**
     * 密码编码器:BCrypt(默认 10 rounds)。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS 配置。
     * <p>允许 Swagger UI / 前端 SPA 跨域调用。</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
