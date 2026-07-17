-- ============================================================
-- Ink Realm - 完整初始 schema(单一基线文件)
-- ============================================================
-- 整合来源(开发期合并,消除增量迁移):
--   - 原 V1: 核心业务表(novel / character / world_setting / timeline
--           / outline / chapter_content / review_issue / 用户体系)
--   - 原 V2: 统一审计字段命名(created_at/updated_at → ct_time/ut_time)
--            并补全逻辑删除列 is_del(所有业务表)
--   - 原 V3: 全表列 COMMENT(中文注释,幂等)
--   - 原 V4: sys_users.nickname(唯一,可空;注册后补充)
--   - 原 V8: novel_chapter_history 章节历史快照(含 updated_at)
--   - 原 V9: agent_log 漏斗埋点(含 updated_at)
--   - 原 V10: novel_collaborator 多用户协作
--
-- 约定(与 ink.realm.common.entity.BaseEntity 保持一致):
--   所有业务表统一使用 ct_time / ut_time(审计) + is_del(逻辑删除),
--   由 MybatisMetaObjectHandler 在 INSERT/UPDATE 时自动填充。
--
-- 使用方式:
--   1. 删库重建:手动 DROP DATABASE; CREATE DATABASE; 然后启动应用
--   2. 清表重建:先执行下方 DROP 段,再执行 CREATE 段
--   3. 全新部署:无需手动操作,自动建表 + 种子数据
--
-- 注意:本文件已整合 V1~V4,旧的 V2/V3/V4 迁移已删除。
--      若数据库中 flyway_schema_history 仍记录 V2/V3/V4,
--      需在重启前清空该表(详见本次变更的说明),否则 Flyway 校验会失败。
-- ============================================================

-- ============================================================
-- 0. 清表(按外键依赖逆序,方便删库重建场景)
--    注释掉此段即为"仅建表"模式;解注释即为"删了重建"
-- ============================================================
-- DROP TABLE IF EXISTS agent_log CASCADE;
-- DROP TABLE IF EXISTS novel_chapter_history CASCADE;
-- DROP TABLE IF EXISTS novel_chapter_content CASCADE;
-- DROP TABLE IF EXISTS novel_collaborator CASCADE;
-- DROP TABLE IF EXISTS novel_review_issue CASCADE;
-- DROP TABLE IF EXISTS novel_outline CASCADE;
-- DROP TABLE IF EXISTS novel_chapter_timeline CASCADE;
-- DROP TABLE IF EXISTS novel_world_setting CASCADE;
-- DROP TABLE IF EXISTS novel_character CASCADE;
-- DROP TABLE IF EXISTS sys_user_roles CASCADE;
-- DROP TABLE IF EXISTS sys_users CASCADE;
-- DROP TABLE IF EXISTS sys_roles CASCADE;
-- DROP TABLE IF EXISTS novel CASCADE;

-- ============================================================
-- 1. 小说主表(含 R5 用户隔离:owner_id / shared_for_reference)
-- ============================================================
CREATE TABLE IF NOT EXISTS novel (
    id                    BIGSERIAL PRIMARY KEY,
    title                 VARCHAR(200) NOT NULL,
    author                VARCHAR(100),
    description           TEXT,
    owner_id              BIGINT NOT NULL DEFAULT 1,             -- 小说所有者
    shared_for_reference  BOOLEAN NOT NULL DEFAULT FALSE,        -- 是否公开到公共参考池
    ct_time               TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ut_time               TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_del                INTEGER NOT NULL DEFAULT 0             -- 逻辑删除:0-未删除,1-已删除
);

CREATE INDEX IF NOT EXISTS idx_novel_owner ON novel(owner_id);
CREATE INDEX IF NOT EXISTS idx_novel_shared ON novel(shared_for_reference) WHERE shared_for_reference = true;
-- 用户维度小说名唯一:同一 owner_id 下 title 不可重复(不同用户可重名)
CREATE UNIQUE INDEX IF NOT EXISTS uq_novel_owner_title ON novel(owner_id, title);

