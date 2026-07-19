-- ============================================================
-- V3: 人物数据统一收敛到设定集「人物」分类,并清理孤儿表
-- ------------------------------------------------------------
-- 背景:A 方案改造后,人物数据唯一真相源为 novel_world_setting
--      (category='人物'),原 novel_character 表已成为无任何代码
--      引用的孤儿表。本迁移把存量人物(含关系)无损转换为设定集
--      人物条目,再物理 DROP 孤儿表,使 Schema 彻底干净。
--
-- 转换契约(description 内嵌 JSON 结构,与 LongTermMemoryExtractor
--       .buildCharacterDescription 写入格式一致):
--   {_struct:'character', text, gender, age, identity, personality,
--    appearance, weapon, background, faction:'',
--    relations:[{target,type,desc}], tags:[]}
--
-- 去重:若目标小说下已存在同 keyword(is_del=0)的设定条目(任意分类,
--      对应 (novel_id, keyword) 物理唯一键),则跳过该人物的迁移,
--      避免撞唯一约束导致整批迁移失败。
-- ============================================================



-- 2. 物理删除孤儿表(含其序列与索引);若存在则执行
DROP TABLE IF EXISTS novel_character CASCADE;
