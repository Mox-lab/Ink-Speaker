package ink.realm.novel.service;

import ink.realm.common.context.NovelContext;
import ink.realm.novel.domain.dto.NovelCreateRequest;
import ink.realm.novel.domain.dto.NovelUpdateRequest;
import ink.realm.novel.domain.vo.NovelExportPayload;
import ink.realm.novel.domain.vo.NovelOverviewVo;
import ink.realm.novel.domain.vo.NovelVo;
import ink.realm.novel.domain.vo.SaveResultVo;
import ink.realm.novel.domain.vo.SharedNovelBrowseVo;

import java.util.List;

/**
 * 小说主表服务接口。
 *
 * <p>第 5 阶段(R5 用户隔离):所有方法都基于当前用户的 {@code NovelContext.requireUserId()}
 * 过滤,确保每本小说只对作者可见。</p>
 *
 * <p>第 6 阶段(以小说为主体):新增 CRUD 与概览接口,支持"我的小说列表 →
 * 进入某本小说 → 总览/续写/修改"的信息架构。</p>
 */
public interface NovelService {

    /**
     * 列出当前用户的全部小说(R5 用户隔离)。
     * <p>从 {@link NovelContext#requireUserId()} 拿当前用户,
     * 仅返回属于该用户的小说。</p>
     *
     * @return 当前用户的小说列表
     */
    List<NovelVo> listNovels();

    /**
     * 列出公开到公共参考池的小说(R5 跨小说参考)。
     * <p>所有用户都能看到,但仅返回脱敏字段(id/title/author/description),
     * 不暴露 owner_id 等敏感信息。</p>
     *
     * @return 公开小说列表(脱敏)
     */
    List<NovelVo> listSharedForReference();

    /**
     * 创建新小说。
     * <p>ownerId 从 {@link NovelContext#requireUserId()} 取,
     * 不允许调用方传入 ownerId(防止越权指定他人作为所有者)。</p>
     *
     * @param request 创建请求(title/author/description/sharedForReference)
     * @return 新建小说的 ID
     */
    SaveResultVo createNovel(NovelCreateRequest request);

    /**
     * 更新小说基础信息(title/author/description/sharedForReference)。
     * <p>必须校验所有权:不属于当前用户的小说返回 403/404。</p>
     *
     * @param id      小说 ID
     * @param request 更新请求
     */
    void updateNovel(Long id, NovelUpdateRequest request);

    /**
     * 删除小说(含级联删除章节/大纲/人物/设定/时间线/审查问题)。
     * <p>必须校验所有权:不属于当前用户的小说返回 403/404。</p>
     * <p>整个操作在单个事务中完成,任一子表删除失败则全部回滚。</p>
     *
     * @param id 小说 ID
     */
    void deleteNovel(Long id);

    /**
     * 获取单本小说的基础信息。
     * <p>必须校验所有权。</p>
     *
     * @param id 小说 ID
     * @return 小说 VO(不存在或不属于当前用户时抛 NOT_FOUND)
     */
    NovelVo getNovel(Long id);

    /**
     * 获取小说概览(基础信息 + 各子模块统计)。
     * <p>用于"进入某本小说"后的第一屏展示,避免前端发起多次请求拉取各模块列表。</p>
     * <p>必须校验所有权。</p>
     *
     * @param id 小说 ID
     * @return 概览 VO
     */
    NovelOverviewVo getNovelOverview(Long id);

    /**
     * 获取公共参考池中某本小说的只读详情(BASE-09)。
     *
     * <p>用于"公共参考池浏览页":聚合脱敏后的基础信息 + 全部章节摘要 + 全部大纲版本 +
     * 全部人物 + 全部世界观设定,供前端一次性渲染只读视图。</p>
     *
     * <p><b>权限校验:</b>仅 {@code sharedForReference=true} 的小说可被获取,
     * 否则抛 NOT_FOUND(避免暴露存在性)。不校验 ownerId —— 公共池跨用户可见。</p>
     *
     * @param id 小说 ID
     * @return 脱敏聚合 VO(不含 ownerId)
     */
    SharedNovelBrowseVo getSharedNovelBrowse(Long id);

    /**
     * 导出小说(BASE-10)。
     * <p>聚合 Novel 基础信息 + 全部 Outlines + 全部 Characters + 全部 WorldSettings +
     * 全部 Chapters(按章节序升序),按 {@code format} 拼装为下载文件。</p>
     *
     * <p>必须校验所有权;仅 owner 可导出。</p>
     *
     * <p>格式说明:</p>
     * <ul>
     *   <li>{@code md} — Markdown 格式,含小说信息块 + 大纲 + 人物表 + 设定表 + 章节正文,
     *       适合阅读与二次编辑</li>
     *   <li>{@code txt} — 纯文本格式,仅按章节顺序拼接标题 + 正文,适合阅读</li>
     *   <li>{@code json} — 完整结构化数据,适合后续工具处理或导入</li>
     * </ul>
     *
     * @param id     小说 ID
     * @param format 文件格式(md / txt / json),大小写不敏感,null 时默认 md
     * @return 导出载荷(文件名 + MIME 类型 + 字节内容)
     */
    NovelExportPayload exportNovel(Long id, String format);
}
