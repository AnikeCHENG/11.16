package com.example.campussecondhand.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.campussecondhand.entity.Order;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 订单 Mapper。
 *
 * <p>订单状态流转一律走下面的条件 UPDATE，不要用
 * {@code selectById → setStatus → updateById}：那是「先读后写」，
 * 两个并发请求都会读到同一个旧状态，然后各自把状态改成自己想要的值，
 * 后者静默覆盖前者（例如「取消」覆盖掉刚成功的「支付」）。</p>
 *
 * <p>MyBatis-Plus 的 {@code orders} 实体没有逻辑删除字段，故这里不需要
 * 追加 {@code deleted = 0} 条件；{@code updated_time} 列本身带
 * {@code ON UPDATE CURRENT_TIMESTAMP}，显式写出只为可读性。</p>
 */
public interface OrderRepository extends BaseMapper<Order> {

    /**
     * 状态机条件更新：仅当订单当前状态等于预期状态时才推进。
     *
     * @param expectedStatus 期望的旧状态；不匹配则返回 0，调用方必须视为失败
     * @param newStatus      目标状态
     * @return 影响行数，1=推进成功，0=状态已被并发请求改变
     */
    @Update("UPDATE orders SET status = #{newStatus}, updated_time = NOW() "
            + "WHERE id = #{id} AND status = #{expectedStatus}")
    int updateStatusIfMatch(@Param("id") Long id,
                            @Param("expectedStatus") Integer expectedStatus,
                            @Param("newStatus") Integer newStatus);

    /**
     * 支付成功：状态与流水字段在<b>同一条语句</b>内写入。
     *
     * <p>拆成「先条件改状态、再 updateById 写流水」会多一个中间态：
     * 状态已变成待发货但流水字段还没落库，此时进程挂掉就出现
     * 「已支付但查不到交易号」的账目缺口。</p>
     *
     * @return 影响行数，0 表示订单已不是待支付（并发支付/取消），本次不得记流水
     */
    @Update("UPDATE orders SET status = #{newStatus}, payment_method = #{payMethod}, "
            + "paid_time = NOW(), transaction_id = #{tradeNo}, updated_time = NOW() "
            + "WHERE id = #{id} AND status = #{expectedStatus}")
    int markPaidIfPending(@Param("id") Long id,
                          @Param("expectedStatus") Integer expectedStatus,
                          @Param("newStatus") Integer newStatus,
                          @Param("payMethod") String payMethod,
                          @Param("tradeNo") String tradeNo);
}
