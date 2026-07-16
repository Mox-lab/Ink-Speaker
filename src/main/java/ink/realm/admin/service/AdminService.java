package ink.realm.admin.service;

import ink.realm.admin.domain.vo.AdminNovelVo;
import ink.realm.admin.domain.vo.AdminUserVo;
import ink.realm.common.result.PageResult;

/**
 * 管理后台服务。
 *
 * <p>仅管理员可调用(SecurityConfig 已对 {@code /api/admin/**} 施加 {@code hasRole("ADMIN")})。</p>
 *
 * @author songshan.li (ID: 17099618)
 */
public interface AdminService {

    /** 分页列出全平台小说(只读)。 */
    PageResult<AdminNovelVo> listNovels(long page, long size);

    /** 分页列出全部用户(含角色)。 */
    PageResult<AdminUserVo> listUsers(long page, long size);

    /** 启用/禁用指定用户(允许修改)。 */
    void setUserEnabled(Long userId, boolean enabled);
}
