package com.ink.speaker.novel.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 大纲详情 VO(含正文全文)。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutlineDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long novelId;
    private String title;
    private String theme;
    private Integer chapters;
    private String content;
    private Integer version;
    private boolean active;
    private LocalDateTime createdAt;
}
