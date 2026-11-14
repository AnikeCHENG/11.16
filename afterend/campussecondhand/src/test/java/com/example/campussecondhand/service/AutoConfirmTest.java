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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自动确认收货（P0）：卖家发货后超过 N 天，买家不点确认也不能让货款永远卡住。
 */
class AutoConfirmTest {

    private static final Long ORDER_ID = 20L;
    private static final Long BUYER_ID = 2L;
    private static final Long SELLER_ID = 3L;

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
        when(orderRepository.markCompletedIfPending(anyLong(), anyInt(), anyInt())).thenReturn(1);
    }

    private Order orderWithShippedTime(LocalDateTime shippedTime) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("20251001120000001");
        order.setProductId(100L);
        order.setBuyerId(BUYER_ID);
        order.setSellerId(SELLER_ID);
        order.setStatus(OrderStatus.PENDING_RECEIPT.getCode());
        order.setShippedTime(shippedTime);
        order.setPrice(new BigDecimal("100.00"));
        order.setShippingFee(new BigDecimal("0.00"));
        return order;
    }

    @Test
    @DisplayName("关闭自动确认（days <= 0）时不查库")
    void disabled_when_days_not_positive() {
        assertThat(orderService.autoConfirmReceiptBatch(0)).isZero();
        assertThat(orderService.autoConfirmReceiptBatch(-1)).isZero();
        verify(orderRepository, never()).selectList(any());
    }

    @Test
    @DisplayName("没有到期订单时不做任何更新")
    void nothing_due() {
        when(orderRepository.selectList(any())).thenReturn(List.of());

        assertThat(orderService.autoConfirmReceiptBatch(7)).isZero();
        verify(orderRepository, never()).markCompletedIfPending(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("发货超过 7 天：自动完成并给卖家放款")
    void due_order_is_completed_and_credited() {
        Order due = orderWithShippedTime(LocalDateTime.now().minusDays(8));
        Order fresh = orderWithShippedTime(LocalDateTime.now().minusDays(8));
        when(orderRepository.selectList(any())).thenReturn(List.of(due));
        when(orderRepository.selectById(ORDER_ID)).thenReturn(fresh);

        int completed = orderService.autoConfirmReceiptBatch(7);

        assertThat(completed).isEqualTo(1);
        assertThat(fresh.getStatus()).isEqualTo(OrderStatus.COMPLETED.getCode());
        assertThat(fresh.getCompletedTime()).isNotNull();
        verify(orderRepository).markCompletedIfPending(ORDER_ID,
                OrderStatus.PENDING_RECEIPT.getCode(), OrderStatus.COMPLETED.getCode());
        verify(fundService).creditSellerIncome(fresh);
    }

    @Test
    @DisplayName("扫描后被买家手动确认：事务内二次校验，不重复放款")
    void skips_order_already_completed() {
        Order due = orderWithShippedTime(LocalDateTime.now().minusDays(8));
        Order fresh = orderWithShippedTime(LocalDateTime.now().minusDays(8));
        fresh.setStatus(OrderStatus.COMPLETED.getCode());
        when(orderRepository.selectList(any())).thenReturn(List.of(due));
        when(orderRepository.selectById(ORDER_ID)).thenReturn(fresh);

        assertThat(orderService.autoConfirmReceiptBatch(7)).isZero();
        verify(orderRepository, never()).markCompletedIfPending(anyLong(), anyInt(), anyInt());
        verify(fundService, never()).creditSellerIncome(any(Order.class));
    }

    @Test
    @DisplayName("扫描后卖家补写发货时间：未到期则跳过")
    void skips_order_not_old_enough() {
        Order due = orderWithShippedTime(LocalDateTime.now().minusDays(8));
        Order fresh = orderWithShippedTime(LocalDateTime.now().minusDays(1));
        when(orderRepository.selectList(any())).thenReturn(List.of(due));
        when(orderRepository.selectById(ORDER_ID)).thenReturn(fresh);

        assertThat(orderService.autoConfirmReceiptBatch(7)).isZero();
        verify(orderRepository, never()).markCompletedIfPending(anyLong(), anyInt(), anyInt());
    }
}
