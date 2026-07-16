package ink.realm.novel.service.impl;

import ink.realm.novel.mapper.NovelChapterContentMapper;
import ink.realm.novel.mapper.NovelCharacterMapper;
import ink.realm.novel.domain.dto.CharacterBatchSaveRequest;
import ink.realm.novel.domain.entity.NovelChapterContent;
import ink.realm.novel.domain.entity.NovelCharacter;
import ink.realm.novel.service.CharacterService;
import ink.realm.novel.domain.vo.CharacterBatchSaveResultVo;
import ink.realm.novel.domain.vo.CharacterVo;
import ink.realm.novel.domain.vo.ChapterSummaryVo;
import ink.realm.util.ArgsUtil;
import ink.realm.util.JsonUtil;
import ink.realm.util.NovelConstants;
import ink.realm.util.VoConverters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 人物档案服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterServiceImpl implements CharacterService {

    /** 章节摘要预览长度上限。 */
    private static final int CHAPTER_PREVIEW_LIMIT = 150;

    private final NovelCharacterMapper characterDao;
    private final NovelChapterContentMapper chapterDao;

    @Override
    public List<CharacterVo> listCharacters(Long novelId) {
        Long resolved = novelId != null ? novelId : NovelConstants.DEFAULT_NOVEL_ID;
        return characterDao.listByNovelId(resolved).stream()
                .map(VoConverters::toVo)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CharacterBatchSaveResultVo saveBatch(CharacterBatchSaveRequest request) {
        Long novelId = request.novelId() != null ? request.novelId() : NovelConstants.DEFAULT_NOVEL_ID;
        int saved = 0;
        for (CharacterBatchSaveRequest.CharacterItem c : request.characters()) {
            NovelCharacter entity = resolveOrCreate(novelId, c.name());
            applyCharacterFields(entity, c);
            if (entity.getId() == null) {
                characterDao.insert(entity);
            } else {
                characterDao.updateById(entity);
            }
            saved++;
        }
        log.info("[saveCharactersBatch] novelId={}, saved={}", novelId, saved);
        return new CharacterBatchSaveResultVo(saved);
    }

    /** 按名字查找已有 entity,不存在则构造新 entity(仅设 novelId+name)。 */
    private NovelCharacter resolveOrCreate(Long novelId, String name) {
        return characterDao.findByNovelIdAndName(novelId, name)
                .orElseGet(() -> {
                    NovelCharacter nc = new NovelCharacter();
                    nc.setNovelId(novelId);
                    nc.setName(name);
                    return nc;
                });
    }

    /** 把 DTO 中的非空字段合并到 entity(空字段不覆盖既有值)。 */
    private void applyCharacterFields(NovelCharacter entity, CharacterBatchSaveRequest.CharacterItem c) {
        if (c.age() != null) {
            entity.setAge(c.age());
        }
        if (c.gender() != null) {
            entity.setGender(c.gender());
        }
        if (c.identity() != null) {
            entity.setIdentity(c.identity());
        }
        if (c.personality() != null) {
            entity.setPersonality(c.personality());
        }
        if (c.appearance() != null) {
            entity.setAppearance(c.appearance());
        }
        if (c.weapon() != null) {
            entity.setWeapon(c.weapon());
        }
        if (c.background() != null) {
            entity.setBackground(c.background());
        }
        if (c.relationships() != null) {
            entity.setRelationships(toJson(c.relationships()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCharacter(Long id) {
        characterDao.deleteById(id);
    }

    @Override
    public List<CharacterVo> searchByName(Long novelId, String name) {
        Long resolved = novelId != null ? novelId : NovelConstants.DEFAULT_NOVEL_ID;
        List<NovelCharacter> list;
        if (name == null || name.isBlank()) {
            list = characterDao.listByNovelId(resolved);
        } else {
            list = characterDao.searchByNovelIdAndNameContaining(resolved, name.trim());
        }
        return list.stream().map(VoConverters::toVo).toList();
    }

    @Override
    public List<ChapterSummaryVo> listAppearedChapters(Long novelId, Long characterId) {
        if (novelId == null || characterId == null) {
            return Collections.emptyList();
        }
        NovelCharacter character = characterDao.selectById(characterId);
        if (character == null || !novelId.equals(character.getNovelId())) {
            return Collections.emptyList();
        }
        String name = character.getName();
        if (name == null || name.isBlank()) {
            return Collections.emptyList();
        }
        List<NovelChapterContent> chapters = chapterDao.listByNovelIdOrderByChapterNoAsc(novelId);
        return chapters.stream()
                .filter(c -> c.getContent() != null && c.getContent().contains(name))
                .map(c -> VoConverters.toSummaryVo(c, ArgsUtil.truncate(c.getContent(), CHAPTER_PREVIEW_LIMIT)))
                .toList();
    }

    private static String toJson(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return JsonUtil.MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            log.warn("[CharacterServiceImpl] 序列化 relationships 失败: {}", e.getMessage());
            return null;
        }
    }
}
