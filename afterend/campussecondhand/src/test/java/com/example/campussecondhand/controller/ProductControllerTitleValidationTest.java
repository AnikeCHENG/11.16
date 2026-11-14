package com.example.campussecondhand.controller;

import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.entity.Product;
import com.example.campussecondhand.entity.User;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品标题的长度与空值校验（P2）。
 *
 * <p>{@code products.title} 是 {@code VARCHAR(100) NOT NULL}，此前 Controller
 * 直接把请求里的 title 塞进实体，既不拦空白，也不拦超长：
 * 超长会在写库时被数据库拒绝，用户只能看到一句没有线索的「发布失败」。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductControllerTitleValidationTest {

    private static final String AUTH = "Bearer test-token";

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

    private void tokenResolvesTo(long userId) {
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn("user" + userId);
        User u = new User();
        u.setId(userId);
        u.setUsername("user" + userId);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(u));
    }

    private Map<String, Object> params(String title) {
        Map<String, Object> params = new HashMap<>();
        params.put("title", title);
        params.put("description", "自用一年");
        params.put("price", "19.90");
        params.put("conditionLevel", 2);
        params.put("category", "living");
        return params;
    }

    /** 编辑场景只改标题，不带价格：避免和「价格锁定」那条规则互相干扰 */
    private Map<String, Object> updateParams(String title) {
        Map<String, Object> params = new HashMap<>();
        params.put("title", title);
        return params;
    }

    private static int codeOf(ResponseEntity<ApiResponse<?>> resp) {
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody().getCode();
    }

    @Test
    @DisplayName("发布：标题超 100 字返回 400，且不落库")
    void create_rejects_too_long_title() {
        tokenResolvesTo(1L);

        var resp = controller.create(AUTH, params("标".repeat(101)));

        assertThat(codeOf(resp)).isEqualTo(400);
        assertThat(resp.getBody().getMessage()).contains("100");
        verify(productService, never()).create(any(Product.class));
    }

    @Test
    @DisplayName("发布：标题为空白返回 400")
    void create_rejects_blank_title() {
        tokenResolvesTo(1L);

        var resp = controller.create(AUTH, params("   "));

        assertThat(codeOf(resp)).isEqualTo(400);
        verify(productService, never()).create(any(Product.class));
    }

    @Test
    @DisplayName("发布：标题缺失返回 400")
    void create_rejects_missing_title() {
        tokenResolvesTo(1L);
        Map<String, Object> p = params("闲置台灯");
        p.remove("title");

        var resp = controller.create(AUTH, p);

        assertThat(codeOf(resp)).isEqualTo(400);
        verify(productService, never()).create(any(Product.class));
    }

    @Test
    @DisplayName("发布：合法标题正常入库，并去掉首尾空格")
    void create_accepts_valid_title() {
        tokenResolvesTo(1L);
        when(productService.create(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = controller.create(AUTH, params("  闲置台灯  "));

        assertThat(codeOf(resp)).isEqualTo(200);
        var captor = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(productService).create(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("闲置台灯");
    }

    @Test
    @DisplayName("编辑：标题超 100 字返回 400，且不落库")
    void update_rejects_too_long_title() {
        tokenResolvesTo(1L);
        Product product = new Product();
        product.setId(5L);
        product.setUserId(1L);
        product.setStatus(1);
        product.setTitle("旧标题");
        when(productService.findById(5L)).thenReturn(product);

        var resp = controller.update(5L, AUTH, updateParams("标".repeat(101)));

        assertThat(codeOf(resp)).isEqualTo(400);
        verify(productService, never()).update(any(Product.class));
    }

    @Test
    @DisplayName("编辑：标题为空白返回 400")
    void update_rejects_blank_title() {
        tokenResolvesTo(1L);
        Product product = new Product();
        product.setId(5L);
        product.setUserId(1L);
        product.setStatus(1);
        product.setTitle("旧标题");
        when(productService.findById(5L)).thenReturn(product);

        var resp = controller.update(5L, AUTH, updateParams("  "));

        assertThat(codeOf(resp)).isEqualTo(400);
        verify(productService, never()).update(any(Product.class));
    }
}
