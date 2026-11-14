package com.example.campussecondhand.repository;

import com.example.campussecondhand.dto.CreditLogView;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 信用分读写。
 *
 * <p><b>为什么用原生 SQL 而不是给 User 实体加 credit_score 字段：</b>
 * 实体一旦多一个字段，MyBatis-Plus 的每条 {@code selectById/selectPage}
 * 都会把它带进 SQL——迁移没跑之前，<b>登录、个人资料、订单列表全会抛
 * Unknown column</b>。写成原生 SQL 后，{@code users} 表结构对实体不可见，
 * "这个分能不能记"被限制在 {@link com.example.campussecondhand.service.CreditService}
 * 一个点上。理由与 {@link AccountRepository} 完全一致。</p>
 *
 * <p>对应的表结构见 {@code resources/migration-credit-score.sql}。</p>
 */
@Mapper
public interface CreditRepository {

    /**
     * 记一条信用流水。
     *
     * <p>{@code INSERT IGNORE} 而非普通 {@code INSERT}：撞上
     * {@code uk_user_event_biz} 时返回 0（表示"这个事件已经记过"），
     * 而不是抛异常。抛异常会污染调用方事务——信用分是附属能力，
     * 不能因为一次重复触发就把下单、确认收货回滚掉。</p>
     *
     * @return 1 = 本次确实记了一笔；0 = 重复事件，已记过
     */
    @Insert("INSERT IGNORE INTO credit_log (user_id, event_type, biz_id, delta, remark) "
            + "VALUES (#{userId}, #{eventType}, #{bizId}, #{delta}, #{remark})")
    int insertLogIgnore(@Param("userId") Long userId,
                        @Param("eventType") String eventType,
                        @Param("bizId") Long bizId,
                        @Param("delta") int delta,
                        @Param("remark") String remark);

    /**
     * 在库里完成加减与上下限截断。
     *
     * <p>用 {@code credit_score = credit_score + #{delta}} 而不是"先读后写"：
     * 同一用户可能同时完成两笔交易，读-改-写会丢掉其中一笔。
     * 截断同样交给数据库（{@code LEAST/GREATEST}），避免分数越界。</p>
     *
     * <p><b>{@code AND deleted = 0}</b>：{@code users} 是本项目唯一带逻辑删除列的表，
     * {@code @Update} 是原生 SQL 不走 MyBatis-Plus 的逻辑删除插件，必须手写过滤，
     * 否则已注销账号的信用分仍会被加减，而实体侧又查不到这个人。</p>
     *
     * @return 影响行数，0 表示用户不存在
     */
    @Update("UPDATE users SET credit_score = LEAST(#{max}, GREATEST(#{min}, credit_score + #{delta})) "
            + "WHERE id = #{userId} AND deleted = 0")
    int adjust(@Param("userId") Long userId, @Param("delta") int delta,
               @Param("min") int min, @Param("max") int max);

    /**
     * 读取当前信用分；用户不存在（或已注销）返回 null。
     *
     * <p>{@code AND deleted = 0}：同 {@link #adjust}。</p>
     */
    @Select("SELECT credit_score FROM users WHERE id = #{userId} AND deleted = 0")
    Integer scoreOf(@Param("userId") Long userId);

    /** 最近的变动记录，倒序 */
    @Select("SELECT event_type AS eventType, delta, remark, create_time AS createTime "
            + "FROM credit_log WHERE user_id = #{userId} ORDER BY id DESC LIMIT #{limit}")
    List<CreditLogView> recentLogs(@Param("userId") Long userId, @Param("limit") int limit);
}
