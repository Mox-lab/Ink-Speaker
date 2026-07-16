package ink.realm.novel.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 时间线事件 VO(BASE-08 工作台时间线聚合)。
 * <p>聚合章节保存、大纲创建、审查问题反馈等事件,统一格式输出。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineEventVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件类型:chapter_saved / outline_created / review_added / character_added 等。 */
    private String type;

    /** 事件关联的资源 ID。 */
    private Long resourceId;

    /** 事件标题(前端直接展示)。 */
    private String title;

    /** 事件描述/摘要。 */
    private String description;

    /** 事件发生时间。 */
    private LocalDateTime timestamp;
}
