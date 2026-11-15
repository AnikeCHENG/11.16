package com.example.campussecondhand.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 审核状态取值必须与数据库 products.review_status 一致 */
class ProductReviewStatusTest {

    @Test
    @DisplayName("枚举 code 与库值一一对应")
    void codes_match_database() {
        assertThat(ProductReviewStatus.PENDING.getCode()).isEqualTo(0);
        assertThat(ProductReviewStatus.APPROVED.getCode()).isEqualTo(1);
        assertThat(ProductReviewStatus.REJECTED.getCode()).isEqualTo(2);
    }

    @Test
    @DisplayName("fromCode：合法值解析，未知/空返回 null")
    void from_code() {
        assertThat(ProductReviewStatus.fromCode(0)).isEqualTo(ProductReviewStatus.PENDING);
        assertThat(ProductReviewStatus.fromCode(1)).isEqualTo(ProductReviewStatus.APPROVED);
        assertThat(ProductReviewStatus.fromCode(2)).isEqualTo(ProductReviewStatus.REJECTED);
        assertThat(ProductReviewStatus.fromCode(3)).isNull();
        assertThat(ProductReviewStatus.fromCode(null)).isNull();
    }

    @Test
    @DisplayName("只有 1 视为已通过；null 不能当作通过")
    void is_approved() {
        assertThat(ProductReviewStatus.isApproved(1)).isTrue();
        assertThat(ProductReviewStatus.isApproved(0)).isFalse();
        assertThat(ProductReviewStatus.isApproved(2)).isFalse();
        assertThat(ProductReviewStatus.isApproved(null)).isFalse();
    }
}
