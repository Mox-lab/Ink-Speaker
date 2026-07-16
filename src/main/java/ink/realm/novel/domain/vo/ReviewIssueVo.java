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
 * 章节审查问题 VO。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewIssueVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long novelId;
    private Integer chapterNo;
    private String severity;
    private String category;
    private String location;
    private String description;
    private String suggestion;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
