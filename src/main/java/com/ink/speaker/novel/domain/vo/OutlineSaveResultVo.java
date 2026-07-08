package com.ink.speaker.novel.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 大纲保存结果 VO。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OutlineSaveResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Integer version;
}
