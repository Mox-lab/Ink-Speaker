package com.ink.speaker.novel.service;

import com.ink.speaker.novel.domain.vo.NovelVo;

import java.util.List;

/**
 * 小说主表服务接口。
 *
 * <p>第 5 阶段(R5 用户隔离):所有方法都基于当前用户的 {@code NovelContext.requireUserId()}
 * 过滤,确保每本小说只对作者可见。</p>
 */
public interface NovelService {

    /**
     * 列出当前用户的全部小说(R5 用户隔离)。
     * <p>从 {@link com.ink.speaker.common.NovelContext#requireUserId()} 拿当前用户,
     * 仅返回属于该用户的小说。</p>
     *
     * @return 当前用户的小说列表
     */
    List<NovelVo> listNovels();

    /**
     * 列出公开到公共参考池的小说(R5 跨小说参考)。
     * <p>所有用户都能看到,但仅返回脱敏字段(id/title/author/description),
     * 不暴露 owner_id 等敏感信息。</p>
     *
     * @return 公开小说列表(脱敏)
     */
    List<NovelVo> listSharedForReference();
}
