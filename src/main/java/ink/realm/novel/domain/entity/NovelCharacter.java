package ink.realm.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.realm.common.entity.BaseEntity;
import ink.realm.common.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * 人物档案实体。
 * <p>对应表 novel_character,记录每个角色的设定,供 LLM 通过 queryCharacter 工具查询。</p>
 * <p>唯一约束 (novel_id, name) 由 Flyway 在库表层维护。</p>
 *
 * @author songshan.li
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "人物档案实体")
@TableName(value = "novel_character", autoResultMap = true)
public class NovelCharacter extends BaseEntity {

    /** 所属小说 ID,NOT NULL。 */
    @Schema(description = "所属小说 ID")
    private Long novelId;

    /** 角色名,NOT NULL。 */
    @Schema(description = "角色名")
    private String name;

    /** 年龄。 */
    @Schema(description = "年龄")
    private Integer age;

    /** 性别。 */
    @Schema(description = "性别")
    private String gender;

    /** 性格。 */
    @Schema(description = "性格描述")
    private String personality;

    /** 武器。 */
    @Schema(description = "武器")
    private String weapon;

    /** 背景。 */
    @Schema(description = "背景故事")
    private String background;

    /** 身份。 */
    @Schema(description = "身份/职业")
    private String identity;

    /** 外貌。 */
    @Schema(description = "外貌描述")
    private String appearance;

    /**
     * 关系列表,JSON 字符串,格式:[{"target":"张三","type":"师徒","note":"..."}]
     * <p>由前端解析为图谱边。DB 列类型为 jsonb,通过 MyBatis-Plus 的
     * {@link JsonbTypeHandler} 以 jsonb 类型读写,避免与 varchar 类型冲突。</p>
     */
    @Schema(description = "关系列表(JSON 字符串,格式:[{target,type,note}])")
    @TableField(value = "relationships", typeHandler = JsonbTypeHandler.class)
    private String relationships;
}
