package com.novel.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 小说创作工具集(Agent 的"手和眼")
 * <p>
 * 写作 Agent 的本质 = LLM(脑) + Tools(查设定/扩写/计数) + Memory(人物/剧情连贯) + RAG(世界观库)。
 * LLM 自己只能"说话",无法访问设定文档、无法精确计数、无法校验时间线。
 * 通过 Tools 让 LLM 在写作过程中能:
 *   - 查询人物档案(避免人设崩塌);
 *   - 查询世界观设定(避免设定矛盾);
 *   - 查询已发生剧情时间线(避免剧情穿帮);
 *   - 精确计数字数(满足"3000 字章节"这类硬要求);
 *   - 调用扩写引擎生成场景细节。
 * </p>
 * <p>
 * LangChain4j 工具定义规则:
 *   1. 方法上加 @Tool 注解,写清 name 与 description(LLM 据此决定何时调用);
 *   2. 参数加 @P 注解,描述参数含义与格式;
 *   3. 工具类需注册到 AiServices.builder().tools(...) 中;
 *   4. 返回类型最好是 String 或可序列化对象。
 * </p>
 * <p>
 * 写作场景下的工具设计要点:
 *   - description 要写明"触发条件",避免 LLM 把工具当成万能 API;
 *   - 模拟实现用内存 Map,生产环境替换为数据库/向量库查询即可。
 * </p>
 */
@Slf4j
@Component
public class NovelTools {

    /**
     * 人物档案库(演示用内存存储)
     * 生产环境:替换为数据库表 character_profile
     */
    private static final Map<String, String> CHARACTER_DB = new HashMap<>();
    static {
        CHARACTER_DB.put("林晚", "女主角,24岁,孤儿,善于观察。性格:外冷内热,记仇但讲原则。武器:短刀'霜序'。");
        CHARACTER_DB.put("苏砚", "男主角,27岁,江湖游医。性格:温和寡言,隐藏身份实为前朝皇室遗孤。");
        CHARACTER_DB.put("赵九", "反派,40岁,锦衣卫百户。性格:多疑狠辣,但对亡妻一往情深。");
    }

    /**
     * 工具1: 查询人物档案
     * <p>
     * 当 LLM 写到某个人物时,调用此工具获取其设定,确保人设一致。
     * </p>
     */
    @dev.langchain4j.agent.tool.Tool(name = "queryCharacter",
            value = {"根据人物姓名查询其档案(年龄/性格/外貌/武器/背景)。当需要描写某个角色的言行、确保人设不崩塌时调用此工具。"})
    public String queryCharacter(
            @dev.langchain4j.agent.tool.P("人物姓名,例如 林晚、苏砚、赵九") String name) {
        log.info("[Tool] 调用 queryCharacter, name={}", name);
        String profile = CHARACTER_DB.get(name);
        if (profile == null) {
            return String.format("人物档案库中未找到 '%s',可自行创作但要保持前后一致", name);
        }
        return String.format("【%s 的人物档案】%s", name, profile);
    }

    /**
     * 工具2: 查询世界观设定
     * <p>
     * 用于查询地理、势力、历史、武学体系等设定,避免设定矛盾。
     * </p>
     */
    @dev.langchain4j.agent.tool.Tool(name = "queryWorldSetting",
            value = {"查询世界观设定关键词(地理/势力/历史/武学体系等)。当描写某个地点、势力、规则时调用此工具确认设定。"})
    public String queryWorldSetting(
            @dev.langchain4j.agent.tool.P("设定关键词,例如 青州、听潮阁、武学品阶") String keyword) {
        log.info("[Tool] 调用 queryWorldSetting, keyword={}", keyword);
        Map<String, String> worldDb = new HashMap<>();
        worldDb.put("青州", "青州位于大陆东南,水路通达,盛产灵草。州城为云陵城,有'千桥之城'美誉。");
        worldDb.put("听潮阁", "听潮阁是江湖最大情报组织,中立不结盟,规矩:情报买卖,从不赊账。");
        worldDb.put("武学品阶", "武学分为:外门、内门、先天、宗师、大宗师五个品阶。先天之上可御气飞行。");
        String setting = worldDb.get(keyword);
        if (setting == null) {
            return String.format("世界观库中未找到 '%s' 相关设定,可参考同类作品自由发挥", keyword);
        }
        return String.format("【%s 的世界观设定】%s", keyword, setting);
    }

