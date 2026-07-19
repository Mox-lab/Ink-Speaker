package ink.realm.config.security;

import tools.jackson.databind.ObjectMapper;
import ink.realm.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 未认证的统一入口响应。
 * <p>当请求到达受保护资源却没有有效身份(匿名 / token 失效)时,
 * Spring Security 的 {@code ExceptionTranslationFilter} 会调用本入口。</p>
 * <p><b>关键:</b>本项目在 {@code SecurityConfig} 中关闭了 formLogin 与 httpBasic,
 * 若不显式指定入口,Spring 会退化使用 {@code Http403ForbiddenEntryPoint} 返回 403。
 * 但前端 {@code client.js} 仅在收到 <b>401</b>(或业务码 2001)时触发 token 刷新 / 跳登录,
 * 收到 403 只会弹 toast 而不跳转,导致"token 失效却无法自动跳登录"。</p>
 * <p>因此这里显式返回 <b>401</b>,语义为"未认证",使前端自动走 refresh / 跳登录流程;
 * 真正的"已认证但无权限"仍由 {@code AccessDeniedHandler} 返回 403,二者不混淆。</p>
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> body = Map.of(
                "code", ResultCode.UNAUTHORIZED.getCode(),
                "message", ResultCode.UNAUTHORIZED.getMessage(),
                "data", (Object) null
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
