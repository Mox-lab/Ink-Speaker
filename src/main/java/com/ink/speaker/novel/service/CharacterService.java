package com.ink.speaker.novel.service;

import com.ink.speaker.novel.domain.dto.CharacterBatchSaveRequest;
import com.ink.speaker.novel.domain.vo.CharacterBatchSaveResultVo;
import com.ink.speaker.novel.domain.vo.CharacterVo;

import java.util.List;

/**
 * 人物档案服务接口。
 */
public interface CharacterService {

    /** 列出某小说全部人物。 */
    List<CharacterVo> listCharacters(Long novelId);

    /** 批量保存人物(upsert by novelId+name)。 */
    CharacterBatchSaveResultVo saveBatch(CharacterBatchSaveRequest request);

    /** 删除某人物。 */
    void deleteCharacter(Long id);
}
