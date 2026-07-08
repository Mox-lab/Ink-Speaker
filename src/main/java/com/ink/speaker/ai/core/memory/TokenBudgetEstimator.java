package com.ink.speaker.ai.core.memory;

import org.springframework.stereotype.Service;

/**
 * Token 预算估算器。
 * <p>对标 cc-haha {@code src/services/tokenEstimation.ts}。
 * 由于本项目接入的多为中文模型,采用保守估算:1 中文字符 ≈ 1.5 token,1 英文字符 ≈ 0.25 token。</p>
 */
@Service
public class TokenBudgetEstimator {

    /**
     * 估算文本的 token 数。
     *
     * @param text 待估算文本(null 时返回 0)
     * @return 估算 token 数
     */
    public int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chinese = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                chinese++;
            } else {
                other++;
            }
        }
        return (int) Math.ceil(chinese * 1.5 + other * 0.25);
    }

    /**
     * 判断是否超出预算。
     */
    public boolean exceeds(String text, int budget) {
        return estimate(text) > budget;
    }
}
