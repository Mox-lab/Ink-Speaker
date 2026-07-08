package com.ink.speaker.novel.service.impl;

import com.ink.speaker.novel.mapper.NovelWorldSettingMapper;
import com.ink.speaker.novel.domain.dto.SettingSaveRequest;
import com.ink.speaker.novel.domain.entity.NovelWorldSetting;
import com.ink.speaker.novel.service.SettingService;
import com.ink.speaker.novel.domain.vo.SaveResultVo;
import com.ink.speaker.novel.domain.vo.WorldSettingVo;
import com.ink.speaker.util.NovelConstants;
import com.ink.speaker.util.VoConverters;
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
        worldSettingDao.deleteById(id);
    }
}
