package com.ink.speaker.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ink.speaker.novel.domain.entity.NovelCharacter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 人物档案 DAO。
 * <p>对应表 novel_character,SQL 见 resources/mapper/NovelCharacterDao.xml。</p>
 */
@Mapper
public interface NovelCharacterMapper extends BaseMapper<NovelCharacter> {

    /**
     * 按小说 ID + 姓名精确查找。
     *
     * @param novelId 小说 ID
     * @param name    人物姓名
     * @return 人物档案(可能为空)
     */
    Optional<NovelCharacter> findByNovelIdAndName(@Param("novelId") Long novelId,
                                                  @Param("name") String name);

    /**
     * 列出某本小说的全部人物。
     *
     * @param novelId 小说 ID
     * @return 人物列表
     */
    List<NovelCharacter> listByNovelId(@Param("novelId") Long novelId);

    /**
     * 模糊查询姓名(用于 LLM 传"林晚姑娘"这种带后缀的输入)。
     *
     * @param novelId 小说 ID
     * @param name    姓名片段
     * @return 匹配的人物列表
     */
    List<NovelCharacter> searchByNovelIdAndNameContaining(@Param("novelId") Long novelId,
                                                          @Param("name") String name);

    /**
     * 删除指定小说的全部人物档案(级联删除使用)。
     *
     * @param novelId 小说 ID
     * @return 受影响行数
     */
    int deleteByNovelId(@Param("novelId") Long novelId);
}
