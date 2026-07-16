package ink.realm.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import ink.realm.common.entity.BaseEntity;
import ink.realm.common.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 漏斗埋点事件实体(UX-11)。
 * <p>对应表 agent_log,记录用户在关键漏斗节点的行为轨迹,
 * 用于后续转化率分析。允许 user_id / novel_id 为空,兼容匿名上报。</p>
 *
 * @author songshan.li
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "漏斗埋点事件实体")
@TableName(value = "agent_log", autoResultMap = true)
public class AgentLog extends BaseEntity {

    /** 触发用户 id;未登录场景可为空。 */
    @Schema(description = "触发用户 ID(未登录可为空)")
    private Long userId;

    /** 关联小说 id;login 等全局事件可为空。 */
    @Schema(description = "关联小说 ID(全局事件可为空)")
    private Long novelId;

    /** 事件类型,例如 funnel.login,NOT NULL。 */
    @Schema(description = "事件类型(如 funnel.login)")
    private String eventType;

    /**
     * 附加属性,以 JSON 字符串形式落入 JSONB 列。
     * <p>通过 MyBatis-Plus 的 {@link JsonbTypeHandler} 以 jsonb 类型读写,
     * 避免与 varchar 类型冲突。</p>
     */
    @Schema(description = "附加属性(JSON 字符串,落入 JSONB 列)")
    @TableField(value = "props", typeHandler = JsonbTypeHandler.class)
    private String props;
}
