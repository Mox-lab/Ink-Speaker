package com.ink.speaker.novel.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 小说概览 VO。
 * <p>返回一本小说的基础信息 + 各子模块的最近活跃统计,
 * 供前端"小说总览"页(进入小说后第一屏)直接渲染。</p>
 *
 * <ul>
 *   <li>{@code chapterCount} 章节总数</li>
 *   <li>{@code latestChapterNo} 最大章节序号(无章节时 null)</li>
 *   <li>{@code outlineCount} 大纲版本数</li>
 *   <li>{@code hasActiveOutline} 是否存在激活的大纲</li>
 *   <li>{@code characterCount} 人物档案数</li>
 *   <li>{@code settingCount} 世界观设定数</li>
 *   <li>{@code unresolvedIssueCount} 未解决审查问题数</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelOverviewVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private String author;
    private String description;
    private boolean sharedForReference;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 章节总数。 */
    private int chapterCount;
    /** 最大章节序号(无章节时为 null)。 */
    private Integer latestChapterNo;
    /** 大纲版本总数。 */
    private int outlineCount;
    /** 是否存在激活的大纲。 */
    private boolean hasActiveOutline;
    /** 人物档案数。 */
    private int characterCount;
    /** 世界观设定数。 */
    private int settingCount;
    /** 未解决审查问题数(status=open)。 */
    private int unresolvedIssueCount;

    /** 最近 5 章摘要(按章节序号倒序),便于总览页直接展示。 */
    private List<ChapterSummaryVo> recentChapters;
    /** 大纲版本摘要列表(最新在前)。 */
    private List<OutlineSummaryVo> outlines;
}
