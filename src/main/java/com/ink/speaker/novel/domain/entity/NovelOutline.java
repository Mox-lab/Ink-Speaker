package com.ink.speaker.novel.domain.entity;

import com.ink.speaker.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 大纲实体(多版本)。
 * <p>对应表 novel_outline。每次保存插入新版本,保留历史。</p>
 * <p>is_active=true 表示当前激活版本,用于「续生」时取上一版本尾段。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "novel_outline")
public class NovelOutline extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String theme;

    @Column
    private Integer chapters;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    /** 当前激活版本(用于「续生」时取上一版本尾段)。数据库列为 NOT NULL,使用基本类型。 */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = false;
}
