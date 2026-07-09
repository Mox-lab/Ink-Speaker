package com.ink.speaker.novel.domain.vo;

/**
 * 小说导出载荷。
 * <p>承载导出文件的字节数据、文件名与 MIME 类型,Controller 据此构造下载响应。</p>
 *
 * <p>第 6 阶段 BASE-10:支持将小说基础信息 + 大纲 + 人物 + 设定 + 全部章节
 * 聚合后以 md / txt / json 三种格式导出。</p>
 *
 * @param filename    下载文件名(已做安全处理,移除路径分隔符等危险字符)
 * @param contentType MIME 类型(已含 charset=UTF-8)
 * @param content     文件字节内容(UTF-8 编码)
 */
public record NovelExportPayload(
        String filename,
        String contentType,
        byte[] content
) {
}
