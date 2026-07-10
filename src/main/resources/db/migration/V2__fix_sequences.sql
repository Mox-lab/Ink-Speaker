-- ============================================================
-- Ink Speaker - V2:修复 BIGSERIAL sequence 与显式插入的 id 不同步
-- ============================================================
-- 背景:
--   V1__init_schema.sql 中通过 INSERT INTO novel (id, 1, ...) 显式插入 id=1 的默认小说,
--   但 PostgreSQL 的 BIGSERIAL sequence 不会因显式 id 插入而自动推进。
--   后续 JPA/MyBatis-Plus 走 sequence 自动分配时,会从 currval=1 开始,
--   与已存在的 id=1 冲突,抛出 "重复键违反唯一约束 novel_pkey"。
--
-- 修复:对每张受影响的表,把 sequence 推进到 MAX(id)+1。
--   setval(seq, MAX(id)) 后,nextval 会从 MAX(id)+1 开始。
--   若表为空(MAX(id) 返回 NULL),用 COALESCE 兜底为 1,不调整 sequence。
--
-- 幂等:setval 本身可重复执行,以 MAX(id) 为基准,重复运行结果一致。
-- ============================================================

-- novel:V1 显式插入了 id=1,必须修复
SELECT setval(pg_get_serial_sequence('novel', 'id'),
              COALESCE((SELECT MAX(id) FROM novel), 1),
              true);

-- 其余业务表:V1 虽然没显式插 id,但 DataInitializer(已删除)曾显式 insert 过带 novel_id 的行,
-- 主键 id 走 sequence 是安全的;为防止后续 V3+ 有类似显式插入,统一修复一次。
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

-- 用户/角色表:V1 也显式插入了 admin/user 两个用户,但用的是默认 sequence,
-- 若未显式指定 id 则 sequence 已正确推进;此处兜底修复一次,避免历史遗留。
SELECT setval(pg_get_serial_sequence('users', 'id'),
              COALESCE((SELECT MAX(id) FROM users), 1),
              true);

SELECT setval(pg_get_serial_sequence('roles', 'id'),
              COALESCE((SELECT MAX(id) FROM roles), 1),
              true);
