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
 * 章节时间线实体。
 * <p>对应表 novel_chapter_timeline,记录每章剧情摘要,供 LLM 通过 queryTimeline 工具查询,避免剧情穿帮。</p>
 * <p>唯一约束 (novel_id, chapter_no) 由 Flyway 在库表层维护。</p>
 *
 * @author songshan.li
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "章节时间线实体")
@TableName(value = "novel_chapter_timeline")
public class NovelChapterTimeline extends BaseEntity {

    /** 所属小说 ID,NOT NULL。 */
    @Schema(description = "所属小说 ID")
    private Long novelId;

    /** 章节号,NOT NULL。 */
    @Schema(description = "章节号")
    private Integer chapterNo;

    /** 标题。 */
    @Schema(description = "章节标题")
    private String title;

    /** 摘要。 */
    @Schema(description = "剧情摘要")
    private String summary;
}
