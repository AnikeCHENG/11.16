package com.example.campussecondhand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.campussecondhand.entity.Post;
import com.example.campussecondhand.entity.Product;
import com.example.campussecondhand.exception.BadRequestException;
import com.example.campussecondhand.repository.PostRepository;
import com.example.campussecondhand.repository.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 商品价格上界守卫。
 *
 * <p><b>这个洞是怎么漏出去的：</b>{@code products.price} 的列类型是 {@code decimal(10,2)}，
 * 上限 {@code 99999999.99}。但从输入到入库这一路上，<b>没有任何一层校验上界</b>——
 * 前端只判 {@code price <= 0}，大厅建商品的 {@code PostService#buildProduct} 只判
 * {@code > 0}，于是价格填大了就直接撞数据库：
 * {@code MysqlDataTruncation: Out of range value for column 'price'}，
 * 最终被全局异常处理器兜成一句毫无信息量的「服务器内部错误」。</p>
 *
 * <p>守卫放在 {@code ProductService} 而不是各个 Controller：全库只有它的一个方法调
 * {@code productRepository.insert}；能改动 price 的 {@code updateById} 也只有它的
 * {@code update()}。其余 5 处 updateById 都是「先查出来、只改一个字段再写回」，
 * price 原样回写、引入不了新值。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServicePriceRangeTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private PostRepository postRepository;

    private ProductService productService;
    private PostService postService;

    @BeforeEach
    void setUp() {
        // 这里刻意装配<b>真实</b>的 ProductService，而不是 mock：
        // 本测试要验证的正是「大厅发一条出售动态」这条真实链路上界被拦住，
        // 一旦把 ProductService 换成 mock，守卫就成了摆设，测试会假通过。
        productService = new ProductService();
        ReflectionTestUtils.setField(productService, "productRepository", productRepository);

        postService = new PostService();
        ReflectionTestUtils.setField(postService, "postRepository", postRepository);
        ReflectionTestUtils.setField(postService, "productService", productService);

        when(postRepository.insert(any(Post.class))).thenReturn(1);
    }

    private Product productWithPrice(String price) {
        Product p = new Product();
        p.setUserId(1L);
        p.setTitle("测试商品");
        p.setDescription("描述");
        p.setPrice(price == null ? null : new BigDecimal(price));
        return p;
    }

    /* ---------------- create ---------------- */

    @Test
    @DisplayName("价格恰好等于 decimal(10,2) 上限：放行（边界不能误伤）")
    void create_allows_exact_column_max() {
        assertThatCode(() -> productService.create(productWithPrice("99999999.99")))
                .doesNotThrowAnyException();
        verify(productRepository).insert(any(Product.class));
    }

    @Test
    @DisplayName("价格超上限：400，且不落库")
    void create_rejects_over_max() {
        assertThatThrownBy(() -> productService.create(productWithPrice("100000000")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("99999999.99");
        verify(productRepository, never()).insert(any(Product.class));
    }

    @Test
    @DisplayName("远超上限（1e18）：同样被拒，不能指望数据库报错兜底")
    void create_rejects_absurd_value() {
        assertThatThrownBy(() -> productService.create(productWithPrice("1000000000000000000")))
                .isInstanceOf(BadRequestException.class);
        verify(productRepository, never()).insert(any(Product.class));
    }

    @Test
    @DisplayName("常规价格、0 元（免费赠送）、未填价格：都放行")
    void create_allows_ordinary_zero_and_null() {
        assertThatCode(() -> productService.create(productWithPrice("9.90"))).doesNotThrowAnyException();
        assertThatCode(() -> productService.create(productWithPrice("0"))).doesNotThrowAnyException();
        assertThatCode(() -> productService.create(productWithPrice(null))).doesNotThrowAnyException();
        verify(productRepository, org.mockito.Mockito.times(3)).insert(any(Product.class));
    }

    @Test
    @DisplayName("负数价格：400，且不落库；0 元仍保留给广场免费赠送")
    void create_rejects_negative_price() {
        assertThatThrownBy(() -> productService.create(productWithPrice("-1")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不能为负数");
        verify(productRepository, never()).insert(any(Product.class));
    }

    /* ---------------- update ---------------- */

    @Test
    @DisplayName("改价超上限：400，且不写库")
    void update_rejects_over_max() {
        assertThatThrownBy(() -> productService.update(productWithPrice("123456789")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("99999999.99");
        verify(productRepository, never()).updateById(any(Product.class));
    }

    @Test
    @DisplayName("改价在上限内：放行")
    void update_allows_within_max() {
        assertThatCode(() -> productService.update(productWithPrice("99999999.98")))
                .doesNotThrowAnyException();
        verify(productRepository).updateById(any(Product.class));
    }

    @Test
    @DisplayName("改价为负数：400，且不写库")
    void update_rejects_negative_price() {
        assertThatThrownBy(() -> productService.update(productWithPrice("-0.01")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不能为负数");
        verify(productRepository, never()).updateById(any(Product.class));
    }

    /* ---------------- 常量与 schema 的一致性 ---------------- */

    @Test
    @DisplayName("MAX_PRICE 必须与 products.price 的 decimal(10,2) 对齐")
    void max_price_matches_column_definition() {
        // decimal(10,2) = 整数 8 位 + 小数 2 位 => 99999999.99
        assertThat(ProductService.MAX_PRICE).isEqualByComparingTo("99999999.99");
        assertThat(ProductService.MAX_PRICE.scale()).isEqualTo(2);
    }

    /* ---------------- 大厅出售动态：真实链路 ---------------- */

    @Test
    @DisplayName("大厅出售动态价格超上限：被拦住，动态与商品都不落库")
    void plaza_sell_post_with_over_max_price_is_rejected() {
        PostService.CreateCommand cmd = new PostService.CreateCommand(
                "SELL", "出一台闲置笔记本", null, null,
                "闲置笔记本", new BigDecimal("99999999999"), null, null);

        // 关键：走真实链路。PostService 只校验 price > 0（一亿满足），
        // 因此必须由 ProductService 的上界守卫接手拦住——这正是修复前会变成 500 的场景。
        assertThatThrownBy(() -> postService.create(1L, cmd))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("99999999.99");

        verify(productRepository, never()).insert(any(Product.class));
        verify(postRepository, never()).insert(any(Post.class));
    }

    @Test
    @DisplayName("大厅出售动态价格在上限内：正常建商品并建动态")
    void plaza_sell_post_within_max_price_succeeds() {
        PostService.CreateCommand cmd = new PostService.CreateCommand(
                "SELL", "出一台闲置笔记本", null, null,
                "闲置笔记本", new BigDecimal("1999.00"), null, null);

        assertThatCode(() -> postService.create(1L, cmd)).doesNotThrowAnyException();
        verify(productRepository).insert(any(Product.class));
        verify(postRepository).insert(any(Post.class));
    }
}