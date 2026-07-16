package ink.realm.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局配置。
 * <p>注册 {@link OptimisticLockerInnerInterceptor},使实体上的
 * {@code @Version} 乐观锁字段真正生效(本项目 novel_chapter_content /
 * novel_review_issue 依赖它防止并发覆写)。</p>
 *
 * @author songshan.li
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 拦截器链。
     * <p>乐观锁拦截器会在 UPDATE 时自动追加
     * {@code SET version = version + 1 WHERE version = ?} 条件。
     * 若实体版本已过期,WHERE 无法匹配目标行,导致 {@code updateById} 返回 0 行,
     * 由业务层(ChapterServiceImpl)检测 0 行并主动抛出
     * {@link com.baomidou.mybatisplus.core.exceptions.MybatisPlusException} 触发重试。</p>
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
