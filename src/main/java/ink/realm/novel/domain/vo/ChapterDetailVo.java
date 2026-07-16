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
 * 章节详情 VO(含正文全文)。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long novelId;
    private Long outlineId;
    private Integer chapterNo;
    private String title;
    private String content;
    private Integer wordCount;
    private String sessionId;
    private LocalDateTime createdAt;
    /** 服务端最后更新时间(UX-08 多设备冲突检测用,客户端保存时回传 clientUpdatedAt)。 */
    private LocalDateTime updatedAt;
}
