package com.ink.speaker.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ink.speaker.novel.domain.entity.NovelOutline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 大纲 DAO。
 * <p>对应表 novel_outline,SQL 见 resources/mapper/NovelOutlineDao.xml。</p>
 * <p>每次保存插入新版本;激活版本唯一,切换前先清空旧的。</p>
 */
@Mapper
public interface NovelOutlineMapper extends BaseMapper<NovelOutline> {

    /**
     * 列出某小说的全部大纲,按版本倒序(最新在前)。
     *
     * @param novelId 小说 ID
     * @return 大纲列表
     */
    List<NovelOutline> listByNovelIdOrderByVersionDesc(@Param("novelId") Long novelId);

    /**
     * 当前激活版本。
     *
     * @param novelId 小说 ID
     * @return 激活的大纲(可能为空)
     */
    Optional<NovelOutline> findByNovelIdAndActiveTrue(@Param("novelId") Long novelId);

    /**
     * 最新版本号(用于续生时给新版本赋值)。
     *
     * @param novelId 小说 ID
     * @return 最新版本号,无任何版本时返回 null
     */
    Integer findMaxVersion(@Param("novelId") Long novelId);

    /**
     * 切换激活版本前,先把同小说其他版本全部置为 inactive。
     *
     * @param novelId 小说 ID
     * @return 受影响行数
     */
    int clearActiveFlag(@Param("novelId") Long novelId);

    /**
     * 激活指定版本。
     *
     * @param id     大纲 ID
     * @param active 是否激活
     * @return 受影响行数
     */
    int updateActive(@Param("id") Long id, @Param("active") boolean active);

    /**
     * 删除指定小说的全部大纲版本(级联删除使用)。
     *
     * @param novelId 小说 ID
     * @return 受影响行数
     */
    int deleteByNovelId(@Param("novelId") Long novelId);
}
