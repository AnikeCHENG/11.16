package com.example.campussecondhand.service;

import com.example.campussecondhand.entity.Order;
import com.example.campussecondhand.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 放款（P1-10）。
 *
 * <p>确认收货时把 {@code seller_income} 计入卖家余额并写一条资金流水。
 * 这里覆盖四件事：正常入账、余额快照、零金额跳过、账户不存在必须整体失败。</p>
 */
class FundServiceTest {

    private static final Long SELLER_ID = 3L;
    private static final Long ORDER_ID = 1L;

    private AccountRepository accountRepository;
    private FundService fundService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        fundService = new FundService(accountRepository);
    }

    private Order completedOrder(String income) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("20261012000816321");
        order.setSellerId(SELLER_ID);
        order.setStatus(3);
        order.setSellerIncome(income == null ? null : new BigDecimal(income));
        return order;
    }

    @Test
    @DisplayName("正常入账：加余额 + 写 IN 方向流水 + 记录记账后余额")
    void credits_balance_and_writes_flow() {
        BigDecimal income = new BigDecimal("1495.50");
        when(accountRepository.credit(SELLER_ID, income)).thenReturn(1);
        when(accountRepository.balanceOf(SELLER_ID)).thenReturn(new BigDecimal("1495.50"));

        fundService.creditSellerIncome(completedOrder("1495.50"));

        verify(accountRepository).credit(SELLER_ID, income);
        verify(accountRepository).insertFlow(eq(SELLER_ID), eq(ORDER_ID), eq("20261012000816321"),
                eq("IN"), eq(income), eq(new BigDecimal("1495.50")), anyString());
    }

    @Test
    @DisplayName("入账用 balance = balance + ? ，不是先读后写")
    void credit_is_a_single_atomic_statement() {
        when(accountRepository.credit(any(), any())).thenReturn(1);
        when(accountRepository.balanceOf(SELLER_ID)).thenReturn(new BigDecimal("10.00"));

        fundService.creditSellerIncome(completedOrder("10.00"));

        // 只加不加读改写：读到余额后再写回，会在并发完成两笔订单时丢掉一笔
        verify(accountRepository).credit(SELLER_ID, new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("零金额订单：跳过，不加钱也不写流水")
    void zero_income_is_skipped() {
        fundService.creditSellerIncome(completedOrder("0.00"));

        verify(accountRepository, never()).credit(any(), any());
        verify(accountRepository, never()).insertFlow(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("历史订单没有 seller_income（null）：跳过")
    void null_income_is_skipped() {
        fundService.creditSellerIncome(completedOrder(null));

        verify(accountRepository, never()).credit(any(), any());
    }

    @Test
    @DisplayName("卖家账户不存在：抛异常让外层事务回滚，绝不静默记账")
    void missing_account_fails_loudly() {
        when(accountRepository.credit(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> fundService.creditSellerIncome(completedOrder("100.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("卖家账户不存在");
        verify(accountRepository, never()).insertFlow(any(), any(), any(), any(), any(), any(), any());
    }
}
