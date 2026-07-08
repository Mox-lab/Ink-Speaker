package com.ink.speaker.novel.domain.entity;

import com.ink.speaker.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 章节正文实体。
 * <p>对应表 novel_chapter_content,存章节正文全文。</p>
 * <p>与 novel_chapter_timeline(summary 摘要)分工:timeline 由 Agent 自动写,
 * content 由用户主动保存。</p>
 *
 * <p>第 4 阶段:加 {@link Version} 乐观锁,防止并发保存覆写。
 * DB 列 {@code version} 由 V6 迁移添加,JPA 通过 @Version 在 UPDATE 时检查。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "novel_chapter_content")
public class NovelChapterContent extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "outline_id")
    private Long outlineId;

    @Column(name = "chapter_no", nullable = false)
    private Integer chapterNo;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    /**
     * 乐观锁版本号。
     * <p>JPA 在 UPDATE 时自动加 {@code WHERE version = ?} 条件,版本不匹配则抛
     * {@link jakarta.persistence.OptimisticLockException}。</p>
     * <p>用于"作者本地保存 + 服务端异步审查改 status"等并发场景。</p>
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
