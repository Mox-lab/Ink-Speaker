package ink.realm.novel.service.impl;

import ink.realm.common.context.NovelContext;
import ink.realm.common.exception.BusinessException;
import ink.realm.common.result.ResultCode;
import ink.realm.novel.mapper.NovelWorldSettingMapper;
import ink.realm.novel.domain.dto.SettingSaveRequest;
import ink.realm.novel.domain.entity.NovelWorldSetting;
import ink.realm.novel.service.CollaboratorService;
import ink.realm.novel.service.SettingService;
import ink.realm.novel.domain.vo.SaveResultVo;
import ink.realm.novel.domain.vo.WorldSettingVo;
import ink.realm.util.NovelConstants;
import ink.realm.util.VoConverters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 世界观设定服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingServiceImpl implements SettingService {

    private final NovelWorldSettingMapper worldSettingDao;
    private final CollaboratorService collaboratorService;

    @Override
    public List<WorldSettingVo> listSettings(Long novelId) {
        Long resolved = novelId != null ? novelId : NovelConstants.DEFAULT_NOVEL_ID;
        return worldSettingDao.listByNovelId(resolved).stream()
                .map(VoConverters::toVo)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaveResultVo saveSetting(SettingSaveRequest request) {
        Long novelId = request.novelId() != null ? request.novelId() : NovelConstants.DEFAULT_NOVEL_ID;
        // 仅 owner / editor 可保存设定(BASE-11 多用户协作)
        collaboratorService.requireEditorAccess(novelId, NovelContext.requireUserId());

        NovelWorldSetting entity;
        String originalKeyword = null;
        if (request.id() != null) {
            // 编辑态:按主键定位既存行并就地更新;即便 keyword 被改名也不会产生重复行。
            // 主键无效(不属于本小说)则退化为按 keyword 的 upsert。
            entity = worldSettingDao.selectById(request.id());
            if (entity == null || !novelId.equals(entity.getNovelId())) {
                entity = worldSettingDao.findByNovelIdAndKeyword(novelId, request.keyword()).orElse(null);
            }
        } else {
            entity = worldSettingDao.findByNovelIdAndKeyword(novelId, request.keyword()).orElse(null);
        }
        if (entity != null) {
            originalKeyword = entity.getKeyword();
        }

        if (entity == null) {
            // 同名(keyword)可能因逻辑删除残留(is_del=1)而仍占用唯一键,
            // 物理清理同唯一键的旧行后再插入,避免 (novel_id, keyword) 唯一约束冲突
            worldSettingDao.physicallyDeleteByNovelIdAndKeyword(novelId, request.keyword());
            entity = new NovelWorldSetting();
            entity.setNovelId(novelId);
        }
        if (request.keyword() != null) {
            entity.setKeyword(request.keyword());
        }
        if (request.category() != null) {
            entity.setCategory(request.category());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }

        if (entity.getId() == null) {
            worldSettingDao.insert(entity);
        } else {
            // 编辑态若改名(keyword 变化)到被软删的旧行占用,先物理清理避免唯一约束冲突
            if (request.keyword() != null && !request.keyword().equals(originalKeyword)) {
                worldSettingDao.physicallyDeleteByNovelIdAndKeyword(novelId, request.keyword());
            }
            worldSettingDao.updateById(entity);
        }
        log.info("[saveSetting] novelId={}, keyword={}, id={}", novelId, request.keyword(), entity.getId());
        return new SaveResultVo(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSetting(Long id) {
        NovelWorldSetting setting = worldSettingDao.selectById(id);
        if (setting == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "世界观设定不存在: " + id);
        }
        // 仅 owner / editor 可删除设定(BASE-11 多用户协作)
        collaboratorService.requireEditorAccess(setting.getNovelId(), NovelContext.requireUserId());
        // 物理删除:彻底移除该行,释放 (novel_id, keyword) 唯一键,允许同名设定重新创建
        worldSettingDao.physicallyDeleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SaveResultVo> batchSaveSettings(List<SettingSaveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        // 复用单条 upsert 逻辑(按 novelId+keyword 覆盖),逐条做权限校验
        return requests.stream()
                .filter(r -> r != null)
                .map(this::saveSetting)
                .toList();
    }

    @Override
    public List<WorldSettingVo> searchByKeyword(Long novelId, String keyword, String category) {
        Long resolved = novelId != null ? novelId : NovelConstants.DEFAULT_NOVEL_ID;
        List<NovelWorldSetting> list;
        boolean hasCategory = category != null && !category.isBlank();
        if (keyword == null || keyword.isBlank()) {
            // 无关键词:有分类则按分类筛选,否则返回全部
            list = hasCategory
                    ? worldSettingDao.listByNovelIdAndCategory(resolved, category.trim())
                    : worldSettingDao.listByNovelId(resolved);
        } else {
            // 有关键词:有分类则在指定分类内模糊匹配,否则全分类模糊匹配
            list = hasCategory
                    ? worldSettingDao.searchByNovelIdAndKeywordContainingAndCategory(resolved, keyword.trim(), category.trim())
                    : worldSettingDao.searchByNovelIdAndKeywordContaining(resolved, keyword.trim());
        }
        return list.stream().map(VoConverters::toVo).toList();
    }
}