    /**
     * 工具3: 查询剧情时间线
     * <p>
     * 用于查询已经发生的剧情节点,避免剧情穿帮、时间线冲突。
     * </p>
     */
    @dev.langchain4j.agent.tool.Tool(name = "queryTimeline",
            value = {"查询已发生的剧情节点。当需要回顾前情、衔接前后章节、避免剧情冲突时调用此工具。"})
    public String queryTimeline(
            @dev.langchain4j.agent.tool.P("章节序号或关键词,例如 第3章、云陵城相遇") String keyword) {
        log.info("[Tool] 调用 queryTimeline, keyword={}", keyword);
        Map<String, String> timeline = new HashMap<>();
        timeline.put("第1章", "林晚在云陵城码头捡到受伤的苏砚,带回家中疗伤。");
        timeline.put("第2章", "听潮阁探子夜访林晚,询问苏砚身份,林晚谎称不识。");
        timeline.put("第3章", "赵九率锦衣卫搜查云陵城,苏砚藏身地窖,二人初生情愫。");
        String node = timeline.get(keyword);
        if (node == null) {
            return String.format("时间线中未找到 '%s' 对应节点,可根据上下文自由衔接", keyword);
        }
        return String.format("【%s 的剧情节点】%s", keyword, node);
    }

    /**
     * 工具4: 字数统计
     * <p>
     * LLM 数不清字数,通过工具精确统计,避免"写到 3000 字"这类要求被忽略。
     * </p>
     */
    @dev.langchain4j.agent.tool.Tool(name = "countWords",
            value = {"统计给定文本的字符数(含标点)。当需要确认章节字数、检查是否达到目标长度时调用此工具。"})
    public String countWords(
            @dev.langchain4j.agent.tool.P("待统计的文本内容") String text) {
        log.info("[Tool] 调用 countWords, 长度={}", text == null ? 0 : text.length());
        if (text == null || text.isEmpty()) {
            return "字数: 0";
        }
        int chars = text.length();
        int charsNoSpace = text.replaceAll("\\s", "").length();
        return String.format("字数统计: 总字符 %d, 去空白 %d", chars, charsNoSpace);
    }

    /**
     * 工具5: 场景扩写建议
     * <p>
     * 给定一个简短场景描述,返回感官维度的扩写建议(视觉/听觉/嗅觉/触觉)。
     * 用于帮助 LLM 在卡文时找到扩写方向。
     * </p>
     */
    @dev.langchain4j.agent.tool.Tool(name = "expandScene",
            value = {"为简短场景提供感官维度的扩写建议。当用户要求扩写、需要丰富细节、卡文时调用此工具。"})
    public String expandScene(
            @dev.langchain4j.agent.tool.P("简短场景描述,例如 雨夜 林晚在码头等苏砚") String scene) {
        log.info("[Tool] 调用 expandScene, scene={}", scene);
        String[] senses = {"视觉", "听觉", "嗅觉", "触觉", "情绪"};
        String[] hints = {
                "雨幕如何拍打青石板?灯火如何映在水面?",
                "雨声是否盖住脚步?远处有没有更鼓?",
                "潮湿的江风带着什么气味?鱼腥还是泥土?",
                "她的衣袖湿透,贴在皮肤上是冷是黏?",
                "等待的焦灼与一丝不敢承认的期待如何交织?"
        };
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(scene).append(" 的扩写建议】\n");
        Random rnd = new Random();
        for (int i = 0; i < senses.length; i++) {
            sb.append("- ").append(senses[i]).append(": ").append(hints[i]).append("\n");
        }
        return sb.toString();
    }
}
