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

    /** 流水方向：收入（卖家应收入账）；将来若有提现/退款，加 OUT 即可 */
    private static final String DIRECTION_IN = "IN";

    private final AccountRepository accountRepository;

    public FundService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
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
}