-- ============================================================
-- 2. 人物档案表(含 identity / appearance / relationships)
-- ============================================================
CREATE TABLE IF NOT EXISTS novel_character (
    id             BIGSERIAL PRIMARY KEY,
    novel_id       BIGINT NOT NULL DEFAULT 1,
    name           VARCHAR(50) NOT NULL,
    age            INT,
    gender         VARCHAR(10),
    personality    TEXT,                                           -- 性格描述
    weapon         VARCHAR(100),                                   -- 武器
    background     TEXT,                                           -- 背景故事
    identity       TEXT,                                           -- 身份/职业
    appearance     TEXT,                                           -- 外貌描述
    relationships  JSONB DEFAULT '[]'::jsonb,                      -- 关系列表 [{target, type, note}]
    ct_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ut_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_del         INTEGER NOT NULL DEFAULT 0,
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
    keyword      VARCHAR(100) NOT NULL,                            -- 设定关键词
    category     VARCHAR(50),                                      -- 分类:地理/势力/武学/历史
    description  TEXT,
    ct_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ut_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_del       INTEGER NOT NULL DEFAULT 0,
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
    chapter_no   INT NOT NULL,                                    -- 章节序号
    title        VARCHAR(200),                                    -- 章节标题
    summary      TEXT,                                             -- 剧情摘要
    ct_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ut_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_del       INTEGER NOT NULL DEFAULT 0,
    UNIQUE (novel_id, chapter_no),
    CONSTRAINT fk_timeline_novel FOREIGN KEY (novel_id) REFERENCES novel(id)
);

CREATE INDEX IF NOT EXISTS idx_timeline_novel ON novel_chapter_timeline(novel_id);
CREATE INDEX IF NOT EXISTS idx_timeline_chapter ON novel_chapter_timeline(novel_id, chapter_no);


-- ============================================================
-- 5. 大纲多版本表
-- ============================================================
CREATE TABLE IF NOT EXISTS novel_outline (
    id           BIGSERIAL PRIMARY KEY,
    novel_id     BIGINT NOT NULL DEFAULT 1,
    title        VARCHAR(200),                                     -- 用户起名
    theme        TEXT,                                              -- 题材蓝图
    chapters     INT,                                               -- 目标章节数
    content      TEXT NOT NULL,                                    -- 大纲全文(markdown)
    version      INT NOT NULL DEFAULT 1,                           -- 版本号
    is_active    BOOLEAN NOT NULL DEFAULT FALSE,                   -- 当前激活版本
    ct_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ut_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_del       INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_outline_novel FOREIGN KEY (novel_id) REFERENCES novel(id)
);

