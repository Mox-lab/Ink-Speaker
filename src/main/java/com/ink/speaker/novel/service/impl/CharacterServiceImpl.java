package com.ink.speaker.novel.service.impl;

import com.ink.speaker.novel.mapper.NovelCharacterMapper;
import com.ink.speaker.novel.domain.dto.CharacterBatchSaveRequest;
import com.ink.speaker.novel.domain.entity.NovelCharacter;
import com.ink.speaker.novel.service.CharacterService;
import com.ink.speaker.novel.domain.vo.CharacterBatchSaveResultVo;
import com.ink.speaker.novel.domain.vo.CharacterVo;
import com.ink.speaker.util.JsonUtil;
import com.ink.speaker.util.NovelConstants;
import com.ink.speaker.util.VoConverters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 人物档案服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterServiceImpl implements CharacterService {

    private final NovelCharacterMapper characterDao;

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
