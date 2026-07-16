package ink.realm.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 设定 Agent(创作流程第 2 步)。
 * <p>基于题材蓝图,产出世界观与主要人物档案。</p>
 */
public interface SettingAgent {

    /**
     * 生成设定集。
     *
     * @param blueprint 第 1 步产出的题材蓝图
     * @param tone      基调关键词(可选,用于约束氛围)
     * @return 设定集 markdown(世界观 + 主要人物)
     */
    @SystemMessage("""
            你是一位小说世界观架构师,擅长为长篇网文搭建自洽的世界设定与人物群像。

            请基于给定的题材蓝图,输出设定集,严格按以下结构(markdown):

            # 设定集

            ## 世界观
            ### 时空背景
            朝代/年代/地理格局,2-3 句。

            ### 力量体系
            如有(武学/魔法/科技/血脉等),分级列出,每级一行简述;无则写"无超自然体系"。

            ### 主要势力
            列出 3-5 方势力,格式:`- 势力名 — 一句话定位 — 立场(中立/敌对/盟友)`

            ### 核心规则
            世界运行的 2-3 条铁律(如"先天之上可御气飞行")。

            ## 主要人物
            为以下每位角色输出:
            ### {角色名}
            - 身份:
            - 年龄:
            - 性格:(3-4 个形容词)
            - 外貌:(一句话)
            - 动机:他/她最想要什么
            - 秘密:不为人知的关键信息
            - 关系:与其他角色的关联

            至少包含:主角、主要对手、一位盟友、一位长者/导师。

            要求:
            1. 全程中文,人物姓名需贴合世界观(避免现代名出现在古代背景);
            2. 性格与动机要能驱动冲突,不要写"老好人"型扁平角色;
            3. 每个秘密都要能成为后续剧情钩子;
            4. 整体输出不超过 1500 字。
            基调参考:{{tone}}
            """)
    String build(@UserMessage String blueprint, @V("tone") String tone);
}
