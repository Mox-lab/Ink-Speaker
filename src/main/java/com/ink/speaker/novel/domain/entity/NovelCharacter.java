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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 人物档案实体。
 * <p>对应表 novel_character,记录每个角色的设定,供 LLM 通过 queryCharacter 工具查询。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "novel_character",
        uniqueConstraints = @UniqueConstraint(columnNames = {"novel_id", "name"})
)
public class NovelCharacter extends BaseEntity {

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column
    private Integer age;

    @Column(length = 10)
    private String gender;

    @Column(columnDefinition = "TEXT")
    private String personality;

    @Column(length = 100)
    private String weapon;

    @Column(columnDefinition = "TEXT")
    private String background;

    @Column(columnDefinition = "TEXT")
    private String identity;

    @Column(columnDefinition = "TEXT")
    private String appearance;

    /**
     * 关系列表,JSON 字符串,格式:[{"target":"张三","type":"师徒","note":"..."}]
     * <p>由前端解析为图谱边。DB 列类型为 jsonb,用 Hibernate 6 的 @JdbcTypeCode(JSON)
     * 让 Hibernate 识别为 JSON 类型,避免 validate 模式下与 VARCHAR 校验失败。</p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String relationships;
}
