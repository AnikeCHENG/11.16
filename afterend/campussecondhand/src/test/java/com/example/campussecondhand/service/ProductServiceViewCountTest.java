package com.example.campussecondhand.service;

import com.example.campussecondhand.entity.Product;
import com.example.campussecondhand.repository.ProductRepository;
import com.example.campussecondhand.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品浏览量自增的空值防护（P2）。
 *
 * <p>{@code products.view_count} 是可空列（{@code INT DEFAULT 0}），
 * 历史数据或绕过实体默认值的写入都可能让它为 null。
 * 此前直接 {@code getViewCount() + 1} 会抛 NPE，商品详情页直接 500。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceViewCountTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("view_count 为 null：按 0 起算，自增后为 1")
    void null_view_count_is_treated_as_zero() {
        Product product = new Product();
        product.setId(5L);
        product.setViewCount(null);
        when(productRepository.selectById(5L)).thenReturn(product);

        Product result = productService.findByIdWithViewCount(5L);

        assertThat(result).isNotNull();
        assertThat(result.getViewCount()).isEqualTo(1);
        verify(productRepository).updateById(product);
    }

    @Test
    @DisplayName("view_count 已有值：正常加一")
    void existing_view_count_is_incremented() {
        Product product = new Product();
        product.setId(5L);
        product.setViewCount(41);
        when(productRepository.selectById(5L)).thenReturn(product);

        Product result = productService.findByIdWithViewCount(5L);

        assertThat(result.getViewCount()).isEqualTo(42);
        verify(productRepository).updateById(product);
    }

    @Test
    @DisplayName("商品不存在：返回 null，不做更新")
    void missing_product_returns_null() {
        when(productRepository.selectById(404L)).thenReturn(null);

        assertThat(productService.findByIdWithViewCount(404L)).isNull();
    }
}
