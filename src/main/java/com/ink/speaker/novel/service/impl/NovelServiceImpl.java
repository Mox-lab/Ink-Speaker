package com.ink.speaker.novel.service.impl;

import com.ink.speaker.common.NovelContext;
import com.ink.speaker.novel.domain.entity.Novel;
import com.ink.speaker.novel.mapper.NovelMapper;
import com.ink.speaker.novel.service.NovelService;
import com.ink.speaker.novel.domain.vo.NovelVo;
import com.ink.speaker.util.VoConverters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 小说主表服务实现。
 *
 * <p>第 5 阶段(R5 用户隔离):</p>
 * <ul>
 *   <li>{@link #listNovels()} 仅返回当前用户拥有的小说(走 {@link NovelMapper#listByOwnerId})</li>
 *   <li>{@link #listSharedForReference()} 返回公共参考池的脱敏小说列表(跨小说参考)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NovelServiceImpl implements NovelService {

    private final NovelMapper novelDao;

    @Override
    public List<NovelVo> listNovels() {
        Long userId = NovelContext.requireUserId();
        List<NovelVo> novels = novelDao.listByOwnerId(userId).stream()
                .map(VoConverters::toVo)
                .toList();
        log.info("[listNovels] userId={}, size={}", userId, novels.size());
        return novels;
    }

    @Override
    public List<NovelVo> listSharedForReference() {
        List<Novel> novels = novelDao.listSharedForReference();
        List<NovelVo> result = novels.stream()
                .map(n -> NovelVo.builder()
                        .id(n.getId())
                        .title(n.getTitle())
                        .author(n.getAuthor())
                        .description(n.getDescription())
                        .build())
                .toList();
        log.info("[listSharedForReference] size={}", result.size());
        return result;
    }
}
