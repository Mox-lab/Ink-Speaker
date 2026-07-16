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
 * 大纲摘要 VO(列表用,不含正文)。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutlineSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long novelId;
    private String title;
    private String theme;
    private Integer chapters;
    private Integer version;
    private boolean active;
    private String contentPreview;
    private int contentLength;
    private LocalDateTime createdAt;
}
