package com.ink.speaker.novel.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前激活大纲 VO(用于续生时取上一版本尾段)。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutlineActiveVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private String content;
    private Integer chapters;
    private Integer version;
}
