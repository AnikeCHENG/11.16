package com.example.campussecondhand.repository;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 逻辑删除过滤的 SQL 契约（回归防线，反射锁定）。
 *
 * <p>{@code users} 是本项目<b>唯一</b>带逻辑删除列 {@code deleted} 的表，
 * 而 {@code @Select / @Update} 写的是原生 SQL，<b>不走</b> MyBatis-Plus 的逻辑删除插件，
 * 必须手写 {@code deleted = 0}。</p>
 *
 * <p>漏写的后果很隐蔽：实体侧 {@code userRepository.selectById} 查不到已注销的用户，
 * 而资金/信用侧仍能给他扣款、入账、改分——同一个用户在两套代码里"存在性"判断不一致。
 * 单元测试若只 mock mapper 察觉不到，所以这里把 SQL 文本钉住。</p>
 *
 * <p>注意：{@code orders} / {@code products} / {@code messages} / {@code account_flow} /
 * {@code payment_record} 等表<b>没有</b> deleted 列，它们的原生 SQL 不需要也不应该加过滤。</p>
 */
class LogicalDeleteSqlContractTest {

    private static String sqlOf(Class<?> type, String methodName) throws Exception {
        for (Method m : type.getDeclaredMethods()) {
            if (!m.getName().equals(methodName)) {
                continue;
            }
            Update update = m.getAnnotation(Update.class);
            if (update != null) {
                return String.join(" ", update.value()).replaceAll("\\s+", " ").trim();
            }
            Select select = m.getAnnotation(Select.class);
            if (select != null) {
                return String.join(" ", select.value()).replaceAll("\\s+", " ").trim();
            }
            throw new AssertionError(
                    type.getSimpleName() + "#" + methodName + " 必须标注 @Update 或 @Select");
        }
        throw new AssertionError("找不到方法 " + type.getSimpleName() + "#" + methodName);
    }

    @Test
    @DisplayName("账户加钱必须过滤已注销账号")
    void account_credit_filters_deleted() throws Exception {
        String sql = sqlOf(AccountRepository.class, "credit");

        assertThat(sql).contains("UPDATE users");
        assertThat(sql)
                .as("原生 SQL 不走逻辑删除插件，必须手写 deleted = 0，否则已注销账号仍能入账")
                .contains("deleted = 0");
    }

    @Test
    @DisplayName("账户扣钱必须过滤已注销账号")
    void account_debit_filters_deleted() throws Exception {
        String sql = sqlOf(AccountRepository.class, "debit");

        assertThat(sql).contains("UPDATE users");
        assertThat(sql).contains("deleted = 0");
        // 余额不足的判定条件不能被顺手删掉
        assertThat(sql).contains("balance >= #{amount}");
    }

    @Test
    @DisplayName("读取余额必须过滤已注销账号")
    void account_balance_query_filters_deleted() throws Exception {
        String sql = sqlOf(AccountRepository.class, "balanceOf");

        assertThat(sql).contains("deleted = 0");
    }

    @Test
    @DisplayName("信用分加减必须过滤已注销账号")
    void credit_adjust_filters_deleted() throws Exception {
        String sql = sqlOf(CreditRepository.class, "adjust");

        assertThat(sql).contains("UPDATE users");
        assertThat(sql).contains("deleted = 0");
        // 上下限截断不能丢
        assertThat(sql).contains("LEAST(#{max}, GREATEST(#{min}, credit_score + #{delta}))");
    }

    @Test
    @DisplayName("读取信用分必须过滤已注销账号")
    void credit_score_query_filters_deleted() throws Exception {
        String sql = sqlOf(CreditRepository.class, "scoreOf");

        assertThat(sql).contains("deleted = 0");
    }

    @Test
    @DisplayName("按用户名/学号查用户必须过滤已注销账号")
    void user_lookup_filters_deleted() throws Exception {
        assertThat(sqlOf(UserRepository.class, "findByUsername")).contains("deleted = 0");
        assertThat(sqlOf(UserRepository.class, "selectByStudentNo")).contains("deleted = 0");
    }
}
