package com.example.campussecondhand.enums;

/**
 * 学生认证申请（支付宝学生认证截图辅助认证）的审核状态。
 *
 * <p>与举报的数字状态码不同，这里直接用字符串作数据库值与对外契约：
 * 四个状态需要原样暴露给用户端与后台，字符串可以和需求文档逐字对照，
 * 避免前后端各自维护一套 0/1/2/3 → 文案的映射表。</p>
 */
public enum StudentVerificationStatus {

    PENDING("pending", "待审核"),
    APPROVED("approved", "审核通过"),
    REJECTED("rejected", "审核拒绝"),
    EXPIRED("expired", "已过期");

    private final String code;
    private final String label;

    StudentVerificationStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static StudentVerificationStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (StudentVerificationStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
