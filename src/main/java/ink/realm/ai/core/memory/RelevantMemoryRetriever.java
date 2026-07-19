package ink.realm.ai.core.memory;

import ink.realm.ai.domain.agent.LoreSearchHit;
import ink.realm.ai.service.KnowledgeBaseService;
import ink.realm.common.context.NovelContext;
import ink.realm.novel.mapper.NovelChapterTimelineMapper;
import ink.realm.novel.mapper.NovelWorldSettingMapper;
import ink.realm.novel.domain.entity.NovelChapterTimeline;
import ink.realm.novel.domain.entity.NovelWorldSetting;
import ink.realm.novel.domain.vo.CharacterVo;
import ink.realm.util.NovelConstants;
import ink.realm.util.VoConverters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 相关记忆召回器。
 * <p>混合两部分:</p>
 * <ul>
 *   <li>结构化业务表:人物档案、最近 N 章时间线(精确,优先级高)</li>
 *   <li>向量库 RAG:从知识库中检索语义相关片段(模糊,补充)</li>
 * </ul>
 *
 * <p>用于章节生成前的上下文组装,以及"前情提要"构造。</p>
 *
 * <p><b>novelId 解析:</b>优先使用 {@link NovelContext#requireNovelId()}(由 X-Novel-Id 头注入);
 * 异步场景或上下文缺失时回退到 {@code ink.current-id} 配置(仅用于单小说兜底)。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelevantMemoryRetriever {

    private final KnowledgeBaseService knowledgeBaseService;
    private final NovelWorldSettingMapper worldSettingDao;
    private final NovelChapterTimelineMapper timelineDao;

    @Value("${ink.current-id:" + NovelConstants.DEFAULT_NOVEL_ID + "}")
    private Long fallbackNovelId;

    @Value("${ink.rag-top-k:5}")
    private int topK;

    /**
     * 召回相关记忆。
     *
     * @param query 关键词 / 当前章节大纲 / 用户问题
     * @return 组装好的上下文文本(可直接拼到 prompt 里)
     */
    public String retrieve(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        Long novelId = resolveNovelId();
        StringBuilder sb = new StringBuilder();
        appendCharacters(sb, novelId);
        appendRecentTimeline(sb, novelId);
        appendSemanticHits(sb, query);
        return sb.toString();
    }

    /** 追加人物档案段(全部,来源:设定集「人物」分类)。 */
    private void appendCharacters(StringBuilder sb, Long novelId) {
        List<CharacterVo> characters = worldSettingDao.listByNovelIdAndCategory(novelId, "人物")
                .stream().map(VoConverters::toCharacterVo).toList();
        if (characters.isEmpty()) {
            return;
        }
        sb.append("【人物档案】\n");
        for (CharacterVo c : characters) {
            sb.append("- ").append(c.getName());
            if (c.getPersonality() != null) {
                sb.append(" / ").append(c.getPersonality());
            }
            if (c.getWeapon() != null) {
                sb.append(" / 武器:").append(c.getWeapon());
            }
            sb.append("\n");
        }
    }

    /** 追加最近 3 章时间线段。 */
    private void appendRecentTimeline(StringBuilder sb, Long novelId) {
        List<NovelChapterTimeline> recent = timelineDao.findRecentChapters(novelId, 3);
        if (recent.isEmpty()) {
            return;
        }
        sb.append("\n【最近剧情】\n");
        for (NovelChapterTimeline t : recent) {
            sb.append("- 第").append(t.getChapterNo()).append("章 ");
            if (t.getTitle() != null) {
                sb.append(t.getTitle());
            }
            if (t.getSummary() != null) {
                sb.append(":").append(t.getSummary());
            }
            sb.append("\n");
        }
    }

    /** 追加向量库语义召回段(失败时降级,不阻断主流程)。 */
    private void appendSemanticHits(StringBuilder sb, String query) {
        try {
            List<LoreSearchHit> hits = knowledgeBaseService.search(query);
            if (hits.isEmpty()) {
                return;
            }
            sb.append("\n【相关知识库片段】\n");
            int limit = Math.min(hits.size(), topK);
            for (int i = 0; i < limit; i++) {
                String text = hits.get(i).text();
                sb.append("- ").append(text, 0, Math.min(200, text.length())).append("\n");
            }
        } catch (Exception e) {
            log.warn("[RelevantMemoryRetriever] 向量检索失败:{}", e.getMessage());
        }
    }

    /**
     * 仅召回语义片段(用于"前情提要"构造,不掺结构化数据)。
     */
    public List<String> retrieveSemantic(String query) {
        List<String> result = new ArrayList<>();
        try {
            for (LoreSearchHit hit : knowledgeBaseService.search(query)) {
                result.add(hit.text());
            }
        } catch (Exception e) {
            log.warn("[RelevantMemoryRetriever] semantic 检索失败:{}", e.getMessage());
        }
        return result;
    }

    /**
     * 解析当前请求的 novelId。
     * <p>同步请求走 NovelContext;异步调用方应直接调用 {@link #retrieve(String, Long)} 显式传参。</p>
     */
    private Long resolveNovelId() {
        Long ctxId = NovelContext.getNovelId();
        return ctxId != null ? ctxId : fallbackNovelId;
    }
}

