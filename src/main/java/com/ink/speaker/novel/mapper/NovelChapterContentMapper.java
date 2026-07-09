package com.ink.speaker.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ink.speaker.novel.domain.entity.NovelChapterContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 章节正文 DAO。
 * <p>对应表 novel_chapter_content,SQL 见 resources/mapper/NovelChapterContentDao.xml。</p>
 */
@Mapper
public interface NovelChapterContentMapper extends BaseMapper<NovelChapterContent> {

    /**
     * 列出某小说的全部章节,按章节序号升序。
     *
     * @param novelId 小说 ID
     * @return 章节列表
     */
    List<NovelChapterContent> listByNovelIdOrderByChapterNoAsc(@Param("novelId") Long novelId);

    /**
     * 取某小说某章(唯一)。
     *
     * @param novelId   小说 ID
     * @param chapterNo 章节序号
     * @return 章节实体(可能为空)
     */
    Optional<NovelChapterContent> findByNovelIdAndChapterNo(@Param("novelId") Long novelId,
                                                            @Param("chapterNo") Integer chapterNo);

    /**
     * 取最大章节序号(用于续写时确定下一章号)。
     *
     * @param novelId 小说 ID
     * @return 最新章节(可能为空)
     */
    Optional<NovelChapterContent> findFirstByNovelIdOrderByChapterNoDesc(@Param("novelId") Long novelId);

    /**
     * 删除指定小说的全部章节(级联删除使用)。
     *
     * @param novelId 小说 ID
     * @return 受影响行数
     */
    int deleteByNovelId(@Param("novelId") Long novelId);
}
