package ink.realm.novel.service.impl;

import ink.realm.auth.domain.entity.User;
import ink.realm.auth.mapper.UserMapper;
import ink.realm.common.context.NovelContext;
import ink.realm.common.exception.BusinessException;
import ink.realm.common.result.ResultCode;
import ink.realm.config.security.SecurityRoles;
import ink.realm.novel.domain.dto.CollaboratorInviteRequest;
import ink.realm.novel.domain.dto.CollaboratorUpdateRequest;
import ink.realm.novel.domain.entity.Novel;
import ink.realm.novel.domain.entity.NovelCollaborator;
import ink.realm.novel.domain.vo.CollaboratorVo;
import ink.realm.novel.mapper.NovelCollaboratorMapper;
import ink.realm.novel.mapper.NovelMapper;
import ink.realm.novel.service.CollaboratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 小说协作者服务实现(BASE-11 多用户协作)。
 * <p>统一的角色解析与权限校验入口:owner 在 {@code novel.owner_id} 体现,
 * 协作者(editor / viewer)在 {@code novel_collaborator} 表体现。
 * 各业务 Service(章节/大纲/人物/设定)通过 {@link #requireEditorAccess} /
 * {@link #requireViewerAccess} 复用此处逻辑,避免重复实现。</p>
 *
 * @author songshan.li
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollaboratorServiceImpl implements CollaboratorService {

    private final NovelCollaboratorMapper collaboratorMapper;
    private final NovelMapper novelDao;
    private final UserMapper userMapper;

    @Override
    public List<CollaboratorVo> listByNovelId(Long novelId) {
        Long ownerId = NovelContext.requireUserId();
        requireOwner(novelId, ownerId);
        return collaboratorMapper.listByNovelId(novelId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollaboratorVo invite(Long novelId, CollaboratorInviteRequest request) {
        Long ownerId = NovelContext.requireUserId();
        requireOwner(novelId, ownerId);

        User user = userMapper.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(ResultCode.PARAM_INVALID,
                        "用户不存在: " + request.username()));

        // 不能把作者本人加为协作者(owner 已天然拥有最高权限)
        if (user.getId().equals(ownerId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "不能将自己添加为协作者");
        }
        // 已存在则冲突(幂等保护)
        if (collaboratorMapper.findByNovelIdAndUserId(novelId, user.getId()) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "该用户已是本小说的协作者");
        }

        NovelCollaborator entity = NovelCollaborator.builder()
                .novelId(novelId)
                .userId(user.getId())
                .role(request.role())
                .build();
        collaboratorMapper.insert(entity);
        log.info("[invite] novelId={}, userId={}, role={}", novelId, user.getId(), request.role());

        return CollaboratorVo.builder()
                .id(entity.getId())
                .novelId(novelId)
                .userId(user.getId())
                .username(user.getUsername())
                .role(entity.getRole())
                .createdAt(entity.getCtTime())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long novelId, Long collaboratorId, CollaboratorUpdateRequest request) {
        Long ownerId = NovelContext.requireUserId();
        requireOwner(novelId, ownerId);

        NovelCollaborator entity = collaboratorMapper.selectById(collaboratorId);
        if (entity == null || !novelId.equals(entity.getNovelId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "协作者不存在");
        }
        entity.setRole(request.role());
        collaboratorMapper.updateById(entity);
        log.info("[updateRole] novelId={}, collaboratorId={}, role={}", novelId, collaboratorId, request.role());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long novelId, Long collaboratorId) {
        Long ownerId = NovelContext.requireUserId();
        requireOwner(novelId, ownerId);

        NovelCollaborator entity = collaboratorMapper.selectById(collaboratorId);
        if (entity == null || !novelId.equals(entity.getNovelId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "协作者不存在");
        }
        // 逻辑删除(配合实体 @TableLogic,is_del=1)
        collaboratorMapper.deleteById(collaboratorId);
        log.info("[remove] novelId={}, collaboratorId={}", novelId, collaboratorId);
    }

    @Override
    public String resolveRole(Long novelId, Long userId) {
        Novel novel = novelDao.selectById(novelId);
        if (novel == null) {
            return null;
        }
        if (novel.getOwnerId().equals(userId)) {
            return "owner";
        }
        if (isAdmin()) {
            return "admin";
        }
        NovelCollaborator collaborator = collaboratorMapper.findByNovelIdAndUserId(novelId, userId);
        return collaborator != null ? collaborator.getRole() : null;
    }

    @Override
    public void requireViewerAccess(Long novelId, Long userId) {
        if (resolveRole(novelId, userId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "小说不存在或无权访问: " + novelId);
        }
    }

    @Override
    public void requireEditorAccess(Long novelId, Long userId) {
        String role = resolveRole(novelId, userId);
        if (!"owner".equals(role) && !"editor".equals(role)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无编辑权限");
        }
    }

    /** 仅所有者可管理协作者(管理员对他人小说亦为只读,不可管理)。 */
    private void requireOwner(Long novelId, Long ownerId) {
        Novel novel = novelDao.selectById(novelId);
        if (novel == null || !novel.getOwnerId().equals(ownerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅小说所有者可管理协作者");
        }
    }

    /** 判断当前登录用户是否为管理员(ROLE_ADMIN)。 */
    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> SecurityRoles.ADMIN.equals(a.getAuthority()));
    }
}
