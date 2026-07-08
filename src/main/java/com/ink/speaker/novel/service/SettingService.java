package com.ink.speaker.novel.service;

import com.ink.speaker.novel.domain.dto.SettingSaveRequest;
import com.ink.speaker.novel.domain.vo.SaveResultVo;
import com.ink.speaker.novel.domain.vo.WorldSettingVo;

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
}
