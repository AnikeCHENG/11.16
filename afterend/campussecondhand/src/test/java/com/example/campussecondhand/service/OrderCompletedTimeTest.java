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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单完成时 {@code completed_time} 必须落库（回归防线）。
 *
 * <p>完成路径有三条：买家确认收货、发货后自动确认、管理员标记完成。它们原先都走
 * {@code updateStatusIfMatch}，而那条 SQL 只写 {@code status} 与 {@code updated_time}，
 * {@code completed_time} 仅存在于 Service 的内存对象里——数据库中永远是 {@code NULL}，
 * 订单列表与商品详情展示的"完成时间"全部为空，也无法用于对账。</p>
 */
class OrderCompletedTimeTest {

    private static final Long ORDER_ID = 11L;
    private static final Long BUYER_ID = 1L;
    private static final Long SELLER_ID = 2L;

    private OrderRepository orderRepository;
    private FundService fundService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        fundService = mock(FundService.class);
        orderService = new OrderService(orderRepository, mock(PaymentRecordRepository.class),
                mock(ProductRepository.class), mock(UserRepository.class), mock(ProductService.class),
                new OrderFeeService(), fundService, new MockPaymentServiceImpl(),
                new ResourcelessTransactionTemplate(), 30);
    }

    private Order pendingReceiptOrder() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("ORD-11");
        order.setProductId(100L);
        order.setBuyerId(BUYER_ID);
        order.setSellerId(SELLER_ID);
        order.setStatus(OrderStatus.PENDING_RECEIPT.getCode());
        order.setPrice(new BigDecimal("100.00"));
        return order;
    }

    @Test
    @DisplayName("买家确认收货：用带 completed_time 的条件更新")
    void confirm_receipt_uses_completion_update() {
        when(orderRepository.selectById(ORDER_ID)).thenReturn(pendingReceiptOrder());
        when(orderRepository.markCompletedIfPending(anyLong(), anyInt(), anyInt())).thenReturn(1);

        orderService.confirmReceipt(ORDER_ID, BUYER_ID);

        verify(orderRepository).markCompletedIfPending(ORDER_ID,
                OrderStatus.PENDING_RECEIPT.getCode(), OrderStatus.COMPLETED.getCode());
        // 不能再走不写 completed_time 的那条
        verify(orderRepository, never()).updateStatusIfMatch(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("管理员标记完成：同样走带 completed_time 的条件更新")
    void admin_complete_uses_completion_update() {
        when(orderRepository.selectById(ORDER_ID)).thenReturn(pendingReceiptOrder());
        when(orderRepository.markCompletedIfPending(anyLong(), anyInt(), anyInt())).thenReturn(1);

        orderService.adminComplete(ORDER_ID);

        verify(orderRepository).markCompletedIfPending(ORDER_ID,
                OrderStatus.PENDING_RECEIPT.getCode(), OrderStatus.COMPLETED.getCode());
        verify(orderRepository, never()).updateStatusIfMatch(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("确认收货时内存对象带上完成时间，与库里一致")
    void confirm_receipt_sets_completed_time_in_memory() {
        when(orderRepository.selectById(ORDER_ID)).thenReturn(pendingReceiptOrder());
        when(orderRepository.markCompletedIfPending(anyLong(), anyInt(), anyInt())).thenReturn(1);

        Order result = orderService.confirmReceipt(ORDER_ID, BUYER_ID);

        assertThat(result.getCompletedTime()).isNotNull();
    }

    @Test
    @DisplayName("条件更新未命中（并发已被改状态）时不放款")
    void concurrent_state_change_blocks_completion_side_effects() {
        when(orderRepository.selectById(ORDER_ID)).thenReturn(pendingReceiptOrder());
        when(orderRepository.markCompletedIfPending(anyLong(), anyInt(), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> orderService.confirmReceipt(ORDER_ID, BUYER_ID))
                .isInstanceOf(RuntimeException.class);

        verify(fundService, never()).creditSellerIncome(any(Order.class));
    }
}
