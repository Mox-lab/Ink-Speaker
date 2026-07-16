# 架构说明

> 本文档用图和表讲清项目整体结构、模块协作关系、以及产品化扩展后的全貌。

---

## 1. 整体架构(产品化扩展后)

```mermaid
graph TB
    subgraph Client[客户端]
        Web[Web 前端]
        API_C[外部 API 调用方]
        DOC[设定稿文件<br/>docx/pdf/txt]
    end

    subgraph Gateway[接入层]
        NGINX[Nginx<br/>TLS / 域名 / 静态资源]
    end

    subgraph App[Ink Realm 单体应用 :9688]
        direction TB

        subgraph Web_Layer[1. Web 层]
            CTRL[AgentController<br/>REST + SSE]
            SEC[Spring Security<br/>JWT 鉴权]
            VAL[Validation<br/>参数校验]
            LIMIT[Bucket4j<br/>接口限流]
        end

        subgraph Service_Layer[2. 业务/Agent 层]
            OUTLINE[OutlineAgent]
            CHAPTER[ChapterAgent<br/>多轮 Memory + Tools]
            WRITING[WritingAssistantAgent]
            CHAR_EXT[CharacterExtractionAgent]
            LORE[LoreAgent<br/>RAG 检索]
            ASYNC["@Async<br/>章节生成异步化"]
            SCHED["@Scheduled + ShedLock<br/>集群定时任务锁]
        end

        subgraph Resilience[3. 弹性层]
            RES[Resilience4j<br/>限流/熔断/重试]
            CACHE_L1[Caffeine L1<br/>本地热缓存]
        end

        subgraph AI_Layer[4. AI 客户端层]
            LC4J[LangChain4j AiServices]
            JDK_HTTP[http-client-jdk<br/>规避 SB4.x 不兼容]
            EMB[all-minilm-l6-v2<br/>384 维嵌入]
        end

        subgraph Persistence[5. 持久化层]
            JPA[JPA Repository]
            MP[MyBatis-Plus<br/>复杂查询/分页]
            KB[KnowledgeBaseService<br/>向量检索]
            FLY[Flyway<br/>schema 迁移]
        end

        subgraph Tools[6. NovelTools]
            T1[queryCharacter]
            T2[queryWorldSetting]
            T3[expandScene]
        end

        subgraph Observability[7. 可观测性 :9689]
            ACT[Actuator<br/>/health /metrics]
            PROM[Prometheus 指标]
            LOG4J2[Log4j2<br/>结构化日志]
        end

        subgraph Config[8. 配置 & 加密]
            JASYPT[Jasypt<br/>DB/API Key 加密]
        end
    end

    subgraph External_AI[外部 AI]
        LLM[(OpenAI 兼容<br/>DeepSeek/通义/Moonshot)]
        OLLAMA[(Ollama 本地<br/>qwen2.5)]
    end

    subgraph Storage[数据 & 缓存]
        PG[(PostgreSQL 5432<br/>业务表 + pgvector)]
        REDIS[(Redis 6379<br/>L2 缓存/分布式锁)]
    end

    subgraph Monitor[监控层]
        PROM_SERV[(Prometheus)]
        GRAFANA[(Grafana)]
    end

    Web --> NGINX
    API_C --> NGINX
    DOC --> CTRL
    NGINX --> SEC --> LIMIT --> VAL --> CTRL
    CTRL --> OUTLINE & CHAPTER & WRITING & CHAR_EXT & LORE & ASYNC
    OUTLINE & CHAPTER & WRITING & CHAR_EXT & LORE --> RES
    RES --> LC4J --> JDK_HTTP
    JDK_HTTP --> LLM
    JDK_HTTP --> OLLAMA
    LORE --> EMB
    CHAR_EXT --> EMB
    CHAPTER -.工具调用.-> Tools
    WRITING -.工具调用.-> Tools
    Tools --> JPA
    WRITING & CHAPTER --> CACHE_L1
    WRITING & CHAPTER --> REDIS
    Service_Layer --> JPA & MP
    KB --> EMB --> PG
    JPA --> PG
    MP --> PG
    FLY -.建表.-> PG
    ACT --> PROM --> PROM_SERV
    PROM_SERV --> GRAFANA
    SCHED --> REDIS
    JASYPT -.解密.-> App

    classDef ai fill:#dbeafe,stroke:#2563eb
    classDef store fill:#fce7f3,stroke:#db2777
    classDef mon fill:#dcfce7,stroke:#16a34a
    class LLM,OLLAMA,EMB,LC4J,JDK_HTTP ai
    class PG,REDIS store
    class PROM_SERV,GRAFANA mon
```

