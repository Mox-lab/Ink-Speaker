package ink.realm.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import ink.realm.common.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 章节审查问题(DirectorAgent 协作产物)。
 * <p>当 ReviewAgent 检测到人设崩塌 / 世界观矛盾 / 时间线冲突 / 伏笔断链等问题时,
 * 落库一条记录,供前端侧栏展示与作者处理。</p>
 *
 * <p>乐观锁:version 字段由 MyBatis-Plus {@link Version} 维护,
 * 配合 OptimisticLockerInnerInterceptor 在 UPDATE 时校验版本,
 * 防止作者手动改 status 与后台异步新增问题时的并发覆写。</p>
 *
 * @author songshan.li
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "章节审查问题实体")
@TableName(value = "novel_review_issue")
public class NovelReviewIssue extends BaseEntity {

    /** 所属小说 ID,NOT NULL。 */
    @Schema(description = "所属小说 ID")
    private Long novelId;

    /** 章节号,NOT NULL。 */
    @Schema(description = "问题所属章节号")
    private Integer chapterNo;

    /** 严重度:low / medium / high,NOT NULL。 */
    @Schema(description = "严重度:low / medium / high")
    private String severity;

    /** 分类:人设 / 世界观 / 时间线 / 伏笔 / 节奏 / 其他,NOT NULL。 */
    @Schema(description = "分类(人设/世界观/时间线/伏笔/节奏/其他)")
    private String category;

    /** 问题定位(原文片段或行号描述)。 */
    @Schema(description = "问题定位(原文片段)")
    private String location;

    /** 问题描述,NOT NULL。 */
    @Schema(description = "问题描述")
    private String description;

    /** 修改建议。 */
    @Schema(description = "修改建议")
    private String suggestion;

    /** 状态:open / resolved / ignored,NOT NULL,默认 open。 */
    @Schema(description = "状态:open / resolved / ignored")
    @Builder.Default
    private String status = "open";

    /**
     * 乐观锁版本号。
     * <p>MyBatis-Plus 在 UPDATE 时自动 {@code SET version = version + 1 WHERE version = ?},
     * 版本不匹配则抛 {@link com.baomidou.mybatisplus.core.exceptions.OptimisticLockException}。</p>
     */
    @Schema(description = "乐观锁版本号")
    @Version
    @TableField("version")
    private Long version;
}
