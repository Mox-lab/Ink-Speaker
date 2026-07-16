package ink.realm.novel.service;

import ink.realm.novel.domain.dto.SettingSaveRequest;
import ink.realm.novel.domain.vo.SaveResultVo;
import ink.realm.novel.domain.vo.WorldSettingVo;

import java.util.List;

/**
 * 世界观设定服务接口。
 */
public interface SettingService {

    /** 列出某小说全部设定。 */
    List<WorldSettingVo> listSettings(Long novelId);

    /** 保存单条设定(覆盖式)。 */
    SaveResultVo saveSetting(SettingSaveRequest request);

    /** 删除某设定。 */
    void deleteSetting(Long id);

    /**
     * 按关键词模糊匹配设定(UX-06 写作侧边栏设定 RAG 检索)。
     *
     * @param novelId  小说 ID
     * @param keyword  关键词片段(空串时返回全部)
     * @return 匹配的设定列表
     */
    List<WorldSettingVo> searchByKeyword(Long novelId, String keyword);
}
