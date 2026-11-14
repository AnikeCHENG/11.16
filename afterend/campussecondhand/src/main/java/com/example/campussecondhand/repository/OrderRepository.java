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
     * 订单完成：状态推进与 {@code completed_time} 在同一条语句写入。
     *
     * <p>{@code completed_time} 是订单的完成凭证时间：订单列表、商品详情与管理端
     * 都直接读这一列展示"完成时间"。此前完成路径统一走 {@link #updateStatusIfMatch}，
     * 而那条 SQL 只写 {@code status} 与 {@code updated_time}——时间戳只停留在
     * Service 的内存对象上，数据库里永远是 {@code NULL}，刷新页面就消失。</p>
     *
     * <p>不能用 {@code updated_time} 代替：它会被后续任何改动刷新，无法作为对账依据。</p>
     *
     * @return 影响行数，0 表示订单已不是预期状态（并发确认/申请退款）
     */
    @Update("UPDATE orders SET status = #{newStatus}, completed_time = NOW(), updated_time = NOW() "
            + "WHERE id = #{id} AND status = #{expectedStatus}")
    int markCompletedIfPending(@Param("id") Long id,
                               @Param("expectedStatus") Integer expectedStatus,
                               @Param("newStatus") Integer newStatus);

    /**
     * 支付占位失败后的回滚：把订单退回「待支付」并清掉支付痕迹。
     *
     * <p>用于「已占位 → 调渠道失败」这条路径：渠道失败意味着钱没扣，
     * 订单必须回到待支付，否则用户看到的是「已支付」但没收到货。</p>
     *
     * <p>条件里带 {@code status = #{paidStatus}}：只有仍处于本次占位状态
     * （待发货）才回滚，避免把已被并发改走的订单状态覆盖回去。</p>
     *
     * @return 影响行数，0 表示状态已被并发改变，无需回滚
     */
    @Update("UPDATE orders SET status = #{pendingStatus}, payment_method = NULL, "
            + "transaction_id = NULL, paid_time = NULL, updated_time = NOW() "
            + "WHERE id = #{id} AND status = #{paidStatus}")
    int revertToPendingIfPaid(@Param("id") Long id,
                               @Param("paidStatus") Integer paidStatus,
                               @Param("pendingStatus") Integer pendingStatus);

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

    /**
     * 卖家发货：状态推进与 {@code shipped_time} 在同一条语句写入。
     *
     * <p>自动确认收货以该字段为计时起点，不能依赖 {@code updated_time}——
     * 管理员改备注、补数据都会刷新 updated_time，会把计时起点往后推。</p>
     *
     * @return 影响行数，0 表示订单已不是待发货
     */
    @Update("UPDATE orders SET status = #{newStatus}, shipped_time = NOW(), updated_time = NOW() "
            + "WHERE id = #{id} AND status = #{expectedStatus}")
    int markShippedIfPending(@Param("id") Long id,
                             @Param("expectedStatus") Integer expectedStatus,
                             @Param("newStatus") Integer newStatus);

    /**
     * 买家申请退款：仅「待发货/待收货」可申请。
     *
     * <p>退款金额与申请前状态都由数据库从当前行取值，避免应用层先读后写
     * 期间订单被发货导致 {@code refund_from_status} 记录错误。</p>
     *
     * @return 影响行数，0 表示状态已变化（例如刚被确认收货）
     */
    // MySQL 的 UPDATE 赋值按从左到右求值：refund_from_status 必须写在
    // status = #{refunding} 之前，否则读到的是刚改成的 5，驳回时会回到 5。
    @Update("UPDATE orders SET refund_from_status = status, status = #{refunding}, "
            + "refund_reason = #{reason}, refund_amount = price + shipping_fee, "
            + "refund_apply_time = NOW(), updated_time = NOW() "
            + "WHERE id = #{id} AND status IN (#{fromA}, #{fromB})")
    int markRefundingIfPaid(@Param("id") Long id,
                            @Param("refunding") Integer refunding,
                            @Param("fromA") Integer fromA,
                            @Param("fromB") Integer fromB,
                            @Param("reason") String reason);

    /**
     * 同意退款：退款中(5) → 已退款(6)。
     *
     * @return 影响行数，0 表示已被其他请求处理
     */
    @Update("UPDATE orders SET status = #{refunded}, refund_time = NOW(), "
            + "refund_reply = #{reply}, updated_time = NOW() "
            + "WHERE id = #{id} AND status = #{refunding}")
    int markRefundedIfRefunding(@Param("id") Long id,
                                @Param("refunding") Integer refunding,
                                @Param("refunded") Integer refunded,
                                @Param("reply") String reply);

    /**
     * 拒绝退款/买家撤销：退款中(5) → 申请前状态(1/2)。
     *
     * <p>回退目标取自 {@code refund_from_status}，不能写死 1 或 2：
     * 已发货订单被驳回后必须回到「待收货」，否则买家会失去确认收货入口。</p>
     */
    @Update("UPDATE orders SET status = refund_from_status, refund_time = NOW(), "
            + "refund_reply = #{reply}, updated_time = NOW() "
            + "WHERE id = #{id} AND status = #{refunding}")
    int markRefundRejected(@Param("id") Long id,
                           @Param("refunding") Integer refunding,
                           @Param("reply") String reply);

    /**
     * 管理员强制退款：已支付未确认收货(1/2) → 已退款(6)。
     *
     * @return 影响行数，0 表示状态已变化
     */
    // 同上：refund_from_status 必须先取原状态，再覆盖 status
    @Update("UPDATE orders SET refund_from_status = status, status = #{refunded}, "
            + "refund_reason = #{reason}, refund_reply = #{reply}, "
            + "refund_amount = price + shipping_fee, refund_apply_time = NOW(), "
            + "refund_time = NOW(), updated_time = NOW() "
            + "WHERE id = #{id} AND status IN (#{fromA}, #{fromB})")
    int markRefundedIfPaid(@Param("id") Long id,
                           @Param("refunded") Integer refunded,
                           @Param("fromA") Integer fromA,
                           @Param("fromB") Integer fromB,
                           @Param("reason") String reason,
                           @Param("reply") String reply);
}
