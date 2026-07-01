package com.novel.agent.runner;

import com.novel.agent.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 启动时自动导入示例知识库
 * <p>
 * 方便第一次启动即可体验 RAG 能力。
 * 把示例文档放到 ./knowledge-base 目录下,启动后自动入库。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBaseInitializer implements CommandLineRunner {

    private final KnowledgeBaseService knowledgeBaseService;

    @Value("${agent.knowledge-base-dir:./knowledge-base}")
    private String knowledgeBaseDir;

    @Override
    public void run(String... args) {
        File dir = new File(knowledgeBaseDir);
        if (dir.exists() && dir.isDirectory()) {
            log.info("检测到知识库目录 {},开始自动导入...", knowledgeBaseDir);
            int count = knowledgeBaseService.importDocuments(knowledgeBaseDir);
            log.info("知识库自动导入完成,片段数: {}", count);
        } else {
            log.info("知识库目录 {} 不存在,跳过自动导入(可通过 /api/rag/import 接口手动导入)", knowledgeBaseDir);
        }
    }
}
