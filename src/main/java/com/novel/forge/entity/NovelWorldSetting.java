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
 * 世界观设定实体。
 * <p>对应表 novel_world_setting,记录地理/势力/武学/历史等设定,供 LLM 通过 queryWorldSetting 工具查询。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "novel_world_setting",
        uniqueConstraints = @UniqueConstraint(columnNames = {"novel_id", "keyword"})
)
public class NovelWorldSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(nullable = false, length = 100)
    private String keyword;         // 设定关键词(青州/听潮阁/武学品阶)

    @Column(length = 50)
    private String category;        // 分类:地理/势力/武学/历史

    @Column(columnDefinition = "TEXT")
    private String description;     // 详细设定文本

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
