package ink.realm.config.security;

import ink.realm.util.JwtConstants;
import ink.realm.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 鉴权过滤器。
 * <p>每个请求执行一次:</p>
 * <ol>
 *   <li>从 Authorization: Bearer xxx 头提取 Token</li>
 *   <li>解析 + 验签 → 拿到 username 与 roles</li>
 *   <li>构建 Authentication 写入 SecurityContext</li>
 *   <li>清空 SecurityContext(若 Token 无效)</li>
 * </ol>
 * <p>不在链中抛异常,无效 Token 时按匿名访问处理,由后续 AuthorizationFilter 决定 401/403。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        // 没有 Bearer Token → 直接放行,后续按匿名处理
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();

        try {
            Claims claims = jwtUtil.parse(token);
            String username = claims.getSubject();
            Long userId = jwtUtil.extractUserId(token);

            // 仅 access 类型 Token 可用于业务接口鉴权;refresh 仅限 /api/auth/refresh
            String type = claims.get(JwtConstants.TOKEN_TYPE_CLAIM, String.class);
            if (!JwtConstants.TYPE_ACCESS.equals(type)) {
                log.debug("[Auth] 非 access 类型 Token,跳过鉴权: type={}, user={}", type, username);
                SecurityContextHolder.clearContext();
                chain.doFilter(request, response);
                return;
            }

            // 提取角色列表 → 转 SimpleGrantedAuthority
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get(JwtConstants.ROLES_CLAIM, List.class);
            List<SimpleGrantedAuthority> authorities = roles == null
                    ? List.of()
                    : roles.stream().map(SimpleGrantedAuthority::new).toList();

            // principal 直接用 userId(Long),供下游 NovelContextFilter 写入 ThreadLocal
            // details 中保留 username,供 NovelServiceImpl.resolveCurrentUsername() 使用
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("[Auth] 鉴权成功: userId={}, user={}, roles={}", userId, username, roles);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("[Auth] Token 无效: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
