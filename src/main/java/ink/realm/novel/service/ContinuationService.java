package ink.realm.novel.service;

import ink.realm.novel.domain.vo.ContinuationSuggestionVo;

/**
 * 续写建议服务(BASE-12)。
 * <p>对外暴露同步入口,组装上下文并调用 {@code ContinuationAgent}。</p>
 */
public interface ContinuationService {

    /**
     * 预测下一章走向,返回结构化建议。
     *
     * @param novelId 小说 ID(必须属于当前用户)
     * @return 续写建议 VO
     */
    ContinuationSuggestionVo suggestNextChapter(Long novelId);
}
