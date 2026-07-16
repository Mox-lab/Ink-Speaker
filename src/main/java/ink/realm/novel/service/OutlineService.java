package ink.realm.novel.service;

import ink.realm.novel.domain.dto.OutlineActivateRequest;
import ink.realm.novel.domain.dto.OutlineSaveRequest;
import ink.realm.novel.domain.vo.OutlineActiveVo;
import ink.realm.novel.domain.vo.OutlineDetailVo;
import ink.realm.novel.domain.vo.OutlineSaveResultVo;
import ink.realm.novel.domain.vo.OutlineSummaryVo;

import java.util.List;
import java.util.Optional;

/**
 * 大纲服务接口。
 * <p>负责大纲多版本管理(列表/详情/激活/保存/删除)。</p>
 */
public interface OutlineService {

    /** 列出某小说的全部大纲版本(最新在前)。 */
    List<OutlineSummaryVo> listOutlines(Long novelId);

    /** 获取某版本大纲全文。 */
    OutlineDetailVo getOutline(Long id);

    /** 获取当前激活版本(可空)。 */
    Optional<OutlineActiveVo> getActiveOutline(Long novelId);

    /** 保存大纲(新建版本,自动切换激活)。 */
    OutlineSaveResultVo saveOutline(OutlineSaveRequest request);

    /** 激活某历史版本。 */
    boolean activateOutline(OutlineActivateRequest request);

    /** 删除某版本。 */
    void deleteOutline(Long id);
}
