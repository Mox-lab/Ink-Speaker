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
 * 下一章续写建议 VO(BASE-12)。
 * <p>由 {@code ContinuationServiceImpl} 调用 {@code ContinuationAgent} 生成,
 * 供前端总览页"AI 续写建议"面板渲染。</p>
 *
 * <ul>
 *   <li>{@code nextChapterNo} 待写章节序号</li>
 *   <li>{@code title} 建议的章名(4-8 字)</li>
 *   <li>{@code direction} 剧情发展方向</li>
 *   <li>{@code conflict} 本章核心矛盾</li>
 *   <li>{@code keyCharacters} 关键角色名列表</li>
 *   <li>{@code hook} 章尾悬念</li>
 *   <li>{@code risks} 潜在风险提示</li>
 *   <li>{@code generatedAt} 生成时间(用于前端展示新鲜度)</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContinuationSuggestionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待写的下一章序号。 */
    private int nextChapterNo;
    /** 建议的章名。 */
    private String title;
    /** 剧情发展方向。 */
    private String direction;
    /** 本章核心矛盾。 */
    private String conflict;
    /** 关键角色名列表。 */
    private List<String> keyCharacters;
    /** 章尾悬念。 */
    private String hook;
    /** 潜在风险提示。 */
    private List<String> risks;
    /** 生成时间。 */
    private LocalDateTime generatedAt;
}
