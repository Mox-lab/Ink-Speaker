package ink.realm.ai.core.memory;

import ink.realm.ai.domain.agent.CharacterProfile;
import ink.realm.ai.domain.agent.CharacterRelationship;
import ink.realm.ai.agent.CharacterExtractionAgent;
import ink.realm.common.context.NovelContext;
import ink.realm.novel.domain.entity.NovelWorldSetting;
import ink.realm.novel.mapper.NovelWorldSettingMapper;
import ink.realm.util.JsonUtil;
import ink.realm.util.NovelConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 长期记忆抽取器。
 * <p>对标 cc-haha {@code src/services/extractMemories.ts}。
 * 每生成完一段文本(章节/大纲)后,异步调用 LLM 抽取:</p>
 * <ul>
 *   <li>新出现的人物 → 写入设定集「人物」分类(novel_world_setting, category='人物')</li>
 *   <li>人物状态变化(受伤/升级/黑化) → 更新 background 字段(后续可扩展)</li>
 *   <li>新设定关键词 → 写入 {@code novel_world_setting}(待实现,P2 阶段补)</li>
 * </ul>
 *
 * <p>调用方式:章节保存后异步触发,不阻塞用户。</p>
 *
 * <p><b>novelId 解析:</b>同步入口走 {@link NovelContext};
 * {@code @Async} 异步入口由调用方显式传入 novelId(ThreadLocal 不跨线程)。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongTermMemoryExtractor {

    private final CharacterExtractionAgent extractionAgent;
    private final NovelWorldSettingMapper worldSettingDao;

    @Value("${ink.current-id:" + NovelConstants.DEFAULT_NOVEL_ID + "}")
    private Long fallbackNovelId;

    @Value("${ink.memory.extract-on-chapter-save:true}")
    private boolean extractEnabled;

    /**
     * 异步从文本中抽取人物档案并写回设定集「人物」分类。
     * <p>幂等:同名人物已存在则跳过(不覆盖作者手填的设定)。</p>
     *
     * @param text      章节正文 / 大纲 / 任意含人物描写的文本
     * @param chapterNo 章节序号(仅用于日志)
     */
    @Async
    public void extractAndPersistCharacters(String text, int chapterNo) {
        extractAndPersistCharacters(text, chapterNo, NovelContext.getNovelId());
    }

    /**
     * 异步入口 + 显式 novelId(供 ChapterServiceImpl 等异步调用方使用)。
     * <p>异步线程无法继承 ThreadLocal,调用方必须显式传入。</p>
     */
    @Async
    public void extractAndPersistCharacters(String text, int chapterNo, Long novelId) {
        Long resolved = novelId != null ? novelId : fallbackNovelId;
        if (!extractEnabled || text == null || text.isBlank()) {
            return;
        }
        try {
            log.info("[LongTermMemory] 开始抽取第 {} 章(novelId={})人物,文本长度={}",
                    chapterNo, resolved, text.length());
            CharacterProfile profile = extractionAgent.extract(text);
            if (profile == null || profile.name() == null || profile.name().isBlank()) {
                log.info("[LongTermMemory] 第 {} 章未抽取到具名人物", chapterNo);
                return;
            }

            // 同名跳过:作者手填的设定优先,不自动覆盖
            if (worldSettingDao.findByNovelIdAndKeyword(resolved, profile.name()).isPresent()) {
                log.info("[LongTermMemory] 人物 '{}' 已存在,跳过自动入库", profile.name());
                return;
            }

            NovelWorldSetting entity = NovelWorldSetting.builder()
                    .novelId(resolved)
                    .keyword(profile.name())
                    .category("人物")
                    .description(buildCharacterDescription(profile))
                    .build();
            worldSettingDao.insert(entity);
            log.info("[LongTermMemory] 第 {} 章抽取人物 '{}' 已入库(设定集人物分类)", chapterNo, profile.name());
        } catch (Exception e) {
            log.warn("[LongTermMemory] 第 {} 章人物抽取失败:{}", chapterNo, e.getMessage());
        }
    }

    /**
     * 把抽取到的人物档案序列化为设定集「人物」结构的 description(JSON 字符串)。
     */
    private String buildCharacterDescription(CharacterProfile p) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("_struct", "character");
        data.put("text", p.background() == null ? "" : p.background());
        data.put("gender", p.gender() == null ? "" : p.gender());
        data.put("age", p.age() == null ? 0 : p.age());
        data.put("identity", p.identity() == null ? "" : p.identity());
        data.put("personality", p.personality() == null ? "" : p.personality());
        data.put("appearance", p.appearance() == null ? "" : p.appearance());
        data.put("weapon", p.weapon() == null ? "" : p.weapon());
        data.put("background", p.background() == null ? "" : p.background());
        data.put("faction", "");
        List<Map<String, Object>> relations = new ArrayList<>();
        if (p.relationships() != null) {
            for (CharacterRelationship r : p.relationships()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("target", r.target());
                m.put("type", r.type());
                m.put("desc", r.note());
                relations.add(m);
            }
        }
        data.put("relations", relations);
        data.put("tags", new ArrayList<>());
        try {
            return JsonUtil.MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("[LongTermMemory] 序列化人物 description 失败: {}", e.getMessage());
            return "{}";
        }
    }
}
