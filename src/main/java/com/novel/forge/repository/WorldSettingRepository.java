package com.novel.forge.repository;

import com.novel.forge.entity.NovelWorldSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 世界观设定 Repository。
 */
public interface WorldSettingRepository extends JpaRepository<NovelWorldSetting, Long> {

    /**
     * 按小说 ID + 关键词精确查找。
     *
     * @param novelId 小说 ID
     * @param keyword 关键词(青州/听潮阁)
     * @return 设定记录(可能为空)
     */
    Optional<NovelWorldSetting> findByNovelIdAndKeyword(Long novelId, String keyword);

    /**
     * 列出某本小说的全部设定。
     */
    List<NovelWorldSetting> findByNovelId(Long novelId);

    /**
     * 按分类筛选(地理/势力/武学/历史)。
     *
     * @param novelId  小说 ID
     * @param category 分类名
     * @return 该分类下所有设定
     */
    List<NovelWorldSetting> findByNovelIdAndCategory(Long novelId, String category);

    /**
     * 关键词模糊匹配(LLM 可能传"青州的特产"这种长串)。
     *
     * @param novelId 小说 ID
     * @param keyword 关键词片段
     * @return 匹配的设定列表
     */
    List<NovelWorldSetting> findByNovelIdAndKeywordContaining(Long novelId, String keyword);
}
