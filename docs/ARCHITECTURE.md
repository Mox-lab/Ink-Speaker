# 架构说明

> 本文档用图和表讲清项目整体结构、模块协作关系。

---

## 1. 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                       客户端 (curl/前端编辑器)                    │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP
                             ↓
┌─────────────────────────────────────────────────────────────────┐
│                     AgentController (REST)                      │
│  /api/chat  /api/writing  /api/outline  /api/chapter            │
│  /api/character  /api/lore  /api/memory                          │
└────────┬──────────────┬──────────────┬──────────────┬───────────┘
         │              │              │              │
         ↓              ↓              ↓              ↓
   ┌──────────┐  ┌──────────┐   ┌──────────┐  ┌──────────────┐
   │ChatModel│  │ Writing  │   │ Outline  │  │  Character   │
   │ (直接)  │  │ Assistant│   │  Agent   │  │ExtractionAgt │
   │          │  │  Agent   │   │          │  │              │
   └────┬─────┘  └────┬─────┘   └────┬─────┘  └──────┬───────┘
        │              │              │               │
        │         ┌────┴────┐         │               │
        │         ↓         ↓         ↓               ↓
   ┌────┴──────────────┴───────────────────────────────┴────┐
   │              LangChain4j 框架(自动代理)                │
   │  - 拼装 Prompt  - 调用 LLM  - 解析工具调用             │
   │  - 调度 Tools   - 管理 Memory  - 检索 RAG             │
   └────┬─────────────────┬──────────────────┬─────────────┘
        │                 │                  │
        ↓                 ↓                  ↓
   ┌─────────┐      ┌──────────┐       ┌──────────────┐
   │ChatModel│      │  Novel   │       │EmbeddingStore│
   │  Bean   │      │  Tools   │       │  + Model     │
   │(OpenAI/ │      │ (Java    │       │  (RAG 向量库)│
   │ Ollama) │      │  方法)   │       │              │
   └────┬────┘      └────┬─────┘       └──────┬───────┘
        │                │                   │
        ↓                ↓                   ↓
   ┌─────────┐      ┌──────────┐       ┌──────────────┐
   │LLM 服务 │      │ 设定/    │       │ 小说设定库   │
   │(DeepSeek│      │ 人物/    │       │(世界观/人物/ │
   │ /本地)  │      │ 时间线库 │       │ 时间线文档)  │
   └─────────┘      └──────────┘       └──────────────┘
```

---

## 2. 模块职责一览

| 包 | 主要类 | 职责 |
|----|--------|------|
| `config` | `ModelConfig` | 装配 `ChatModel` / `StreamingChatModel` Bean(OpenAI 兼容 + Ollama) |
| `config` | `EmbeddingConfig` | 装配 `EmbeddingStore` Bean(内存向量库) |
| `config` | `AgentConfig` | 装配 5 个 Agent Bean + `ChatMemoryProvider` |
| `tools` | `NovelTools` | 工具集:查人物/查设定/查时间线/字数统计/扩写场景 |
| `agent` | `WritingAssistantAgent` | 写作助手:多轮对话 + 工具调用 |
| `agent` | `OutlineAgent` | 大纲生成 |
| `agent` | `ChapterAgent` | 章节生成(带记忆+工具) |
| `agent` | `LoreAgent` | 设定问答 Agent(RAG) |
| `agent` | `CharacterExtractionAgent` | 人物抽取(结构化输出) |
| `service` | `KnowledgeBaseService` | 文档加载/切片/向量化/检索 |
| `controller` | `AgentController` | REST 接口入口 |
| `runner` | `KnowledgeBaseInitializer` | 启动时自动导入设定库 |

---

## 3. 数据流(以章节生成为例)

```
作者: POST /api/chapter {"outline":"雨夜林晚初遇苏砚","wordCount":1500}
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
│ 3. 调用 ChatModel.chat(prompt)                       │
└──────────────────────────────────────────────────────┘
   │
   ↓ HTTP POST /v1/chat/completions
