# Agent 开发详细指南(Novel Forge 版)

> 本文面向**第一次接触 Agent 开发的 Java 工程师**。
> 读完后你会明白:Agent 到底是什么、它和普通后端服务有什么区别、怎么用 Java 写一个 AI 小说生成 Agent。

---

## 目录

1. [Agent 是什么?](#1-agent-是什么)
2. [核心概念速览](#2-核心概念速览)
3. [LangChain4j 工作原理](#3-langchain4j-工作原理)
4. [项目结构详解](#4-项目结构详解)
5. [各模块开发详解](#5-各模块开发详解)
6. [调试与排错](#6-调试与排错)
7. [进阶路线](#7-进阶路线)

---

## 1. Agent 是什么?

### 1.1 一句话定义

> **Agent = LLM(大脑) + Tools(手) + Memory(记忆) + Planning(规划)**

普通 LLM 调用:用户问问题 → LLM 直接回答(只能基于训练数据)。
Agent 调用:用户问问题 → LLM 思考"我需要先查人物设定" → 调用 `queryCharacter` 工具 → 拿到结果 → 综合设定生成正文。

### 1.2 一个具体例子(小说写作)

作者说:"帮我写林晚在码头初遇苏砚的章节"

**普通 LLM** 会回答:它会编一段,但可能把林晚写成男性、把苏砚写成刀客——因为它不知道你的人设。

**Agent** 的处理流程:

```
作者: 帮我写林晚在码头初遇苏砚的章节,1500 字
   ↓
LLM 思考: 我需要先确认林晚和苏砚是谁
LLM 输出: function_call(name="queryCharacter", args={"name":"林晚"})
   ↓
框架拦截: 调用 Java 方法 NovelTools.queryCharacter("林晚")
Java 方法返回: "林晚,24岁,云陵城码头脚夫,外冷内热,左手腕有旧疤,武器短刀'霜序'"
   ↓
LLM 再次输出: function_call(name="queryCharacter", args={"name":"苏砚"})
框架返回: "苏砚,27岁,江湖游医,实为前朝皇室遗孤"
   ↓
LLM 再次输出: function_call(name="queryWorldSetting", args={"keyword":"云陵城"})
框架返回: "云陵城,青州州城,水路通达,有'千桥之城'美誉"
   ↓
LLM 综合设定,生成正文:
   "雨打在码头的青石板上。林晚踮着脚,把最后一袋米推上船舷..."
   ↓
作者收到: 一段符合人设与世界观的开篇
```

**关键点**:LLM 自己**决定**要不要调工具、调哪个工具、传什么参数。这就是"智能"的来源。

### 1.3 Agent vs 普通后端服务

| 维度 | 普通后端服务 | Agent |
|------|--------------|-------|
| 流程控制 | 代码写死(if/else/for) | LLM 动态决策 |
| 输入处理 | 参数校验 + 业务逻辑 | 自然语言理解 |
| 输出 | 固定数据结构 | 自然语言生成 |
| 扩展新功能 | 加新接口、改代码 | 加新工具(无需改主流程) |
| 不可预测性 | 低(确定流程) | 高(LLM 可能选错工具) |

> ⚠️ 注意:Agent 不是银弹。**确定性强的业务(转账、库存扣减)千万不要用 Agent**,用普通接口。Agent 适合**意图识别、内容生成、知识问答**这类需要"理解"与"创造"的场景。小说生成正是 Agent 的典型应用。

---

## 2. 核心概念速览

### 2.1 ChatModel(对话模型)

最基础的组件,就是"和 LLM 对话"的客户端。

```java
String reply = chatModel.chat("写一句武侠开场白");
```

类比:`ChatModel` 之于 Agent,就像 `HttpClient` 之于普通后端。

### 2.2 Tool(工具)

Java 方法,但加了 `@Tool` 注解,LLM 可以"看见"它的描述并主动调用。

```java
@Tool(name = "queryCharacter", description = "根据人物姓名查询其档案")
public String queryCharacter(@P("人物姓名") String name) { ... }
```

LLM 怎么"看见"?框架会把所有 Tool 的 `name + description + 参数` 拼成一段文本,塞进给 LLM 的请求里(OpenAI 叫 `tools` 字段)。

### 2.3 Memory(记忆)

让 Agent 记住之前说过的话,实现多轮对话。

```java
// 第一轮
agent.chat("novel-001", "主角叫林晚,24 岁");
// 第二轮
agent.chat("novel-001", "我刚才说主角多大?"); // 应该回答"24 岁"
```

实现方式:每次请求都把**历史消息**一起发给 LLM。Memory 组件负责管理这些历史消息(限制条数、淘汰旧消息)。

对写作来说,Memory 让 Agent 记住"上一章写到哪了"、"作者刚改了什么设定"。

### 2.4 RAG(检索增强生成)

让 Agent 能回答**私有知识**(你的世界观、人物卡、剧情时间线)。

```
作者提问 → 向量检索相关设定片段 → 把片段塞进 Prompt → LLM 基于片段作答
```

为什么需要?LLM 训练数据是公开的、静态的,你问"我小说里听潮阁有什么规矩"它根本不知道。RAG 让 LLM 临时"翻你的设定本"。

### 2.5 AiService(Agent 接口)

LangChain4j 的核心抽象。你写一个接口,框架自动生成实现:

```java
public interface WritingAssistantAgent {
    @SystemMessage("你是网文写作助手...")
    String chat(@MemoryId String userId, @UserMessage String message);
}

// 构建方式(在 AgentConfig 中)
WritingAssistantAgent agent = AiServices.builder(WritingAssistantAgent.class)
    .chatModel(chatModel)
    .tools(novelTools)
    .chatMemoryProvider(chatMemoryProvider)
    .build();
```

调用时就像调用普通 Java 方法,背后框架自动处理:拼 Prompt → 调 LLM → 解析工具调用 → 调 Java 方法 → 把结果回传 LLM → 返回最终回复。

---

## 3. LangChain4j 工作原理

### 3.1 一次 Agent 调用的完整流程

```
┌─────────────────────────────────────────────────────────────┐
│  你的代码: writingAgent.chat("novel-001", "写林晚初遇苏砚")   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  LangChain4j AiService 代理:                                │
│  1. 从 ChatMemoryProvider 取出 novel-001 的历史消息          │
│  2. 拼装 Prompt: SystemMessage + 历史 + 用户消息 + Tools 描述 │
│  3. 调用 ChatModel.chat(...)                                 │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  LLM (DeepSeek/OpenAI/Ollama) 思考并返回:                    │
│  "我需要调用 queryCharacter(name='林晚')"                    │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  LangChain4j 框架:                                          │
│  1. 解析 LLM 返回的 function_call                            │
│  2. 通过反射调用 NovelTools.queryCharacter("林晚")           │
│  3. 把 Java 方法返回值作为 tool_result 再发给 LLM             │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  LLM 基于工具结果继续思考:                                   │
│  "还需要查苏砚和云陵城..." → 再次调用工具                     │
│  收集完信息后,生成正文返回                                   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  LangChain4j: 把整轮对话存入 ChatMemory,返回正文给你         │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 Prompt 的实际结构

发送给 LLM 的请求大致长这样(简化版):

```json
{
  "model": "deepseek-chat",
  "messages": [
    {"role": "system", "content": "你是墨语,一名资深网文写作助手..."},
    {"role": "user", "content": "主角叫林晚,24 岁"},
    {"role": "assistant", "content": "好的,记下了..."},
    {"role": "user", "content": "写林晚初遇苏砚的章节"}
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "queryCharacter",
        "description": "根据人物姓名查询其档案(年龄/性格/外貌/武器/背景)...",
        "parameters": {"type": "object", "properties": {"name": {"type": "string", "description": "人物姓名,例如 林晚"}}}
      }
    },
    ...
  ]
}
```

**关键理解**:Tools 不是代码注入,只是把工具的"说明书"发给 LLM。LLM 决定调用后,框架在**你自己的进程内**执行 Java 方法,LLM 永远碰不到你的代码。

---

## 4. 项目结构详解

```
novel-forge/
├── pom.xml                              # Maven 依赖
├── README.md                            # 入门文档
├── docs/
│   ├── GUIDE.md                         # ⭐ 你正在看的这份
│   ├── ARCHITECTURE.md                  # 架构图与模块说明
│   └── FAQ.md                           # 常见问题
├── knowledge-base/                      # 设定库目录(放世界观/人物/时间线文档)
│   ├── world-setting.md
│   └── characters.md
└── src/main/
    ├── resources/
    │   └── application.yml              # 配置文件(API Key、模型参数)
    └── java/com/novel/agent/
        ├── AgentApplication.java        # Spring Boot 启动类
        ├── config/                      # 配置类
        │   ├── ModelConfig.java         #   - ChatModel Bean(OpenAI/Ollama)
        │   ├── EmbeddingConfig.java     #   - 向量库 Bean
        │   └── AgentConfig.java         #   - 各 Agent Bean 装配
        ├── tools/                       # 工具集(Agent 的"手")
        │   └── NovelTools.java          #   - 查人物/查设定/查时间线/字数/扩写
        ├── agent/                       # Agent 接口定义
        │   ├── WritingAssistantAgent.java  #   - 写作助手(多轮+工具)
        │   ├── OutlineAgent.java           #   - 大纲生成
        │   ├── ChapterAgent.java           #   - 章节生成
        │   ├── LoreAgent.java              #   - 设定问答(RAG)
        │   └── CharacterExtractionAgent.java # - 人物抽取(结构化输出)
        ├── service/                     # 业务服务
        │   └── KnowledgeBaseService.java#   - 设定库导入与检索
        ├── controller/                  # REST 接口
        │   └── AgentController.java     #   - 暴露 HTTP API
        └── runner/                      # 启动回调
            └── KnowledgeBaseInitializer.java # 启动时导入设定库
```

### 4.1 分层职责

| 层 | 职责 | 类比传统 MVC |
|----|------|--------------|
| `config` | 装配 Bean | 一样 |
| `tools` | Agent 可调用的方法 | Service 层方法 |
| `agent` | Agent 接口(LLM 大脑) | Controller(但实际逻辑由 LLM 决定) |
| `service` | 普通业务逻辑(设定库导入) | Service 层 |
| `controller` | HTTP 入口 | Controller |

**最大区别**:传统 MVC 是 `Controller → Service → DB` 的固定流程;Agent 是 `Controller → Agent(LLM 决策) → Tools(对应 Service) → DB` 的动态流程。

---

## 5. 各模块开发详解

### 5.1 模型配置(ModelConfig.java)

**目标**:把 LangChain4j 的 `ChatModel` 装配成 Spring Bean。

```java
@Bean
@Primary
@Profile("openai")
public ChatModel openAiChatModel(...) {
    return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)     // DeepSeek/通义/Moonshot 都改这里
            .modelName("deepseek-chat")
            .temperature(0.85)    // 写作需要更高 temperature,增加创意
            .maxTokens(4000)      // 长文生成篇幅大
            .timeout(Duration.parse("PT120S"))
            .build();
}
```

**关键参数**(对写作的影响):
- `temperature`:0~1,**小说创作建议 0.8~0.9**,太低文字干瘪,太高容易跑题;
- `maxTokens`:限制单次输出长度,**章节生成建议 4000+**;
- `timeout`:长文生成慢,建议 120s+。

**切换 Provider**:改 `application.yml` 的 `spring.profiles.active` 即可,无需改代码。

### 5.2 工具开发(NovelTools.java)

**目标**:写一个 Java 方法,让 LLM 能调用它。

**3 个关键点**:

1. **类必须是 Spring Bean**(加 `@Component`),因为 AiService 通过 Bean 名字找到它;
2. **方法加 `@Tool` 注解**,写清楚 `name` 和 `description`(LLM 据此决定何时调用);
3. **参数加 `@P` 注解**,描述参数含义。

```java
@Tool(name = "queryCharacter",
      description = "根据人物姓名查询其档案。当需要描写某个角色的言行、确保人设不崩塌时调用此工具。")
public String queryCharacter(@P("人物姓名,例如 林晚、苏砚、赵九") String name) {
    // 这里就是普通 Java 代码,该查库查库、该调接口调接口
    return CHARACTER_DB.getOrDefault(name, "未找到该人物");
}
```

**写好 description 的技巧(写作场景)**:
- 说清楚"什么时候该调用"(触发条件);
- 说清楚"输入是什么格式";
- 给具体例子(对中文 LLM 尤其重要);
- **避免歧义**:如果有两个相似工具,要在 description 里区分。

**反例**:
```java
@Tool(description = "查人物")  // 太模糊,LLM 不知道何时调用
```

**正例**:
```java
@Tool(description = "根据人物姓名查询其档案(年龄/性格/外貌/武器/背景)。当需要描写某个角色的言行、确保人设不崩塌时调用此工具。")
```

### 5.3 Agent 接口(WritingAssistantAgent.java)

**目标**:定义一个 Java 接口,LangChain4j 自动生成实现。

```java
public interface WritingAssistantAgent {

    @SystemMessage("""
            你是"墨语",一名资深网文写作助手。你的职责:
            1. 与作者协作完成小说创作:写章节、改稿子、补设定;
            2. 涉及具体人物、地点、势力、剧情节点时,**必须**先调用对应工具查询设定;
            ...
            """)
    String chat(@MemoryId String userId, @UserMessage String message);
}
```

**注解说明**:
- `@SystemMessage`:系统提示词,定义 Agent 人设、规则、限制;
- `@UserMessage`:用户消息(就是方法参数);
- `@MemoryId`:会话 ID,框架据此隔离不同用户/作品的历史。

**为什么用接口?**
- 调用就像普通 Java 方法,不用拼 Prompt 字符串;
- 类型安全,IDE 有提示;
- 框架自动处理工具调用循环,你不用写"调 LLM → 解析 → 调工具 → 回传"的样板代码。

### 5.4 多轮记忆(AgentConfig.java)

**目标**:让 Agent 记住之前的对话。

```java
@Bean
public ChatMemoryProvider chatMemoryProvider() {
    // 每个作品独立 Memory,保留最近 20 条
    return memoryId -> MessageWindowChatMemory.builder()
            .id(memoryId)
            .maxMessages(20)
            .build();
}
```

**两种主流策略**:
- `MessageWindowChatMemory`:按消息条数限制(简单,本项目用);
- `TokenWindowChatMemory`:按 token 数限制(精确,适合长对话)。

**写作场景注意**:Memory 默认在内存里,重启就丢。生产环境要自定义实现,把消息持久化到 Redis/DB,这样作者关掉浏览器下次还能接着写。

### 5.5 RAG 设定库(KnowledgeBaseService.java + LoreAgent)

**目标**:让 Agent 能回答私有设定。

**两步流程**:

**Step 1 - 入库(离线)**:
```java
Document doc = FileSystemDocumentLoader.loadDocument("world-setting.md");
DocumentSplitter splitter = DocumentSplitters.recursive(300, 30);  // 每段 300 字,重叠 30
List<TextSegment> segments = splitter.split(doc);
for (TextSegment seg : segments) {
    Embedding emb = embeddingModel.embed(seg.text()).content();  // 文本→向量
    embeddingStore.add(emb, seg);                                 // 向量+原文 入库
}
```

**Step 2 - 检索(在线,由 LoreAgent 自动完成)**:
```java
// 框架自动执行:
Embedding queryEmb = embeddingModel.embed("听潮阁有什么规矩").content();
List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(queryEmb, 5);  // top-5
// 然后把 matches 中的文本拼到 Prompt 中
```

**关键概念**:
- **Embedding**:把文本转成 384 维(本模型)的浮点数组,语义相近的文本向量也相近;
- **相似度**:用余弦相似度衡量两个向量的"距离";
- **切片**:设定文档太长不能整篇塞进 Prompt,要切成小段;重叠是为了避免切断语义。

**LoreAgent 的特别之处**:在构建时配了 `ContentRetriever`(见 `AgentConfig`),框架会自动在每次调用前检索相关片段并塞进 Prompt。

### 5.6 结构化输出(CharacterExtractionAgent.java)

**目标**:让 LLM 输出 Java 对象,而不是 String。

```java
public interface CharacterExtractionAgent {
    @SystemMessage("...")
    CharacterProfile extract(@UserMessage String text);

    record CharacterProfile(String name, Integer age, String identity,
                            String personality, String appearance) {}
}

// 调用
CharacterProfile p = agent.extract("林晚,24岁,云陵城码头脚夫,外冷内热,左手腕有旧疤");
// p.name() == "林晚", p.age() == 24, ...
```

**原理**:框架在 Prompt 里加一句"请返回 JSON,格式为 {name, age, identity, personality, appearance}",拿到响应后用 Jackson 反序列化。

**写作场景应用**:
- 从一段人物描写中抽取结构化人物卡;
- 把对话片段分类为"日常/战斗/感情/转折"等场景类型;
- 从剧情大纲中抽取关键事件要素。

### 5.7 大纲与章节生成(OutlineAgent / ChapterAgent)

这两个 Agent 是写作场景的"专职"Agent,职责单一:

- **OutlineAgent**:输入题材+章节数,输出 markdown 大纲。无 Memory、无 Tools,纯生成。
- **ChapterAgent**:输入章节大纲+字数,输出正文。带 Memory(衔接前文)与 Tools(查设定)。

**设计要点**:不要把所有功能塞进一个 Agent。专职 Agent 提示词更聚焦,LLM 表现更稳定。

---

## 6. 调试与排错

### 6.1 打开详细日志

`application.yml` 已配置:
```yaml
langchain4j:
  open-ai:
    chat-model:
      log-requests: true
      log-responses: true
logging:
  level:
    dev.langchain4j: DEBUG
    com.novel.forge: DEBUG
```

启动后会看到完整的 Prompt 和 LLM 响应,排查问题非常方便。

### 6.2 常见问题

#### Q1: LLM 不调用工具,直接凭空写

**原因**:`@Tool` 的 description 写得不清楚,LLM 不知道何时该调用。

**解决**:重写 description,明确"当需要描写人物时调用"。

#### Q2: 工具被调用了,但参数不对

**原因**:`@P` 描述不够具体,LLM 猜错了参数格式。

**解决**:在 `@P` 中给例子,如 `@P("人物姓名,例如 林晚、苏砚、赵九")`。

#### Q3: 多轮对话记不住

**原因**:`@MemoryId` 传成了不同的值。

**解决**:确保同一作品用同一 ID,如 novelId 从数据库取。

#### Q4: 启动报错"找不到 chatModel Bean"

**原因**:`spring.profiles.active` 没设或拼写错。

**解决**:检查 `application.yml`,应为 `openai` 或 `ollama`。

#### Q5: 调用 DeepSeek 报 401

**原因**:API Key 错误或未设置。

**解决**:检查 `application.yml` 的 `api-key` 或环境变量 `OPENAI_API_KEY`。

#### Q6: 生成的章节字数严重不达标

**原因**:`maxTokens` 太小,或 LLM 偷懒。

**解决**:
- 调大 `maxTokens`(如 6000);
- 在 SystemMessage 强调"字数必须达到 X,不足时继续扩展";
- 用 `countWords` 工具让 LLM 自检。

更多问题见 [FAQ.md](FAQ.md)。

---

## 7. 进阶路线

学完本项目后,按以下顺序扩展:

### 7.1 替换向量库

`InMemoryEmbeddingStore` 重启即丢,生产换 Redis/Chroma/Milvus:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-redis-spring-boot-starter</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
```

### 7.2 持久化 Memory

实现自己的 `ChatMemoryStore`,把消息存 Redis:

```java
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {
    // ... 从 Redis 读写消息列表
}
```

### 7.3 多 Agent 协作(总编 Agent)

一个"小说总编 Agent"根据作者意图分发给不同子 Agent:
- 大纲 Agent(规划剧情)
- 章节 Agent(写正文)
- 设定 Agent(查/补设定)
- 校对 Agent(检查人设矛盾、时间线冲突)

LangChain4j 没有内置 Multi-Agent 框架,需要自己组织。可参考 LangGraph 思路。

### 7.4 引入 Prompt 管理

把 SystemMessage 外置到文件,方便产品/编辑修改:

```yaml
novel:
  prompts:
    writing-assistant: classpath:prompts/writing-assistant.txt
    chapter-writer: classpath:prompts/chapter-writer.txt
```

### 7.5 监控与可观测性

- 接入 LangSmith / LangFuse 做 Trace;
- 记录每次调用的 token 消耗、延迟、工具调用次数;
- 对接 Prometheus + Grafana 监控 QPS、错误率。

### 7.6 安全与限流

- 输入过滤(防 Prompt Injection,比如作者输入"忽略之前的指令");
- 输出审核(敏感词、违规内容);
- 按 userId 限流;
- 工具白名单(不同作者能用不同工具集)。

### 7.7 长文档生成的特殊技巧

- **分段生成 + 衔接**:超长章节分段生成,每段结尾保存"钩子",下一段开头接上;
- **大纲驱动**:不要让 LLM 一次写一万字,先拆 5 个小节,每节单独生成;
- **风格一致性**:在 SystemMessage 中提供"风格样本"(几段范文),让 LLM 模仿。

---

## 总结

Agent 开发的核心心智模型:

> **你写工具(Java 方法),LLM 决定何时调用、传什么参数,框架负责调度。**

不要把 LLM 当作"会说话的数据库",它更像一个"会推理的写手"——你给它工具说明书(工具)和参考资料(RAG),它根据作者的需求决定该查什么、怎么写。

接下来,建议你:
1. 把项目跑起来,挨个接口试一遍;
2. 在 `NovelTools` 加一个自己的工具(如"查询武功招式"),看看 LLM 能不能正确调用;
3. 把自己的小说设定放进 `knowledge-base/`,跑一遍 RAG 流程;
4. 阅读 [ARCHITECTURE.md](ARCHITECTURE.md) 加深对整体结构的理解。

祝你创作愉快!
