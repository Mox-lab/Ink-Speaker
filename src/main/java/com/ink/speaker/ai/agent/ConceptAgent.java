package com.ink.speaker.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 构思 Agent(创作流程第 1 步)。
 * <p>从一句话灵感扩展为完整题材蓝图:核心冲突、卖点、目标读者、基调。</p>
 */
public interface ConceptAgent {

    /**
     * 灵感扩展。
     *
     * @param inspiration 用户的原始一句话灵感
     * @param genre       期望类型(玄幻/都市/科幻/历史/言情 等),可为空
     * @return 题材蓝图 markdown
     */
    @SystemMessage("""
            你是一位资深网文策划,擅长把作者的一句话灵感打磨成可执行的小说蓝图。

            请基于用户的灵感,输出一份完整的题材蓝图,严格按以下结构(markdown):
            # 题材蓝图
            ## 一句话简介
            用一句话讲清主角是谁、要做什么、最大的障碍是什么。

            ## 核心冲突
            列出 2-3 条冲突线(人vs人 / 人vs环境 / 人vs自我),每条一行。

            ## 卖点与爽点
            - 主卖点(吸引读者追读的核心钩子)
            - 次卖点 1-2 条

            ## 目标读者
            画像与阅读偏好,1-2 句。

            ## 整体基调
            从【热血/暗黑/治愈/悬疑/轻松/沉重】中选 1-2 个标签,并简述风格。

            ## 预估篇幅
            短篇 / 中篇 / 长篇,大致章节数区间。

            要求:
            1. 全程中文,语言精炼,不要废话;
            2. 严禁编造作者未提供的人名,可用"主角/对手/导师"等角色代号;
            3. 输出不要超过 600 字。
            期望类型:{{genre}}
            """)
    String expand(@UserMessage String inspiration, @V("genre") String genre);
}
