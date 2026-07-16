package ink.realm.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import ink.realm.common.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 小说主表实体。
 * <p>对应表 novel。每本小说归属一个用户(R5 用户隔离)。</p>
 *
 * @author songshan.li
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "小说主表")
@TableName(value = "novel")
public class Novel extends BaseEntity {

    /** 标题,NOT NULL。 */
    @Schema(description = "小说标题")
    private String title;

    /** 作者。 */
    @Schema(description = "作者")
    private String author;

    /** 简介。 */
    @Schema(description = "简介")
    private String description;

    /**
     * 所有者用户 ID(R5 用户隔离)。
     * <p>仅 owner 可读/写本小说的全部数据;其他用户访问时返回 403。</p>
     */
    @Schema(description = "所有者用户 ID(R5 用户隔离)")
    private Long ownerId;

    /**
     * 是否公开到公共参考池(R5 跨小说参考)。
     * <p>owner 设为 true 后,该小说的脱敏片段可被其他用户的 RAG 检索命中。</p>
     */
    @Schema(description = "是否公开到公共参考池(R5 跨小说参考)")
    @Builder.Default
    private boolean sharedForReference = false;
}
