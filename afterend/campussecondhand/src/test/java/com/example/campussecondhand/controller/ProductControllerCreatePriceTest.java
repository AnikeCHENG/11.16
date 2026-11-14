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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
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
 * 发布商品的价格下界（P2）。
 *
 * <p>此前 {@code POST /api/products/create} 只校验「价格能解析成数字」，
 * 0 元与负数照单全收。0 元商品在订单与退款链路里没有明确语义，
 * 负数更会让金额计算出现倒贴。现在出售入口要求价格 &gt; 0；
 * 广场「免费赠送」仍走 {@code PostService}，其 0 元语义不受影响。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductControllerCreatePriceTest {

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

    private Map<String, Object> params(String price) {
        Map<String, Object> params = new HashMap<>();
        params.put("title", "闲置台灯");
        params.put("description", "自用一年");
        params.put("price", price);
        params.put("conditionLevel", 2);
        params.put("category", "living");
        return params;
    }

    private static int codeOf(ResponseEntity<ApiResponse<?>> resp) {
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody().getCode();
    }

    @Test
    @DisplayName("0 元：400，提示免费赠送走广场动态")
    void create_rejects_zero_price() {
        tokenResolvesTo(1L);

        var resp = controller.create(AUTH, params("0"));

        assertThat(codeOf(resp)).isEqualTo(400);
        assertThat(resp.getBody().getMessage()).contains("免费赠送");
        verify(productService, never()).create(any(Product.class));
    }

    @Test
    @DisplayName("负数：400，且不落库")
    void create_rejects_negative_price() {
        tokenResolvesTo(1L);

        var resp = controller.create(AUTH, params("-1"));

        assertThat(codeOf(resp)).isEqualTo(400);
        verify(productService, never()).create(any(Product.class));
    }

    @Test
    @DisplayName("正数：正常下发 Service，价格按输入入库")
    void create_allows_positive_price() {
        tokenResolvesTo(1L);
        when(productService.create(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = controller.create(AUTH, params("19.90"));

        assertThat(codeOf(resp)).isEqualTo(200);
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productService).create(captor.capture());
        assertThat(captor.getValue().getPrice()).isEqualByComparingTo(new BigDecimal("19.90"));
    }
}
