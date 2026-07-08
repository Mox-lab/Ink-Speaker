package com.ink.speaker.ai.core.director;

import com.ink.speaker.common.BusinessException;
import com.ink.speaker.common.NovelContext;
import com.ink.speaker.common.ResultCode;
import com.ink.speaker.novel.mapper.NovelReviewIssueMapper;
import com.ink.speaker.novel.domain.entity.NovelReviewIssue;
import com.ink.speaker.novel.domain.vo.ReviewIssueVo;
import com.ink.speaker.ai.core.memory.RelevantMemoryRetriever;
import com.ink.speaker.util.JsonUtil;
import com.ink.speaker.util.NovelConstants;
import com.ink.speaker.util.VoConverters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 导演 Agent:多 Agent 协作的编排中枢。
 * <p>对标 cc-haha {@code src/agents/coordinator.ts}。</p>
 *
 * <p>职责:</p>
 * <ol>
 *   <li>章节生成完毕后,自动调用 {@link ReviewAgent} 做一致性审查</li>
 *   <li>解析审查结果,落库到 {@code novel_review_issue} 表</li>
 *   <li>(未来)调度 OutlineAgent 重写大纲 / ChapterAgent 局部重写</li>
 * </ol>
 *
 * <p>当前实现聚焦"生成 → 审查 → 落库"链路,不阻塞主流程,
 * 通过 {@code @Async} 在章节保存后异步触发。</p>
 *
 * <p><b>novelId 解析:</b>同步接口走 {@link NovelContext};
 * {@code @Async reviewChapter} 异步入口由调用方显式传入 novelId。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirectorAgent {

    private final ReviewAgent reviewAgent;
    private final NovelReviewIssueMapper reviewIssueDao;
    private final RelevantMemoryRetriever memoryRetriever;

    @Value("${ink-speaker.current-id:" + NovelConstants.DEFAULT_NOVEL_ID + "}")
    private Long fallbackNovelId;

    @Value("${ink-speaker.coordinator.review-enabled:true}")
    private boolean reviewEnabled;

    /**
     * 异步审查单章正文并落库审查问题。
     * <p>章节保存后由 ChapterController 触发。失败不抛异常,仅记日志。</p>
     *
     * @param chapterText 本章正文
     * @param chapterNo   本章序号
     */
    @Async
    public void reviewChapter(String chapterText, int chapterNo) {
        reviewChapter(chapterText, chapterNo, NovelContext.getNovelId());
    }

    /**
     * 异步入口 + 显式 novelId(供 ChapterServiceImpl 等异步调用方使用)。
     * <p>异步线程无法继承 ThreadLocal,调用方必须显式传入。</p>
     */
    @Async
    public void reviewChapter(String chapterText, int chapterNo, Long novelId) {
        Long resolved = novelId != null ? novelId : fallbackNovelId;
        if (!reviewEnabled || chapterText == null || chapterText.isBlank()) {
            return;
        }
        try {
            log.info("[Director] 开始审查第 {} 章(novelId={}),文本长度={}",
                    chapterNo, resolved, chapterText.length());

            // 1) 组装上下文:人物档案 + 最近 3 章时间线 + 知识库语义片段
            String context = memoryRetriever.retrieve("第" + chapterNo + "章 审查上下文");

            // 2) 调用 ReviewAgent 输出 JSON
            String json = reviewAgent.review(chapterText, chapterNo, context);
            if (json == null || json.isBlank()) {
                log.info("[Director] 第 {} 章审查返回空", chapterNo);
                return;
            }

            // 3) 解析 JSON 数组并落库
            List<Map<String, Object>> issues = parseIssues(json);
            if (issues.isEmpty()) {
                log.info("[Director] 第 {} 章未发现审查问题", chapterNo);
                return;
            }

            for (Map<String, Object> issue : issues) {
                NovelReviewIssue entity = NovelReviewIssue.builder()
                        .novelId(resolved)
                        .chapterNo(chapterNo)
                        .severity(getStr(issue, "severity", "medium"))
                        .category(getStr(issue, "category", "其他"))
                        .location(getStr(issue, "location", null))
                        .description(getStr(issue, "description", ""))
                        .suggestion(getStr(issue, "suggestion", null))
                        .status("open")
                        .build();
                reviewIssueDao.insert(entity);
            }
            log.info("[Director] 第 {} 章共落库 {} 条审查问题", chapterNo, issues.size());
        } catch (Exception e) {
            log.warn("[Director] 第 {} 章审查失败:{}", chapterNo, e.getMessage());
        }
    }

    /** 列出某章的全部审查问题(供前端侧栏展示)。 */
    public List<ReviewIssueVo> listIssues(int chapterNo) {
        Long novelId = resolveNovelId();
        return reviewIssueDao.listByNovelIdAndChapterNoOrderBySeverityDesc(novelId, chapterNo).stream()
                .map(VoConverters::toVo)
                .toList();
    }

    /** 列出某小说全部未解决的审查问题。 */
    public List<ReviewIssueVo> listOpenIssues() {
        Long novelId = resolveNovelId();
        return reviewIssueDao.listByNovelIdAndStatusOrderByChapterNoAsc(novelId, "open").stream()
                .map(VoConverters::toVo)
                .toList();
    }

    /** 更新审查问题状态(open / resolved / ignored)。 */
    public ReviewIssueVo updateStatus(Long issueId, String status) {
        NovelReviewIssue issue = reviewIssueDao.selectById(issueId);
        if (issue == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "审查问题不存在: " + issueId);
        }
        issue.setStatus(status);
        reviewIssueDao.updateById(issue);
        return VoConverters.toVo(issue);
    }

    /**
     * 同步入口:解析当前请求的 novelId。
     * <p>异步方法走 {@link #reviewChapter(String, int, Long)} 显式传入。</p>
     */
    private Long resolveNovelId() {
        Long ctxId = NovelContext.getNovelId();
        return ctxId != null ? ctxId : fallbackNovelId;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseIssues(String json) {
        try {
            // 兼容 LLM 偶发输出 ```json ... ``` 包裹的情况
            String trimmed = json.trim();
            if (trimmed.startsWith("```")) {
                int start = trimmed.indexOf('\n');
                int end = trimmed.lastIndexOf("```");
                if (start > 0 && end > start) {
                    trimmed = trimmed.substring(start + 1, end).trim();
                }
            }
            Object parsed = JsonUtil.MAPPER.readValue(trimmed, Object.class);
            if (parsed instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
        } catch (Exception e) {
            log.warn("[Director] 解析审查 JSON 失败:{}, raw={}", e.getMessage(),
                    json.length() > 500 ? json.substring(0, 500) + "..." : json);
        }
        return List.of();
    }

    private String getStr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }
}

