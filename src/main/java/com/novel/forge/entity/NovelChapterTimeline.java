package com.novel.forge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 章节时间线实体。
 * <p>对应表 novel_chapter_timeline,记录每章剧情摘要,供 LLM 通过 queryTimeline 工具查询,避免剧情穿帮。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "novel_chapter_timeline",
        uniqueConstraints = @UniqueConstraint(columnNames = {"novel_id", "chapter_no"})
)
public class NovelChapterTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "chapter_no", nullable = false)
    private Integer chapterNo;      // 章节序号

    @Column(length = 200)
    private String title;           // 章节标题

    @Column(columnDefinition = "TEXT")
    private String summary;         // 剧情摘要

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
