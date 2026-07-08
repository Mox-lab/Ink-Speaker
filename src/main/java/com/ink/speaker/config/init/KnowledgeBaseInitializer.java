package com.ink.speaker.config.init;

import com.ink.speaker.ai.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.File;

/**
 * 启动时按需导入示例知识库。
 * <p>使用 pgvector 持久化后,向量库重启不丢失;只有当向量表为空时才执行首次导入,
 * 避免每次重启都重复入库相同片段。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBaseInitializer implements CommandLineRunner {

    private final KnowledgeBaseService knowledgeBaseService;  // 业务服务:执行文档加载/切片/向量化/入库
    private final DataSource dataSource;                       // PG 数据源,用于查向量表行数判断是否需要导入

    @Value("${ink-speaker.knowledge-base-dir:./knowledge-base}")    // 知识库文档目录(与 application.yml 中 ink-speaker.* 配置对齐)
    private String knowledgeBaseDir;

    @Value("${ink-speaker.pgvector.table:langchain4j_embeddings}")  // pgvector 向量表名(与 EmbeddingConfig 中保持一致)
    private String vectorTable;

    /**
     * 严格的 PostgreSQL 标识符白名单:字母/下划线开头,只允许字母数字下划线。
     * <p>vectorTable 来自配置项,理论上可信;但 JDBC 不支持表名参数化,
     * 故拼接前必须校验,杜绝 SQL 注入风险(SonarQube S2077)。</p>
     */
    private static final String IDENTIFIER_PATTERN = "^[a-zA-Z_]\\w*$";

    /**
     * Spring 启动完成后执行。
     *
     * @param args 启动命令行参数(此处未使用)
     */
    @Override
    public void run(String @NonNull ... args) {
        File dir = new File(knowledgeBaseDir);                 // 把路径字符串转成 File
        if (!dir.exists() || !dir.isDirectory()) {            // 目录不存在直接跳过
            log.info("知识库目录不存在,跳过自动导入(可通过 /api/rag/import 接口手动导入)");
            return;
        }

        // 标识符白名单校验:不合法直接拒绝,避免 SQL 注入
        if (vectorTable == null || !vectorTable.matches(IDENTIFIER_PATTERN)) {
            log.warn("向量表名非法,跳过自动导入: {}", vectorTable);
            return;
        }

        // 用 JdbcTemplate 直接查表行数;表由 PgVectorEmbeddingStore 首次 build 时自动创建
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);     // 临时实例,仅用于一次性查询
        long existing;
        try {
            // 校验通过后拼接表名:仅允许字母/数字/下划线,且必须以字母或下划线开头
            existing = jdbc.queryForObject("SELECT COUNT(*) FROM " + vectorTable, Long.class);
        } catch (Exception e) {
            // 表尚未创建或 pgvector 扩展未安装,本次启动跳过自动导入,等下次启动或手动调用接口
            log.warn("向量存储尚未就绪,跳过自动导入: {}", e.getMessage());
            return;
        }

        if (existing > 0) {                                   // 已有数据,绝不重复导入
            log.info("向量存储已有 {} 条记录,跳过自动导入(如需重新导入请清空对应存储)", existing);
            return;
        }

        log.info("检测到知识库目录且向量存储为空,开始首次导入...");
        int count = knowledgeBaseService.importDocuments(knowledgeBaseDir);  // 批量入库
        log.info("知识库首次导入完成,片段数: {}", count);
    }
}
