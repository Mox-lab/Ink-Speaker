# Novel Forge 项目

> 基于 **Spring Boot 3 + LangChain4j** 的 AI 小说生成 Agent 入门示例
> 面向 Java 开发者,带你 30 分钟搞懂 Agent 开发的核心概念与落地方式。

---

## 这是什么?

一个最小可运行的 AI 写作 Agent 项目,涵盖写作 Agent 开发的 4 个核心能力:

| 能力 | 说明 | 接口 |
|------|------|------|
| **对话** | 单轮/流式对话 | `/api/chat`、`/api/chat/stream` |
| **工具调用** | Agent 主动调用 Java 方法(查人物/查设定/扩写场景) | `/api/writing` |
| **RAG** | 设定库问答(检索增强生成) | `/api/lore` |
| **结构化输出** | 让 LLM 输出 Java 对象(人物卡) | `/api/character` |

外加**大纲生成**(`/api/outline`)、**章节生成**(`/api/chapter`)与**多轮记忆**(`/api/memory`)能力演示。

---

## 快速上手

### 1. 环境要求

- JDK 21+
- Maven 3.8+
- (可选)Ollama,如果要用本地大模型

### 2. 配置 API Key

修改 `src/main/resources/application.yml`:

```yaml
langchain4j:
  open-ai:
    chat-model:
      api-key: sk-your-deepseek-key   # 替换成你的 DeepSeek key
      base-url: https://api.deepseek.com/v1
      model-name: deepseek-chat
```

> 也可设置环境变量 `OPENAI_API_KEY`,代码会自动读取。

### 3. 启动

```bash
cd novel-forge
mvn spring-boot:run
```

看到下面的输出就成功了:

```
Novel Forge 启动成功!
```

### 4. 体验

打开新终端,调用接口:

```bash
# 普通对话
curl -X POST http://localhost:9688/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"你好,介绍一下你自己"}'

# 写作助手(带工具调用)
curl -X POST http://localhost:9688/api/writing \
  -H "Content-Type: application/json" \
  -d '{"message":"帮我查一下林晚的人物档案"}'

# 大纲生成
curl -X POST http://localhost:9688/api/outline \
  -H "Content-Type: application/json" \
  -d '{"theme":"东方玄幻+女主复仇+江湖权谋","chapters":20}'

# 章节生成
curl -X POST http://localhost:9688/api/chapter \
  -H "Content-Type: application/json" \
  -d '{"outline":"雨夜,林晚在云陵城码头捡到受伤的苏砚","wordCount":1500}'

# 人物抽取
curl -X POST http://localhost:9688/api/character \
  -H "Content-Type: application/json" \
  -d '{"text":"林晚,24岁,云陵城码头脚夫,外冷内热,左手腕有旧疤"}'

# 设定问答(RAG)
curl -X POST http://localhost:9688/api/lore \
  -H "Content-Type: application/json" \
  -d '{"question":"听潮阁有什么规矩?"}'

# 记忆测试
curl http://localhost:9688/api/memory
```

更多示例见 **[详细文档](docs/GUIDE.md)**。

---

## 文档导航

- **[详细开发指南](docs/GUIDE.md)** ⭐ 必读 - 从零讲清 Agent 是什么、怎么开发
- **[架构说明](docs/ARCHITECTURE.md)** - 项目结构、模块职责
- **[FAQ](docs/FAQ.md)** - 常见问题与排错

---

## 技术栈

| 组件 | 版本 | 作用 |
|------|------|------|
| Spring Boot | 3.3.4 | Web 框架与 IoC 容器 |
| LangChain4j | 1.0.0-beta3 | Agent 开发框架 |
| Java | 21 | 编程语言 |
| InMemoryEmbeddingStore | - | 内存向量库(演示用) |

---

## 下一步

读完 [GUIDE.md](docs/GUIDE.md) 后,建议按以下顺序练手:

1. 在 `NovelTools` 中新增一个工具(如"查询武功招式");
2. 把 InMemoryEmbeddingStore 替换为 Redis 向量库;
3. 实现一个"小说总编 Agent",根据用户题材路由给大纲/章节/设定子 Agent;
4. 引入 Prompt Template 管理,把系统提示词外置到文件。
