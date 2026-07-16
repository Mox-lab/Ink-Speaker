package ink.realm.config.async;

import ink.realm.common.context.NovelContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步任务配置。
 * <p>统一管理线程池 Bean,由 Spring 容器在关闭时销毁,
 * 调用方无需自行 shutdown,规避 SonarQube S2095。</p>
 *
 * <p>第 3 阶段(线程池隔离 + TaskDecorator):</p>
 * <ul>
 *   <li>分离两类线程池:SSE 流式 vs @Async 业务(章节审查、记忆抽取等)</li>
 *   <li>{@link #novelContextTaskDecorator} 把当前线程的 {@link NovelContext} 复制到子线程,
 *       让 @Async 方法也能用 {@link NovelContext#getNovelId()} 读取,无需调用方显式传参</li>
 *   <li>所有参数(核心数/最大数/队列/keepalive)全部配置化,避免硬编码</li>
 *   <li>拒绝策略:业务线程池用 CallerRuns(压回去让调用方慢一点);SSE 用 Abort(快速失败)</li>
 * </ul>
 */
@Slf4j
@Configuration
public class AsyncConfig {

    /** 线程名前缀:SSE 流式。 */
    public static final String SSE_THREAD_PREFIX = "sse-stream-";
    /** 线程名前缀:业务异步任务。 */
    public static final String ASYNC_THREAD_PREFIX = "ink-async-";

    /**
     * SSE 流式输出专用线程池。
     * <p>SSE 任务长耗时(120s),与 @Async 业务线程池隔离,避免阻塞 RAG/记忆抽取。</p>
     * <p>队列满后用 CallerRunsPolicy 会让请求线程同步执行,导致 SSE 响应延迟,
     * 改用 AbortPolicy 快速返回 503 让前端重试(更好的失败语义)。</p>
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService sseStreamExecutor(
            @Value("${ink.async.sse.core-size:4}") int coreSize,
            @Value("${ink.async.sse.max-size:16}") int maxSize,
            @Value("${ink.async.sse.queue-capacity:64}") int queueCapacity,
            @Value("${ink.async.sse.keepalive-sec:60}") long keepAliveSec) {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, SSE_THREAD_PREFIX + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        return new ThreadPoolExecutor(
                coreSize, maxSize,
                keepAliveSec, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * @Async 业务线程池(章节审查、记忆抽取等)。
     * <p>使用 {@link ThreadPoolTaskExecutor} 而非 raw {@link ThreadPoolExecutor},原因:</p>
     * <ul>
     *   <li>原生支持 {@link TaskDecorator},可透传 ThreadLocal</li>
     *   <li>Spring 容器优雅关闭:shutdown 时等待 in-flight 任务完成</li>
     *   <li>对 Spring 4.x 的 {@code @Async} 注解解析更友好</li>
     * </ul>
     * <p>队列满后用 CallerRunsPolicy:让调用方同步执行,自然形成背压,避免任务丢失。</p>
     */
    @Bean(name = "novelAsyncExecutor", destroyMethod = "shutdown")
    public Executor novelAsyncExecutor(
            @Value("${ink.async.novel.core-size:4}") int coreSize,
            @Value("${ink.async.novel.max-size:16}") int maxSize,
            @Value("${ink.async.novel.queue-capacity:100}") int queueCapacity,
            @Value("${ink.async.novel.keepalive-sec:60}") long keepAliveSec) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds((int) keepAliveSec);
        executor.setThreadNamePrefix(ASYNC_THREAD_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setTaskDecorator(novelContextTaskDecorator());
        executor.initialize();
        log.info("[AsyncConfig] novelAsyncExecutor initialized: core={}, max={}, queue={}",
                coreSize, maxSize, queueCapacity);
        return executor;
    }

    /**
     * TaskDecorator:把当前线程的 NovelContext(novelId + userId)复制到子线程,并在子线程结束时清理。
     *
     * <p>这是 R5 用户隔离的关键:异步任务里调用 {@link NovelContext#requireNovelId()}
     * / {@link NovelContext#requireUserId()} 也能拿到正确的上下文,无需调用方显式传参。</p>
     *
     * <p>实现细节:</p>
     * <ul>
     *   <li>在装饰阶段捕获主线程的 novelId/userId(可能为 null)</li>
     *   <li>子线程开始时 set,结束时 clear(避免线程池复用导致数据串联)</li>
     *   <li>即使 task 抛异常,finally 也会清理</li>
     * </ul>
     */
    public TaskDecorator novelContextTaskDecorator() {
        return runnable -> {
            Long novelId = NovelContext.getNovelId();
            Long userId = NovelContext.getUserId();
            return () -> {
                boolean setNovel = false;
                boolean setUser = false;
                if (novelId != null) {
                    NovelContext.setNovelId(novelId);
                    setNovel = true;
                }
                if (userId != null) {
                    NovelContext.setUserId(userId);
                    setUser = true;
                }
                try {
                    runnable.run();
                } finally {
                    if (setNovel) {
                        NovelContext.clear();
                    } else if (setUser) {
                        NovelContext.clear();
                    }
                }
            };
        };
    }
}
