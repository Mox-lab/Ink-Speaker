package ink.realm.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器。
 * <p>统一维护审计字段与逻辑删除标记的填充规则:</p>
 * <ul>
 *     <li>INSERT:填充 {@code ct_time}、{@code ut_time} 为当前时间,{@code is_del} 为 0(未删除)</li>
 *     <li>UPDATE:刷新 {@code ut_time} 为当前时间</li>
 * </ul>
 * <p>字段名与 {@code BaseEntity} 中声明的属性名保持一致。</p>
 *
 * @author songshan.li
 */
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    /** 逻辑删除默认值:未删除。 */
    private static final Integer NOT_DELETED = 0;

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        // strict 模式下仅当字段为空时才填充,避免覆盖已显式赋值
        this.strictInsertFill(metaObject, "ctTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "utTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "isDel", Integer.class, NOT_DELETED);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "utTime", LocalDateTime.class, LocalDateTime.now());
    }
}
