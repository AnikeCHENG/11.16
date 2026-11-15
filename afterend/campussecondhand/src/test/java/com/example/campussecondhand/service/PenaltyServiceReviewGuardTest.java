package com.example.campussecondhand.service;

import com.example.campussecondhand.entity.Product;
import com.example.campussecondhand.enums.ProductReviewStatus;
import com.example.campussecondhand.enums.ProductStatus;
import com.example.campussecondhand.exception.BadRequestException;
import com.example.campussecondhand.repository.ProductRepository;
import com.example.campussecondhand.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 后台「上架」不能绕过人工审核。
 *
 * <p>审核接口与处罚/上架接口是两条独立入口：如果只给审核接口加门禁，
 * 管理员（或被盗管理员账号）仍可通过 {@code PUT /api/admin/products/{id}/status}
 * 把未审核商品直接上架，审核就形同虚设。</p>
 */
@ExtendWith(MockitoExtension.class)
class PenaltyServiceReviewGuardTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PenaltyService service;

    private Product product(ProductReviewStatus reviewStatus) {
        Product p = new Product();
        p.setId(1L);
        p.setReviewStatus(reviewStatus.getCode());
        p.setStatus(ProductStatus.OFF_SHELF.getCode());
        return p;
    }

    @Test
    @DisplayName("未通过审核的商品不能直接上架")
    void cannot_put_unreviewed_product_on_sale() {
        when(productRepository.selectById(1L)).thenReturn(product(ProductReviewStatus.PENDING));

        assertThatThrownBy(() -> service.updateProductStatus(
                1L, ProductStatus.ON_SALE.getCode(), "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("审核");
    }

    @Test
    @DisplayName("已通过审核的商品可正常上下架")
    void approved_product_can_toggle() {
        when(productRepository.selectById(1L)).thenReturn(product(ProductReviewStatus.APPROVED));

        Product updated = service.updateProductStatus(
                1L, ProductStatus.ON_SALE.getCode(), "admin");

        assertThat(updated.getStatus()).isEqualTo(ProductStatus.ON_SALE.getCode());
    }

    @Test
    @DisplayName("下架未审核商品是允许的（不涉及绕过审核）")
    void off_shelf_action_is_always_allowed() {
        when(productRepository.selectById(1L)).thenReturn(product(ProductReviewStatus.PENDING));

        Product updated = service.updateProductStatus(
                1L, ProductStatus.OFF_SHELF.getCode(), "admin");

        assertThat(updated.getStatus()).isEqualTo(ProductStatus.OFF_SHELF.getCode());
    }
}