---

## 2. 端口与外部依赖一览

| 组件 | 端口/路径 | 用途 |
|------|-----------|------|
| 业务端口 | `:9688` | REST API + SSE 流式输出 |
| 管理端口 | `:9689` | Actuator `/actuator/**`(独立端口,便于防火墙隔离) |
| Swagger UI | `:9688/swagger-ui.html` | API 文档 |
| PostgreSQL | `localhost:5432/ink_realm` | 业务表 + pgvector 向量库 |
| Redis | `localhost:6379` | L2 缓存 + ShedLock 锁后端 |
| OpenAI 兼容 | `https://aihub.bielcrystal.com/v1` | 主 LLM Provider |
| Ollama | `http://localhost:11434` | 本地备用 LLM |

---

## 3. 模块职责一览

| 包 | 主要类 | 职责 |
|----|--------|------|
| `config` | `ModelConfig` | 装配 `ChatModel` / `StreamingChatModel` Bean(OpenAI 兼容 + Ollama) |
| `config` | `EmbeddingConfig` | 装配 `EmbeddingStore` Bean(pgvector) |
| `config` | `AgentConfig` | 装配 5 个 Agent Bean + `ChatMemoryProvider` |
| `config` | `SecurityConfig` | Spring Security 鉴权链(JWT + 角色控制) |
| `tools` | `NovelTools` | 工具集:查人物/查设定/查时间线/字数统计/扩写场景 |
| `agent` | `WritingAssistantAgent` | 写作助手:多轮对话 + 工具调用 |
| `agent` | `OutlineAgent` | 大纲生成 |
| `agent` | `ChapterAgent` | 章节生成(带记忆+工具) |
| `agent` | `LoreAgent` | 设定问答 Agent(RAG) |
| `agent` | `CharacterExtractionAgent` | 人物抽取(结构化输出) |
| `service` | `KnowledgeBaseService` | 文档加载/切片/向量化/检索 |
| `controller` | `AgentController` | REST 接口入口 |
| `runner` | `KnowledgeBaseInitializer` | 启动时自动导入设定库 |
| `runner` | `DataInitializer` | 启动时插入示例人物/设定 |

---

## 4. 数据流(以章节生成为例)

```
作者: POST /api/chapter {"outline":"雨夜林晚初遇苏砚","wordCount":1500}
   │
   ↓
[Spring Security] 验 JWT → [Bucket4j] 限流 → [Validation] 参数校验
   │
   ↓
AgentController.chapter(body)
   │
   ↓
ChapterAgent.write("novel-001", "雨夜林晚初遇苏砚", 1500)
   │  (LangChain4j 代理)
   ↓
┌──────────────────────────────────────────────────────┐
│ 1. 从 ChatMemoryProvider 取 novel-001 历史章节        │
│ 2. 拼 Prompt:                                       │
│    SystemMessage + 历史 + 用户消息                   │
│    + 所有 @Tool 的 name/desc/params                  │
│ 3. [Resilience4j] 限流 + 熔断 + 重试                  │
│ 4. 调用 ChatModel.chat(prompt)                       │
└──────────────────────────────────────────────────────┘
   │
   ↓ HTTP POST /v1/chat/completions (JDK HttpClient)
┌──────────────────────────────────────────────────────┐
│ LLM 返回 tool_calls: queryCharacter("林晚")           │
└──────────────────────────────────────────────────────┘
   │
   ↓
[NovelTools.queryCharacter] → JPA → PostgreSQL
   │
   ↓
LLM 继续调用 queryCharacter("苏砚")、queryWorldSetting("云陵城")...
   │
   ↓
收集完信息后,生成 1500 字正文
   │
   ↓
返回 {"content": "..."} 给客户端
```

---

## 5. 产品化扩展模块说明

### 5.1 健康监控(Actuator + Prometheus)

| 端点 | 路径 | 用途 |
|------|------|------|
| health | `/actuator/health` | K8s 探针、运维查看 |
| info | `/actuator/info` | 应用元信息(版本/Java/OS) |
| metrics | `/actuator/metrics` | JVM/HTTP/HikariCP 指标 |
| prometheus | `/actuator/prometheus` | Prometheus 抓取端点 |
| env | `/actuator/env` | 配置项查看(敏感字段已脱敏) |
| loggers | `/actuator/loggers` | 运行时动态调整日志级别 |

**关键设计**:管理端口 `9689` 独立于业务端口 `9688`,防火墙只需对运维网段放行 9689,公网仅暴露 9688。

