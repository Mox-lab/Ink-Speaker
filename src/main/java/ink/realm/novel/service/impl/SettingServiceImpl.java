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
        NovelWorldSetting entity = worldSettingDao
                .findByNovelIdAndKeyword(novelId, request.keyword())
                .orElseGet(() -> {
                    NovelWorldSetting w = new NovelWorldSetting();
                    w.setNovelId(novelId);
                    w.setKeyword(request.keyword());
                    return w;
                });
        if (request.category() != null) {
            entity.setCategory(request.category());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (entity.getId() == null) {
            worldSettingDao.insert(entity);
        } else {
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
        worldSettingDao.deleteById(id);
    }

    @Override
    public List<WorldSettingVo> searchByKeyword(Long novelId, String keyword) {
        Long resolved = novelId != null ? novelId : NovelConstants.DEFAULT_NOVEL_ID;
        List<NovelWorldSetting> list;
        if (keyword == null || keyword.isBlank()) {
            list = worldSettingDao.listByNovelId(resolved);
        } else {
            list = worldSettingDao.searchByNovelIdAndKeywordContaining(resolved, keyword.trim());
        }
        return list.stream().map(VoConverters::toVo).toList();
    }
}
