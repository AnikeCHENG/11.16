package com.example.campussecondhand.service;

import com.example.campussecondhand.entity.Order;
import com.example.campussecondhand.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 资金记账（P1-10）。
 *
 * <p><b>此前项目里没有放款这一步：</b>订单里有 {@code seller_income}（卖家应收）
 * 与 {@code service_fee}（平台服务费），确认收货只把状态改成「已完成」，
 * 钱从来没有真正到过卖家账上——既没有余额字段，也没有流水表。
 * 答辩时这只能算"模拟担保"，不能写成"已实现资金结算"。</p>
 *
 * <p><b>事务边界：</b>本方法<b>不带</b>事务注解，靠调用方
 * （{@link OrderService#confirmReceipt}）的 {@code @Transactional} 把
 * "订单置为已完成" 与 "入账" 绑成一个原子操作——钱和状态要么都成，要么都不成。
 * 这里若自己再开一个 {@code REQUIRED} 事务，语义相同但会让人误以为它可以独立调用。</p>
 *
 * <p><b>幂等：</b>靠 {@code account_flow.uk_order_direction(order_id, direction)}。
 * 即使有人将来把确认收货改成可重入，第二次入账也会撞唯一键并整体回滚，
 * 不会出现"同一单放两次款"。</p>
 */
@Service
public class FundService {

    private static final Logger log = LoggerFactory.getLogger(FundService.class);

    /** 流水方向：收入（卖家应收入账 / 退款退回买家余额） */
    private static final String DIRECTION_IN = "IN";

    /** 流水方向：支出（买家余额支付扣款） */
    private static final String DIRECTION_OUT = "OUT";

    private final AccountRepository accountRepository;

    public FundService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** 余额支付可用：兼容前端传 {@code balance} 与历史/测试里传的「余额」 */
    public static boolean isBalanceMethod(String payMethod) {
        return payMethod != null
                && ("balance".equalsIgnoreCase(payMethod.trim())
                || "余额".equals(payMethod.trim()));
    }

    /**
     * 该订单是否产生过资金流水。
     *
     * <p>用于判断订单能否被物理删除：只要动过钱（放款给卖家、退款给买家、
     * 余额支付扣款），删掉订单就只剩一条没有订单可对应的流水，对账时无法解释
     * 这笔钱冲哪笔单来的。</p>
     *
     * @param orderId 订单 id
     * @return true 表示有流水
     */
    public boolean hasFundFlow(Long orderId) {
        if (orderId == null) {
            return false;
        }
        return accountRepository.countFlowsByOrder(orderId) > 0;
    }

    /** 余额不足：由 Controller 转成 400，事务内抛出以保证支付状态一起回滚 */
    public static class InsufficientBalanceException extends RuntimeException {
        public InsufficientBalanceException(String message) {
            super(message);
        }
    }

    /**
     * 确认收货时把卖家应收计入其账户余额，并写一条资金流水。
     *
     * <p>金额为 0（例如历史脏数据）时跳过：既不加钱也不写流水，
     * 但<b>要让调用方继续完成订单</b>——不能因为一条没有金额的老订单
     * 卡住买家的确认收货。</p>
     *
     * @param order 已推进到「已完成」的订单，需含 sellerId / sellerIncome
     * @throws IllegalStateException 卖家账户不存在（此时必须整体回滚，不能静默放过）
     */
    public void creditSellerIncome(Order order) {
        BigDecimal amount = order.getSellerIncome();
        if (order.getSellerId() == null || amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("订单无可入账金额，跳过放款: orderNo={}, sellerIncome={}",
                    order.getOrderNo(), amount);
            return;
        }

        int updated = accountRepository.credit(order.getSellerId(), amount);
        if (updated == 0) {
            // 卖家账户查不到：货已交付、状态已改，钱却没有去处。
            // 抛异常让外层事务回滚，宁可这次确认收货失败也不能账实不符。
            throw new IllegalStateException("卖家账户不存在，无法入账: userId=" + order.getSellerId());
        }

        BigDecimal balanceAfter = accountRepository.balanceOf(order.getSellerId());
        accountRepository.insertFlow(order.getSellerId(), order.getId(), order.getOrderNo(),
                DIRECTION_IN, amount,
                balanceAfter != null ? balanceAfter : amount,
                "订单完成，卖家应收入账");
        log.info("卖家入账: userId={}, orderNo={}, amount={}, balanceAfter={}",
                order.getSellerId(), order.getOrderNo(), amount, balanceAfter);
    }

    /**
     * 余额支付：从买家账户扣款并写一条 OUT 流水。
     *
     * <p>调用方必须处在 {@code payOrder} 的事务里。扣款是单条条件 UPDATE，
     * 余额不足时影响行数为 0，抛 {@link InsufficientBalanceException} 让外层
     * 事务回滚——绝不能用"返回失败"的方式收场，否则已经写好的支付状态会提交。</p>
     *
     * @param order   待支付订单（提供 orderNo / id 用于流水）
     * @param buyerId 付款买家
     * @param amount  买家实付（成交价 + 运费）
     */
    public void debitForPurchase(Order order, Long buyerId, BigDecimal amount) {
        if (buyerId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("订单无需扣款，跳过: orderNo={}, amount={}", order.getOrderNo(), amount);
            return;
        }

        int updated = accountRepository.debit(buyerId, amount);
        if (updated == 0) {
            throw new InsufficientBalanceException("余额不足，请选择其他支付方式或先出售闲置");
        }

        BigDecimal balanceAfter = accountRepository.balanceOf(buyerId);
        accountRepository.insertFlow(buyerId, order.getId(), order.getOrderNo(),
                DIRECTION_OUT, amount,
                balanceAfter != null ? balanceAfter : BigDecimal.ZERO,
                "订单支付，余额扣款");
        log.info("余额支付扣款: userId={}, orderNo={}, amount={}, balanceAfter={}",
                buyerId, order.getOrderNo(), amount, balanceAfter);
    }

    /**
     * 退款成功时把实付金额退回买家余额（仅余额支付的订单）。
     *
     * <p>第三方渠道（Mock 支付宝/微信）的退款由渠道侧处理，本方法只维护
     * 平台内余额账本。退款只发生在确认收货前，卖家尚未入账，因此不需要
     * 从卖家余额扣回；{@code uk_order_direction(order_id, direction)}
     * 保证同一订单不会退回两次。</p>
     *
     * @param order  已推进到「已退款」的订单
     * @param amount 退款金额（成交价 + 运费）
     */
    public void refundToBuyer(Order order, BigDecimal amount) {
        if (!isBalanceMethod(order.getPaymentMethod())) {
            return;
        }
        if (order.getBuyerId() == null || amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("退款无需回补余额，跳过: orderNo={}, amount={}", order.getOrderNo(), amount);
            return;
        }

        int updated = accountRepository.credit(order.getBuyerId(), amount);
        if (updated == 0) {
            // 钱已收下却退不回去：必须整体回滚，让退款状态保持原样
            throw new IllegalStateException(
                    "买家账户不存在，无法退回余额: userId=" + order.getBuyerId());
        }

        BigDecimal balanceAfter = accountRepository.balanceOf(order.getBuyerId());
        accountRepository.insertFlow(order.getBuyerId(), order.getId(), order.getOrderNo(),
                DIRECTION_IN, amount,
                balanceAfter != null ? balanceAfter : amount,
                "订单退款，余额退回");
        log.info("退款回补余额: userId={}, orderNo={}, amount={}, balanceAfter={}",
                order.getBuyerId(), order.getOrderNo(), amount, balanceAfter);
    }
}
