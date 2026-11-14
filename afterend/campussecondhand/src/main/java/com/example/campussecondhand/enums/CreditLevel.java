package com.example.campussecondhand.enums;

/**
 * 信用等级。
 *
 * <p>只有 {@link #RESTRICTED} 带业务含义（不能下单、不能发布商品），
 * 其余三档纯粹用于展示。阈值与 {@code credit_score} 的 0~150 同口径。</p>
 */
public enum CreditLevel {
    // 顺序不能乱：of() 从高到低匹配，遇不到更高档就落在这里
    EXCELLENT(120, "信用优秀"),
    GOOD(80, "信用良好"),
    NORMAL(60, "信用一般"),
    RESTRICTED(Integer.MIN_VALUE, "信用受限");

    private final int minScore;
    private final String label;

    CreditLevel(int minScore, String label) {
        this.minScore = minScore;
        this.label = label;
    }

    public int getMinScore() {
        return minScore;
    }

    public String getLabel() {
        return label;
    }

    public static CreditLevel of(int score) {
        for (CreditLevel level : values()) {
            if (score >= level.minScore) {
                return level;
            }
        }
        return RESTRICTED;
    }
}
