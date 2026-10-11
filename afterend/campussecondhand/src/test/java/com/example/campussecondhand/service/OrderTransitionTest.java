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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发货 / 确认收货的角色与状态约束（P0 回归防线）。
 *
 * <p><b>漏洞是怎么发生的：</b>原先没有这两个接口，前端"确认收货"发的是
 * 通用的 {@code PUT /api/orders/{id}} + {@code {status:3}}。那个端点只检查
 * 「你是这笔订单的买家或卖家之一」，然后把请求体里的 status 原样写库，于是：</p>
 * <ul>
 *   <li>买家可以对刚创建的<b>待支付</b>订单直接 PUT status=2（待收货）甚至 status=3，
 *       不付钱拿到"已完成"；</li>
 *   <li>卖家可以替买家点"确认收货"，把款项结算给自己；</li>
 *   <li>已取消的订单也能被改回已完成。</li>
 * </ul>
 *
 * <p>现在每个状态跃迁都是独立端点，绑定<b>特定角色 + 特定前置状态</b>，
 * 并用条件 UPDATE 的影响行数判定成败。本测试把这三条钉死。</p>
 */
class OrderTransitionTest {

    private static final Long ORDER_ID = 1L;
    private static final Long BUYER_ID = 2L;
    private static final Long SELLER_ID = 3L;
    private static final Long STRANGER_ID = 9L;

    private OrderRepository orderRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderService = new OrderService(orderRepository, mock(PaymentRecordRepository.class),
                mock(ProductRepository.class), mock(UserRepository.class), mock(ProductService.class),
                new OrderFeeService(), new MockPaymentServiceImpl(),
                new ResourcelessTransactionTemplate(), 30);
        when(orderRepository.updateStatusIfMatch(anyLong(), anyInt(), anyInt())).thenReturn(1);
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

    @Test
    @DisplayName("卖家发货：待发货(1) → 待收货(2)")
    void seller_ships_pending_shipment_to_pending_receipt() {
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.PENDING_SHIPMENT.getCode()));

        Order shipped = orderService.shipOrder(ORDER_ID, SELLER_ID);

        assertThat(shipped.getStatus()).isEqualTo(OrderStatus.PENDING_RECEIPT.getCode());
        verify(orderRepository).updateStatusIfMatch(ORDER_ID,
                OrderStatus.PENDING_SHIPMENT.getCode(), OrderStatus.PENDING_RECEIPT.getCode());
    }

    @Test
    @DisplayName("买家不能发货：角色不符直接拒绝，且不触碰数据库")
    void buyer_cannot_ship() {
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.PENDING_SHIPMENT.getCode()));

        assertThatThrownBy(() -> orderService.shipOrder(ORDER_ID, BUYER_ID))
                .isInstanceOf(OrderService.OrderBusinessException.class)
                .hasMessageContaining("只有卖家");
        verify(orderRepository, org.mockito.Mockito.never())
                .updateStatusIfMatch(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("无关用户不能发货")
    void stranger_cannot_ship() {
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.PENDING_SHIPMENT.getCode()));

        assertThatThrownBy(() -> orderService.shipOrder(ORDER_ID, STRANGER_ID))
                .isInstanceOf(OrderService.OrderBusinessException.class)
                .hasMessageContaining("只有卖家");
    }

    @Test
    @DisplayName("待支付订单不能被发货：条件 UPDATE 0 行即失败")
    void pending_payment_order_cannot_be_shipped() {
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.PENDING_PAYMENT.getCode()));
        when(orderRepository.updateStatusIfMatch(ORDER_ID,
                OrderStatus.PENDING_SHIPMENT.getCode(), OrderStatus.PENDING_RECEIPT.getCode()))
                .thenReturn(0);

        assertThatThrownBy(() -> orderService.shipOrder(ORDER_ID, SELLER_ID))
                .isInstanceOf(OrderService.OrderBusinessException.class)
                .hasMessageContaining("不允许发货");
    }

    @Test
    @DisplayName("买家确认收货：待收货(2) → 已完成(3)")
    void buyer_confirms_receipt() {
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.PENDING_RECEIPT.getCode()));

        Order completed = orderService.confirmReceipt(ORDER_ID, BUYER_ID);

        assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED.getCode());
        assertThat(completed.getCompletedTime()).isNotNull();
        verify(orderRepository).updateStatusIfMatch(ORDER_ID,
                OrderStatus.PENDING_RECEIPT.getCode(), OrderStatus.COMPLETED.getCode());
    }

    @Test
    @DisplayName("卖家不能替买家确认收货（否则自己给自己放款）")
    void seller_cannot_confirm_receipt() {
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.PENDING_RECEIPT.getCode()));

        assertThatThrownBy(() -> orderService.confirmReceipt(ORDER_ID, SELLER_ID))
                .isInstanceOf(OrderService.OrderBusinessException.class)
                .hasMessageContaining("只有买家");
        verify(orderRepository, org.mockito.Mockito.never())
                .updateStatusIfMatch(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("待支付订单不能被买家直接确认收货（跳过发货与付款）")
    void pending_payment_order_cannot_be_confirmed() {
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.PENDING_PAYMENT.getCode()));
        when(orderRepository.updateStatusIfMatch(ORDER_ID,
                OrderStatus.PENDING_RECEIPT.getCode(), OrderStatus.COMPLETED.getCode()))
                .thenReturn(0);

        assertThatThrownBy(() -> orderService.confirmReceipt(ORDER_ID, BUYER_ID))
                .isInstanceOf(OrderService.OrderBusinessException.class)
                .hasMessageContaining("不允许确认收货");
    }

    @Test
    @DisplayName("已取消订单不能被确认收货")
    void cancelled_order_cannot_be_confirmed() {
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.CANCELLED.getCode()));
        when(orderRepository.updateStatusIfMatch(ORDER_ID,
                OrderStatus.PENDING_RECEIPT.getCode(), OrderStatus.COMPLETED.getCode()))
                .thenReturn(0);

        assertThatThrownBy(() -> orderService.confirmReceipt(ORDER_ID, BUYER_ID))
                .isInstanceOf(OrderService.OrderBusinessException.class)
                .hasMessageContaining("不允许确认收货");
    }

    @Test
    @DisplayName("已支付订单不允许取消（资金已收却释放商品）")
    void paid_order_cannot_be_cancelled() {
        when(orderRepository.selectById(ORDER_ID))
                .thenReturn(orderWithStatus(OrderStatus.PENDING_SHIPMENT.getCode()));

        assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID))
                .isInstanceOf(OrderService.OrderBusinessException.class)
                .hasMessageContaining("不允许取消");
    }
}
