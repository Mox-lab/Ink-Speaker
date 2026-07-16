package ink.realm.novel.service.impl;

import ink.realm.common.exception.BusinessException;
import ink.realm.common.result.ResultCode;
import ink.realm.novel.mapper.NovelOutlineMapper;
import ink.realm.novel.domain.dto.OutlineActivateRequest;
import ink.realm.novel.domain.dto.OutlineSaveRequest;
import ink.realm.novel.domain.entity.NovelOutline;
import ink.realm.novel.service.OutlineService;
import ink.realm.novel.domain.vo.OutlineActiveVo;
import ink.realm.novel.domain.vo.OutlineDetailVo;
import ink.realm.novel.domain.vo.OutlineSaveResultVo;
import ink.realm.novel.domain.vo.OutlineSummaryVo;
import ink.realm.util.ArgsUtil;
import ink.realm.util.NovelConstants;
import ink.realm.util.VoConverters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 大纲服务实现。
 * <p>多版本管理:每次保存插入新版本并切换激活;切换激活为原子事务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutlineServiceImpl implements OutlineService {

    private final NovelOutlineMapper outlineDao;

    @Override
    public List<OutlineSummaryVo> listOutlines(Long novelId) {
        Long resolved = novelId != null ? novelId : NovelConstants.DEFAULT_NOVEL_ID;
        return outlineDao.listByNovelIdOrderByVersionDesc(resolved).stream()
                .map(o -> VoConverters.toSummaryVo(o, ArgsUtil.truncate(o.getContent(), 200)))
                .toList();
    }

    @Override
    public OutlineDetailVo getOutline(Long id) {
        NovelOutline o = outlineDao.selectById(id);
        if (o == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "大纲不存在: " + id);
        }
        return VoConverters.toDetailVo(o);
    }

    @Override
    public Optional<OutlineActiveVo> getActiveOutline(Long novelId) {
        Long resolved = novelId != null ? novelId : NovelConstants.DEFAULT_NOVEL_ID;
        return outlineDao.findByNovelIdAndActiveTrue(resolved)
                .map(VoConverters::toActiveVo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutlineSaveResultVo saveOutline(OutlineSaveRequest request) {
        Long novelId = request.novelId() != null ? request.novelId() : NovelConstants.DEFAULT_NOVEL_ID;
        Integer chapters = request.chapters() != null ? request.chapters() : 20;
        String content = request.content();

        Integer maxVer = outlineDao.findMaxVersion(novelId);
        int newVer = (maxVer == null ? 0 : maxVer) + 1;

        // 切换激活版本:原子事务内 清空旧 + 插入新
        outlineDao.clearActiveFlag(novelId);
        NovelOutline outline = NovelOutline.builder()
                .novelId(novelId)
                .title(request.title() == null || request.title().isBlank() ? "v" + newVer : request.title())
                .theme(request.theme() != null ? request.theme() : "")
                .chapters(chapters)
                .content(content)
                .version(newVer)
                .active(true)
                .build();
        outlineDao.insert(outline);
        log.info("[saveOutline] novelId={}, version={}, id={}", novelId, newVer, outline.getId());
        return new OutlineSaveResultVo(outline.getId(), newVer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean activateOutline(OutlineActivateRequest request) {
        Long novelId = request.novelId() != null ? request.novelId() : NovelConstants.DEFAULT_NOVEL_ID;
        Long id = request.id();
        outlineDao.clearActiveFlag(novelId);
        int updated = outlineDao.updateActive(id, true);
        log.info("[activateOutline] novelId={}, id={}, updated={}", novelId, id, updated);
        return updated > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOutline(Long id) {
        outlineDao.deleteById(id);
    }
}
