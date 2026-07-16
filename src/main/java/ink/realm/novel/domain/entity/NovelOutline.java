package ink.realm.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.realm.common.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 大纲实体(多版本)。
 * <p>对应表 novel_outline。每次保存插入新版本,保留历史。</p>
 * <p>isActive=true 表示当前激活版本,用于「续生」时取上一版本尾段。</p>
 *
 * @author songshan.li
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "大纲实体(多版本)")
@TableName(value = "novel_outline")
public class NovelOutline extends BaseEntity {

    /** 所属小说 ID,NOT NULL。 */
    @Schema(description = "所属小说 ID")
    private Long novelId;

    /** 标题。 */
    @Schema(description = "大纲标题(用户起名)")
    private String title;

    /** 主题蓝图。 */
    @Schema(description = "题材蓝图")
    private String theme;

    /** 目标章节数。 */
    @Schema(description = "目标章节数")
    private Integer chapters;

    /** 大纲全文,NOT NULL。 */
    @Schema(description = "大纲全文(markdown)")
    private String content;

    /** 版本号,NOT NULL,默认 1(业务版本,非乐观锁)。 */
    @Schema(description = "版本号(业务版本,非乐观锁)")
    @Builder.Default
    private Integer version = 1;

    /** 当前激活版本(用于「续生」时取上一版本尾段)。NOT NULL,默认 false。 */
    @Schema(description = "当前激活版本(用于「续生」时取上一版本尾段)")
    @TableField("is_active")
    @Builder.Default
    private boolean active = false;
}
