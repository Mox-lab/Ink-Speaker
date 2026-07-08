package com.ink.speaker.novel.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 保存操作结果 VO(返回主键)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SaveResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
}
