package com.ink.speaker.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 实体基类。
 * <p>统一封装主键 id 与 createdAt / updatedAt 时间戳字段。
 * created_at / updated_at 由数据库 DEFAULT CURRENT_TIMESTAMP 维护,
 * 此处通过 insertable=false / updatable=false 禁止 JPA 覆盖。</p>
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
