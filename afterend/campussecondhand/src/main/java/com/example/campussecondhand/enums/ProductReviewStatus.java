package com.example.campussecondhand.enums;

/**
 * 商品人工审核状态。
 *
 * <p>审核是独立于 {@link ProductStatus} 的维度：{@code status} 管「上架/在售/已售出」，
 * 本枚举管「能不能出现在大厅」。两者组合关系：</p>
 * <ul>
 *   <li>新发布：{@code review_status=0} + {@code status=0}（下架），大厅不可见；</li>
 *   <li>审核通过：{@code review_status=1} + {@code status=1}（在售）；</li>
 *   <li>审核拒绝：{@code review_status=2} + {@code status=0}（下架），卖家可改后重提。</li>
 * </ul>
 *
 * <p>数据库 {@code products.review_status} 的取值必须与本枚举的 {@code code} 严格一致。</p>
 */
public enum ProductReviewStatus {

    /** 0：待审核，尚未进入审核流程的结果 */
    PENDING(0, "待审核"),

    /** 1：审核通过，可正常在售 */
    APPROVED(1, "已通过"),

    /** 2：审核拒绝，卖家可修改后重新提交 */
    REJECTED(2, "未通过");

    private final int code;
    private final String label;

    ProductReviewStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 按数据库值解析枚举；未知值返回 {@code null} 由调用方决定兜底策略 */
    public static ProductReviewStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ProductReviewStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /** 是否已通过审核。null 视为未通过（历史脏数据不应被当作可展示商品） */
    public static boolean isApproved(Integer code) {
        return fromCode(code) == APPROVED;
    }
}
