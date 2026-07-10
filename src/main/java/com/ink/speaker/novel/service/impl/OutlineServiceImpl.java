package com.ink.speaker.novel.service.impl;

import com.ink.speaker.common.BusinessException;
import com.ink.speaker.common.ResultCode;
import com.ink.speaker.novel.mapper.NovelOutlineMapper;
import com.ink.speaker.novel.domain.dto.OutlineActivateRequest;
import com.ink.speaker.novel.domain.dto.OutlineSaveRequest;
import com.ink.speaker.novel.domain.entity.NovelOutline;
import com.ink.speaker.novel.service.OutlineService;
import com.ink.speaker.novel.domain.vo.OutlineActiveVo;
import com.ink.speaker.novel.domain.vo.OutlineDetailVo;
import com.ink.speaker.novel.domain.vo.OutlineSaveResultVo;
import com.ink.speaker.novel.domain.vo.OutlineSummaryVo;
import com.ink.speaker.util.ArgsUtil;
import com.ink.speaker.util.NovelConstants;
import com.ink.speaker.util.VoConverters;
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
                .isActive(true)
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
