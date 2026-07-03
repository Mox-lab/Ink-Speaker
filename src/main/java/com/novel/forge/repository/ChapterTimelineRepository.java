package com.novel.forge.repository;

import com.novel.forge.entity.NovelChapterTimeline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 章节时间线 Repository。
 */
public interface ChapterTimelineRepository extends JpaRepository<NovelChapterTimeline, Long> {

    /**
     * 按章节序号精确查找。
     *
     * @param novelId   小说 ID
     * @param chapterNo 章节序号
     * @return 该章时间线节点(可能为空)
     */
    Optional<NovelChapterTimeline> findByNovelIdAndChapterNo(Long novelId, Integer chapterNo);

    /**
     * 列出某本小说的全部章节(按章节序号升序)。
     *
     * @param novelId 小说 ID
     * @return 章节列表
     */
    List<NovelChapterTimeline> findByNovelIdOrderByChapterNoAsc(Long novelId);

    /**
     * 按标题模糊查找(支持 LLM 传"云陵城相遇"这种关键词)。
     *
     * @param novelId 小说 ID
     * @param keyword 标题或摘要中的关键词
     * @return 匹配的章节列表
     */
    List<NovelChapterTimeline> findByNovelIdAndTitleContainingOrSummaryContaining(
            Long novelId, String titleKeyword, String summaryKeyword);

    /**
     * 查询最近的 N 章(用于 LLM 衔接剧情时的上下文)。
     * <p>单次查询 + 内存切片,避免重复访问数据库。</p>
     *
     * @param novelId 小说 ID
     * @param limit   返回条数
     * @return 最近的章节列表(按章节序号升序)
     */
    default List<NovelChapterTimeline> findRecentChapters(Long novelId, int limit) {
        List<NovelChapterTimeline> all = findByNovelIdOrderByChapterNoAsc(novelId);
        if (all.size() <= limit) {
            return all;
        }
        return all.subList(all.size() - limit, all.size());
    }
}