CREATE INDEX IF NOT EXISTS idx_outline_novel ON novel_outline(novel_id);
CREATE INDEX IF NOT EXISTS idx_outline_version ON novel_outline(novel_id, version DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_outline_novel_version ON novel_outline(novel_id, version);
-- 部分索引:仅当 is_active=true 时索引,提升激活版本查询效率
CREATE INDEX IF NOT EXISTS idx_outline_active_only ON novel_outline(novel_id) WHERE is_active = true;


-- ============================================================
-- 6. 章节正文全文表(含唯一约束 + 乐观锁 + 部分索引)
-- ============================================================
CREATE TABLE IF NOT EXISTS novel_chapter_content (
    id           BIGSERIAL PRIMARY KEY,
    novel_id     BIGINT NOT NULL DEFAULT 1,
    outline_id   BIGINT,                                           -- 关联的大纲版本(可空)
    chapter_no   INT NOT NULL,                                    -- 章节序号
    title        VARCHAR(200),
    content      TEXT NOT NULL,                                    -- 章节正文
    word_count   INT,                                               -- 实际字数
    session_id   VARCHAR(100),                                     -- Memory 会话 ID
    version      BIGINT NOT NULL DEFAULT 0,                        -- 乐观锁版本字段
    ct_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ut_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_del       INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_chapter_novel FOREIGN KEY (novel_id) REFERENCES novel(id),
    CONSTRAINT fk_chapter_outline FOREIGN KEY (outline_id) REFERENCES novel_outline(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_chapter_novel ON novel_chapter_content(novel_id);
CREATE INDEX IF NOT EXISTS idx_chapter_no ON novel_chapter_content(novel_id, chapter_no);
-- 同一小说下章节序号唯一(防止重复保存)
CREATE UNIQUE INDEX IF NOT EXISTS uq_chapter_novel_chapter_no ON novel_chapter_content(novel_id, chapter_no);
-- 部分索引:仅对最新章节(< 100 章)建,提升列表查询命中率
CREATE INDEX IF NOT EXISTS idx_chapter_novel_recent
    ON novel_chapter_content(novel_id, chapter_no DESC)
    WHERE chapter_no < 100;


-- ============================================================
-- 7. P1 审查问题表(含复合索引 + 乐观锁)
-- ============================================================
CREATE TABLE IF NOT EXISTS novel_review_issue (
    id           BIGSERIAL PRIMARY KEY,
    novel_id     BIGINT NOT NULL DEFAULT 1,
    chapter_no   INT NOT NULL,                                    -- 问题所属章节序号
    severity     VARCHAR(20) NOT NULL DEFAULT 'medium',           -- low / medium / high
    category     VARCHAR(50) NOT NULL DEFAULT '其他',              -- 人设/世界观/时间线/伏笔/节奏/其他
    location     TEXT,                                              -- 问题定位(原文片段)
    description  TEXT NOT NULL,                                    -- 问题描述
    suggestion   TEXT,                                              -- 修改建议
    status       VARCHAR(20) NOT NULL DEFAULT 'open',             -- open / resolved / ignored
    version      BIGINT NOT NULL DEFAULT 0,                        -- 乐观锁版本字段
    ct_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ut_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_del       INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_novel FOREIGN KEY (novel_id) REFERENCES novel(id)
);

CREATE INDEX IF NOT EXISTS idx_review_novel_chapter ON novel_review_issue(novel_id, chapter_no);
CREATE INDEX IF NOT EXISTS idx_review_status ON novel_review_issue(novel_id, status);
CREATE INDEX IF NOT EXISTS idx_review_severity ON novel_review_issue(severity);
-- 部分索引:仅对 open 状态建,提升"未解决问题列表"查询效率
CREATE INDEX IF NOT EXISTS idx_review_open_list
    ON novel_review_issue(novel_id, status, chapter_no)
    WHERE status = 'open';


-- ============================================================
-- 8. 用户 / 角色 / 关联表(含种子数据)
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_roles (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(50) NOT NULL UNIQUE,
    ct_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ut_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_del     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_users (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(50) NOT NULL UNIQUE,
    nickname   VARCHAR(50) UNIQUE,                                 -- 昵称(唯一,可空;注册后补充,作为小说作者名)
    password   VARCHAR(100) NOT NULL,                              -- BCrypt 哈希
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    ct_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ut_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_del     INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_users_username ON sys_users(username);

CREATE TABLE IF NOT EXISTS sys_user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES sys_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES sys_roles(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_roles_user ON sys_user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role ON sys_user_roles(role_id);

-- 默认角色
INSERT INTO sys_roles (name) VALUES ('ROLE_ADMIN') ON CONFLICT (name) DO NOTHING;
INSERT INTO sys_roles (name) VALUES ('ROLE_USER') ON CONFLICT (name) DO NOTHING;

-- 默认示例用户(BCrypt 10 rounds 哈希)
--   admin123 -> $2a$10$Ncb0EaO4g8MTzrBkYoUX.e26FzzL2KGR2g1DPlqsxRAK8MdGdrJc2
INSERT INTO sys_users (username, nickname, password, enabled)
VALUES ('admin', '你拉不拉屎', '$2a$10$Ncb0EaO4g8MTzrBkYoUX.e26FzzL2KGR2g1DPlqsxRAK8MdGdrJc2', TRUE)
ON CONFLICT (username) DO NOTHING;

-- 绑定默认角色
INSERT INTO sys_user_roles (user_id, role_id)
SELECT u.id, r.id FROM sys_users u, sys_roles r
WHERE u.username = 'admin' AND r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO sys_user_roles (user_id, role_id)
SELECT u.id, r.id FROM sys_users u, sys_roles r
WHERE u.username = 'admin' AND r.name = 'ROLE_USER'
ON CONFLICT DO NOTHING;

-- ============================================================
-- 9. 多用户协作者表(BASE-11)
-- ============================================================
CREATE TABLE IF NOT EXISTS novel_collaborator (
    id           BIGSERIAL PRIMARY KEY,
    novel_id     BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,                                   -- 协作者用户 ID
    role         VARCHAR(20) NOT NULL DEFAULT 'editor',             -- editor / viewer
    ct_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ut_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_del       INTEGER NOT NULL DEFAULT 0,
    UNIQUE (novel_id, user_id),
    CONSTRAINT fk_collaborator_novel FOREIGN KEY (novel_id)
        REFERENCES novel(id) ON DELETE CASCADE,
    CONSTRAINT fk_collaborator_user FOREIGN KEY (user_id)
        REFERENCES sys_users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_collaborator_novel ON novel_collaborator(novel_id);
CREATE INDEX IF NOT EXISTS idx_collaborator_user ON novel_collaborator(user_id);


-- ============================================================
-- 10. 章节历史版本快照表(BASE-07)
-- ============================================================
CREATE TABLE IF NOT EXISTS novel_chapter_history (
    id                BIGSERIAL PRIMARY KEY,
    novel_id          BIGINT NOT NULL,
    chapter_id        BIGINT NOT NULL,                              -- 关联 novel_chapter_content.id
    chapter_no        INT NOT NULL,
    title             VARCHAR(200),
    content           TEXT NOT NULL,
    word_count        INT,
    session_id        VARCHAR(100),
    snapshot_version  BIGINT NOT NULL,                              -- 对应章节当时的乐观锁版本号
    ct_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ut_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_del            INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_history_chapter FOREIGN KEY (chapter_id)
        REFERENCES novel_chapter_content(id) ON DELETE CASCADE,
    CONSTRAINT fk_history_novel FOREIGN KEY (novel_id)
        REFERENCES novel(id)
);

CREATE INDEX IF NOT EXISTS idx_history_chapter ON novel_chapter_history(chapter_id, ct_time DESC);
CREATE INDEX IF NOT EXISTS idx_history_novel_chapter ON novel_chapter_history(novel_id, chapter_no, ct_time DESC);


-- ============================================================
-- 11. 漏斗埋点表(UX-11)
-- ============================================================
CREATE TABLE IF NOT EXISTS agent_log (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT,                                            -- 可空:匿名/未登录事件也能上报
    novel_id     BIGINT,                                            -- 可空:与具体小说无关的事件(如 login)
    event_type   VARCHAR(50) NOT NULL,                              -- 漏斗事件类型,如 funnel.login
    props        JSONB,                                              -- 附加属性,灵活 schema
    ct_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ut_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_del       INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_agent_log_user FOREIGN KEY (user_id)
        REFERENCES sys_users(id) ON DELETE SET NULL,
    CONSTRAINT fk_agent_log_novel FOREIGN KEY (novel_id)
        REFERENCES novel(id) ON DELETE SET NULL
);

-- 按用户聚合:单用户漏斗路径回放
CREATE INDEX IF NOT EXISTS idx_agent_log_user_created
    ON agent_log(user_id, ct_time DESC);

-- 按事件类型聚合:整体漏斗每步计数
CREATE INDEX IF NOT EXISTS idx_agent_log_event_type
    ON agent_log(event_type, ct_time DESC);


-- ============================================================
-- 12. 全表列 COMMENT(中文注释,幂等;对应实体字段 @Schema)
--     COMMENT ON COLUMN 可重复执行,无需 IF EXISTS。
-- ============================================================

-- 公共基类审计字段(id / ct_time / ut_time / is_del)
COMMENT ON COLUMN novel.id           IS '主键,数据库 IDENTITY 自增';
COMMENT ON COLUMN novel.ct_time      IS '创建时间,INSERT 时自动填充';
COMMENT ON COLUMN novel.ut_time      IS '更新时间,INSERT / UPDATE 时自动填充';
COMMENT ON COLUMN novel.is_del       IS '逻辑删除标记:0-未删除,1-已删除';

COMMENT ON COLUMN novel_character.id           IS '主键,数据库 IDENTITY 自增';
COMMENT ON COLUMN novel_character.ct_time      IS '创建时间,INSERT 时自动填充';
COMMENT ON COLUMN novel_character.ut_time      IS '更新时间,INSERT / UPDATE 时自动填充';
COMMENT ON COLUMN novel_character.is_del       IS '逻辑删除标记:0-未删除,1-已删除';

COMMENT ON COLUMN novel_world_setting.id           IS '主键,数据库 IDENTITY 自增';
COMMENT ON COLUMN novel_world_setting.ct_time      IS '创建时间,INSERT 时自动填充';
COMMENT ON COLUMN novel_world_setting.ut_time      IS '更新时间,INSERT / UPDATE 时自动填充';
COMMENT ON COLUMN novel_world_setting.is_del       IS '逻辑删除标记:0-未删除,1-已删除';

COMMENT ON COLUMN novel_chapter_timeline.id           IS '主键,数据库 IDENTITY 自增';
COMMENT ON COLUMN novel_chapter_timeline.ct_time      IS '创建时间,INSERT 时自动填充';
COMMENT ON COLUMN novel_chapter_timeline.ut_time      IS '更新时间,INSERT / UPDATE 时自动填充';
COMMENT ON COLUMN novel_chapter_timeline.is_del       IS '逻辑删除标记:0-未删除,1-已删除';

COMMENT ON COLUMN novel_outline.id           IS '主键,数据库 IDENTITY 自增';
COMMENT ON COLUMN novel_outline.ct_time      IS '创建时间,INSERT 时自动填充';
COMMENT ON COLUMN novel_outline.ut_time      IS '更新时间,INSERT / UPDATE 时自动填充';
COMMENT ON COLUMN novel_outline.is_del       IS '逻辑删除标记:0-未删除,1-已删除';

COMMENT ON COLUMN novel_chapter_content.id           IS '主键,数据库 IDENTITY 自增';
COMMENT ON COLUMN novel_chapter_content.ct_time      IS '创建时间,INSERT 时自动填充';
COMMENT ON COLUMN novel_chapter_content.ut_time      IS '更新时间,INSERT / UPDATE 时自动填充';
COMMENT ON COLUMN novel_chapter_content.is_del       IS '逻辑删除标记:0-未删除,1-已删除';

COMMENT ON COLUMN novel_review_issue.id           IS '主键,数据库 IDENTITY 自增';
COMMENT ON COLUMN novel_review_issue.ct_time      IS '创建时间,INSERT 时自动填充';
COMMENT ON COLUMN novel_review_issue.ut_time      IS '更新时间,INSERT / UPDATE 时自动填充';
COMMENT ON COLUMN novel_review_issue.is_del       IS '逻辑删除标记:0-未删除,1-已删除';

COMMENT ON COLUMN novel_collaborator.id           IS '主键,数据库 IDENTITY 自增';
COMMENT ON COLUMN novel_collaborator.ct_time      IS '创建时间,INSERT 时自动填充';
COMMENT ON COLUMN novel_collaborator.ut_time      IS '更新时间,INSERT / UPDATE 时自动填充';
COMMENT ON COLUMN novel_collaborator.is_del       IS '逻辑删除标记:0-未删除,1-已删除';

COMMENT ON COLUMN novel_chapter_history.id           IS '主键,数据库 IDENTITY 自增';
COMMENT ON COLUMN novel_chapter_history.ct_time      IS '创建时间,INSERT 时自动填充';
COMMENT ON COLUMN novel_chapter_history.ut_time      IS '更新时间,INSERT / UPDATE 时自动填充';
COMMENT ON COLUMN novel_chapter_history.is_del       IS '逻辑删除标记:0-未删除,1-已删除';

COMMENT ON COLUMN agent_log.id           IS '主键,数据库 IDENTITY 自增';
COMMENT ON COLUMN agent_log.ct_time      IS '创建时间,INSERT 时自动填充';
COMMENT ON COLUMN agent_log.ut_time      IS '更新时间,INSERT / UPDATE 时自动填充';
COMMENT ON COLUMN agent_log.is_del       IS '逻辑删除标记:0-未删除,1-已删除';

-- 1. novel
COMMENT ON COLUMN novel.title                IS '小说标题';
COMMENT ON COLUMN novel.author               IS '作者';
COMMENT ON COLUMN novel.description          IS '简介';
COMMENT ON COLUMN novel.owner_id             IS '所有者用户 ID(R5 用户隔离)';
COMMENT ON COLUMN novel.shared_for_reference IS '是否公开到公共参考池(R5 跨小说参考)';

-- 2. novel_character
COMMENT ON COLUMN novel_character.novel_id      IS '所属小说 ID';
COMMENT ON COLUMN novel_character.name         IS '角色名';
COMMENT ON COLUMN novel_character.age          IS '年龄';
COMMENT ON COLUMN novel_character.gender       IS '性别';
COMMENT ON COLUMN novel_character.personality  IS '性格描述';
COMMENT ON COLUMN novel_character.weapon       IS '武器';
COMMENT ON COLUMN novel_character.background   IS '背景故事';
COMMENT ON COLUMN novel_character.identity     IS '身份/职业';
COMMENT ON COLUMN novel_character.appearance   IS '外貌描述';
COMMENT ON COLUMN novel_character.relationships IS '关系列表(JSON 字符串,格式:[{target,type,note}])';

-- 3. novel_world_setting
COMMENT ON COLUMN novel_world_setting.novel_id    IS '所属小说 ID';
COMMENT ON COLUMN novel_world_setting.keyword     IS '设定关键词';
COMMENT ON COLUMN novel_world_setting.category    IS '分类(地理/势力/武学/历史)';
COMMENT ON COLUMN novel_world_setting.description IS '描述';

-- 4. novel_chapter_timeline
COMMENT ON COLUMN novel_chapter_timeline.novel_id    IS '所属小说 ID';
COMMENT ON COLUMN novel_chapter_timeline.chapter_no  IS '章节号';
COMMENT ON COLUMN novel_chapter_timeline.title       IS '章节标题';
COMMENT ON COLUMN novel_chapter_timeline.summary     IS '剧情摘要';

-- 5. novel_outline
COMMENT ON COLUMN novel_outline.novel_id    IS '所属小说 ID';
COMMENT ON COLUMN novel_outline.title       IS '大纲标题(用户起名)';
COMMENT ON COLUMN novel_outline.theme       IS '题材蓝图';
COMMENT ON COLUMN novel_outline.chapters    IS '目标章节数';
COMMENT ON COLUMN novel_outline.content      IS '大纲全文(markdown)';
COMMENT ON COLUMN novel_outline.version     IS '版本号(业务版本,非乐观锁)';
COMMENT ON COLUMN novel_outline.is_active   IS '当前激活版本(用于「续生」时取上一版本尾段)';

-- 6. novel_chapter_content
COMMENT ON COLUMN novel_chapter_content.novel_id     IS '所属小说 ID';
COMMENT ON COLUMN novel_chapter_content.outline_id   IS '关联的大纲版本 ID(可空)';
COMMENT ON COLUMN novel_chapter_content.chapter_no   IS '章节号';
COMMENT ON COLUMN novel_chapter_content.title        IS '章节标题';
COMMENT ON COLUMN novel_chapter_content.content      IS '章节正文';
COMMENT ON COLUMN novel_chapter_content.word_count   IS '实际字数';
COMMENT ON COLUMN novel_chapter_content.session_id   IS 'Memory 会话 ID';
COMMENT ON COLUMN novel_chapter_content.version      IS '乐观锁版本号';

-- 7. novel_review_issue
COMMENT ON COLUMN novel_review_issue.novel_id    IS '所属小说 ID';
COMMENT ON COLUMN novel_review_issue.chapter_no  IS '问题所属章节号';
COMMENT ON COLUMN novel_review_issue.severity    IS '严重度:low / medium / high';
COMMENT ON COLUMN novel_review_issue.category    IS '分类(人设/世界观/时间线/伏笔/节奏/其他)';
COMMENT ON COLUMN novel_review_issue.location    IS '问题定位(原文片段)';
COMMENT ON COLUMN novel_review_issue.description IS '问题描述';
COMMENT ON COLUMN novel_review_issue.suggestion  IS '修改建议';
COMMENT ON COLUMN novel_review_issue.status      IS '状态:open / resolved / ignored';
COMMENT ON COLUMN novel_review_issue.version     IS '乐观锁版本号';

-- 8. novel_collaborator
COMMENT ON COLUMN novel_collaborator.novel_id IS '所属小说 ID';
COMMENT ON COLUMN novel_collaborator.user_id  IS '协作者用户 ID';
COMMENT ON COLUMN novel_collaborator.role     IS '协作角色:editor / viewer';

-- 9. novel_chapter_history
COMMENT ON COLUMN novel_chapter_history.novel_id          IS '所属小说 ID';
COMMENT ON COLUMN novel_chapter_history.chapter_id         IS '关联章节 ID(novel_chapter_content.id)';
COMMENT ON COLUMN novel_chapter_history.chapter_no         IS '章节号';
COMMENT ON COLUMN novel_chapter_history.title              IS '章节标题';
COMMENT ON COLUMN novel_chapter_history.content            IS '章节正文';
COMMENT ON COLUMN novel_chapter_history.word_count         IS '字数';
COMMENT ON COLUMN novel_chapter_history.session_id         IS '会话 ID';
COMMENT ON COLUMN novel_chapter_history.snapshot_version   IS '对应章节当时的乐观锁版本号';

-- 10. agent_log
COMMENT ON COLUMN agent_log.user_id    IS '触发用户 ID(未登录可为空)';
COMMENT ON COLUMN agent_log.novel_id   IS '关联小说 ID(全局事件可为空)';
COMMENT ON COLUMN agent_log.event_type IS '事件类型(如 funnel.login)';
COMMENT ON COLUMN agent_log.props      IS '附加属性(JSON 字符串,落入 JSONB 列)';

-- 11. sys_roles / sys_users(用户体系,亦继承审计字段)
COMMENT ON COLUMN sys_roles.id        IS '主键,数据库 IDENTITY 自增';
COMMENT ON COLUMN sys_roles.name      IS '角色名(如 ROLE_ADMIN / ROLE_USER)';
COMMENT ON COLUMN sys_roles.ct_time   IS '创建时间,INSERT 时自动填充';
COMMENT ON COLUMN sys_roles.ut_time   IS '更新时间,INSERT / UPDATE 时自动填充';
COMMENT ON COLUMN sys_roles.is_del    IS '逻辑删除标记:0-未删除,1-已删除';

COMMENT ON COLUMN sys_users.id        IS '主键,数据库 IDENTITY 自增';
COMMENT ON COLUMN sys_users.username  IS '用户名(唯一)';
COMMENT ON COLUMN sys_users.nickname  IS '昵称(唯一,可空;注册后由用户补充,作为小说作者名展示)';
COMMENT ON COLUMN sys_users.password  IS '密码(BCrypt 哈希)';
COMMENT ON COLUMN sys_users.enabled   IS '是否启用';
COMMENT ON COLUMN sys_users.ct_time   IS '创建时间,INSERT 时自动填充';
COMMENT ON COLUMN sys_users.ut_time   IS '更新时间,INSERT / UPDATE 时自动填充';
COMMENT ON COLUMN sys_users.is_del    IS '逻辑删除标记:0-未删除,1-已删除';


-- ============================================================
-- 13. BIGSERIAL sequence 修复(兜底,防止历史遗留显式插入导致 id 冲突)
-- ============================================================
-- 对每张表,把 sequence 推进到 MAX(id)+1,避免后续 INSERT 冲突
-- 若表为空则 COALESCE 兜底为 1,不调整 sequence

SELECT setval(pg_get_serial_sequence('novel', 'id'),
              COALESCE((SELECT MAX(id) FROM novel), 1),
              true);

SELECT setval(pg_get_serial_sequence('novel_character', 'id'),
              COALESCE((SELECT MAX(id) FROM novel_character), 1),
              true);

SELECT setval(pg_get_serial_sequence('novel_world_setting', 'id'),
              COALESCE((SELECT MAX(id) FROM novel_world_setting), 1),
              true);

SELECT setval(pg_get_serial_sequence('novel_chapter_timeline', 'id'),
              COALESCE((SELECT MAX(id) FROM novel_chapter_timeline), 1),
              true);

SELECT setval(pg_get_serial_sequence('novel_outline', 'id'),
              COALESCE((SELECT MAX(id) FROM novel_outline), 1),
              true);

SELECT setval(pg_get_serial_sequence('novel_chapter_content', 'id'),
              COALESCE((SELECT MAX(id) FROM novel_chapter_content), 1),
              true);

SELECT setval(pg_get_serial_sequence('novel_review_issue', 'id'),
              COALESCE((SELECT MAX(id) FROM novel_review_issue), 1),
              true);

SELECT setval(pg_get_serial_sequence('novel_collaborator', 'id'),
              COALESCE((SELECT MAX(id) FROM novel_collaborator), 1),
              true);

SELECT setval(pg_get_serial_sequence('novel_chapter_history', 'id'),
              COALESCE((SELECT MAX(id) FROM novel_chapter_history), 1),
              true);

SELECT setval(pg_get_serial_sequence('agent_log', 'id'),
              COALESCE((SELECT MAX(id) FROM agent_log), 1),
              true);

SELECT setval(pg_get_serial_sequence('sys_users', 'id'),
              COALESCE((SELECT MAX(id) FROM sys_users), 1),
              true);

SELECT setval(pg_get_serial_sequence('sys_roles', 'id'),
              COALESCE((SELECT MAX(id) FROM sys_roles), 1),
              true);


-- ============================================================
-- 14. langchain4j_embeddings 元数据索引
--    表由 LangChain4j PgVectorEmbeddingStore 首次启动自动创建
-- ============================================================
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
