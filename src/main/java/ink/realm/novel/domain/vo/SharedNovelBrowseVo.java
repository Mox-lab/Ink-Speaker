package ink.realm.novel.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 公共参考池浏览聚合 VO(BASE-09)。
 *
 * <p>用于在浏览他人共享小说时,一次性返回脱敏后的基础信息 + 各子模块只读列表,
 * 避免前端发起多次请求。</p>
 *
 * <p><b>脱敏说明:</b>不包含 ownerId 等敏感字段;仅 {@code sharedForReference=true}
 * 的小说可被获取(由 Service 层校验)。</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedNovelBrowseVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private String author;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 章节总数。 */
    private int chapterCount;
    /** 大纲版本数。 */
    private int outlineCount;
    /** 人物档案数。 */
    private int characterCount;
    /** 世界观设定数。 */
    private int settingCount;

    /** 章节摘要列表(按章节序升序,不含正文)。 */
    private List<ChapterSummaryVo> chapters;
    /** 大纲摘要列表(版本号倒序)。 */
    private List<OutlineSummaryVo> outlines;
    /** 人物档案列表(含关系图)。 */
    private List<CharacterVo> characters;
    /** 世界观设定列表。 */
    private List<WorldSettingVo> settings;
}
