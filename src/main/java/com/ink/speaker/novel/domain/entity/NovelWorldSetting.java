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
 * 世界观设定实体。
 * <p>对应表 novel_world_setting,记录地理/势力/武学/历史等设定,供 LLM 通过 queryWorldSetting 工具查询。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "novel_world_setting",
        uniqueConstraints = @UniqueConstraint(columnNames = {"novel_id", "keyword"})
)
public class NovelWorldSetting extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(nullable = false, length = 100)
    private String keyword;

    @Column(length = 50)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;
}
