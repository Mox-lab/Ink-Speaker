package com.novel.forge.runner;

import com.novel.forge.entity.NovelCharacter;
import com.novel.forge.entity.NovelChapterTimeline;
import com.novel.forge.entity.NovelWorldSetting;
import com.novel.forge.repository.CharacterRepository;
import com.novel.forge.repository.ChapterTimelineRepository;
import com.novel.forge.repository.WorldSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 业务数据初始化器。
 * <p>启动时检查 novel_character / novel_world_setting / novel_chapter_timeline 三张表,
 * 若为空则插入示例数据(林晚/苏砚/赵九 + 青州/听潮阁/武学品阶 + 第1-3章剧情)。
 * 已有数据则跳过,确保幂等。</p>
 *
 * <p>Order(10):晚于 KnowledgeBaseInitializer(默认 Order 值)执行,
 * 避免与向量库初始化竞争数据库连接。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(10)
public class DataInitializer implements CommandLineRunner {

    private final CharacterRepository characterRepo;        // 人物表
    private final WorldSettingRepository worldRepo;          // 世界观表
    private final ChapterTimelineRepository timelineRepo;    // 时间线表

    @Value("${novel.current-id:1}")
    private Long novelId;                                    // 当前小说 ID

    /**
     * Spring 启动完成后执行。
     *
     * @param args 启动命令行参数(此处未使用)
     */
    @Override
    public void run(String... args) {
        initCharacters();                                   // 人物档案
        initWorldSettings();                                // 世界观设定
        initTimeline();                                     // 章节时间线
    }

    /**
     * 初始化人物档案(仅在表为空时插入)。
     */
    private void initCharacters() {
        if (characterRepo.count() > 0) {                    // 已有数据,跳过
            log.info("人物档案表已有 {} 条数据,跳过初始化", characterRepo.count());
            return;
        }
        log.info("人物档案表为空,开始插入示例人物...");

        List<NovelCharacter> characters = List.of(
                NovelCharacter.builder()
                        .novelId(novelId).name("林晚").age(24).gender("女")
                        .personality("外冷内热,记仇但讲原则")
                        .weapon("短刀'霜序'")
                        .background("孤儿,自幼在云陵城码头长大,善于观察")
                        .build(),
                NovelCharacter.builder()
                        .novelId(novelId).name("苏砚").age(27).gender("男")
                        .personality("温和寡言,隐藏身份实为前朝皇室遗孤")
                        .weapon("无(以医术行走江湖)")
                        .background("江湖游医,真名为苏砚之,前朝皇室后裔")
                        .build(),
                NovelCharacter.builder()
                        .novelId(novelId).name("赵九").age(40).gender("男")
                        .personality("多疑狠辣,但对亡妻一往情深")
                        .weapon("绣春刀")
                        .background("锦衣卫百户,奉命追查前朝皇室遗孤")
                        .build()
        );
        characterRepo.saveAll(characters);                  // 批量入库
        log.info("示例人物初始化完成,共 {} 条", characters.size());
    }

    /**
     * 初始化世界观设定(仅在表为空时插入)。
     */
    private void initWorldSettings() {
        if (worldRepo.count() > 0) {
            log.info("世界观设定表已有 {} 条数据,跳过初始化", worldRepo.count());
            return;
        }
        log.info("世界观设定表为空,开始插入示例设定...");

        List<NovelWorldSetting> settings = List.of(
                NovelWorldSetting.builder()
                        .novelId(novelId).keyword("青州").category("地理")
                        .description("青州位于大陆东南,水路通达,盛产灵草。州城为云陵城,有'千桥之城'美誉。")
                        .build(),
                NovelWorldSetting.builder()
                        .novelId(novelId).keyword("听潮阁").category("势力")
                        .description("听潮阁是江湖最大情报组织,中立不结盟,规矩:情报买卖,从不赊账。")
                        .build(),
                NovelWorldSetting.builder()
                        .novelId(novelId).keyword("武学品阶").category("武学")
                        .description("武学分为:外门、内门、先天、宗师、大宗师五个品阶。先天之上可御气飞行。")
                        .build()
        );
        worldRepo.saveAll(settings);
        log.info("示例世界观初始化完成,共 {} 条", settings.size());
    }

    /**
     * 初始化章节时间线(仅在表为空时插入)。
     */
    private void initTimeline() {
        if (timelineRepo.count() > 0) {
            log.info("章节时间线表已有 {} 条数据,跳过初始化", timelineRepo.count());
            return;
        }
        log.info("章节时间线表为空,开始插入示例剧情...");

        List<NovelChapterTimeline> timeline = List.of(
                NovelChapterTimeline.builder()
                        .novelId(novelId).chapterNo(1).title("码头初遇")
                        .summary("林晚在云陵城码头捡到受伤的苏砚,带回家中疗伤。")
                        .build(),
                NovelChapterTimeline.builder()
                        .novelId(novelId).chapterNo(2).title("夜访")
                        .summary("听潮阁探子夜访林晚,询问苏砚身份,林晚谎称不识。")
                        .build(),
                NovelChapterTimeline.builder()
                        .novelId(novelId).chapterNo(3).title("搜城")
                        .summary("赵九率锦衣卫搜查云陵城,苏砚藏身地窖,二人初生情愫。")
                        .build()
        );
        timelineRepo.saveAll(timeline);
        log.info("示例时间线初始化完成,共 {} 条", timeline.size());
    }
}
