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
     * @return 影响行数，0 表示用户不存在
     */
    @Update("UPDATE users SET balance = balance + #{amount} WHERE id = #{userId}")
    int credit(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /** 读取当前余额，用于流水里的 balance_after 快照 */
    @Select("SELECT balance FROM users WHERE id = #{userId}")
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
}
