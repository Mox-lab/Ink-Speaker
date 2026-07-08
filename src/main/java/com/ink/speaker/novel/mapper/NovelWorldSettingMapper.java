package com.ink.speaker.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ink.speaker.novel.domain.entity.NovelWorldSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 世界观设定 DAO。
 * <p>对应表 novel_world_setting,SQL 见 resources/mapper/NovelWorldSettingDao.xml。</p>
 */
@Mapper
public interface NovelWorldSettingMapper extends BaseMapper<NovelWorldSetting> {

    /**
     * 按小说 ID + 关键词精确查找。
     *
     * @param novelId 小说 ID
     * @param keyword 关键词(青州/听潮阁)
     * @return 设定记录(可能为空)
     */
    Optional<NovelWorldSetting> findByNovelIdAndKeyword(@Param("novelId") Long novelId,
                                                        @Param("keyword") String keyword);

    /**
     * 列出某本小说的全部设定。
     *
     * @param novelId 小说 ID
     * @return 设定列表
     */
    List<NovelWorldSetting> listByNovelId(@Param("novelId") Long novelId);

    /**
     * 按分类筛选(地理/势力/武学/历史)。
     *
     * @param novelId  小说 ID
     * @param category 分类名
     * @return 该分类下所有设定
     */
    List<NovelWorldSetting> listByNovelIdAndCategory(@Param("novelId") Long novelId,
                                                     @Param("category") String category);

    /**
     * 关键词模糊匹配。
     *
     * @param novelId 小说 ID
     * @param keyword 关键词片段
     * @return 匹配的设定列表
     */
    List<NovelWorldSetting> searchByNovelIdAndKeywordContaining(@Param("novelId") Long novelId,
                                                                @Param("keyword") String keyword);
}
