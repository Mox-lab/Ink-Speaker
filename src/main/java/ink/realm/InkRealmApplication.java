package ink.realm;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Ink Realm 启动类。
 * <p>基于 Spring Boot 4 + LangChain4j,端口 9688。</p>
 */
@Slf4j
@EnableAsync
@EnableCaching
@SpringBootApplication
@MapperScan(basePackages = "ink.realm.**.mapper")
public class InkRealmApplication {

    private static final String STARTUP_BANNER = """
            ====================================================
            Ink Realm 启动成功!
            ====================================================
            """;

    /**
     * 程序入口:启动 Spring 容器并打印简化启动横幅。
     */
    public static void main(String[] args) {
        // 关闭 Spring Boot 默认 Banner,避免暴露版本信息
        SpringApplication app = new SpringApplication(InkRealmApplication.class);
        app.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
        app.run(args);

        System.out.println(STARTUP_BANNER);
    }
}
