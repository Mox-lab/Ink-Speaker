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
 * 章节审查问题(DirectorAgent 协作产物)。
 * <p>当 ReviewAgent 检测到人设崩塌 / 世界观矛盾 / 时间线冲突 / 伏笔断链等问题时,
 * 落库一条记录,供前端侧栏展示与作者处理。</p>
 *
 * <p>第 4 阶段:加 {@link Version} 乐观锁,防止作者手动改 status 与
 * 后台异步新增问题时的并发覆写。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "novel_review_issue")
public class NovelReviewIssue extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "chapter_no", nullable = false)
    private Integer chapterNo;

    /** low / medium / high */
    @Column(name = "severity", length = 20, nullable = false)
    private String severity;

    /** 人设 / 世界观 / 时间线 / 伏笔 / 节奏 / 其他 */
    @Column(name = "category", length = 50, nullable = false)
    private String category;

    /** 问题定位(原文片段或行号描述) */
    @Column(name = "location", length = 2048)
    private String location;

    /** 问题描述 */
    @Column(name = "description", nullable = false, length = 4096)
    private String description;

    /** 修改建议 */
    @Column(name = "suggestion", length = 4096)
    private String suggestion;

    /** open / resolved / ignored */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "open";

    /**
     * 乐观锁版本号。
     * <p>更新 status 时 JPA 自动校验 version,避免作者与后台异步同时改写。</p>
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
