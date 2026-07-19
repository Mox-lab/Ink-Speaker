package ink.realm.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 大纲 Agent(创作流程第 3 步)。
 * <p>分三阶段生成"卷-章"双层骨架,并保证跨卷 / 跨章连贯与主题自检:</p>
 * <ol>
 *   <li>{@link #planVolumes} 卷规划:依主题决定卷数、卷名与每卷主线,章数合理分配</li>
 *   <li>{@link #expandVolume} 分卷展开:把一卷大纲细化为逐章细纲,携带前情保证连贯</li>
 *   <li>{@link #selfCheck} 自检:核对是否偏离主题 / 与前文矛盾 / 编号连续,供重试修正</li>
 * </ol>
 */
public interface OutlineAgent {

    /**
     * 阶段一:卷规划。
     * <p>依据题材蓝图、设定集,决定卷数、每卷卷名与"详细卷纲"。各卷章数由模型依据该卷剧情体量自行决定
     * (每卷建议 100-300 章量级,可上下浮动、可更多或更少),卷数建议 3-7 卷。全书总量为各卷之和,
     * 不再由外部章节数约束。</p>
     *
     * @param blueprint 题材蓝图
     * @param setting   设定集(可空)
     * @return 结构化卷规划 markdown(可被前端编辑后回传)
     */
    @SystemMessage("""
            你是一位网文结构师,擅长用"卷-章"双层骨架把题材落地为可执行的写作大纲。

            请结合【题材蓝图】与【设定集】,规划整本书的"卷"结构。卷规划需足够详细,
            使每一卷都能据此展开充足章节。

            关于章数(重要):这是长篇连载网文,单卷体量很大。**每一卷的章数建议在 100-300 章量级**,
            并可依据该卷剧情体量上下浮动——剧情厚重的卷可达 300-400 章甚至更多,轻量过渡卷也可少于 100 章。
            切勿把 100-300 理解为"全书总章数";全书总章数 = 各卷章数之和,通常是数百到上千章。

            输出格式严格如下(markdown),卷标题使用「·」分隔:

            # 卷规划(共 N 卷,约 M 章)

            ## 第 1 卷 · 卷名
            卷主线:用 3-5 句话详细描写本卷核心事件、主要转折点、人物成长与情绪曲线,要具体可落地,能直接据此展开约 X 章细纲
            章数:X

            ## 第 2 卷 · 卷名
            卷主线:...
            章数:Y

            ...

            要求:
            1. 卷数依题材体量合理决定,建议 3-7 卷;
            2. **每卷章数建议 100-300 章量级,可依剧情体量浮动(可更多或更少),不要为凑数硬性统一**;
            3. 各卷"章数"之和即为全书总章数(通常数百到上千章),不必凑整数;
            4. 卷名 2-8 字,有主题意境、不剧透;
            5. 每卷"卷主线"必须详细具体,覆盖核心冲突、关键转折、人物命运与重要伏笔,确保后续能展开该卷上百章、不写空;
            6. 卷主线要体现"起承转合"的递进,后卷必须建立在前卷结局之上;
            7. 必须有明确的全局主线(如成长 / 复仇 / 探秘),各卷围绕它推进;
            8. 末卷要收束主要伏笔,不烂尾;
            9. 全程中文,不要输出多余解释。
            """)
    String planVolumes(@UserMessage String blueprint, @V("setting") String setting);

    /**
     * 阶段二:分卷展开为逐章细纲。
     * <p>携带【整书卷规划】理解全局,并被告知【前情:已完成各卷摘要】以保证连贯;
     * 生成的每章细纲必须紧跟前一章结局,并为本卷末章向后续埋过渡钩子。</p>
     *
     * @param blueprint    题材蓝图(续生/重试时可在此追加自检反馈)
     * @param setting      设定集
     * @param volumePlan   整书卷规划(让本卷理解全局位置与前后卷关系)
     * @param prevVolumes  已完成各卷的摘要(自检上下文,不要重复其内容)
     * @param volumeName   本卷卷名
     * @param volumeArc    本卷主线
     * @param startChapter 本卷起始章号(全局连续)
     * @param endChapter   本卷结束章号(全局连续)
     * @return 本卷逐章细纲 markdown(### 第 N 章 章名 ...)
     */
    @SystemMessage("""
            你是一位网文结构师。现在基于【整书卷规划】展开其中"{{volumeName}}"的逐章细纲。

            上下文:
            【题材蓝图】
            {{blueprint}}

            【设定集】
            {{setting}}

            【整书卷规划】
            {{volumePlan}}

            【前情:已完成各卷摘要】(用于保证连贯,不要重复其内容)
            {{prevVolumes}}

            任务:只生成本卷(第 {{startChapter}} - {{endChapter}} 章,章号全局连续)的逐章细纲。
            本卷主线:{{volumeArc}}

            输出格式(严格 markdown,章号必须全局连续,从 {{startChapter}} 到 {{endChapter}}):
            ### 第 {{startChapter}} 章 章名
            - 主线:本章一句话核心事件
            - 冲突:本章关键矛盾
            - 钩子:章尾悬念
            - 细纲:(2-4 条要点,描述场景/转折/人物反应,承接上一章、铺垫下一章)

            ...依次到 ### 第 {{endChapter}} 章

            要求:
            1. 章号连续递增,从 {{startChapter}} 到 {{endChapter}},绝不跳号、绝不重复;
            2. 每章细纲必须紧跟前一章结局,场景/人物/伏笔自然衔接,严禁突兀转场;
            3. 本卷首章要承接【前情】中上一卷的结局;本卷末章要为下一卷埋下过渡钩子;
            4. 全程紧扣【题材蓝图】的主题,不偏离、不引入与前文矛盾的设定;
            5. 章名 4-8 字,简洁有画面感;细纲具体可执行,不是空话;
            6. 全局伏笔要在对应卷回收,新增伏笔要登记以便后续回收;
            7. 全程中文,不要输出多余解释;只输出本卷章节,不要重复卷规划。
            """)
    String expandVolume(@UserMessage String blueprint,
                        @V("setting") String setting,
                        @V("volumePlan") String volumePlan,
                        @V("prevVolumes") String prevVolumes,
                        @V("volumeName") String volumeName,
                        @V("volumeArc") String volumeArc,
                        @V("startChapter") int startChapter,
                        @V("endChapter") int endChapter);

    /**
     * 阶段三:自检。
     * <p>核对一段(单卷)大纲是否偏离主题、与前情矛盾、章号是否连续。
     * 返回"通过"或具体问题清单(供重试时作为反馈修正)。</p>
     *
     * @param theme    主题(自检基准)
     * @param segment  待检大纲片段(本卷)
     * @param context  前情与全局上下文(主题 / 规划 / 前卷摘要)
     * @return 自检结论文本
     */
    @SystemMessage("""
            你是一位严苛的网文结构审校,只核对"是否偏离主题 / 是否与前文矛盾 / 编号是否连续",不评价文笔。

            请核对【待检大纲】是否满足以下三项,基于【主题】与【上下文】:

            1. 主题一致:待检大纲是否紧扣主题,有无凭空冒出与前文设定冲突的新设定/新势力/新人设;
            2. 连贯性:章与章之间是否衔接自然,是否承接【上下文】中上一卷的结局,有无突兀断档或重复已发生事件;
            3. 编号连续:章号是否从给定起点连续递增、无跳号、无重复。

            输出格式(纯文本,不要 markdown 代码块):
            结论:通过
            或
            结论:需修正
            问题:
            1) [主题] 具体偏离点...
            2) [连贯] 具体矛盾点...
            3) [编号] 具体错误...

            没有问题时只输出"结论:通过"。问题最多列 6 条,具体可操作。
            """)
    String selfCheck(@UserMessage String theme,
                     @V("segment") String segment,
                     @V("context") String context);
}
