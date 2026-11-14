package com.example.campussecondhand.service;

import com.example.campussecondhand.entity.Order;
import com.example.campussecondhand.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 订单能否被物理删除的判定（回归防线）。
 *
 * <p>两道门槛：状态必须已终结（已完成/已取消/已退款），且<b>不能有资金流水</b>。
 * 后者是关键：{@code account_flow} / {@code payment_record} 通过 {@code order_id}
 * 关联 {@code orders}，没有外键也没有级联删除；已完成的订单早已放款给卖家，
 * 删掉订单就会留下「有流水无订单」的孤儿记录，对账时无法解释这笔钱的去向。</p>
 */
class OrderDeletableTest {

    private OrderService serviceWith(FundService fundService) {
        return new OrderService(mock(com.example.campussecondhand.repository.OrderRepository.class),
                mock(com.example.campussecondhand.repository.PaymentRecordRepository.class),
                mock(com.example.campussecondhand.repository.ProductRepository.class),
                mock(com.example.campussecondhand.repository.UserRepository.class),
                mock(ProductService.class),
                new OrderFeeService(),
                fundService,
                mock(com.example.campussecondhand.service.impl.MockPaymentServiceImpl.class),
                new ResourcelessTransactionTemplate(),
                30);
    }

    private Order orderWithStatus(Integer status) {
        Order order = new Order();
        order.setId(7L);
        order.setOrderNo("ORD-7");
        order.setStatus(status);
        return order;
    }

    @Test
    @DisplayName("已取消且无流水：可以删除")
    void cancelled_without_flow_is_deletable() {
        FundService fundService = mock(FundService.class);
        when(fundService.hasFundFlow(anyLong())).thenReturn(false);

        assertThat(serviceWith(fundService).canDelete(orderWithStatus(OrderStatus.CANCELLED.getCode())))
                .isTrue();
    }

    @Test
    @DisplayName("已完成且无流水：可以删除")
    void completed_without_flow_is_deletable() {
        FundService fundService = mock(FundService.class);
        when(fundService.hasFundFlow(anyLong())).thenReturn(false);

        assertThat(serviceWith(fundService).canDelete(orderWithStatus(OrderStatus.COMPLETED.getCode())))
                .isTrue();
    }

    @Test
    @DisplayName("已有资金流水：不能删除（否则留下孤儿流水）")
    void order_with_fund_flow_is_not_deletable() {
        FundService fundService = mock(FundService.class);
        when(fundService.hasFundFlow(anyLong())).thenReturn(true);

        assertThat(serviceWith(fundService).canDelete(orderWithStatus(OrderStatus.COMPLETED.getCode())))
                .isFalse();
    }

    @Test
    @DisplayName("进行中的订单：不能删除")
    void pending_order_is_not_deletable() {
        FundService fundService = mock(FundService.class);

        assertThat(serviceWith(fundService)
                .canDelete(orderWithStatus(OrderStatus.PENDING_SHIPMENT.getCode())))
                .isFalse();
        assertThat(serviceWith(fundService)
                .canDelete(orderWithStatus(OrderStatus.PENDING_RECEIPT.getCode())))
                .isFalse();
    }

    @Test
    @DisplayName("订单为 null：不能删除")
    void null_order_is_not_deletable() {
        assertThat(serviceWith(mock(FundService.class)).canDelete(null)).isFalse();
    }
}
