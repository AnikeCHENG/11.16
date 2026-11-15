package com.example.campussecondhand.service;

import com.example.campussecondhand.entity.Product;
import com.example.campussecondhand.enums.ProductReviewStatus;
import com.example.campussecondhand.enums.ProductStatus;
import com.example.campussecondhand.exception.BadRequestException;
import com.example.campussecondhand.exception.ForbiddenException;
import com.example.campussecondhand.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品人工审核的状态机。
 *
 * <p>审核必须同时改写 review_status 与 status：只改一个会出现
 * 「已通过但没上架」或「已拒绝却还在售」。拒绝必须带理由，卖家只能重提自己的、
 * 且当前确实未通过的商品。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductReviewServiceTest {

    private static final Long PRODUCT_ID = 10L;
    private static final Long SELLER_ID = 7L;
    private static final Long ADMIN_ID = 1L;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductReviewService service;

    private Product product(ProductReviewStatus reviewStatus, Long ownerId) {
        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setUserId(ownerId);
        p.setTitle("闲置相机");
        p.setReviewStatus(reviewStatus.getCode());
        // 待审核/未通过都处于下架
        p.setStatus(reviewStatus == ProductReviewStatus.APPROVED
                ? ProductStatus.ON_SALE.getCode()
                : ProductStatus.OFF_SHELF.getCode());
        return p;
    }

    @Test
    @DisplayName("审核通过：review_status=已通过 且 status=在售，并记录审核人/时间")
    void approve_turns_review_on_and_puts_on_sale() {
        when(productRepository.selectById(PRODUCT_ID))
                .thenReturn(product(ProductReviewStatus.PENDING, SELLER_ID));

        Product result = service.review(PRODUCT_ID, "approve", null, ADMIN_ID);

        assertThat(result.getReviewStatus()).isEqualTo(ProductReviewStatus.APPROVED.getCode());
        assertThat(result.getStatus()).isEqualTo(ProductStatus.ON_SALE.getCode());
        assertThat(result.getReviewedBy()).isEqualTo(ADMIN_ID);
        assertThat(result.getReviewedTime()).isNotNull();
        assertThat(result.getReviewReason()).isNull();
        verify(productRepository).updateById(result);
    }

    @Test
    @DisplayName("拒绝必须填写理由；理由为空/空白直接 400，商品状态不变")
    void reject_requires_reason() {
        when(productRepository.selectById(PRODUCT_ID))
                .thenReturn(product(ProductReviewStatus.PENDING, SELLER_ID));

        assertThatThrownBy(() -> service.review(PRODUCT_ID, "reject", "  ", ADMIN_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("拒绝理由");

        verify(productRepository, never()).updateById(any(Product.class));
    }

    @Test
    @DisplayName("拒绝：status 保持下架，理由落库，卖家可见")
    void reject_keeps_off_shelf_and_stores_reason() {
        when(productRepository.selectById(PRODUCT_ID))
                .thenReturn(product(ProductReviewStatus.PENDING, SELLER_ID));

        Product result = service.review(PRODUCT_ID, "reject", "截图与描述不符", ADMIN_ID);

        assertThat(result.getReviewStatus()).isEqualTo(ProductReviewStatus.REJECTED.getCode());
        assertThat(result.getStatus()).isEqualTo(ProductStatus.OFF_SHELF.getCode());
        assertThat(result.getReviewReason()).isEqualTo("截图与描述不符");
    }

    @Test
    @DisplayName("已通过的商品不能重复审核")
    void cannot_review_approved_product() {
        when(productRepository.selectById(PRODUCT_ID))
                .thenReturn(product(ProductReviewStatus.APPROVED, SELLER_ID));

        assertThatThrownBy(() -> service.review(PRODUCT_ID, "approve", null, ADMIN_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("无需重复审核");
    }

    @Test
    @DisplayName("非法审核动作被拒")
    void rejects_unknown_action() {
        when(productRepository.selectById(PRODUCT_ID))
                .thenReturn(product(ProductReviewStatus.PENDING, SELLER_ID));

        assertThatThrownBy(() -> service.review(PRODUCT_ID, "delete", null, ADMIN_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("审核动作不合法");
    }

    @Test
    @DisplayName("卖家重新提交：未通过 → 待审核，清空理由与审核留痕，保持下架")
    void seller_resubmits_rejected_product() {
        Product rejected = product(ProductReviewStatus.REJECTED, SELLER_ID);
        rejected.setReviewReason("图片模糊");
        rejected.setReviewedBy(ADMIN_ID);
        when(productRepository.selectById(PRODUCT_ID)).thenReturn(rejected);

        Product result = service.resubmit(PRODUCT_ID, SELLER_ID);

        assertThat(result.getReviewStatus()).isEqualTo(ProductReviewStatus.PENDING.getCode());
        assertThat(result.getStatus()).isEqualTo(ProductStatus.OFF_SHELF.getCode());
        assertThat(result.getReviewReason()).isNull();
        assertThat(result.getReviewedBy()).isNull();
        assertThat(result.getReviewedTime()).isNull();
    }

    @Test
    @DisplayName("不能重提别人的商品；只有未通过的商品可重提")
    void resubmit_guards_ownership_and_state() {
        when(productRepository.selectById(PRODUCT_ID))
                .thenReturn(product(ProductReviewStatus.REJECTED, SELLER_ID));

        assertThatThrownBy(() -> service.resubmit(PRODUCT_ID, 999L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权");

        when(productRepository.selectById(PRODUCT_ID))
                .thenReturn(product(ProductReviewStatus.APPROVED, SELLER_ID));
        assertThatThrownBy(() -> service.resubmit(PRODUCT_ID, SELLER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("只有审核未通过");
    }

    @Test
    @DisplayName("批量通过：只处理待审核/未通过，其它跳过并计数")
    void batch_approve_counts_skipped() {
        Product pending = product(ProductReviewStatus.PENDING, SELLER_ID);
        pending.setId(1L);
        Product rejected = product(ProductReviewStatus.REJECTED, SELLER_ID);
        rejected.setId(2L);
        Product approved = product(ProductReviewStatus.APPROVED, SELLER_ID);
        approved.setId(3L);

        when(productRepository.selectById(1L)).thenReturn(pending);
        when(productRepository.selectById(2L)).thenReturn(rejected);
        when(productRepository.selectById(3L)).thenReturn(approved);
        when(productRepository.selectById(4L)).thenReturn(null);

        Map<String, Integer> result = service.batchApprove(
                Arrays.asList(1L, 2L, 3L, 4L), ADMIN_ID);

        assertThat(result.get("approvedCount")).isEqualTo(2);
        assertThat(result.get("skippedCount")).isEqualTo(2);
        verify(productRepository, times(2)).updateById(any(Product.class));
    }
}
