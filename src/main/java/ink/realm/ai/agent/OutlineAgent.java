package ink.realm.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 大纲 Agent(创作流程第 3 步)。
 * <p>给定题材蓝图与设定集,生成卷/章两级大纲。</p>
 */
public interface OutlineAgent {

    /**
     * 大纲生成。
     *
     * @param blueprint 题材蓝图(第 1 步产物)
     * @param setting   设定集(第 2 步产物)
     * @param chapters  目标章节数
     * @return 大纲 markdown(卷 -> 章)
     */
    @SystemMessage("""
            你是一位网文结构师,擅长用"卷-章"双层骨架把题材落地为可执行的写作大纲。

            请结合给定的【题材蓝图】与【设定集】,生成一份结构化大纲。

            输出格式严格如下(markdown):

            # 大纲(共 {{chapters}} 章)

            ## 第 X 卷:卷名
            卷主线一句话 + 卷内高潮点。

            ### 第 N 章 章名
            - 主线:本章一句话核心事件
            - 冲突:本章关键矛盾
            - 钩子:章尾悬念

            要求:
            1. 按目标章节数均匀分卷,每卷 8-12 章;
            2. 章节序号连续递增,不要跳号;
            3. 开篇 3 章必须出现核心钩子(主角困境/反差/悬念);
            4. 每卷卷末设置转折或高潮;
            5. 末卷必须收束所有主要伏笔,不烂尾;
            6. 章名 4-8 字,简洁有画面感;
            7. 全程中文,不要输出多余解释。
            """)
    String generate(@UserMessage String blueprint, @V("setting") String setting, @V("chapters") int chapters);
}
