package ink.realm.ai.domain.agent;

/**
 * 知识库导入请求 DTO(/api/lore/import)。
 * <p>{@code text} 与 {@code dir} 二选一:优先 text。校验在 Service 层进行。</p>
 */
public record LoreImportRequest(
        String dir,
        String text) {
}
