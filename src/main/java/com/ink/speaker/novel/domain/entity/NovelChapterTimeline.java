package com.ink.speaker.novel.domain.entity;

import com.ink.speaker.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 章节时间线实体。
 * <p>对应表 novel_chapter_timeline,记录每章剧情摘要,供 LLM 通过 queryTimeline 工具查询,避免剧情穿帮。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "novel_chapter_timeline",
        uniqueConstraints = @UniqueConstraint(columnNames = {"novel_id", "chapter_no"})
)
public class NovelChapterTimeline extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "chapter_no", nullable = false)
    private Integer chapterNo;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;
}
