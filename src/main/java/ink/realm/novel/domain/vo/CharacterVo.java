package ink.realm.novel.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 人物档案 VO。
 * <p>relationships 在 Entity 中是 JSON 字符串,VO 层暴露为 Map 便于前端直接消费。</p>
 *
 * <p><b>序列化说明:</b>HTTP 接口走 Jackson(JSON),不依赖 Java 原生序列化。
 * {@link Serializable} 仅作为 Spring 缓存场景的兜底契约,relationships 标记为
 * {@code transient} —— Java 原生序列化时跳过 Map 字段(避免不可序列化值抛异常),
 * Jackson 序列化时不影响输出。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacterVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long novelId;
    private String name;
    private Integer age;
    private String gender;
    private String personality;
    private String weapon;
    private String background;
    private String identity;
    private String appearance;
    /** 人物关系图(Java 原生序列化时跳过,Jackson 输出不受影响)。 */
    private transient Map<String, Object> relationships;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
