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
 * 人物档案实体。
 * <p>对应表 novel_character,记录每个角色的设定,供 LLM 通过 queryCharacter 工具查询。</p>
 */
@Data                               // Lombok:自动生成 getter/setter/toString/equals/hashCode
@Builder                            // Lombok:生成 Builder 模式构造器
@NoArgsConstructor                  // Lombok:无参构造(JPA 要求)
@AllArgsConstructor                 // Lombok:全参构造
@Entity                             // JPA:标记为实体类
@Table(
        name = "novel_character",   // 映射表名
        uniqueConstraints = @UniqueConstraint(columnNames = {"novel_id", "name"})  // 唯一约束:同一小说内角色名唯一
)
public class NovelCharacter {

    @Id                             // 主键
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // PG SERIAL 自增
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;           // 所属小说 ID(目前硬编码 1)

    @Column(nullable = false, length = 50)
    private String name;            // 人物姓名

    @Column
    private Integer age;            // 年龄

    @Column(length = 10)
    private String gender;          // 性别

    @Column(columnDefinition = "TEXT")
    private String personality;     // 性格描述

    @Column(length = 100)
    private String weapon;          // 武器

    @Column(columnDefinition = "TEXT")
    private String background;      // 背景故事

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;  // 由 PG DEFAULT CURRENT_TIMESTAMP 维护

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
