package ink.realm.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分页响应包装。
 *
 * <p>统一承载 {@code list / total / page / size / pages},供管理后台列表接口返回。</p>
 *
 * @param <T> 数据行类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页数据行。 */
    private List<T> list;

    /** 总记录数。 */
    private long total;

    /** 当前页码(从 1 开始)。 */
    private long page;

    /** 每页大小。 */
    private long size;

    /** 总页数。 */
    private long pages;

    /**
     * 基于 MyBatis-Plus 的分页对象构建(数据行已转换为目标 VO 列表)。
     *
     * @param list 已转换的当前页数据
     * @param page MyBatis-Plus 分页对象(提供 total/current/size/pages)
     * @param <T>  数据行类型
     * @return 分页响应包装
     */
    public static <T> PageResult<T> of(List<T> list, IPage<?> page) {
        return new PageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }
}
