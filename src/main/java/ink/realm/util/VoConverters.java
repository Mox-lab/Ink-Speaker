package ink.realm.util;

import ink.realm.novel.domain.entity.Novel;
import ink.realm.novel.domain.entity.NovelChapterContent;
import ink.realm.novel.domain.entity.NovelChapterTimeline;
import ink.realm.novel.domain.entity.NovelCharacter;
import ink.realm.novel.domain.entity.NovelOutline;
import ink.realm.novel.domain.entity.NovelReviewIssue;
import ink.realm.novel.domain.entity.NovelWorldSetting;
import ink.realm.novel.domain.vo.ChapterDetailVo;
import ink.realm.novel.domain.vo.ChapterSummaryVo;
import ink.realm.novel.domain.vo.CharacterVo;
import ink.realm.novel.domain.vo.NovelVo;
import ink.realm.novel.domain.vo.OutlineActiveVo;
import ink.realm.novel.domain.vo.OutlineDetailVo;
import ink.realm.novel.domain.vo.OutlineSummaryVo;
import ink.realm.novel.domain.vo.ReviewIssueVo;
import ink.realm.novel.domain.vo.TimelineVo;
import ink.realm.novel.domain.vo.WorldSettingVo;
import java.util.Collections;

import java.util.Map;

/**
 * Entity → VO 转换器。
 * <p>阿里规范:Controller 不直接返回 Entity,统一通过 VoConverters 转换为 VO。</p>
 */
public final class VoConverters {

    private VoConverters() {
    }

    public static NovelVo toVo(Novel entity) {
        if (entity == null) {
            return null;
        }
        return NovelVo.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .author(entity.getAuthor())
                .description(entity.getDescription())
                .createdAt(entity.getCtTime())
                .updatedAt(entity.getUtTime())
                .build();
    }

    public static ChapterSummaryVo toSummaryVo(NovelChapterContent c, String contentPreview) {
        if (c == null) {
            return null;
        }
        return ChapterSummaryVo.builder()
                .id(c.getId())
                .novelId(c.getNovelId())
                .outlineId(c.getOutlineId())
                .chapterNo(c.getChapterNo())
                .title(c.getTitle())
                .wordCount(c.getWordCount())
                .sessionId(c.getSessionId())
                .contentPreview(contentPreview)
                .createdAt(c.getCtTime())
                .build();
    }

    public static ChapterDetailVo toDetailVo(NovelChapterContent c) {
        if (c == null) {
            return null;
        }
        return ChapterDetailVo.builder()
                .id(c.getId())
                .novelId(c.getNovelId())
                .outlineId(c.getOutlineId())
                .chapterNo(c.getChapterNo())
                .title(c.getTitle())
                .content(c.getContent())
                .wordCount(c.getWordCount())
                .sessionId(c.getSessionId())
                .createdAt(c.getCtTime())
                .updatedAt(c.getUtTime())
                .build();
    }

    public static CharacterVo toVo(NovelCharacter c) {
        if (c == null) {
            return null;
        }
        return CharacterVo.builder()
                .id(c.getId())
                .novelId(c.getNovelId())
                .name(c.getName())
                .age(c.getAge())
                .gender(c.getGender())
                .personality(c.getPersonality())
                .weapon(c.getWeapon())
                .background(c.getBackground())
                .identity(c.getIdentity())
                .appearance(c.getAppearance())
                .relationships(parseRelationships(c.getRelationships()))
                .createdAt(c.getCtTime())
                .updatedAt(c.getUtTime())
                .build();
    }

    public static WorldSettingVo toVo(NovelWorldSetting s) {
        if (s == null) {
            return null;
        }
        return WorldSettingVo.builder()
                .id(s.getId())
                .novelId(s.getNovelId())
                .keyword(s.getKeyword())
                .category(s.getCategory())
                .description(s.getDescription())
                .createdAt(s.getCtTime())
                .updatedAt(s.getUtTime())
                .build();
    }

    public static TimelineVo toVo(NovelChapterTimeline t) {
        if (t == null) {
            return null;
        }
        return TimelineVo.builder()
                .id(t.getId())
                .novelId(t.getNovelId())
                .chapterNo(t.getChapterNo())
                .title(t.getTitle())
                .summary(t.getSummary())
                .createdAt(t.getCtTime())
                .updatedAt(t.getUtTime())
                .build();
    }

    public static OutlineSummaryVo toSummaryVo(NovelOutline o, String contentPreview) {
        if (o == null) {
            return null;
        }
        return OutlineSummaryVo.builder()
                .id(o.getId())
                .novelId(o.getNovelId())
                .title(o.getTitle())
                .theme(o.getTheme())
                .chapters(o.getChapters())
                .version(o.getVersion())
                .active(o.isActive())
                .contentPreview(contentPreview)
                .contentLength(o.getContent() == null ? 0 : o.getContent().length())
                .createdAt(o.getCtTime())
                .build();
    }

    public static OutlineDetailVo toDetailVo(NovelOutline o) {
        if (o == null) {
            return null;
        }
        return OutlineDetailVo.builder()
                .id(o.getId())
                .novelId(o.getNovelId())
                .title(o.getTitle())
                .theme(o.getTheme())
                .chapters(o.getChapters())
                .content(o.getContent())
                .version(o.getVersion())
                .active(o.isActive())
                .createdAt(o.getCtTime())
                .build();
    }

    public static OutlineActiveVo toActiveVo(NovelOutline o) {
        if (o == null) {
            return null;
        }
        return OutlineActiveVo.builder()
                .id(o.getId())
                .title(o.getTitle())
                .content(o.getContent())
                .chapters(o.getChapters())
                .version(o.getVersion())
                .build();
    }

    public static ReviewIssueVo toVo(NovelReviewIssue r) {
        if (r == null) {
            return null;
        }
        return ReviewIssueVo.builder()
                .id(r.getId())
                .novelId(r.getNovelId())
                .chapterNo(r.getChapterNo())
                .severity(r.getSeverity())
                .category(r.getCategory())
                .location(r.getLocation())
                .description(r.getDescription())
                .suggestion(r.getSuggestion())
                .status(r.getStatus())
                .createdAt(r.getCtTime())
                .updatedAt(r.getUtTime())
                .build();
    }

    private static Map<String, Object> parseRelationships(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, Object> parsed = JsonUtil.parseMap(json);
        return parsed.isEmpty() ? null : parsed;
    }
}