┌──────────────────────────────────────────────────────┐
│ LLM (DeepSeek) 返回:                                │
│ {                                                   │
│   "tool_calls": [{                                  │
│     "function": {                                   │
│       "name": "queryCharacter",                     │
│       "arguments": "{\"name\":\"林晚\"}"              │
│     }                                               │
│   }]                                                │
│ }                                                   │
└──────────────────────────────────────────────────────┘
   │
   ↓
┌──────────────────────────────────────────────────────┐
│ 框架解析 tool_calls,反射调用                          │
│ NovelTools.queryCharacter("林晚")                    │
│ 返回: "林晚,24岁,云陵城码头脚夫,外冷内热..."         │
└──────────────────────────────────────────────────────┘
   │
   ↓
┌──────────────────────────────────────────────────────┐
│ LLM 再次调用 queryCharacter("苏砚")、                 │
│ queryWorldSetting("云陵城")...                        │
│ 收集完信息后,生成 1500 字正文                        │
└──────────────────────────────────────────────────────┘
   │
   ↓
ChapterAgent.write() 返回正文
   │
   ↓
AgentController 返回 {"content": "..."}
```

---

## 4. 关键设计决策

### 4.1 为什么用 `AiServices.builder()` 而不是 `@AiService` 注解

| 方式 | 优点 | 缺点 |
|------|------|------|
| `@AiService` 注解 | 代码更简洁 | 不同版本属性差异大,容易踩坑 |
| `AiServices.builder()` | 类型安全、对 Memory/Retriever/Tools 注入更可控 | 多几行代码 |

本项目用 `AiServices.builder()`,因为写作场景对 Memory 和 Tools 的注入需求复杂(有的 Agent 要 RAG,有的不要),手动构建更清晰。

### 4.2 为什么同时配置 OpenAI 和 Ollama

- **OpenAI 兼容**(DeepSeek 等):质量高、便宜、需要网络和 API Key;
- **Ollama 本地**:免费、离线可用、数据不出本机,但需要 GPU/CPU 性能;

通过 Spring Profile 切换,无需改代码。写作场景常见用法:OpenAI 做主模型生成正文,Ollama 做本地校对或敏感场景。

### 4.3 为什么用 InMemoryEmbeddingStore

- 演示项目,零依赖;
- 切换到 Redis/Chroma 只需换 Bean 配置,业务代码不动。

### 4.4 为什么 Memory 用 20 条窗口

- 太小:上下文丢失,Agent 表现"健忘",忘记前文设定;
- 太大:Token 消耗高、延迟增加、可能超出模型上下文长度;
- 20 条是经验值,生产可按场景调优(章节 Agent 可调大到 50)。

### 4.5 为什么拆 5 个 Agent 而不是一个大而全的 Agent

- **提示词聚焦**:专职 Agent 的 SystemMessage 只关心一件事,LLM 表现更稳定;
- **可独立调优**:大纲 Agent 可以用便宜模型,章节 Agent 用强模型;
- **可独立扩展**:校对、改稿等新功能加新 Agent,不影响已有逻辑。

---

## 5. 扩展点

| 想做什么 | 改哪里 |
|----------|--------|
| 换 LLM Provider | `ModelConfig.java` + `application.yml` |
| 加新工具(如"查武功招式") | `NovelTools.java` 加方法 |
| 换向量库 | `EmbeddingConfig.java` + 改 pom 依赖 |
| 持久化 Memory | 自定义 `ChatMemoryStore` 实现 |
| 改 Agent 人设 | 各 Agent 接口的 `@SystemMessage` |
| 调整 RAG 检索数量 | `application.yml` 的 `novel.rag-top-k` |
| 新增"校对 Agent" | 新建接口 + 在 AgentConfig 加 Bean + Controller 加接口 |

---

## 6. 与传统 Spring MVC 对比

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
