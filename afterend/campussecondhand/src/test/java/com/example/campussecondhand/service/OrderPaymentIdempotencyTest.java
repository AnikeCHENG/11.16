package com.example.campussecondhand.service;

import com.example.campussecondhand.entity.Order;
import com.example.campussecondhand.enums.OrderStatus;
import com.example.campussecondhand.repository.OrderRepository;
import com.example.campussecondhand.repository.PaymentRecordRepository;
import com.example.campussecondhand.repository.ProductRepository;
import com.example.campussecondhand.repository.UserRepository;
import com.example.campussecondhand.service.PaymentService.PayResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付流程的顺序契约：<b>先占位、再调渠道</b>（回归防线）。
 *
 * <p>原实现是「先调渠道扣款 → 再条件更新状态」。渠道已扣钱、状态更新失败
 * （并发取消/重复提交）时会出现「钱扣了订单却没推进」；接入真实渠道后
 * 这就是重复扣款与人工退款。</p>
 *
 * <p>现在的顺序：预生成幂等键 → 条件更新占位 → 占位成功才调渠道 →
 * 渠道失败则回滚成待支付。</p>
 */
class OrderPaymentIdempotencyTest {

    private static final Long ORDER_ID = 1L;
    private static final Long BUYER_ID = 2L;

    private OrderRepository orderRepository;
    private FundService fundService;
    private PaymentService paymentService;
    private OrderService orderService;

    /** 渠道被调用的次数 */
    private final AtomicInteger channelCalls = new AtomicInteger();
    /** 渠道调用时拿到的幂等键 */
    private final List<String> receivedIdempotencyKeys = new ArrayList<>();
    /** 渠道是否返回失败 */
    private boolean channelShouldFail = false;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        fundService = mock(FundService.class);
        paymentService = new PaymentService() {
            @Override
            public PayResult pay(Order order, String payMethod) {
                channelCalls.incrementAndGet();
                receivedIdempotencyKeys.add(order.getTransactionId());
                if (channelShouldFail) {
                    return PayResult.fail("渠道支付失败");
                }
                return PayResult.ok(order.getTransactionId());
            }

            @Override
            public String generateIdempotencyKey(Order order) {
                return "IDEMPOTENCY-KEY-1";
            }

            @Override
            public String channelName() {
                return "test";
            }
        };
        orderService = new OrderService(orderRepository, mock(PaymentRecordRepository.class),
                mock(ProductRepository.class), mock(UserRepository.class),
                mock(ProductService.class), new OrderFeeService(), fundService,
                paymentService, new ResourcelessTransactionTemplate(), 30);

        channelCalls.set(0);
        receivedIdempotencyKeys.clear();
        channelShouldFail = false;
        when(orderRepository.markPaidIfPending(any(), any(), any(), any(), any())).thenReturn(1);
    }

    private Order pendingOrder() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("ORD-1");
        order.setBuyerId(BUYER_ID);
        order.setSellerId(3L);
        order.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        order.setPrice(new BigDecimal("50.00"));
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));
        return order;
    }

    @Test
    @DisplayName("占位失败（并发已改状态）：根本不调用渠道，不会重复扣款")
    void channel_is_not_called_when_reservation_fails() {
        when(orderRepository.selectById(ORDER_ID)).thenReturn(pendingOrder());
        when(orderRepository.markPaidIfPending(any(), any(), any(), any(), any())).thenReturn(0);

        var result = orderService.payOrder(ORDER_ID, "alipay", BUYER_ID);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("未扣款");
        // 关键断言：渠道一次都没被调用（占位已尝试过，但没占到，所以不发起扣款）
        assertThat(channelCalls.get()).isZero();
    }

    @Test
    @DisplayName("占位成功：才调用渠道，并把幂等键传进去")
    void channel_receives_idempotency_key_after_reservation() {
        when(orderRepository.selectById(ORDER_ID)).thenReturn(pendingOrder());

        var result = orderService.payOrder(ORDER_ID, "alipay", BUYER_ID);

        assertThat(result.isSuccess()).isTrue();
        assertThat(channelCalls.get()).isEqualTo(1);
        assertThat(receivedIdempotencyKeys).containsExactly("IDEMPOTENCY-KEY-1");
        // 占位时写入的 transaction_id 就是这个幂等键
        verify(orderRepository).markPaidIfPending(ORDER_ID,
                OrderStatus.PENDING_PAYMENT.getCode(),
                OrderStatus.PENDING_SHIPMENT.getCode(),
                "alipay", "IDEMPOTENCY-KEY-1");
    }

    @Test
    @DisplayName("渠道失败：回滚成待支付，且不记支付流水")
    void channel_failure_reverts_to_pending() {
        when(orderRepository.selectById(ORDER_ID)).thenReturn(pendingOrder());
        channelShouldFail = true;

        var result = orderService.payOrder(ORDER_ID, "alipay", BUYER_ID);

        assertThat(result.isSuccess()).isFalse();
        assertThat(channelCalls.get()).isEqualTo(1);
        // 回滚：待发货 → 待支付
        verify(orderRepository).revertToPendingIfPaid(ORDER_ID,
                OrderStatus.PENDING_SHIPMENT.getCode(),
                OrderStatus.PENDING_PAYMENT.getCode());
        // 渠道失败意味着钱没扣，不该扣余额
        verify(fundService, never()).debitForPurchase(any(), anyLong(), any());
    }

    @Test
    @DisplayName("幂等键对同一次支付保持一致（订单号与渠道号三方一致）")
    void transaction_id_matches_payment_record() {
        when(orderRepository.selectById(ORDER_ID)).thenReturn(pendingOrder());

        var result = orderService.payOrder(ORDER_ID, "alipay", BUYER_ID);

        assertThat(result.getTradeNo()).isEqualTo("IDEMPOTENCY-KEY-1");
        assertThat(receivedIdempotencyKeys.get(0)).isEqualTo(result.getTradeNo());
    }
}
