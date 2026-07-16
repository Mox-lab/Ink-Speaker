package ink.realm.novel.service.impl;

import ink.realm.ai.agent.ContinuationAgent;
import ink.realm.common.exception.BusinessException;
import ink.realm.common.context.NovelContext;
import ink.realm.common.result.ResultCode;
import ink.realm.novel.domain.entity.Novel;
import ink.realm.novel.domain.entity.NovelChapterContent;
import ink.realm.novel.domain.entity.NovelCharacter;
import ink.realm.novel.domain.vo.ContinuationSuggestionVo;
import ink.realm.novel.mapper.NovelChapterContentMapper;
import ink.realm.novel.mapper.NovelCharacterMapper;
import ink.realm.novel.mapper.NovelMapper;
import ink.realm.novel.mapper.NovelOutlineMapper;
import ink.realm.novel.service.ContinuationService;
import ink.realm.util.ArgsUtil;
import ink.realm.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 续写建议服务实现(BASE-12)。
 *
 * <p>核心流程:</p>
 * <ol>
 *   <li>校验所有权(走 {@link NovelMapper#findByIdAndOwner} 双重过滤)</li>
 *   <li>组装上下文:最近 3 章摘要 + 激活大纲摘要 + 人物档案</li>
 *   <li>调 {@link ContinuationAgent#suggest} 输出 JSON</li>
 *   <li>解析 JSON 转 {@link ContinuationSuggestionVo}(失败兜底,不抛异常)</li>
 * </ol>
 *
 * <p><b>设计权衡:</b>同步阻塞调用 LLM,因前端总览页是用户主动点按钮触发,
 * 等待 3-8 秒可接受;若后续接入 SSE 流式,可改返回 Flux。</p>
 *
 * <p><b>失败兜底:</b>LLM 返回空 / JSON 解析失败时,返回一个带 fallback direction 的 VO,
 * 避免前端拿到 500 错误。日志中保留原始返回用于排查。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContinuationServiceImpl implements ContinuationService {

    /** 召回最近章节的数量。 */
    private static final int RECENT_CHAPTER_LIMIT = 3;

    /** 大纲摘要截断长度(避免 prompt 过长)。 */
    private static final int OUTLINE_SUMMARY_MAX = 800;

    /** 单章摘要截断长度。 */
    private static final int CHAPTER_SUMMARY_MAX = 200;

    /** 人物档案截断长度(全部人物拼接后)。 */
    private static final int CHARACTERS_MAX = 600;

    private final ContinuationAgent continuationAgent;
    private final NovelMapper novelDao;
    private final NovelChapterContentMapper chapterDao;
    private final NovelOutlineMapper outlineDao;
    private final NovelCharacterMapper characterDao;

    @Override
    public ContinuationSuggestionVo suggestNextChapter(Long novelId) {
        Long userId = NovelContext.requireUserId();
        Novel novel = novelDao.findByIdAndOwner(novelId, userId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "小说不存在或不属于当前用户: " + novelId));

        List<NovelChapterContent> chapters = chapterDao.listByNovelIdOrderByChapterNoAsc(novelId);
        int latestChapterNo = chapters.stream()
                .map(NovelChapterContent::getChapterNo)
                .filter(Objects::nonNull)
                .max(Integer::compare)
                .orElse(0);
        int nextChapterNo = latestChapterNo + 1;

        String recentChaptersText = buildRecentChaptersText(chapters);
        String outlineText = outlineDao.findByNovelIdAndActiveTrue(novelId)
                .map(o -> ArgsUtil.truncate(o.getContent(), OUTLINE_SUMMARY_MAX))
                .orElse("");
        String charactersText = buildCharactersText(characterDao.listByNovelId(novelId));

        ContinuationSuggestionVo fallback = ContinuationSuggestionVo.builder()
                .nextChapterNo(nextChapterNo)
                .direction("AI 暂时无法生成建议,请稍后再试或先补全大纲/人物档案。")
                .keyCharacters(Collections.emptyList())
                .risks(Collections.emptyList())
                .generatedAt(LocalDateTime.now())
                .build();

        try {
            String json = continuationAgent.suggest(
                    outlineText,
                    recentChaptersText,
                    charactersText,
                    latestChapterNo,
                    nextChapterNo);
            if (json == null || json.isBlank()) {
                log.warn("[Continuation] agent 返回空, novelId={}, nextChapterNo={}", novelId, nextChapterNo);
                return fallback;
            }
            return parseSuggestion(json, nextChapterNo);
        } catch (Exception e) {
            log.warn("[Continuation] 调用失败, novelId={}, nextChapterNo={}, err={}",
                    novelId, nextChapterNo, e.getMessage());
            return fallback;
        }
    }

    /** 拼接最近 N 章摘要文本。 */
    private String buildRecentChaptersText(List<NovelChapterContent> chapters) {
        if (chapters == null || chapters.isEmpty()) {
            return "(尚未开始写作)";
        }
        int size = chapters.size();
        int from = Math.max(0, size - RECENT_CHAPTER_LIMIT);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < size; i++) {
            NovelChapterContent c = chapters.get(i);
            sb.append("- 第").append(c.getChapterNo()).append("章 ");
            if (c.getTitle() != null && !c.getTitle().isBlank()) {
                sb.append(c.getTitle());
            }
            sb.append(": ").append(ArgsUtil.truncate(c.getContent(), CHAPTER_SUMMARY_MAX)).append("\n");
        }
        return sb.toString().trim();
    }

    /** 拼接人物档案文本(限制总长度)。 */
    private String buildCharactersText(List<NovelCharacter> characters) {
        if (characters == null || characters.isEmpty()) {
            return "(暂无人物档案)";
        }
        StringBuilder sb = new StringBuilder();
        for (NovelCharacter c : characters) {
            sb.append("- ").append(c.getName());
            if (c.getPersonality() != null && !c.getPersonality().isBlank()) {
                sb.append(" / ").append(c.getPersonality());
            }
            if (c.getWeapon() != null && !c.getWeapon().isBlank()) {
                sb.append(" / 武器:").append(c.getWeapon());
            }
            sb.append("\n");
            if (sb.length() > CHARACTERS_MAX) {
                break;
            }
        }
        return sb.toString().trim();
    }

    /** 解析 LLM JSON 输出为 VO,字段缺失时给兜底。 */
    private ContinuationSuggestionVo parseSuggestion(String json, int nextChapterNo) {
        String trimmed = stripCodeFence(json);
        Map<String, Object> map = JsonUtil.parseMap(trimmed);
        if (map.isEmpty()) {
            log.warn("[Continuation] JSON 解析为空 Map, raw={}",
                    json.length() > 500 ? json.substring(0, 500) + "..." : json);
            return ContinuationSuggestionVo.builder()
                    .nextChapterNo(nextChapterNo)
                    .direction("AI 返回内容无法解析,请重试。")
                    .keyCharacters(Collections.emptyList())
                    .risks(Collections.emptyList())
                    .generatedAt(LocalDateTime.now())
                    .build();
        }
        return ContinuationSuggestionVo.builder()
                .nextChapterNo(nextChapterNo)
                .title(getStr(map, "title"))
                .direction(getStr(map, "direction"))
                .conflict(getStr(map, "conflict"))
                .keyCharacters(getStringList(map, "keyCharacters"))
                .hook(getStr(map, "hook"))
                .risks(getStringList(map, "risks"))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /** 去掉 LLM 偶发输出的 ```json ... ``` 包裹。 */
    private String stripCodeFence(String json) {
        String trimmed = json.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }

    private String getStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) {
                    String s = String.valueOf(item).trim();
                    if (!s.isEmpty()) {
                        result.add(s);
                    }
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
