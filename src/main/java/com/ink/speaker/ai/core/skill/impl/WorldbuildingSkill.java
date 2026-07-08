package com.ink.speaker.ai.core.skill.impl;

import com.ink.speaker.ai.core.skill.Skill;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 世界观构建技能。
 * <p>当大纲/主题包含"世界观、设定集、地理、势力、历史、规则、体系、文明"等关键词时激活。</p>
 *
 * <p>核心价值:专注于世界观/设定的系统性构建——
 * 地理、势力、历史、规则体系的自洽性与可扩展性,
 * 适合奇幻 / 科幻 / 修仙 / 末世等强设定题材的"设定阶段"写作。</p>
 *
 * <p>主要适用:</p>
 * <ul>
 *   <li>奇幻 / 科幻世界观设定集撰写</li>
 *   <li>修仙 / 玄幻 / 末世体系的规则与势力构建</li>
 *   <li>架空历史 / 架空地理的设计</li>
 *   <li>多文明 / 多种族的体系设计</li>
 * </ul>
 */
@Component
public class WorldbuildingSkill implements Skill {

    @Override
    public String id() {
        return "worldbuilding";
    }

    @Override
    public String name() {
        return "世界观构建";
    }

    @Override
    public String description() {
        return "世界观设定:地理 / 势力 / 历史 / 规则体系,自洽且可扩展";
    }

    @Override
    public List<String> triggers() {
        return List.of("世界观", "设定集", "地理", "势力", "历史", "规则", "体系", "文明",
                "种族", "魔法体系", "科技树", "宗教", "政治", "经济", "文化", "神话");
    }

    @Override
    public String promptSuffix() {
        return """
                世界观构建要点:
                1. 系统性:任何设定必须可被归类到「地理 / 势力 / 历史 / 规则 / 文明」五维之一,避免孤立设定;
                2. 自洽性:每条规则必须有"代价 / 限制 / 例外"三要素,禁止无副作用金手指;
                3. 历史纵深:每个势力 / 文明至少有"起源 → 兴盛 → 现状"三段简史,提供伏笔空间;
                4. 地理逻辑:地图上的资源 / 气候 / 交通要符合地理因果(水路 → 商埠 / 矿产 → 城邦);
                5. 势力平衡:至少三方势力相互制衡,避免单极碾压,每方有"诉求 / 弱点 / 资源"三件套;
                6. 规则可见度:核心规则要在前 10 章通过具体事件展示,而非旁白说明;
                7. 留白与扩展:体系边缘保留 2-3 处"未知区域 / 失落文明 / 禁忌领域",为后续剧情留口子;
                8. 文化细节:每个文明 / 势力至少有 1 个独有习俗 / 称呼 / 食物 / 节日,避免脸谱化;
                9. 涉及具体设定时,优先调用 queryWorldSetting 工具核对已有设定,保持前后一致。
                """;
    }

    @Override
    public List<String> toolWhitelist() {
        return List.of("queryWorldSetting", "queryTimeline", "searchExternalKnowledge", "retrieveLore", "countWords");
    }

    @Override
    public int priority() {
        return 9;
    }
}
