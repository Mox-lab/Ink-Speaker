package com.novel.forge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Novel Forge 启动类。
 * <p>基于 Spring Boot 3 + LangChain4j,端口 9688。</p>
 */
@SpringBootApplication(exclude = {dev.langchain4j.openai.spring.AutoConfig.class})
public class AgentApplication {

    /**
     * 程序入口:启动 Spring 容器并打印接口清单。
     *
     * @param args 命令行参数,透传给 SpringApplication(此处未使用)
     */
    public static void main(String[] args) {
        // 启动 Spring Boot:传入主类与命令行参数,返回 ConfigurableApplicationContext
        // 这一行会触发自动装配、Tomcat 启动、所有 @Bean 初始化
        SpringApplication.run(AgentApplication.class, args);

        // 启动成功后打印接口清单,方便开发者直接复制 curl 调试
        // 使用 Java 15+ 文本块 """ 多行字符串,避免拼接 \n
        System.out.println("""
                ====================================================
                Novel Forge 启动成功!
                ====================================================
                """);
    }
}
