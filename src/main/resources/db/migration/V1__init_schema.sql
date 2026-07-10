-- ============================================================
-- Ink Speaker - 整合版初始 schema(由 V1~V7 合并而来)
-- ============================================================
-- 适用场景:
--   1. 全新部署:直接执行本文件,一次建好所有业务表
--   2. 已有 V1~V7 历史:无需执行本文件,Flyway 自动跳过最新版本以下的所有脚本
--
-- 整合原则:
--   - 所有 IF NOT EXISTS / ON CONFLICT DO NOTHING 保持幂等
--   - V5 修复(roles 缺 created_at/updated_at)直接并入 V2 的建表语句
--   - V6 索引/乐观锁直接并入对应表后
--   - V7 owner_id/shared_for_reference 直接并入 novel 表
--   - 默认 novel_id=1 兜底数据保留(单小说兼容老代码)
-- ============================================================

-- ============================================================
-- 1. 小说主表(含 R5 用户隔离字段:owner_id / shared_for_reference)
-- ============================================================
CREATE TABLE IF NOT EXISTS novel (
    id                    BIGSERIAL PRIMARY KEY,
    title                 VARCHAR(200) NOT NULL,
    author                VARCHAR(100),
    description           TEXT,
    owner_id              BIGINT NOT NULL DEFAULT 1,            -- R5:小说所有者
    shared_for_reference  BOOLEAN NOT NULL DEFAULT FALSE,       -- R5:是否公开到公共参考池
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_novel_owner ON novel(owner_id);
CREATE INDEX IF NOT EXISTS idx_novel_shared ON novel(shared_for_reference) WHERE shared_for_reference = true;



-- ============================================================
-- 2. 人物档案表(含 V3 扩展:identity/appearance/relationships)
-- ============================================================
CREATE TABLE IF NOT EXISTS novel_character (
    id             BIGSERIAL PRIMARY KEY,
    novel_id       BIGINT NOT NULL DEFAULT 1,
    name           VARCHAR(50) NOT NULL,
    age            INT,
    gender         VARCHAR(10),
    personality    TEXT,                                         -- 性格描述
    weapon         VARCHAR(100),                                -- 武器
    background     TEXT,                                         -- 背景故事
    identity       TEXT,                                         -- V3:身份/职业
    appearance     TEXT,                                         -- V3:外貌描述
    relationships  JSONB DEFAULT '[]'::jsonb,                   -- V3:关系列表 [{target, type, note}]
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (novel_id, name),
    CONSTRAINT fk_character_novel FOREIGN KEY (novel_id) REFERENCES novel(id)
);

CREATE INDEX IF NOT EXISTS idx_character_novel ON novel_character(novel_id);
CREATE INDEX IF NOT EXISTS idx_character_relationships ON novel_character USING GIN (relationships);


-- ============================================================
-- 3. 世界观设定表
-- ============================================================
CREATE TABLE IF NOT EXISTS novel_world_setting (
    id           BIGSERIAL PRIMARY KEY,
    novel_id     BIGINT NOT NULL DEFAULT 1,
    keyword      VARCHAR(100) NOT NULL,                          -- 设定关键词
    category     VARCHAR(50),                                    -- 分类:地理/势力/武学/历史
    description  TEXT,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (novel_id, keyword),
    CONSTRAINT fk_world_novel FOREIGN KEY (novel_id) REFERENCES novel(id)
);

CREATE INDEX IF NOT EXISTS idx_world_novel ON novel_world_setting(novel_id);
CREATE INDEX IF NOT EXISTS idx_world_category ON novel_world_setting(novel_id, category);


-- ============================================================
-- 4. 章节时间线表
-- ============================================================
CREATE TABLE IF NOT EXISTS novel_chapter_timeline (
    id           BIGSERIAL PRIMARY KEY,
    novel_id     BIGINT NOT NULL DEFAULT 1,
    chapter_no   INT NOT NULL,                                   -- 章节序号
    title        VARCHAR(200),                                   -- 章节标题
    summary      TEXT,                                            -- 剧情摘要
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (novel_id, chapter_no),
    CONSTRAINT fk_timeline_novel FOREIGN KEY (novel_id) REFERENCES novel(id)
);

CREATE INDEX IF NOT EXISTS idx_timeline_novel ON novel_chapter_timeline(novel_id);
CREATE INDEX IF NOT EXISTS idx_timeline_chapter ON novel_chapter_timeline(novel_id, chapter_no);


-- ============================================================
-- 5. 大纲多版本表(V3 新增,V6 补充部分索引)
-- ============================================================
CREATE TABLE IF NOT EXISTS novel_outline (
    id           BIGSERIAL PRIMARY KEY,
    novel_id     BIGINT NOT NULL DEFAULT 1,
    title        VARCHAR(200),                                   -- 用户起名
    theme        TEXT,                                            -- 题材蓝图
    chapters     INT,                                             -- 目标章节数
    content      TEXT NOT NULL,                                  -- 大纲全文(markdown)
    version      INT NOT NULL DEFAULT 1,                         -- 版本号
    is_active    BOOLEAN NOT NULL DEFAULT FALSE,                 -- 当前激活版本
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_outline_novel FOREIGN KEY (novel_id) REFERENCES novel(id)
);

CREATE INDEX IF NOT EXISTS idx_outline_novel ON novel_outline(novel_id);
CREATE INDEX IF NOT EXISTS idx_outline_version ON novel_outline(novel_id, version DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_outline_novel_version ON novel_outline(novel_id, version);
-- V6 部分索引:仅当 is_active=true 时索引,提升激活版本查询效率
CREATE INDEX IF NOT EXISTS idx_outline_active_only ON novel_outline(novel_id) WHERE is_active = true;


-- ============================================================
-- 6. 章节正文全文表(V3 新增,V6 补充唯一约束 + 乐观锁 + 部分索引)
-- ============================================================
CREATE TABLE IF NOT EXISTS novel_chapter_content (
    id           BIGSERIAL PRIMARY KEY,
    novel_id     BIGINT NOT NULL DEFAULT 1,
    outline_id   BIGINT,                                         -- 关联的大纲版本(可空)
    chapter_no   INT NOT NULL,                                   -- 章节序号
    title        VARCHAR(200),
    content      TEXT NOT NULL,                                  -- 章节正文
    word_count   INT,                                             -- 实际字数
    session_id   VARCHAR(100),                                   -- Memory 会话 ID
    version      BIGINT NOT NULL DEFAULT 0,                      -- V6:乐观锁版本字段
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chapter_novel FOREIGN KEY (novel_id) REFERENCES novel(id),
    CONSTRAINT fk_chapter_outline FOREIGN KEY (outline_id) REFERENCES novel_outline(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_chapter_novel ON novel_chapter_content(novel_id);
CREATE INDEX IF NOT EXISTS idx_chapter_no ON novel_chapter_content(novel_id, chapter_no);
-- V6:同一小说下章节序号唯一(防止重复保存)
CREATE UNIQUE INDEX IF NOT EXISTS uq_chapter_novel_chapter_no ON novel_chapter_content(novel_id, chapter_no);
-- V6 部分索引:仅对最新章节(< 100 章)建,提升列表查询命中率
CREATE INDEX IF NOT EXISTS idx_chapter_novel_recent
    ON novel_chapter_content(novel_id, chapter_no DESC)
    WHERE chapter_no < 100;


-- ============================================================
-- 7. P1 审查问题表(V4 新增,V6 补充复合索引 + 乐观锁)
-- ============================================================
CREATE TABLE IF NOT EXISTS novel_review_issue (
    id           BIGSERIAL PRIMARY KEY,
    novel_id     BIGINT NOT NULL DEFAULT 1,
    chapter_no   INT NOT NULL,                                   -- 问题所属章节序号
    severity     VARCHAR(20) NOT NULL DEFAULT 'medium',          -- low / medium / high
    category     VARCHAR(50) NOT NULL DEFAULT '其他',             -- 人设 / 世界观 / 时间线 / 伏笔 / 节奏 / 其他
    location     TEXT,                                            -- 问题定位(原文片段)
    description  TEXT NOT NULL,                                  -- 问题描述
    suggestion   TEXT,                                            -- 修改建议
    status       VARCHAR(20) NOT NULL DEFAULT 'open',            -- open / resolved / ignored
    version      BIGINT NOT NULL DEFAULT 0,                      -- V6:乐观锁版本字段
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_novel FOREIGN KEY (novel_id) REFERENCES novel(id)
);

CREATE INDEX IF NOT EXISTS idx_review_novel_chapter ON novel_review_issue(novel_id, chapter_no);
CREATE INDEX IF NOT EXISTS idx_review_status ON novel_review_issue(novel_id, status);
CREATE INDEX IF NOT EXISTS idx_review_severity ON novel_review_issue(severity);
-- V6 部分索引:仅对 open 状态建,提升"未解决问题列表"查询效率
CREATE INDEX IF NOT EXISTS idx_review_open_list
    ON novel_review_issue(novel_id, status, chapter_no)
    WHERE status = 'open';


-- ============================================================
-- 8. 用户 / 角色 / 关联表(V2 新增,V5 修复 roles 缺失时间戳列)
-- ============================================================
CREATE TABLE IF NOT EXISTS roles (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(50) NOT NULL UNIQUE,
    password   VARCHAR(100) NOT NULL,                            -- BCrypt 哈希
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_roles_user ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role ON user_roles(role_id);

-- 默认角色
INSERT INTO roles (name) VALUES ('ROLE_ADMIN') ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_USER') ON CONFLICT (name) DO NOTHING;

-- 默认示例用户(BCrypt 10 rounds 哈希)
--   admin123 -> $2a$10$Ncb0EaO4g8MTzrBkYoUX.e26FzzL2KGR2g1DPlqsxRAK8MdGdrJc2
--   user123  -> $2a$10$U7ueLgIQ.3S/4WVw2M09EO69snWacOQQjemili1w8rSSynatq0Doe
INSERT INTO users (username, password, enabled)
VALUES ('admin', '$2a$10$Ncb0EaO4g8MTzrBkYoUX.e26FzzL2KGR2g1DPlqsxRAK8MdGdrJc2', TRUE)
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, password, enabled)
VALUES ('user', '$2a$10$U7ueLgIQ.3S/4WVw2M09EO69snWacOQQjemili1w8rSSynatq0Doe', TRUE)
ON CONFLICT (username) DO NOTHING;

-- 绑定默认角色
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'admin' AND r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'admin' AND r.name = 'ROLE_USER'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'user' AND r.name = 'ROLE_USER'
ON CONFLICT DO NOTHING;


-- ============================================================
-- 9. langchain4j_embeddings 元数据索引(V7)
-- ============================================================
-- langchain4j_embeddings 表由 LangChain4j PgVectorEmbeddingStore
-- 首次启动时自动创建,此处假设 metadata 列已存在;若不存在则跳过
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'langchain4j_embeddings'
          AND column_name = 'metadata'
    ) THEN
        CREATE INDEX IF NOT EXISTS idx_embeddings_metadata_gin
            ON langchain4j_embeddings USING GIN (metadata);
    END IF;
END $$;
