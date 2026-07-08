package com.ink.speaker.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ink.speaker.novel.domain.entity.Novel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 小说主表 DAO。
 * <p>对应表 novel,SQL 见 resources/mapper/NovelDao.xml。</p>
 *
 * <p>第 5 阶段:增加 {@code ownerId} 行级过滤方法,确保每本小说只对作者可见。</p>
 */
@Mapper
public interface NovelMapper extends BaseMapper<Novel> {

    /**
     * 按标题精确查找。
     *
     * @param title 小说标题
     * @return 小说实体(可能为空)
     */
    Novel findByTitle(@Param("title") String title);

    /**
     * 列出指定用户拥有的全部小说(R5 用户隔离)。
     *
     * @param ownerId 用户 ID
     * @return 该用户的小说列表
     */
    List<Novel> listByOwnerId(@Param("ownerId") Long ownerId);

    /**
     * 按 id 查询并校验所有权(R5 用户隔离)。
     *
     * <p>SQL 中带 {@code WHERE id = ? AND owner_id = ?} 双重过滤,
     * 若小说不存在或不属于该用户,均返回 null,调用方按 404/403 处理。</p>
     *
     * @param id      小说 ID
     * @param ownerId 当前用户 ID
     * @return 小说实体(不属于该用户时返回 null)
     */
    Novel findByIdAndOwner(@Param("id") Long id, @Param("ownerId") Long ownerId);

    /**
     * 列出所有公开到公共参考池的小说(R5 跨小说参考)。
     *
     * <p>仅返回 {@code shared_for_reference=true} 的小说,且不包含 ownerId 等敏感字段
     * (由 Service 层在转 VO 时脱敏)。</p>
     *
     * @return 公开小说列表
     */
    List<Novel> listSharedForReference();
}
