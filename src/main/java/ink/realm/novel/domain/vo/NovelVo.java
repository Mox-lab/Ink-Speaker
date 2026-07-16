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
 * 小说 VO(Controller 返回视图)。
 *
 * <p>阿里规范:VO 用于 Controller 层向前端返回,不暴露 Entity 的 ORM 注解。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private String author;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
