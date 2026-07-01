package com.novel.agent.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 设定问答 Agent(RAG 检索增强生成)
 * <p>
 * RAG = Retrieval Augmented Generation,检索增强生成。
 * </p>
 * <p>
 * 为什么写作需要 RAG:
 *   一本长篇小说的设定文档可能多达几十万字(人物卡、世界观、势力关系、时间线)。
 *   全塞进 Prompt 不现实,LLM 也记不住。RAG 思路:
 *   把设定文档切片 -> 向量化存向量库 -> 作者提问时检索 top-K 相似片段 ->
 *   把片段塞进 Prompt -> LLM 基于片段作答。
 * </p>
 * <p>
 * 工作流程(本接口背后):
 *   1. 作者输入 "听潮阁有什么规矩?";
 *   2. EmbeddingStoreContentRetriever 把问题转向量,从 embeddingStore 检索 top-K 相似片段;
 *   3. 把检索到的片段拼到 SystemMessage 中(作为"上下文");
 *   4. LLM 基于上下文 + 用户问题生成回答;
 *   5. 如果上下文中没有相关信息,LLM 应回答"设定库中未找到"。
 * </p>
 * <p>
 * 注意:
 *   本接口的实现由 {@link com.novel.agent.config.AgentConfig#loreAgent} 手动构建,
 *   因为需要在构建时注入 ContentRetriever。
 * </p>
 */
public interface LoreAgent {

    /**
     * 设定问答
     *
     * @param sessionId 会话 ID
     * @param question  作者关于世界观/人物/剧情的提问
     * @return 基于设定库的回答
     */
    @SystemMessage("""
            你是小说设定库管家。请严格基于下面的"上下文信息"回答作者问题。
            规则:
            1. 如果上下文中有答案,请简洁准确地回答,并说明出处(如"根据《世界观设定·青州》");
            2. 如果上下文中没有相关信息,请回答"设定库中未找到相关信息",不要编造;
            3. 涉及矛盾设定时,指出冲突并请作者裁决;
            4. 回答使用中文。
            """)
    String ask(@MemoryId String sessionId, @UserMessage String question);
}
