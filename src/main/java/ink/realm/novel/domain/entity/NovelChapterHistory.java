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
 * 章节历史版本快照实体(BASE-07)。
 * <p>对应表 novel_chapter_history,每次章节保存时插入一条快照,
 * 保留全部历史版本供用户回溯。</p>
 *
 * @author songshan.li
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "章节历史版本快照实体")
@TableName(value = "novel_chapter_history")
public class NovelChapterHistory extends BaseEntity {

    /** 所属小说 ID,NOT NULL。 */
    @Schema(description = "所属小说 ID")
    private Long novelId;

    /** 章节 ID,NOT NULL。 */
    @Schema(description = "关联章节 ID(novel_chapter_content.id)")
    private Long chapterId;

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
    @Schema(description = "字数")
    private Integer wordCount;

    /** 会话 ID。 */
    @Schema(description = "会话 ID")
    private String sessionId;

    /** 对应章节当时的乐观锁版本号,NOT NULL。 */
    @Schema(description = "对应章节当时的乐观锁版本号")
    private Long snapshotVersion;
}
