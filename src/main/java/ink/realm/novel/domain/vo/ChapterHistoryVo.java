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
 * 章节历史快照 VO(BASE-07)。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterHistoryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long chapterId;
    private Integer chapterNo;
    private String title;
    private String content;
    private Integer wordCount;
    private String sessionId;
    private Long snapshotVersion;
    private LocalDateTime createdAt;
}
