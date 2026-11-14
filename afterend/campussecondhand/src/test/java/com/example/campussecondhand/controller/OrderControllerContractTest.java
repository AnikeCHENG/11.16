package com.example.campussecondhand.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.campussecondhand.common.ApiResponse;
import com.example.campussecondhand.common.PageResult;
import com.example.campussecondhand.entity.Order;
import com.example.campussecondhand.entity.Review;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.enums.OrderStatus;
import com.example.campussecondhand.repository.OrderRepository;
import com.example.campussecondhand.repository.ProductRepository;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.OrderFeeService;
import com.example.campussecondhand.service.OrderService;
import com.example.campussecondhand.service.PaymentService.PayResult;
import com.example.campussecondhand.service.ReviewService;
import com.example.campussecondhand.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单端点的鉴权、状态与"接口面"契约（P0 回归防线）。
 *
 * <p>钉死四件事：</p>
 * <ol>
 *   <li>通用的 {@code PUT /api/orders/{id}} <b>已不存在</b>——它允许调用方
 *       自己指定目标状态，是"买家白拿商品 / 卖家替买家收货"的根源；</li>
 *   <li>发货与确认收货都必须先过登录（无 token 401），业务失败返回 400；</li>
 *   <li>DELETE 只允许删除已完成或已取消的订单，进行中的订单一律 400
 *       且不得调用 {@code deleteById}；</li>
 *   <li>{@code GET /orders/{id}/review} 只对订单双方可见，非当事人 403。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderControllerContractTest {

    private static final String AUTH = "Bearer test-token";
    private static final Long ORDER_ID = 1L;
    private static final Long BUYER_ID = 2L;
    private static final Long SELLER_ID = 3L;
    private static final Long STRANGER_ID = 9L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderFeeService orderFeeService;
    @Mock
    private OrderService orderService;
    @Mock
    private ReviewService reviewService;
    @Mock
    private JwtUtil jwtUtil;
    @InjectMocks
    private OrderController controller;

    private User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("user" + id);
        return u;
    }

    private void tokenResolvesTo(Long userId) {
        when(jwtUtil.getUsernameFromToken(anyString())).thenReturn("user" + userId);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user(userId)));
    }

    private Order orderWithStatus(Integer status) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("20251006143022001");
        order.setProductId(100L);
        order.setBuyerId(BUYER_ID);
        order.setSellerId(SELLER_ID);
        order.setStatus(status);
        return order;
    }

    private static int codeOf(org.springframework.http.ResponseEntity<ApiResponse<?>> resp) {
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody().getCode();
    }

    @Test
    @DisplayName("通用 PUT /api/orders/{id} 已删除——不允许调用方自己指定目标状态")
    void generic_put_order_endpoint_is_removed() {
        // 先确认反射扫描本身有效：这个类当然还有别的 @PostMapping/@DeleteMapping
        long writes = Arrays.stream(OrderController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(PostMapping.class)
                        || m.isAnnotationPresent(DeleteMapping.class))
                .count();
        assertThat(writes).as("扫描不到写方法说明本测试是空转").isPositive();

        for (Method m : OrderController.class.getDeclaredMethods()) {
            PutMapping put = m.getAnnotation(PutMapping.class);
            if (put == null) {
                continue;
            }
            assertThat(put.value())
                    .as("PUT %s 又把目标状态交回调用方了", String.join(",", put.value()))
                    .doesNotContain("/{id}");
        }
        assertThat(Arrays.stream(OrderController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(org.springframework.web.bind.annotation.RequestMapping.class)
                        && Arrays.asList(m.getAnnotation(
                                org.springframework.web.bind.annotation.RequestMapping.class).method())
                                .contains(RequestMethod.PUT))
                .count()).as("不应再有 PUT 映射").isZero();
    }

    @Test
    @DisplayName("发货：无 token 返回 401，且不进入业务逻辑")
    void ship_requires_login() {
        assertThat(codeOf(controller.shipOrder(null, ORDER_ID))).isEqualTo(401);
        verify(orderService, never()).shipOrder(anyLong(), anyLong());
    }

    @Test
    @DisplayName("发货：业务失败（角色或状态不符）返回 400")
    void ship_maps_business_failure_to_400() {
        tokenResolvesTo(SELLER_ID);
        when(orderService.shipOrder(ORDER_ID, SELLER_ID))
                .thenThrow(new OrderService.OrderBusinessException("当前订单状态（待支付）不允许发货"));

        var resp = controller.shipOrder(AUTH, ORDER_ID);

        assertThat(codeOf(resp)).isEqualTo(400);
        assertThat(resp.getBody().getMessage()).contains("不允许发货");
    }

    @Test
    @DisplayName("发货：卖家调用成功返回 200")
    void ship_succeeds_for_seller() {
        tokenResolvesTo(SELLER_ID);
        when(orderService.shipOrder(ORDER_ID, SELLER_ID))
                .thenReturn(orderWithStatus(OrderStatus.PENDING_RECEIPT.getCode()));

        assertThat(codeOf(controller.shipOrder(AUTH, ORDER_ID))).isEqualTo(200);
    }

    @Test
    @DisplayName("确认收货：无 token 返回 401")
    void confirm_requires_login() {
        assertThat(codeOf(controller.confirmReceipt(null, ORDER_ID))).isEqualTo(401);
        verify(orderService, never()).confirmReceipt(anyLong(), anyLong());
    }

    @Test
    @DisplayName("确认收货：卖家调用被服务层拒绝并映射为 400")
    void confirm_rejects_seller() {
        tokenResolvesTo(SELLER_ID);
        when(orderService.confirmReceipt(ORDER_ID, SELLER_ID))
                .thenThrow(new OrderService.OrderBusinessException("只有买家可以确认收货"));

        assertThat(codeOf(controller.confirmReceipt(AUTH, ORDER_ID))).isEqualTo(400);
    }

    @Test
    @DisplayName("确认收货：买家调用成功返回 200")
    void confirm_succeeds_for_buyer() {
        tokenResolvesTo(BUYER_ID);
        when(orderService.confirmReceipt(ORDER_ID, BUYER_ID))
                .thenReturn(orderWithStatus(OrderStatus.COMPLETED.getCode()));

        assertThat(codeOf(controller.confirmReceipt(AUTH, ORDER_ID))).isEqualTo(200);
    }

    @Test
    @DisplayName("删除订单：无 token 401")
    void delete_requires_login() {
        assertThat(codeOf(controller.deleteOrder(null, ORDER_ID))).isEqualTo(401);
        verify(orderRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除订单：非当事人 403，且不删除")
    void delete_rejects_stranger() {
        tokenResolvesTo(STRANGER_ID);
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.CANCELLED.getCode()));

        assertThat(codeOf(controller.deleteOrder(AUTH, ORDER_ID))).isEqualTo(403);
        verify(orderRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除订单：进行中的订单（待支付/待发货/待收货/退款中）一律 400，不删")
    void delete_rejects_in_progress_orders() {
        tokenResolvesTo(BUYER_ID);

        for (OrderStatus status : new OrderStatus[]{OrderStatus.PENDING_PAYMENT,
                OrderStatus.PENDING_SHIPMENT, OrderStatus.PENDING_RECEIPT,
                OrderStatus.REFUNDING}) {
            when(orderRepository.selectById(ORDER_ID)).thenReturn(orderWithStatus(status.getCode()));
            assertThat(codeOf(controller.deleteOrder(AUTH, ORDER_ID)))
                    .as("状态 %s 的订单不得被硬删除", status.getLabel())
                    .isEqualTo(400);
        }
        verify(orderRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除订单：已取消的订单可删（清理视图，不动进行中的交易）")
    void delete_allows_cancelled_order() {
        tokenResolvesTo(BUYER_ID);
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.CANCELLED.getCode()));
        when(orderRepository.deleteById(ORDER_ID)).thenReturn(1);
        when(orderService.canDelete(any())).thenReturn(true);

        assertThat(codeOf(controller.deleteOrder(AUTH, ORDER_ID))).isEqualTo(200);
        verify(orderRepository).deleteById(ORDER_ID);
    }

    @Test
    @DisplayName("删除订单：已完成的订单可删")
    void delete_allows_completed_order() {
        tokenResolvesTo(SELLER_ID);
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.COMPLETED.getCode()));
        when(orderRepository.deleteById(ORDER_ID)).thenReturn(1);
        when(orderService.canDelete(any())).thenReturn(true);

        assertThat(codeOf(controller.deleteOrder(AUTH, ORDER_ID))).isEqualTo(200);
    }

    @Test
    @DisplayName("删除订单：已退款的订单可删（交易已终结，但流水仍由资金记录保留）")
    void delete_allows_refunded_order() {
        tokenResolvesTo(BUYER_ID);
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.REFUNDED.getCode()));
        when(orderRepository.deleteById(ORDER_ID)).thenReturn(1);
        when(orderService.canDelete(any())).thenReturn(true);

        assertThat(codeOf(controller.deleteOrder(AUTH, ORDER_ID))).isEqualTo(200);
        verify(orderRepository).deleteById(ORDER_ID);
    }

    @Test
    @DisplayName("删除订单：已有资金流水的订单拒绝删除（避免孤儿流水）")
    void delete_rejects_order_with_fund_flow() {
        tokenResolvesTo(SELLER_ID);
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.COMPLETED.getCode()));
        when(orderService.canDelete(any())).thenReturn(false);

        assertThat(codeOf(controller.deleteOrder(AUTH, ORDER_ID))).isEqualTo(400);
        assertThat(controller.deleteOrder(AUTH, ORDER_ID).getBody().getMessage())
                .contains("资金流水");
        verify(orderRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("退款申请：无 token 401，且不进入业务逻辑")
    void refund_apply_requires_login() {
        assertThat(codeOf(controller.applyRefund(null, ORDER_ID,
                java.util.Map.of("reason", "不想要了")))).isEqualTo(401);
        verify(orderService, never()).applyRefund(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("退款申请：服务层拒绝时映射为 400")
    void refund_apply_maps_business_error() {
        tokenResolvesTo(BUYER_ID);
        when(orderService.applyRefund(anyLong(), anyLong(), anyString()))
                .thenThrow(new OrderService.OrderBusinessException("当前订单状态（已完成）不允许申请退款"));

        var resp = controller.applyRefund(AUTH, ORDER_ID,
                java.util.Map.of("reason", "后悔了"));

        assertThat(codeOf(resp)).isEqualTo(400);
        assertThat(resp.getBody().getMessage()).contains("不允许申请退款");
    }

    @Test
    @DisplayName("卖家同意退款：成功返回 200 与退款后的订单")
    void refund_approve_success() {
        tokenResolvesTo(SELLER_ID);
        Order refunded = orderWithStatus(OrderStatus.REFUNDED.getCode());
        when(orderService.approveRefund(anyLong(), anyLong(), any()))
                .thenReturn(refunded);
        when(orderService.buildCashierView(refunded))
                .thenReturn(java.util.Map.of("id", ORDER_ID, "status", 6));

        var resp = controller.approveRefund(AUTH, ORDER_ID,
                java.util.Map.of("reply", "同意"));

        assertThat(codeOf(resp)).isEqualTo(200);
        assertThat(resp.getBody().getMessage()).contains("已同意退款");
        verify(orderService).approveRefund(ORDER_ID, SELLER_ID, "同意");
    }

    @Test
    @DisplayName("订单评价回显：无 token 401（此前完全公开）")
    void review_requires_login() {
        assertThat(codeOf(controller.getReview(null, ORDER_ID))).isEqualTo(401);
        verify(reviewService, never()).findByOrderId(anyLong());
    }

    @Test
    @DisplayName("订单评价回显：非买卖双方 403，不读评价内容")
    void review_hidden_from_non_party() {
        tokenResolvesTo(STRANGER_ID);
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.COMPLETED.getCode()));

        assertThat(codeOf(controller.getReview(AUTH, ORDER_ID))).isEqualTo(403);
        verify(reviewService, never()).findByOrderId(anyLong());
    }

    @Test
    @DisplayName("订单评价回显：订单不存在返回 404")
    void review_returns_404_for_missing_order() {
        tokenResolvesTo(BUYER_ID);
        when(orderRepository.selectById(ORDER_ID)).thenReturn(null);

        assertThat(codeOf(controller.getReview(AUTH, ORDER_ID))).isEqualTo(404);
    }

    @Test
    @DisplayName("订单评价回显：买家本人可见")
    @SuppressWarnings("unchecked")
    void review_visible_to_buyer() {
        tokenResolvesTo(BUYER_ID);
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.COMPLETED.getCode()));
        Review review = new Review();
        review.setId(5L);
        review.setOrderId(ORDER_ID);
        review.setReviewerId(BUYER_ID);
        review.setRating(5);
        review.setContent("很好");
        when(reviewService.findByOrderId(ORDER_ID)).thenReturn(review);

        var resp = controller.getReview(AUTH, ORDER_ID);

        assertThat(codeOf(resp)).isEqualTo(200);
        assertThat((java.util.Map<String, Object>) resp.getBody().getData())
                .containsEntry("content", "很好");
    }

    @Test
    @DisplayName("订单列表返回服务端判定的 isBuyer/isSeller：买家不会被标成卖家")
    @SuppressWarnings("unchecked")
    void my_orders_marks_roles_from_viewer_identity() {
        tokenResolvesTo(BUYER_ID);
        Page<Order> page = new Page<>(1, 10);
        page.setRecords(List.of(orderWithStatus(OrderStatus.PENDING_SHIPMENT.getCode())));
        page.setTotal(1);
        when(orderRepository.selectPage(any(), any())).thenReturn(page);
        when(productRepository.selectBatchIds(any())).thenReturn(List.of());
        when(userRepository.selectBatchIds(any())).thenReturn(List.of());

        var resp = controller.getMyOrders(AUTH, 1, 10);

        assertThat(codeOf(resp)).isEqualTo(200);
        PageResult<Map<String, Object>> data =
                (PageResult<Map<String, Object>>) resp.getBody().getData();
        assertThat(data.list()).hasSize(1);
        assertThat(data.list().get(0))
                .containsEntry("isBuyer", true)
                .containsEntry("isSeller", false);
    }

    /**
     * 批量支付的支付方式参数名回归（P0）。
     *
     * <p>前端 {@code api/order.js} 发的是 {@code pay_method}，后端一度只读
     * {@code payMethod}，于是选「余额」时静默回退成 alipay：订单变成待发货，
     * 余额却一分未扣。这里同时锁住两种写法，避免任一侧改名再次造成免费订单。</p>
     */
    @Test
    @DisplayName("批量支付：pay_method（前端实际写法）必须被识别，不能回退成 alipay")
    void batch_pay_accepts_snake_case_pay_method() {
        tokenResolvesTo(BUYER_ID);
        when(orderService.payOrder(ORDER_ID, "balance", BUYER_ID))
                .thenReturn(PayResult.ok("MOCK-BATCH-1"));
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.PENDING_SHIPMENT.getCode()));

        var resp = controller.batchPay(AUTH, Map.of(
                "orderIds", List.of(ORDER_ID),
                "pay_method", "balance"));

        assertThat(codeOf(resp)).isEqualTo(200);
        verify(orderService).payOrder(ORDER_ID, "balance", BUYER_ID);
    }

    @Test
    @DisplayName("批量支付：payMethod（后端历史契约）继续可用")
    void batch_pay_accepts_camel_case_pay_method() {
        tokenResolvesTo(BUYER_ID);
        when(orderService.payOrder(ORDER_ID, "余额", BUYER_ID))
                .thenReturn(PayResult.ok("MOCK-BATCH-2"));
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.PENDING_SHIPMENT.getCode()));

        var resp = controller.batchPay(AUTH, Map.of(
                "orderIds", List.of(ORDER_ID),
                "payMethod", "余额"));

        assertThat(codeOf(resp)).isEqualTo(200);
        verify(orderService).payOrder(ORDER_ID, "余额", BUYER_ID);
    }

    /**
     * 缺省支付方式不能静默回退（P0 回归防线）。
     *
     * <p>回退到 {@code alipay} 会走到「不扣余额、不记 OUT 流水」的通道，
     * 但订单照常推进为待发货、卖家照常入账——参数名拼错一次就等于白拿货。
     * 这里要求缺省直接 400，且一笔都不进入支付。</p>
     */
    @Test
    @DisplayName("批量支付：缺省支付方式返回 400，且不发起任何支付")
    void batch_pay_rejects_missing_pay_method() {
        tokenResolvesTo(BUYER_ID);

        var resp = controller.batchPay(AUTH, Map.of(
                "orderIds", List.of(ORDER_ID)));

        assertThat(codeOf(resp)).isEqualTo(400);
        assertThat(resp.getBody().getMessage()).contains("支付方式");
        // 一笔都不能走到 payOrder，否则就是「不扣款的订单被推进为已支付」
        verify(orderService, never()).payOrder(anyLong(), any(), any());
    }

    @Test
    @DisplayName("批量支付：支付方式为空串同样返回 400")
    void batch_pay_rejects_blank_pay_method() {
        tokenResolvesTo(BUYER_ID);

        var resp = controller.batchPay(AUTH, Map.of(
                "orderIds", List.of(ORDER_ID),
                "payMethod", "   "));

        assertThat(codeOf(resp)).isEqualTo(400);
        verify(orderService, never()).payOrder(anyLong(), any(), any());
    }
}
