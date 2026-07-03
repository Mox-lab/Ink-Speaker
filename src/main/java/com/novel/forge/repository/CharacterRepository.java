package com.novel.forge.repository;

import com.novel.forge.entity.NovelCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 人物档案 Repository。
 * <p>Spring Data JPA 自动生成实现,无需写 SQL。</p>
 */
public interface CharacterRepository extends JpaRepository<NovelCharacter, Long> {

    /**
     * 按小说 ID + 姓名精确查找。
     * <p>对应 SQL: SELECT * FROM novel_character WHERE novel_id=? AND name=?</p>
     *
     * @param novelId 小说 ID
     * @param name    人物姓名
     * @return 人物档案(可能为空)
     */
    Optional<NovelCharacter> findByNovelIdAndName(Long novelId, String name);

    /**
     * 列出某本小说的全部人物。
     *
     * @param novelId 小说 ID
     * @return 人物列表
     */
    List<NovelCharacter> findByNovelId(Long novelId);

    /**
     * 模糊查询姓名(用于 LLM 传"林晚姑娘"这种带后缀的输入)。
     *
     * @param novelId 小说 ID
     * @param name    姓名片段
     * @return 匹配的人物列表
     */
    List<NovelCharacter> findByNovelIdAndNameContaining(Long novelId, String name);
}
