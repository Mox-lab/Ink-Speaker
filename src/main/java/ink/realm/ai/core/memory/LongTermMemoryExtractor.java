package ink.realm.ai.core.memory;

import ink.realm.ai.domain.agent.CharacterProfile;
import ink.realm.ai.agent.CharacterExtractionAgent;
import ink.realm.common.context.NovelContext;
import ink.realm.novel.mapper.NovelCharacterMapper;
import ink.realm.novel.domain.entity.NovelCharacter;
import ink.realm.novel.domain.vo.CharacterVo;
import ink.realm.util.NovelConstants;
import ink.realm.util.VoConverters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 长期记忆抽取器。
 * <p>对标 cc-haha {@code src/services/extractMemories.ts}。
 * 每生成完一段文本(章节/大纲)后,异步调用 LLM 抽取:</p>
 * <ul>
 *   <li>新出现的人物 → 写入 {@code novel_character} 表</li>
 *   <li>人物状态变化(受伤/升级/黑化) → 更新 background 字段</li>
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
    private final NovelCharacterMapper characterDao;

    @Value("${ink.current-id:" + NovelConstants.DEFAULT_NOVEL_ID + "}")
    private Long fallbackNovelId;

    @Value("${ink.memory.extract-on-chapter-save:true}")
    private boolean extractEnabled;

    /**
     * 异步从文本中抽取人物档案并写回业务表。
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
            if (characterDao.findByNovelIdAndName(resolved, profile.name()).isPresent()) {
                log.info("[LongTermMemory] 人物 '{}' 已存在,跳过自动入库", profile.name());
                return;
            }

            NovelCharacter entity = NovelCharacter.builder()
                    .novelId(resolved)
                    .name(profile.name())
                    .age(profile.age())
                    .gender(profile.gender())
                    .identity(profile.identity())
                    .personality(profile.personality())
                    .appearance(profile.appearance())
                    .weapon(profile.weapon())
                    .background(profile.background())
                    .build();
            characterDao.insert(entity);
            log.info("[LongTermMemory] 第 {} 章抽取人物 '{}' 已入库", chapterNo, profile.name());
        } catch (Exception e) {
            log.warn("[LongTermMemory] 第 {} 章人物抽取失败:{}", chapterNo, e.getMessage());
        }
    }

    /**
     * 列出当前小说的全部已抽取人物(供前端"记忆可视化"面板使用)。
     */
    public List<CharacterVo> listExtractedCharacters() {
        Long novelId = NovelContext.getNovelId();
        Long resolved = novelId != null ? novelId : fallbackNovelId;
        return characterDao.listByNovelId(resolved).stream()
                .map(VoConverters::toVo)
                .toList();
    }
}

