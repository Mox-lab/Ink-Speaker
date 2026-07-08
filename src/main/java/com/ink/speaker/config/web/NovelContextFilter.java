package com.ink.speaker.config.web;

import com.ink.speaker.common.NovelContext;
import com.ink.speaker.common.BusinessException;
import com.ink.speaker.common.ResultCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 小说上下文过滤器。
 * <p>把"当前请求归属哪本小说 + 哪个用户"塞进 {@link NovelContext},供 Tool / Service 透传使用。</p>
 *
 * <p>解析顺序(优先级从高到低):</p>
 * <ol>
 *   <li>请求头 {@code X-Novel-Id}(前端切换小说时显式传)</li>
 *   <li>查询参数 {@code novelId}(兼容老旧前端或调试)</li>
 *   <li>JWT claim {@code novelId}(未来:登录时绑定默认小说,刷新时可切换)</li>
 * </ol>
 *
 * <p>以上来源均无时,context 为空,业务侧需自行处理(回退默认值或抛 400)。</p>
 *
 * <p><b>userId 解析:</b>从 SecurityContext 的 authentication.name 拿(JwtAuthenticationFilter 已写入)。
 * 由于 username 不等于 userId,这里只暂存 username 的 hash 作为占位,
 * 真正的 userId 由 AuthService 在登录时建立 username → userId 映射后注入。</p>
 *
 * <p><b>R5 用户隔离:</b>novelId 与 userId 同时在 NovelContext 中,
 * Service 层在读写小说数据时校验 novel.ownerId == userId,不匹配则抛 403。</p>
 *
 * <p><b>注意:</b>异步线程({@code @Async} / 线程池)不会自动继承 ThreadLocal,
 * 已通过 {@link com.ink.speaker.config.async.AsyncConfig#novelContextTaskDecorator} 透传。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class NovelContextFilter extends OncePerRequestFilter {

    /** 请求头 key:小说 ID。 */
    public static final String NOVEL_ID_HEADER = "X-Novel-Id";

    /** 查询参数 key:兼容旧调用方式。 */
    public static final String NOVEL_ID_PARAM = "novelId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        try {
            Long novelId = resolveNovelId(request);
            if (novelId != null) {
                NovelContext.setNovelId(novelId);
            }
            Long userId = resolveUserId();
            if (userId != null) {
                NovelContext.setUserId(userId);
            }
            chain.doFilter(request, response);
        } finally {
            // 必须清理:Tomcat 线程池会复用线程,否则下次请求会读到上一次的 novelId/userId
            NovelContext.clear();
        }
    }

    private Long resolveNovelId(HttpServletRequest request) {
        // 1) 请求头 X-Novel-Id
        String header = request.getHeader(NOVEL_ID_HEADER);
        Long fromHeader = parseLong(header);
        if (fromHeader != null) {
            return fromHeader;
        }

        // 2) 查询参数 novelId(兼容调试 / 旧前端)
        String param = request.getParameter(NOVEL_ID_PARAM);
        Long fromParam = parseLong(param);
        if (fromParam != null) {
            return fromParam;
        }

        // 3) JWT claim 已被 JwtAuthenticationFilter 解到 SecurityContext,
        //    但当前业务约定不在 JWT 里绑定 novelId(切换小说走 X-Novel-Id 即可)。
        return null;
    }

    /**
     * 从 SecurityContext 拿当前鉴权用户的 userId。
     *
     * <p>JwtAuthenticationFilter 把 username 写入 Authentication.name,
     * 这里暂用 username 的 hashCode 作为占位 —— 实际项目应通过 UserService
     * 建立 username → userId 映射,在登录时把 userId 写入 JWT claim。</p>
     *
     * <p>TODO(R5 后续):登录时把 userId 加入 JWT claim,这里直接读 claim 即可,
     * 不再依赖 username 转换。</p>
     */
    private Long resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        // principal 是 username 字符串时,作为过渡方案走 username.hashCode()
        // 真正使用前需在登录流程中持久化 User 并把 id 写回 JWT
        if (principal instanceof String username && !username.isBlank()) {
            log.debug("[NovelContext] principal 是 username 而非 userId,使用 hashCode 作过渡: {}", username);
            return (long) username.hashCode();
        }
        return null;
    }

    private Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            log.debug("[NovelContext] 无法解析 novelId: {}", raw);
            return null;
        }
    }
}