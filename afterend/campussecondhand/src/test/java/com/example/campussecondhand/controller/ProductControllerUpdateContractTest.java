package com.example.campussecondhand.controller;

import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.entity.Order;
import com.example.campussecondhand.entity.Product;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.enums.ProductStatus;
import com.example.campussecondhand.repository.OrderRepository;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.BrowseHistoryService;
import com.example.campussecondhand.service.ProductService;
import com.example.campussecondhand.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code PUT /api/products/update/{id}} 的鉴权、归属与价格锁（P0 回归防线）。
 *
 * <p><b>漏洞是怎么发生的：</b>本端点此前既不读 {@code Authorization}、
 * 也不校验商品归属，而 {@code SecurityConfig} 是 {@code .anyRequest().permitAll()}，
 * 唯一的拦截器只覆盖 {@code /api/admin/**}。结果：<b>未登录的任何人</b>都能改掉
 * 任意在售商品的标题、价格、描述和图片——把 12999 元的相机改成 1 元只需要一条
 * curl，不需要注册、不需要时序。它是全项目利用率最高的一个洞。</p>
 *
 * <p>本测试把修复后的四条约束钉死：未登录 401、非本人 403、
 * 已成交/有进行中订单时禁止改价、异常详情不外泄。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductControllerUpdateContractTest {

    private static final String AUTH = "Bearer test-token";
    private static final Long PRODUCT_ID = 10L;
    private static final Long OWNER_ID = 2L;
    private static final Long STRANGER_ID = 9L;

    @Mock
    private ProductService productService;
    @Mock
    private BrowseHistoryService browseHistoryService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private JwtUtil jwtUtil;
    @InjectMocks
    private ProductController controller;

    private void tokenResolvesTo(Long userId) {
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn("user" + userId);
        User u = new User();
        u.setId(userId);
        u.setUsername("user" + userId);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(u));
    }

    private Product product(Integer status, String price) {
        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setUserId(OWNER_ID);
        p.setTitle("单反相机");
        p.setPrice(new BigDecimal(price));
        p.setStatus(status);
        return p;
    }

    private static int codeOf(ResponseEntity<ApiResponse<?>> resp) {
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody().getCode();
    }

    private String messageOf(ResponseEntity<ApiResponse<?>> resp) {
        return resp.getBody().getMessage();
    }

    @Test
    @DisplayName("未登录：401，且绝不动数据库")
    void update_without_token_is_unauthorized() {
        int code = codeOf(controller.update(PRODUCT_ID, null, Map.<String, Object>of("title", "改个名")));

        assertThat(code).isEqualTo(401);
        verify(productService, never()).update(any(Product.class));
    }

    @Test
    @DisplayName("无效 token：401")
    void update_with_bad_token_is_unauthorized() {
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn(null);

        assertThat(codeOf(controller.update(PRODUCT_ID, AUTH, Map.<String, Object>of("title", "改个名"))))
                .isEqualTo(401);
    }

    @Test
    @DisplayName("不是自己的商品：403，且不改价")
    void update_rejects_non_owner() {
        tokenResolvesTo(STRANGER_ID);
        when(productService.findById(PRODUCT_ID)).thenReturn(product(ProductStatus.ON_SALE.getCode(), "12999.00"));

        var resp = controller.update(PRODUCT_ID, AUTH, Map.<String, Object>of("price", "1"));

        assertThat(codeOf(resp)).isEqualTo(403);
        verify(productService, never()).update(any(Product.class));
    }

    @Test
    @DisplayName("商品不存在：404")
    void update_returns_404_when_product_missing() {
        tokenResolvesTo(OWNER_ID);
        when(productService.findById(PRODUCT_ID)).thenReturn(null);

        assertThat(codeOf(controller.update(PRODUCT_ID, AUTH, Map.<String, Object>of("title", "x")))).isEqualTo(404);
    }

    @Test
    @DisplayName("本人改标题：放行")
    void update_allows_owner_to_edit_title() {
        tokenResolvesTo(OWNER_ID);
        Product product = product(ProductStatus.ON_SALE.getCode(), "12999.00");
        when(productService.findById(PRODUCT_ID)).thenReturn(product);
        when(productService.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = controller.update(PRODUCT_ID, AUTH, Map.<String, Object>of("title", "九成新单反"));

        assertThat(codeOf(resp)).isEqualTo(200);
        assertThat(product.getTitle()).isEqualTo("九成新单反");
        verify(productService).update(product);
    }

    @Test
    @DisplayName("已售出的商品：禁止改价（价格已是订单凭证）")
    void update_rejects_price_change_on_sold_product() {
        tokenResolvesTo(OWNER_ID);
        when(productService.findById(PRODUCT_ID))
                .thenReturn(product(ProductStatus.SOLD.getCode(), "12999.00"));

        var resp = controller.update(PRODUCT_ID, AUTH, Map.<String, Object>of("price", "1.00"));

        assertThat(codeOf(resp)).isEqualTo(403);
        assertThat(messageOf(resp)).contains("不能修改价格");
        verify(productService, never()).update(any(Product.class));
    }

    @Test
    @DisplayName("存在进行中订单的商品：禁止改价")
    void update_rejects_price_change_with_active_order() {
        tokenResolvesTo(OWNER_ID);
        when(productService.findById(PRODUCT_ID))
                .thenReturn(product(ProductStatus.ON_SALE.getCode(), "12999.00"));
        when(orderRepository.selectCount(any())).thenReturn(1L);

        var resp = controller.update(PRODUCT_ID, AUTH, Map.<String, Object>of("price", "1.00"));

        assertThat(codeOf(resp)).isEqualTo(403);
        verify(productService, never()).update(any(Product.class));
    }

    @Test
    @DisplayName("已售出商品提交原价（未改动价格）：仍可改标题描述")
    void update_allows_other_fields_when_price_unchanged() {
        tokenResolvesTo(OWNER_ID);
        Product product = product(ProductStatus.SOLD.getCode(), "12999.00");
        when(productService.findById(PRODUCT_ID)).thenReturn(product);
        when(productService.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = controller.update(PRODUCT_ID, AUTH,
                Map.<String, Object>of("price", "12999.00", "description", "已售出，留个记录"));

        assertThat(codeOf(resp)).isEqualTo(200);
        assertThat(product.getDescription()).isEqualTo("已售出，留个记录");
    }

    @Test
    @DisplayName("价格格式非法：400，而不是把 NumberFormatException 当 500 抛出")
    void update_rejects_malformed_price() {
        tokenResolvesTo(OWNER_ID);
        when(productService.findById(PRODUCT_ID))
                .thenReturn(product(ProductStatus.ON_SALE.getCode(), "12999.00"));

        var resp = controller.update(PRODUCT_ID, AUTH, Map.<String, Object>of("price", "免费"));

        assertThat(codeOf(resp)).isEqualTo(400);
        verify(productService, never()).update(any(Product.class));
    }

    @Test
    @DisplayName("价格必须为正：0 或负数被拒")
    void update_rejects_non_positive_price() {
        tokenResolvesTo(OWNER_ID);
        when(productService.findById(PRODUCT_ID))
                .thenReturn(product(ProductStatus.ON_SALE.getCode(), "12999.00"));

        assertThat(codeOf(controller.update(PRODUCT_ID, AUTH, Map.<String, Object>of("price", "0")))).isEqualTo(400);
        assertThat(codeOf(controller.update(PRODUCT_ID, AUTH, Map.<String, Object>of("price", "-5")))).isEqualTo(400);
    }

    @Test
    @DisplayName("异常详情只进日志：响应体不得回显底层异常消息")
    void update_does_not_leak_exception_details() {
        tokenResolvesTo(OWNER_ID);
        when(productService.findById(PRODUCT_ID))
                .thenReturn(product(ProductStatus.ON_SALE.getCode(), "12999.00"));
        when(productService.update(any(Product.class)))
                .thenThrow(new RuntimeException("Duplicate entry '1' for key 'products.uk_order_no'"));

        var resp = controller.update(PRODUCT_ID, AUTH, Map.<String, Object>of("title", "x"));

        assertThat(codeOf(resp)).isEqualTo(500);
        assertThat(messageOf(resp))
                .doesNotContain("Duplicate")
                .doesNotContain("products")
                .doesNotContain("uk_order_no");
    }
}
