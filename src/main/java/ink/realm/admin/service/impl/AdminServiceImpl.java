package ink.realm.admin.service.impl;

import ink.realm.admin.domain.vo.AdminNovelVo;
import ink.realm.admin.domain.vo.AdminUserVo;
import ink.realm.admin.service.AdminService;
import ink.realm.auth.domain.entity.User;
import ink.realm.auth.domain.vo.UserRoleName;
import ink.realm.auth.mapper.UserMapper;
import ink.realm.common.exception.BusinessException;
import ink.realm.common.result.PageResult;
import ink.realm.common.result.ResultCode;
import ink.realm.novel.domain.entity.Novel;
import ink.realm.novel.mapper.NovelMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理后台服务实现。
 *
 * <p>所有入口仅对 ROLE_ADMIN 开放,只读列举全平台小说与用户;{"修改"}仅允许启用/禁用用户。</p>
 *
 * @author songshan.li (ID: 17099618)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final NovelMapper novelDao;
    private final UserMapper userMapper;

    @Override
    public PageResult<AdminNovelVo> listNovels(long page, long size) {
        IPage<Novel> p = novelDao.selectPage(new Page<>(page, size), null);
        List<AdminNovelVo> list = p.getRecords().stream()
                .map(n -> AdminNovelVo.builder()
                        .id(n.getId())
                        .title(n.getTitle())
                        .author(n.getAuthor())
                        .ownerId(n.getOwnerId())
                        .updatedAt(n.getUtTime())
                        .build())
                .toList();
        return PageResult.of(list, p);
    }

    @Override
    public PageResult<AdminUserVo> listUsers(long page, long size) {
        IPage<User> p = userMapper.selectPage(new Page<>(page, size), null);
        List<Long> ids = p.getRecords().stream().map(User::getId).toList();

        // 一次性聚合角色,避免 N+1(遵循 SQL 规范:禁止循环逐条查询)
        List<UserRoleName> roleRows = ids.isEmpty() ? List.of() : userMapper.listUserRoleNames(ids);
        Map<Long, List<String>> roleMap = roleRows.stream()
                .collect(Collectors.groupingBy(
                        UserRoleName::getUserId,
                        Collectors.mapping(UserRoleName::getRoleName, Collectors.toList())));

        List<AdminUserVo> list = p.getRecords().stream()
                .map(u -> AdminUserVo.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .enabled(u.isEnabled())
                        .roles(roleMap.getOrDefault(u.getId(), List.of()))
                        .build())
                .toList();
        return PageResult.of(list, p);
    }

    @Override
    public void setUserEnabled(Long userId, boolean enabled) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在: " + userId);
        }
        u.setEnabled(enabled);
        userMapper.updateById(u);
        log.info("[Admin] setUserEnabled userId={}, enabled={}", userId, enabled);
    }
}
