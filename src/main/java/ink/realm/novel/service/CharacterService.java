package ink.realm.novel.service;

import ink.realm.novel.domain.dto.CharacterBatchSaveRequest;
import ink.realm.novel.domain.vo.CharacterBatchSaveResultVo;
import ink.realm.novel.domain.vo.CharacterVo;
import ink.realm.novel.domain.vo.ChapterSummaryVo;

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

    /**
     * 按名字模糊匹配人物(UX-06 写作侧边栏 @人物搜索)。
     *
     * @param novelId 小说 ID
     * @param name    姓名片段(空串时返回全部)
     * @return 匹配的人物列表
     */
    List<CharacterVo> searchByName(Long novelId, String name);

    /**
     * 列出某人物出现的章节(UX-06 人物卡片点击查看出现位置)。
     *
     * @param novelId     小说 ID
     * @param characterId 人物 ID
     * @return 出现该人物的章节摘要列表(按章节序号升序)
     */
    List<ChapterSummaryVo> listAppearedChapters(Long novelId, Long characterId);
}
