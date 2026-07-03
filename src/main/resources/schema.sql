-- ============================================================
-- Novel Forge - 业务表建表脚本(PG 16 + pgvector)
-- ============================================================
-- 执行方式:
--   psql -U postgres -d novel_forge -f schema.sql
-- 或在 pgAdmin / DBeaver 中直接执行
--
-- 说明:
--   1. 向量表 langchain4j_embeddings 由 LangChain4j PgVectorEmbeddingStore
--      首次启动时自动创建,此处不建。
--   2. 业务表用 BIGSERIAL 自增主键,novel_id 预留多本小说隔离能力,
--      目前默认 novel_id=1。
--   3. 所有唯一约束建立在 (novel_id, 业务键) 上,允许不同小说有同名条目。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 小说主表(预留,目前硬编码 novel_id=1)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS novel (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(200) NOT NULL,
    author       VARCHAR(100),
    description  TEXT,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入默认小说(novel_id=1),如果尚未存在
INSERT INTO novel (id, title, author, description)
VALUES (1, '云陵纪事', 'AI 共创', '演示用小说:江湖与朝堂交织的悬疑武侠故事')
ON CONFLICT (id) DO NOTHING;


-- ------------------------------------------------------------
-- 2. 人物档案表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS novel_character (
    id           BIGSERIAL PRIMARY KEY,
    novel_id     BIGINT NOT NULL DEFAULT 1,
    name         VARCHAR(50) NOT NULL,
    age          INT,
    gender       VARCHAR(10),
    personality  TEXT,                              -- 性格描述
    weapon       VARCHAR(100),                      -- 武器
    background   TEXT,                              -- 背景故事
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (novel_id, name),
    CONSTRAINT fk_character_novel FOREIGN KEY (novel_id) REFERENCES novel(id)
);

CREATE INDEX IF NOT EXISTS idx_character_novel ON novel_character(novel_id);


-- ------------------------------------------------------------
-- 3. 世界观设定表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS novel_world_setting (
    id           BIGSERIAL PRIMARY KEY,
    novel_id     BIGINT NOT NULL DEFAULT 1,
    keyword      VARCHAR(100) NOT NULL,             -- 设定关键词(青州/听潮阁/武学品阶)
    category     VARCHAR(50),                       -- 分类:地理/势力/武学/历史
    description  TEXT,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (novel_id, keyword),
    CONSTRAINT fk_world_novel FOREIGN KEY (novel_id) REFERENCES novel(id)
);

CREATE INDEX IF NOT EXISTS idx_world_novel ON novel_world_setting(novel_id);
CREATE INDEX IF NOT EXISTS idx_world_category ON novel_world_setting(novel_id, category);


-- ------------------------------------------------------------
-- 4. 章节时间线表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS novel_chapter_timeline (
    id           BIGSERIAL PRIMARY KEY,
    novel_id     BIGINT NOT NULL DEFAULT 1,
    chapter_no   INT NOT NULL,                      -- 章节序号
    title        VARCHAR(200),                      -- 章节标题
    summary      TEXT,                              -- 剧情摘要
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (novel_id, chapter_no),
    CONSTRAINT fk_timeline_novel FOREIGN KEY (novel_id) REFERENCES novel(id)
);

CREATE INDEX IF NOT EXISTS idx_timeline_novel ON novel_chapter_timeline(novel_id);
CREATE INDEX IF NOT EXISTS idx_timeline_chapter ON novel_chapter_timeline(novel_id, chapter_no);

-- ============================================================
-- 完成提示:
--   表创建后,启动 Spring Boot 应用时 DataInitializer 会自动
--   插入林晚/苏砚/赵九等示例人物,以及青州/听潮阁等示例设定。
--   章节时间线由 LLM 生成章节后通过接口写入(此处不预置)。
-- ============================================================
