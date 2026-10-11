package com.example.campussecondhand.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.entity.Order;
import com.example.campussecondhand.entity.Product;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.enums.OrderStatus;
import com.example.campussecondhand.repository.CategoryRepository;
import com.example.campussecondhand.repository.MessageRepository;
import com.example.campussecondhand.repository.OrderRepository;
import com.example.campussecondhand.repository.ProductRepository;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.AdminStatsService;
import com.example.campussecondhand.service.PenaltyService;
import com.example.campussecondhand.service.ReportService;
import com.example.campussecondhand.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端的"危险操作"护栏（第二轮扫描 N1 / N3 / N4 / N5 / N9）。
 *
 * <p>管理端此前有一个共同问题：<b>能做的操作比该做的多</b>——可以封掉自己、
 * 可以物理删除进行中的订单、可以把任意整数写进 orders.status、
 * 可以对没有上限的 id 数组做批量写。这些都不会被拦截器挡住，因为拦截器只管
 * "你是不是管理员"，不管"这个操作合不合理"。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminControllerGuardTest {

    private static final String AUTH = "Bearer admin-token";
    private static final Long ADMIN_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PenaltyService penaltyService;
    @Mock
    private ReportService reportService;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private AdminStatsService adminStatsService;
    @InjectMocks
    private AdminController controller;

    @BeforeEach
    void setUp() {
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn("admin");
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user(ADMIN_ID, "admin", 1, 1)));
    }

    private User user(Long id, String name, int role, int status) {
        User u = new User();
        u.setId(id);
        u.setUsername(name);
        u.setRole(role);
        u.setStatus(status);
        return u;
    }

    private Order order(Long id, int status) {
        Order o = new Order();
        o.setId(id);
        o.setOrderNo("20261012000816321");
        o.setBuyerId(2L);
        o.setSellerId(3L);
        o.setProductId(100L);
        o.setOrderTitle("相机");
        o.setStatus(status);
        return o;
    }

    private static int codeOf(ResponseEntity<ApiResponse<?>> resp) {
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody().getCode();
    }

    private static String messageOf(ResponseEntity<ApiResponse<?>> resp) {
        return resp.getBody().getMessage();
    }

    // ---------------- 用户状态 ----------------

    @Test
    @DisplayName("不能修改自己的账号状态（管理员是唯一能进后台的人，自我封锁无法恢复）")
    void cannot_modify_own_status() {
        var resp = controller.updateUserStatus(AUTH, ADMIN_ID, 0);

        assertThat(codeOf(resp)).isEqualTo(400);
        assertThat(messageOf(resp)).contains("自己");
        verify(penaltyService, never()).updateUserStatus(any(), any(), anyString());
    }

    @Test
    @DisplayName("不能封禁其他管理员")
    void cannot_modify_another_admin() {
        when(userRepository.selectById(2L)).thenReturn(user(2L, "admin2", 1, 1));

        var resp = controller.updateUserStatus(AUTH, 2L, 0);

        assertThat(codeOf(resp)).isEqualTo(403);
        assertThat(messageOf(resp)).contains("管理员");
        verify(penaltyService, never()).updateUserStatus(any(), any(), anyString());
    }

    @Test
    @DisplayName("非法状态值（7）被拒，不再写进一个既非正常也非封禁的值")
    void rejects_unknown_status_value() {
        var resp = controller.updateUserStatus(AUTH, 5L, 7);

        assertThat(codeOf(resp)).isEqualTo(400);
        assertThat(messageOf(resp)).contains("0");
        verify(penaltyService, never()).updateUserStatus(any(), any(), anyString());
    }

    @Test
    @DisplayName("普通用户可以正常封禁")
    void allows_banning_a_normal_user() {
        when(userRepository.selectById(5L)).thenReturn(user(5L, "buyer1", 0, 1));
        User banned = user(5L, "buyer1", 0, 0);
        when(penaltyService.updateUserStatus(5L, 0, "admin")).thenReturn(banned);

        var resp = controller.updateUserStatus(AUTH, 5L, 0);

        assertThat(codeOf(resp)).isEqualTo(200);
        verify(penaltyService).updateUserStatus(5L, 0, "admin");
    }

    // ---------------- 订单状态 ----------------

    @Test
    @DisplayName("订单状态白名单：30 之类的值被拒")
    void rejects_unknown_order_status() {
        var resp = controller.updateOrderStatus(AUTH, 1L, Map.of("status", "30"));

        assertThat(codeOf(resp)).isEqualTo(400);
        verify(orderRepository, never()).updateById(any(Order.class));
    }

    @Test
    @DisplayName("待支付订单不能被直接标记为已完成（那等于凭空造一笔 GMV）")
    void rejects_completing_an_unpaid_order() {
        when(orderRepository.selectById(1L)).thenReturn(order(1L, OrderStatus.PENDING_PAYMENT.getCode()));

        var resp = controller.updateOrderStatus(AUTH, 1L, Map.of("status", "3"));

        assertThat(codeOf(resp)).isEqualTo(400);
        assertThat(messageOf(resp)).contains("待支付");
        verify(orderRepository, never()).updateById(any(Order.class));
    }

    @Test
    @DisplayName("已完成/已取消的订单不能被改写回中间状态")
    void rejects_rewriting_terminal_order() {
        when(orderRepository.selectById(1L)).thenReturn(order(1L, OrderStatus.COMPLETED.getCode()));

        assertThat(codeOf(controller.updateOrderStatus(AUTH, 1L, Map.of("status", "2")))).isEqualTo(400);
        verify(orderRepository, never()).updateById(any(Order.class));
    }

    @Test
    @DisplayName("待收货 → 已完成：放行并补上完成时间")
    void allows_pending_receipt_to_completed() {
        Order o = order(1L, OrderStatus.PENDING_RECEIPT.getCode());
        when(orderRepository.selectById(1L)).thenReturn(o);

        var resp = controller.updateOrderStatus(AUTH, 1L, Map.of("status", "3"));

        assertThat(codeOf(resp)).isEqualTo(200);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.COMPLETED.getCode());
        assertThat(o.getCompletedTime()).isNotNull();
        verify(orderRepository).updateById(o);
    }

    // ---------------- 订单删除 ----------------

    @Test
    @DisplayName("管理端也不能物理删除进行中的订单")
    void admin_cannot_delete_in_progress_order() {
        when(orderRepository.selectById(1L)).thenReturn(order(1L, OrderStatus.PENDING_SHIPMENT.getCode()));

        var resp = controller.deleteOrder(AUTH, 1L);

        assertThat(codeOf(resp)).isEqualTo(400);
        verify(orderRepository, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("已取消订单可以删")
    void admin_can_delete_cancelled_order() {
        when(orderRepository.selectById(1L)).thenReturn(order(1L, OrderStatus.CANCELLED.getCode()));
        when(orderRepository.deleteById(1L)).thenReturn(1);

        assertThat(codeOf(controller.deleteOrder(AUTH, 1L))).isEqualTo(200);
        verify(orderRepository).deleteById(1L);
    }

    // ---------------- 批量商品状态 ----------------

    @Test
    @DisplayName("ids 传字符串：400，而不是 ClassCastException → 500")
    void batch_rejects_non_list_ids() {
        var resp = controller.batchUpdateProductStatus(AUTH, Map.of("ids", "1,2,3", "status", 1));

        assertThat(codeOf(resp)).isEqualTo(400);
        verify(productRepository, never()).updateById(any(Product.class));
    }

    @Test
    @DisplayName("批量条数超过上限：400，不做 N 次往返")
    void batch_rejects_oversized_request() {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            ids.add(i + 1);
        }

        var resp = controller.batchUpdateProductStatus(AUTH, Map.of("ids", ids, "status", 1));

        assertThat(codeOf(resp)).isEqualTo(400);
        assertThat(messageOf(resp)).contains("100");
        verify(productRepository, never()).updateById(any(Product.class));
    }

    @Test
    @DisplayName("批量里的脏 id 只跳过它自己，不让整批白改")
    void batch_skips_invalid_ids_only() {
        Product p = new Product();
        p.setId(10L);
        when(productRepository.selectById(10L)).thenReturn(p);

        var resp = controller.batchUpdateProductStatus(AUTH,
                Map.of("ids", List.of("abc", 10), "status", 1));

        assertThat(codeOf(resp)).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getBody().getData();
        assertThat(data).containsEntry("updatedCount", 1).containsEntry("skippedCount", 1);
        verify(productRepository).updateById(p);
    }

    // ---------------- 管理端订单搜索（N9） ----------------

    @Test
    @DisplayName("订单搜索真的进了 SQL（此前该参数后端根本不存在，搜索框是摆设）")
    void order_search_reaches_sql() {
        IPage<Order> empty = new Page<>();
        when(orderRepository.selectPage(any(), any())).thenReturn(empty);

        controller.getAllOrders(AUTH, "20261012", null, 1, 10);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<Order>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderRepository).selectPage(any(), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("order_no").contains("order_title");
    }
}
