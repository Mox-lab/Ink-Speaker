package ink.realm.novel.service;

import ink.realm.novel.domain.dto.CollaboratorInviteRequest;
import ink.realm.novel.domain.dto.CollaboratorUpdateRequest;
import ink.realm.novel.domain.vo.CollaboratorVo;

import java.util.List;

/**
 * 小说协作者服务(BASE-11 多用户协作)。
 *
 * @author songshan.li
 */
public interface CollaboratorService {

    /** 列出某本小说的全部协作者(含用户名)。 */
    List<CollaboratorVo> listByNovelId(Long novelId);

    /** 邀请协作者(仅 owner 可操作)。 */
    CollaboratorVo invite(Long novelId, CollaboratorInviteRequest request);

    /** 修改协作者角色(仅 owner 可操作)。 */
    void updateRole(Long novelId, Long collaboratorId, CollaboratorUpdateRequest request);

    /** 移除协作者(仅 owner 可操作)。 */
    void remove(Long novelId, Long collaboratorId);

    /**
     * 解析当前用户对某小说的角色。
     *
     * @return owner / editor / viewer / admin / null(无权访问)
     */
    String resolveRole(Long novelId, Long userId);

    /** 读权限:owner / editor / viewer / admin 均可;否则抛 NOT_FOUND。 */
    void requireViewerAccess(Long novelId, Long userId);

    /** 编辑权限:仅 owner / editor;否则抛 FORBIDDEN。 */
    void requireEditorAccess(Long novelId, Long userId);
}
