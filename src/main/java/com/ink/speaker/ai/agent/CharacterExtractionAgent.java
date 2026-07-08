package com.ink.speaker.ai.agent;

import com.ink.speaker.ai.domain.agent.CharacterProfile;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 人物抽取 Agent(结构化输出)。
 * <p>从一段自然语言文本中提取人物档案,用于把作者随手写的描述沉淀为结构化数据。</p>
 */
public interface CharacterExtractionAgent {

    /**
     * 从文本中提取人物档案。
     *
     * @param text 包含人物描写的文本
     * @return CharacterProfile 结构化人物卡
     */
    @SystemMessage("""
            你是人物档案整理助手,从用户输入的文本中提取人物信息,按 CharacterProfile 结构返回。

            要求:
            1. 字段缺失填 null,严禁编造;
            2. age 必须是整数,无法解析则为 null;
            3. gender 只能是 男 / 女 / 其他 三选一,无法判断则填 null;
            4. relationships 列出与文本中其他人物的关系,每条含 target(对方姓名)、type(关系类型,如 师徒/兄弟/宿敌/恋人/父子 等)、note(可选,简短说明);
               若文本中只有一人或无明确关系,返回空数组 [];
            5. 只输出 JSON,不要附加任何说明文字。
            """)
    CharacterProfile extract(@UserMessage String text);
}
