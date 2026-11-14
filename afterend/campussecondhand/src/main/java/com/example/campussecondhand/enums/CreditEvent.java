package com.example.campussecondhand.enums;

/**
 * 信用分事件类型。
 *
 * <p>每个事件自带一个固定的 {@link #delta}：分值的定义收敛在这一处，
 * 调用方只声明「发生了什么」，不自己算分，避免同一个事件在多个
 * Service 里出现不同的加减值。</p>
 *
 * <p>{@code bizId} 的取值口径见 credit_log 表注释：订单类事件用订单ID、
 * 举报类用举报ID、认证用用户ID。它与信用流水表上的
 * {@code uk_user_event_biz} 唯一键一起构成幂等键。</p>
 */
public enum CreditEvent {
    STUDENT_VERIFIED(10, "完成学生认证"),
    ORDER_COMPLETED(2, "完成一笔交易"),
    REVIEW_GOOD(1, "收到好评"),
    REVIEW_BAD(-3, "收到差评"),
    ORDER_TIMEOUT(-5, "下单后超时未支付"),
    REPORT_UPHELD(-15, "被举报且处理成立");

    private final int delta;
    private final String label;

    CreditEvent(int delta, String label) {
        this.delta = delta;
        this.label = label;
    }

    public int getDelta() {
        return delta;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 流水里存的是枚举名，展示时换成中文；未知值原样返回。
     *
     * <p>不抛异常：历史数据里若出现已删除的事件类型，个人中心应当
     * 照常渲染其余记录，而不是整页 500。</p>
     */
    public static String labelOf(String name) {
        for (CreditEvent e : values()) {
            if (e.name().equals(name)) {
                return e.label;
            }
        }
        return name;
    }
}
