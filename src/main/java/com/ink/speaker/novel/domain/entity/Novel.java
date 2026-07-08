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
 * 小说主表实体。
 * <p>对应表 novel。每本小说归属一个用户(R5 用户隔离)。</p>
 *
 * <p>第 5 阶段:加 {@code ownerId} 字段实现行级隔离,
 * Service 层在读写小说数据时校验 ownerId == 当前 userId。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "novel")
public class Novel extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 100)
    private String author;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 所有者用户 ID(R5 用户隔离)。
     * <p>仅 owner 可读/写本小说的全部数据;其他用户访问时返回 403。</p>
     * <p>跨小说参考通过公共 RAG 池(脱敏后)实现,不暴露原作者信息。</p>
     */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /**
     * 是否公开到公共参考池(R5 跨小说参考)。
     * <p>owner 设为 true 后,该小说的脱敏片段可被其他用户的 RAG 检索命中。</p>
     */
    @Column(name = "shared_for_reference", nullable = false)
    @Builder.Default
    private boolean sharedForReference = false;
}
