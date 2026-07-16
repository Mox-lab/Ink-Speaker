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
 * 章节正文实体。
 * <p>对应表 novel_chapter_content,存章节正文全文。</p>
 * <p>与 novel_chapter_timeline(summary 摘要)分工:timeline 由 Agent 自动写,
 * content 由用户主动保存。</p>
 *
 * <p>乐观锁:version 字段由 MyBatis-Plus {@link Version} 维护,
 * 配合 OptimisticLockerInnerInterceptor 在 UPDATE 时校验版本,防止并发保存覆写。</p>
 *
 * @author songshan.li
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "章节正文实体")
@TableName(value = "novel_chapter_content")
public class NovelChapterContent extends BaseEntity {

    /** 所属小说 ID,NOT NULL。 */
    @Schema(description = "所属小说 ID")
    private Long novelId;

    /** 大纲 ID。 */
    @Schema(description = "关联的大纲版本 ID(可空)")
    private Long outlineId;

    /** 章节号,NOT NULL。 */
    @Schema(description = "章节号")
    private Integer chapterNo;

    /** 标题。 */
    @Schema(description = "章节标题")
    private String title;

    /** 章节正文,NOT NULL。 */
    @Schema(description = "章节正文")
    private String content;

    /** 字数。 */
    @Schema(description = "实际字数")
    private Integer wordCount;

    /** 会话 ID。 */
    @Schema(description = "Memory 会话 ID")
    private String sessionId;

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
