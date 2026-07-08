package com.ink.speaker.config.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 配置。
 * <p>定义 JWT Bearer 认证方案,使 Swagger UI 右上角 Authorize 可填入 Token,
 * 调试受保护接口时自动加 Authorization 头。</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI inkSpeakerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ink Speaker API")
                        .version("1.0.0")
                        .description("基于 LangChain4j 的 AI 小说生成 Agent。鉴权:POST /api/auth/login 拿 accessToken,然后点右上角 Authorize 填入。"))
                // 全局添加 Authorization 头(对所有接口生效)
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .description("JWT Bearer Token。先调 /api/auth/login 获取。")));
    }
}
