package ink.realm.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体基类。
 * <p>统一封装主键 id、审计字段(ct_time / ut_time)与逻辑删除字段(is_del)。</p>
 * <p>审计字段与逻辑删除值由 {@code MybatisMetaObjectHandler} 在 INSERT / UPDATE 时自动填充,
 * 与 Flyway 管理的 schema 保持一致,业务代码无需手动赋值。</p>
 *
 * @author songshan.li
 */
@Data
public abstract class BaseEntity {

    /** 主键,数据库 IDENTITY 自增。 */
    @Schema(description = "主键,数据库 IDENTITY 自增")
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建时间,INSERT 时自动填充。 */
    @Schema(description = "创建时间,INSERT 时自动填充")
    @TableField(value = "ct_time", fill = FieldFill.INSERT)
    private LocalDateTime ctTime;

    /** 更新时间,INSERT / UPDATE 时自动填充。 */
    @Schema(description = "更新时间,INSERT / UPDATE 时自动填充")
    @TableField(value = "ut_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime utTime;

    /** 逻辑删除标记:0-未删除,1-已删除(INSERT 时自动填充为 0)。 */
    @Schema(description = "逻辑删除标记:0-未删除,1-已删除")
    @TableLogic(value = "0", delval = "1")
    @TableField(value = "is_del", fill = FieldFill.INSERT)
    private Integer isDel;
}