### 5.2 数据库版本化迁移(Flyway)

- SQL 文件位置:`src/main/resources/db/migration/V{version}__{name}.sql`
- 启动时自动比对 checksum,变更了已执行文件会报错(强制走增量迁移)
- `baseline-on-migrate: true` 兼容已有库平滑接入
- 当前 V1:初始化 novel / novel_character / novel_world_setting / novel_chapter_timeline

### 5.3 分布式缓存(Redis + Caffeine 二级缓存)

```
请求 → L1 Caffeine(本地,纳秒级) → 未命中 → L2 Redis(跨实例,毫秒级) → 未命中 → DB/LLM
```

**小说业务典型用法**:
- **LLM 结果缓存**:同一 prompt 24h 内复用(prompt hash → reply),省 Token 钱
- **人物/设定热数据**:频繁被 NovelTools 读取,缓存到 L1
- **分布式锁**:防止同一作品并发写章节,锁 key = `novel:lock:write:{novelId}`

### 5.4 弹性容错(Resilience4j)

| 实例 | 类型 | 配置 | 用途 |
|------|------|------|------|
| `llm-call` | CircuitBreaker | 失败率 ≥50% 触发,30s 后半开 | LLM 服务故障时快速失败 |
| `llm-call` | RateLimiter | 5 req/s | 避免触发上游限流(DeepSeek 等) |
| `llm-call` | Retry | 3 次,1s 间隔 | 网络抖动重试,仅 IOException/Timeout |
| `llm-call` | Bulkhead | 8 并发 + 32 队列 | LLM 调用隔离,防止拖垮整个线程池 |

### 5.5 接口限流(Bucket4j)

- 单机限流,基于 Caffeine 存储 token bucket
- 用于 `/api/chat`、`/api/writing` 等用户入口接口
- 跨实例限流可后续切到 `bucket4j-redis`

### 5.6 配置加密(Jasypt)

- 数据库密码、API Key 等敏感字段用 `ENC(...)` 包裹
- 启动时通过 `JASYPT_KEY` 环境变量注入主密钥解密
- 算法:`PBEWITHHMACSHA512ANDAES_256`(AES-256 + HMAC-SHA512)

### 5.7 鉴权(JWT + Spring Security)

- 算法:HS256(后续可换 RS256)
- Access Token TTL:2h,Refresh Token TTL:7d
- 角色模型:`USER`(普通作者)/ `ADMIN`(管理员)
- `/api/**` 走 JWT,`/swagger-ui/**` 仅开发环境放开

### 5.8 集群定时任务锁(ShedLock)

- 后端:Redis(无需建表,直接复用缓存实例)
- 用法:在 `@Scheduled` 方法上加 `@SchedulerLock`
- 单体阶段用不到,多实例部署时防止重复执行(例:每日 Token 用量统计、自动续写)

### 5.9 异步任务(@Async + ThreadPoolTaskExecutor)

- 章节生成是分钟级操作,HTTP 必然超时
- 接口收到请求 → 写入任务表 → 立即返回 taskId → 异步生成 → SSE 推送/写库
- 线程池:core=4, max=16, queue=100(配置见 `spring.task.execution`)

### 5.10 测试栈(Testcontainers + Spring Boot Test)

- 真 PostgreSQL 容器跑集成测试,不 mock 数据库
- 保证 JPA 实体、Flyway 脚本、pgvector 索引在真库下可用
- 测试覆盖:Agent 契约测试(固定 prompt 验输出格式)、Repository 测试、Controller 集成测试

### 5.11 文件处理(Tika + POI)

- **Tika**:设定稿导入,支持 docx/pdf/txt/epub 等格式
- **POI**:作品导出 docx,作者可下载完整小说稿

### 5.12 API 文档(SpringDoc OpenAPI 3)

- 路径:`/swagger-ui.html`
- 自动扫描 `ink.realm.controller` 包
- 配合 JWT 调试:在 Swagger 页面右上角 Authorize 填 token

### 5.13 工具与映射(Lombok + MapStruct + Hutool)

- **Lombok**:实体/DTO 样板代码消除
- **MapStruct**:Entity ↔ DTO 编译期生成映射代码,无反射
- **Hutool**:小说文本处理(分段、Token 计数、正则清洗)

---

## 6. 关键设计决策

### 6.1 为什么用 `AiServices.builder()` 而不是 `@AiService` 注解

| 方式 | 优点 | 缺点 |
|------|------|------|
| `@AiService` 注解 | 代码更简洁 | 不同版本属性差异大,容易踩坑 |
| `AiServices.builder()` | 类型安全、对 Memory/Retriever/Tools 注入更可控 | 多几行代码 |

