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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 删除商品时的「进行中订单」守卫（P2）。
 *
 * <p>此前 {@code DELETE /api/products/delete/{id}} 只校验归属就物理删除。
 * 商品一旦被删，卖家自己挂出的商品列表、买家侧的订单详情就会指向一个
 * 不存在的商品；进行中的订单（待支付 / 待发货 / 待收货）更是完全无法履约。
 * 现在有进行中订单时直接拒绝删除，已完成 / 已取消 / 已退款的历史订单不阻塞。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductControllerDeleteGuardTest {

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

    private void loginAsOwner(long userId) {
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn("user" + userId);
        User u = new User();
        u.setId(userId);
        u.setUsername("user" + userId);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(u));
    }

    private Product ownedProduct(long userId) {
        Product p = new Product();
        p.setId(5L);
        p.setUserId(userId);
        p.setTitle("闲置台灯");
        return p;
    }

    private static int codeOf(ResponseEntity<ApiResponse<?>> resp) {
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody().getCode();
    }

    @Test
    @DisplayName("有进行中订单：400 拒绝删除，商品保留")
    void delete_rejected_when_active_orders_exist() {
        loginAsOwner(1L);
        when(productService.findById(5L)).thenReturn(ownedProduct(1L));
        when(orderRepository.selectCount(any())).thenReturn(1L);

        var resp = controller.delete(5L, AUTH);

        assertThat(codeOf(resp)).isEqualTo(400);
        assertThat(resp.getBody().getMessage()).contains("进行中的订单");
        verify(productService, never()).delete(anyLong());
    }

    @Test
    @DisplayName("没有进行中订单：正常删除")
    void delete_allowed_when_no_active_orders() {
        loginAsOwner(1L);
        when(productService.findById(5L)).thenReturn(ownedProduct(1L));
        when(orderRepository.selectCount(any())).thenReturn(0L);
        when(productService.delete(5L)).thenReturn(1);

        var resp = controller.delete(5L, AUTH);

        assertThat(codeOf(resp)).isEqualTo(200);
        verify(productService).delete(5L);
    }

    @Test
    @DisplayName("不是自己的商品：403，且不查询订单")
    void delete_rejected_when_not_owner() {
        loginAsOwner(1L);
        when(productService.findById(5L)).thenReturn(ownedProduct(2L));

        var resp = controller.delete(5L, AUTH);

        assertThat(codeOf(resp)).isEqualTo(403);
        verify(productService, never()).delete(anyLong());
    }
}
