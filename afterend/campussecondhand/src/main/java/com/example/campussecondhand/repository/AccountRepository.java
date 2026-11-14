package com.example.campussecondhand.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 账户与资金流水（P1-10）。
 *
 * <p><b>为什么用原生 SQL 而不是给 User 实体加 balance 字段：</b>
 * 实体一旦多一个字段，MyBatis-Plus 的每条 {@code selectById/selectPage}
 * 都会把它带进 SQL——迁移没跑之前，<b>登录、个人资料、订单列表全会抛
 * Unknown column</b>，故障面是整个应用。写成原生 SQL 后，
 * {@code users} 表结构对实体不可见，"这笔钱能不能记"被限制在
 * {@link com.example.campussecondhand.service.FundService} 一个点上。</p>
 *
 * <p>对应的表结构见 {@code resources/migration-seller-balance.sql}。</p>
 */
@Mapper
public interface AccountRepository {

    /**
     * 给账户加钱。
     *
     * <p>用 {@code balance = balance + #{amount}} 而不是"先读后写"：
     * 同一卖家可能同时完成两笔订单，读-改-写会丢掉其中一笔。</p>
     *
     * <p><b>{@code AND deleted = 0}</b>：{@code users} 是本项目唯一带逻辑删除列的表，
     * 而 {@code @Update} 是原生 SQL，不走 MyBatis-Plus 的逻辑删除插件，必须手写过滤。
     * 否则已被注销的账号余额仍会被入账，而实体侧 {@code userRepository.selectById}
     * 又查不到这个人——两套「这个用户存不存在」的判断会不一致。</p>
     *
     * @return 影响行数，0 表示用户不存在
     */
    @Update("UPDATE users SET balance = balance + #{amount} WHERE id = #{userId} AND deleted = 0")
    int credit(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 从账户扣钱（余额支付）。
     *
     * <p>条件里带 {@code balance >= #{amount}}：余额是否够、扣款是否执行
     * 由数据库在同一条语句里判定，不存在"先查余额再扣"被并发穿透的问题。
     * 影响行数为 0 既可能是用户不存在，也可能是余额不足，由调用方统一按
     * 「余额不足」处理。</p>
     *
     * <p>{@code AND deleted = 0}：同 {@link #credit}，原生 SQL 需手写逻辑删除过滤。</p>
     *
     * @return 影响行数，0 表示余额不足或用户不存在
     */
    @Update("UPDATE users SET balance = balance - #{amount} "
            + "WHERE id = #{userId} AND deleted = 0 AND balance >= #{amount}")
    int debit(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 读取当前余额，用于流水里的 balance_after 快照。
     *
     * <p>{@code AND deleted = 0}：同 {@link #credit}，已注销账号不参与资金操作。</p>
     */
    @Select("SELECT balance FROM users WHERE id = #{userId} AND deleted = 0")
    BigDecimal balanceOf(@Param("userId") Long userId);

    /**
     * 记一条资金流水。
     *
     * <p>{@code account_flow} 上有 {@code uk_order_direction(order_id, direction)}：
     * 同一张订单的同一方向只能记一次账，这是"钱不会被记两遍"的最终保证——
     * 应用层的条件更新可能被绕过或被将来的改动破坏，唯一键不会。</p>
     */
    @Insert("INSERT INTO account_flow "
            + "(user_id, order_id, order_no, direction, amount, balance_after, remark) "
            + "VALUES (#{userId}, #{orderId}, #{orderNo}, #{direction}, #{amount}, "
            + "#{balanceAfter}, #{remark})")
    int insertFlow(@Param("userId") Long userId,
                   @Param("orderId") Long orderId,
                   @Param("orderNo") String orderNo,
                   @Param("direction") String direction,
                   @Param("amount") BigDecimal amount,
                   @Param("balanceAfter") BigDecimal balanceAfter,
                   @Param("remark") String remark);

    /**
     * 统计某笔订单产生过多少条资金流水。
     *
     * <p>用于判断订单能否被物理删除：只要有流水（放款给卖家、退款给买家、
     * 余额支付扣款），删掉订单就会留下"有流水无订单"的孤儿记录，
     * 对账时无法解释这笔钱是冲哪笔单来的。</p>
     *
     * <p>{@code account_flow} 没有 deleted 列，无需逻辑删除过滤。</p>
     *
     * @param orderId 订单 id
     * @return 流水条数，0 表示该订单没有动过钱
     */
    @Select("SELECT COUNT(*) FROM account_flow WHERE order_id = #{orderId}")
    int countFlowsByOrder(@Param("orderId") Long orderId);
}