本项目用 `AiServices.builder()`,因为写作场景对 Memory 和 Tools 的注入需求复杂(有的 Agent 要 RAG,有的不要),手动构建更清晰。

### 6.2 为什么用 `langchain4j-http-client-jdk` 而不是 `spring-restclient`

LangChain4j 1.17.x 的 `langchain4j-http-client-spring-restclient` 引用了 `org.springframework.boot.http.client.ClientHttpRequestFactorySettings`,该类在 Spring Boot 4.x 中已移除(我已通过解压 4.1.0 jar 验证)。改用纯 JDK HttpClient 实现,完全不依赖 Spring Boot 内部类。

### 6.3 为什么同时配置 OpenAI 和 Ollama

- **OpenAI 兼容**(DeepSeek 等):质量高、便宜、需要网络和 API Key;
- **Ollama 本地**:免费、离线可用、数据不出本机,但需要 GPU/CPU 性能;

通过 Spring Profile 切换,无需改代码。写作场景常见用法:OpenAI 做主模型生成正文,Ollama 做本地校对或敏感场景。

### 6.4 为什么 Memory 用 20 条窗口

- 太小:上下文丢失,Agent 表现"健忘",忘记前文设定;
- 太大:Token 消耗高、延迟增加、可能超出模型上下文长度;
- 20 条是经验值,生产可按场景调优(章节 Agent 可调大到 50)。

### 6.5 为什么拆 5 个 Agent 而不是一个大而全的 Agent

- **提示词聚焦**:专职 Agent 的 SystemMessage 只关心一件事,LLM 表现更稳定;
- **可独立调优**:大纲 Agent 可以用便宜模型,章节 Agent 用强模型;
- **可独立扩展**:校对、改稿等新功能加新 Agent,不影响已有逻辑。

### 6.6 为什么管理端口独立

业务端口 `9688` 对公网开放,管理端口 `9689` 仅内网开放。`/actuator/env`、`/actuator/configprops` 等端点可能泄露配置信息,独立端口 + 防火墙策略是最佳实践。

---

## 7. 扩展点

| 想做什么 | 改哪里 |
|----------|--------|
| 换 LLM Provider | `ModelConfig.java` + `application.yml` 的 `spring.profiles.active` |
| 加新工具(如"查武功招式") | `NovelTools.java` 加方法 |
| 换向量库 | `EmbeddingConfig.java` + 改 pom 依赖 |
| 持久化 Memory | 自定义 `ChatMemoryStore` 实现(用 Redis) |
| 改 Agent 人设 | 各 Agent 接口的 `@SystemMessage` |
| 调整 RAG 检索数量 | `application.yml` 的 `ink.rag-top-k` |
| 新增"校对 Agent" | 新建接口 + 在 AgentConfig 加 Bean + Controller 加接口 |
| 加定时任务 | `@Scheduled` + `@SchedulerLock` 注解 |
| 调整熔断阈值 | `application.yml` 的 `resilience4j.circuitbreaker.instances.llm-call` |
| 修改线程池 | `application.yml` 的 `spring.task.execution` |

---

## 8. 与传统 Spring MVC 对比

```
传统 MVC:
  Controller → Service → Mapper → DB
  (流程固定,代码写死)

Agent 项目:
  Controller → Agent(LLM) ⇄ Tools → Service → DB
                ↓↑
              Memory
                ↓↑
              EmbeddingStore (RAG)
  (流程由 LLM 动态决定,Tools 可插拔)
```

**心智转换**:从"写流程"到"写工具说明书"。你不再决定调用顺序,LLM 决定。你的工作是:
1. 写好工具的 description(让 LLM 知道何时用);
2. 设计好 SystemMessage(给 LLM 立规矩);
3. 准备好 Memory / RAG 数据(给 LLM 提供上下文)。

---

## 9. 微服务化路线图(预留)

| 阶段 | 加什么 | 触发条件 |
|------|--------|----------|
| 当前 | 单体 ink-realm:9688 | 用户量 < 100,日生成量 < 1k 章节 |
| 阶段 1 | + Nginx 反代 + Prometheus/Grafana | 上生产 |
| 阶段 2 | + Spring Cloud Gateway + Nacos | 拆出独立服务(用户服务/作品服务/生成服务) |
| 阶段 3 | + RabbitMQ/Kafka + Micrometer Tracing | 异步生成量大、需要服务间解耦 |
| 阶段 4 | + ShardingSphere + 章节库/向量库分库 | 单库数据 > 1T |
