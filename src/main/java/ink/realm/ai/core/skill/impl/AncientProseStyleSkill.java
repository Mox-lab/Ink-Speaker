package ink.realm.ai.core.skill.impl;

import ink.realm.ai.core.skill.Skill;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 古风文笔技能。
 * <p>当设定/主题包含"宫廷、江湖、青衫、银簪、长安"等古风关键词时激活。</p>
 * <p>核心价值:提升文字的古典质感,避开现代词与口语化表达。</p>
 */
@Component
public class AncientProseStyleSkill implements Skill {

    @Override
    public String id() {
        return "ancient-prose";
    }

    @Override
    public String name() {
        return "古风文笔";
    }

    @Override
    public String description() {
        return "古风/武侠/历史题材:用词典雅,句式整散结合,避免现代词出戏";
    }

    @Override
    public List<String> triggers() {
        return List.of("古风", "宫廷", "长安", "江湖", "青衫", "簪", "江湖儿女", "庙堂", "前朝", "本宫", "本王", "卿家");
    }

    @Override
    public String promptSuffix() {
        return """
                古风文笔要求:
                1. 词汇雅正,称呼 / 物件 / 节气尽量用古称(茶汤 / 银针 / 檀香 / 暮春三月);
                2. 句式整散结合,适度使用对仗与短句,提升节奏感;
                3. 严禁出现"系统 / 数据 / 心理学 / 逻辑链"等现代术语,如有概念需要,用古人说法包装;
                4. 环境描写多用通感与意象(月色如水 / 灯花坠 / 雪意侵衣),避免直白描述;
                5. 对话保持身份感(臣/妾/本王/贫道),称呼差异要符合礼制。
                """;
    }

    @Override
    public int priority() {
        return 8;
    }
}
