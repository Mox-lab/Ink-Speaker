package com.ink.speaker.ai.core.director;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 审查 Agent(P1 多 Agent 协作子系统)。
 * <p>当 ChapterAgent 生成完一章正文后,由 DirectorAgent 调用本 Agent 做一致性审查。
 * 输出 JSON 列表,每条记录是一个问题。DirectorAgent 把它落库到 novel_review_issue。</p>
 *
 * <p>审查维度:</p>
 * <ul>
 *   <li>人设一致性:角色言行是否符合档案</li>
 *   <li>世界观一致:境界/势力/设定是否符合 world_setting</li>
 *   <li>时间线连贯:本章事件是否与前章冲突</li>
 *   <li>伏笔追踪:本章埋的钩子是否会在后续回收 / 之前的伏笔是否在本章被违背</li>
 *   <li>节奏:开篇钩子与章尾悬念是否到位</li>
 * </ul>
 */
public interface ReviewAgent {

    /**
     * 审查单章正文。
     *
     * @param chapterText  本章正文
     * @param chapterNo    本章序号(用于日志与定位)
     * @param context      上下文(人物档案 + 最近 3 章时间线 + 设定摘要),由 DirectorAgent 组装
     * @return JSON 数组字符串,格式见 system prompt
     */
    @SystemMessage("""
            你是一位严苛的网文责编,只关心"作品一致性",不评价文笔好坏。

            请对下面给定的【本章正文】做一致性审查,基于【上下文】核对以下维度:

            1. 人设一致性:角色言行 / 性格 / 称呼是否符合档案;
            2. 世界观一致:境界 / 势力 / 设定是否符合 world_setting;
            3. 时间线连贯:本章事件是否与前章冲突;
            4. 伏笔追踪:本章新埋的钩子是否会在后续回收 / 之前的伏笔是否在本章被违背;
            5. 节奏:开篇钩子与章尾悬念是否到位。

            严格输出以下 JSON 数组(不要输出任何额外文字、不要 markdown 代码块标记):

            [
              {
                "severity": "high",
                "category": "人设",
                "location": "原文片段或定位描述",
                "description": "问题描述",
                "suggestion": "修改建议"
              }
            ]

            字段约束:
            - severity 只能是 low / medium / high
            - category 只能是 人设 / 世界观 / 时间线 / 伏笔 / 节奏 / 其他
            - 没有发现问题时,输出空数组 []
            - 单章最多报 8 条问题,优先 high / medium
            """)
    String review(@UserMessage String chapterText,
                  @V("chapterNo") int chapterNo,
                  @V("context") String context);
}
