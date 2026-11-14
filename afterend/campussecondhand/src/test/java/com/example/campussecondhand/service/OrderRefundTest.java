package com.example.campussecondhand.service;

import com.example.campussecondhand.entity.Order;
import com.example.campussecondhand.enums.OrderStatus;
import com.example.campussecondhand.repository.OrderRepository;
import com.example.campussecondhand.repository.PaymentRecordRepository;
import com.example.campussecondhand.repository.ProductRepository;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.impl.MockPaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款闭环（P0 回归防线）：申请 → 同意 / 拒绝 / 撤销，以及管理端强制退款。
 *
 * <p>退款只发生在确认收货之前：此时卖家还没入账（放款在 completeOrder 里），
 * 所以同意退款只需把商品恢复在售，不涉及从卖家余额扣钱。</p>
 */
class OrderRefundTest {

    private static final Long ORDER_ID = 10L;
    private static final Long BUYER_ID = 2L;
    private static final Long SELLER_ID = 3L;
    private static final Long STRANGER_ID = 9L;
    private static final Long PRODUCT_ID = 100L;

    private OrderRepository orderRepository;
    private ProductService productService;
    private FundService fundService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        productService = mock(ProductService.class);
        fundService = mock(FundService.class);
        orderService = new OrderService(orderRepository, mock(PaymentRecordRepository.class),
                mock(ProductRepository.class), mock(UserRepository.class), productService,
                new OrderFeeService(), fundService, new MockPaymentServiceImpl(),
                new ResourcelessTransactionTemplate(), 30);
        when(orderRepository.markRefundingIfPaid(anyLong(), anyInt(), anyInt(), anyInt(), anyString()))
                .thenReturn(1);
        when(orderRepository.markRefundedIfRefunding(anyLong(), anyInt(), anyInt(), anyString()))
                .thenReturn(1);
        when(orderRepository.markRefundRejected(anyLong(), anyInt(), anyString())).thenReturn(1);
        when(orderRepository.markRefundedIfPaid(anyLong(), anyInt(), anyInt(), anyInt(),
                anyString(), anyString())).thenReturn(1);
        when(productService.markAsOnSale(PRODUCT_ID)).thenReturn(true);
    }

    private Order orderWithStatus(Integer status) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("20251020150000001");
        order.setProductId(PRODUCT_ID);
        order.setBuyerId(BUYER_ID);
        order.setSellerId(SELLER_ID);
        order.setStatus(status);
        order.setPrice(new BigDecimal("100.00"));
        order.setShippingFee(new BigDecimal("10.00"));
        return order;
    }

    @Test
    @DisplayName("退款 SQL 先记录原状态再改 status（MySQL 赋值从左到右求值）")
    void refund_sql_captures_previous_status_first() throws Exception {
        String sql = OrderRepository.class
                .getMethod("markRefundingIfPaid", Long.class, Integer.class,
                        Integer.class, Integer.class, String.class)
                .getAnnotation(org.apache.ibatis.annotations.Update.class)
                .value()[0];
        assertThat(sql.indexOf("refund_from_status = status"))
                .as("refund_from_status 必须写在 status = #{refunding} 之前，否则会被记成 5")
                .isGreaterThanOrEqualTo(0)
                .isLessThan(sql.indexOf("status = #{refunding}"));

        String directSql = OrderRepository.class
                .getMethod("markRefundedIfPaid", Long.class, Integer.class,
                        Integer.class, Integer.class, String.class, String.class)
                .getAnnotation(org.apache.ibatis.annotations.Update.class)
                .value()[0];
        assertThat(directSql.indexOf("refund_from_status = status"))
                .as("管理员直接退款同样必须先记录原状态")
                .isGreaterThanOrEqualTo(0)
                .isLessThan(directSql.indexOf("status = #{refunded}"));
    }

    @Test
    @DisplayName("买家申请退款：1 → 5，记录原因与申请前状态")
    void buyer_applies_refund() {
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.PENDING_SHIPMENT.getCode()));

        Order refunding = orderService.applyRefund(ORDER_ID, BUYER_ID, "  不想要了  ");

        assertThat(refunding.getStatus()).isEqualTo(OrderStatus.REFUNDING.getCode());
        assertThat(refunding.getRefundFromStatus()).isEqualTo(OrderStatus.PENDING_SHIPMENT.getCode());
        assertThat(refunding.getRefundReason()).isEqualTo("不想要了");
        assertThat(refunding.getRefundAmount()).isEqualByComparingTo("110.00");
        assertThat(refunding.getRefundApplyTime()).isNotNull();
    }

    @Test
    @DisplayName("已完成的订单不能申请退款：钱已进卖家余额")
    void completed_order_cannot_refund() {
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.COMPLETED.getCode()));

        assertThatThrownBy(() -> orderService.applyRefund(ORDER_ID, BUYER_ID, "后悔了"))
                .isInstanceOf(OrderService.OrderBusinessException.class)
                .hasMessageContaining("不允许申请退款");
        verify(orderRepository, never())
                .markRefundingIfPaid(anyLong(), anyInt(), anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("退款原因必填，且不能超过 100 字")
    void refund_reason_is_validated() {
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.PENDING_RECEIPT.getCode()));

        assertThatThrownBy(() -> orderService.applyRefund(ORDER_ID, BUYER_ID, "   "))
                .hasMessageContaining("请填写退款原因");
        assertThatThrownBy(() -> orderService.applyRefund(ORDER_ID, BUYER_ID, "x".repeat(101)))
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("只有买家本人可以申请退款")
    void only_buyer_can_apply_refund() {
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.PENDING_SHIPMENT.getCode()));

        assertThatThrownBy(() -> orderService.applyRefund(ORDER_ID, STRANGER_ID, "不是我的单"))
                .hasMessageContaining("只有买家");
    }

    @Test
    @DisplayName("卖家同意退款：5 → 6，商品恢复在售，不回滚卖家余额")
    void seller_approves_refund() {
        Order order = orderWithStatus(OrderStatus.REFUNDING.getCode());
        order.setRefundFromStatus(OrderStatus.PENDING_RECEIPT.getCode());
        order.setRefundAmount(new BigDecimal("110.00"));
        when(orderRepository.selectById(ORDER_ID)).thenReturn(order);

        Order refunded = orderService.approveRefund(ORDER_ID, SELLER_ID, "已同意");

        assertThat(refunded.getStatus()).isEqualTo(OrderStatus.REFUNDED.getCode());
        assertThat(refunded.getRefundReply()).isEqualTo("已同意");
        assertThat(refunded.getRefundTime()).isNotNull();
        verify(productService).markAsOnSale(PRODUCT_ID);
    }

    @Test
    @DisplayName("只有卖家可以处理退款申请")
    void only_seller_can_handle_refund() {
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.REFUNDING.getCode()));

        assertThatThrownBy(() -> orderService.approveRefund(ORDER_ID, STRANGER_ID, null))
                .hasMessageContaining("只有卖家");
    }

    @Test
    @DisplayName("卖家拒绝退款：5 → 申请前状态，商品保持锁定")
    void seller_rejects_refund() {
        Order order = orderWithStatus(OrderStatus.REFUNDING.getCode());
        order.setRefundFromStatus(OrderStatus.PENDING_SHIPMENT.getCode());
        when(orderRepository.selectById(ORDER_ID)).thenReturn(order);

        Order back = orderService.rejectRefund(ORDER_ID, SELLER_ID, "已发货，不支持退款");

        assertThat(back.getStatus()).isEqualTo(OrderStatus.PENDING_SHIPMENT.getCode());
        assertThat(back.getRefundReply()).isEqualTo("已发货，不支持退款");
        verify(productService, never()).markAsOnSale(anyLong());
    }

    @Test
    @DisplayName("买家撤销退款申请：5 → 申请前状态")
    void buyer_cancels_refund_request() {
        Order order = orderWithStatus(OrderStatus.REFUNDING.getCode());
        order.setRefundFromStatus(OrderStatus.PENDING_RECEIPT.getCode());
        when(orderRepository.selectById(ORDER_ID)).thenReturn(order);

        Order back = orderService.cancelRefundRequest(ORDER_ID, BUYER_ID);

        assertThat(back.getStatus()).isEqualTo(OrderStatus.PENDING_RECEIPT.getCode());
    }

    @Test
    @DisplayName("管理员可直接给已支付未收货订单退款")
    void admin_refunds_paid_order() {
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.PENDING_SHIPMENT.getCode()));

        Order refunded = orderService.adminRefund(ORDER_ID, "商品描述不符", "平台介入退款");

        assertThat(refunded.getStatus()).isEqualTo(OrderStatus.REFUNDED.getCode());
        assertThat(refunded.getRefundFromStatus()).isEqualTo(OrderStatus.PENDING_SHIPMENT.getCode());
        assertThat(refunded.getRefundAmount()).isEqualByComparingTo("110.00");
        verify(productService).markAsOnSale(PRODUCT_ID);
    }

    @Test
    @DisplayName("管理员不能对已退款订单再次退款")
    void admin_cannot_refund_twice() {
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.REFUNDED.getCode()));

        assertThatThrownBy(() -> orderService.adminRefund(ORDER_ID, null, null))
                .hasMessageContaining("不允许退款");
        verify(orderRepository, never())
                .markRefundedIfPaid(anyLong(), anyInt(), anyInt(), anyInt(),
                        anyString(), anyString());
    }

    @Test
    @DisplayName("卖家同意退款：余额支付的实付金额退回买家")
    void seller_approval_reverses_buyer_balance() {
        Order order = orderWithStatus(OrderStatus.REFUNDING.getCode());
        order.setRefundFromStatus(OrderStatus.PENDING_SHIPMENT.getCode());
        order.setRefundAmount(new BigDecimal("110.00"));
        order.setPaymentMethod("balance");
        when(orderRepository.selectById(ORDER_ID)).thenReturn(order);

        orderService.approveRefund(ORDER_ID, SELLER_ID, "同意退款");

        verify(fundService).refundToBuyer(eq(order), eq(new BigDecimal("110.00")));
    }

    @Test
    @DisplayName("管理员强制退款：同样把实付金额退回买家")
    void admin_refund_reverses_buyer_balance() {
        Order order = orderWithStatus(OrderStatus.PENDING_RECEIPT.getCode());
        order.setPaymentMethod("余额");
        when(orderRepository.selectById(ORDER_ID)).thenReturn(order);

        orderService.adminRefund(ORDER_ID, "商品与描述不符", "平台介入");

        verify(fundService).refundToBuyer(eq(order), eq(new BigDecimal("110.00")));
    }
}
