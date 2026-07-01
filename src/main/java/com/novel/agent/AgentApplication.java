package com.novel.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Agent Demo 启动类
 * <p>
 * 基于 Spring Boot 3 + LangChain4j 构建的 Agent 开发示例项目。
 * 启动后访问 http://localhost:9688 即可使用。
 * </p>
 */
@SpringBootApplication(exclude = {dev.langchain4j.openai.spring.AutoConfig.class})
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
        System.out.println("""
                ====================================================
                Novel Forge 启动成功!
                ====================================================
                接口列表:
                  - POST http://localhost:9688/api/chat           普通对话
                  - POST http://localhost:9688/api/chat/stream    流式对话(打字机)
                  - POST http://localhost:9688/api/writing        写作助手(多轮+工具)
                  - POST http://localhost:9688/api/outline        大纲生成
                  - POST http://localhost:9688/api/chapter        章节生成
                  - POST http://localhost:9688/api/character      人物抽取(结构化输出)
                  - POST http://localhost:9688/api/lore           设定问答(RAG)
                  - POST http://localhost:9688/api/lore/import    导入设定库
                  - GET  http://localhost:9688/api/memory         多轮记忆测试
                ====================================================
                """);
    }
}
