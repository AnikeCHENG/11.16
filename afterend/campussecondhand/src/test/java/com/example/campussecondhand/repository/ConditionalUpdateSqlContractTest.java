package com.example.campussecondhand.repository;

import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发关键路径的 SQL 契约（P0 回归防线，反射锁定）。
 *
 * <p>下单、支付、取消、发货、确认收货全都依赖"条件 UPDATE + 检查影响行数"。
 * 这种写法有一个很隐蔽的退化方式：有人觉得 {@code WHERE status = ...} 多余，
 * 顺手删掉——代码照样编译、接口照样返回 200、单元测试如果只 mock mapper
 * 也照样全绿，但并发保护已经没了。</p>
 *
 * <p>所以这里直接把注解里的 SQL 文本钉住：条件必须出现在 WHERE 里。
 * 它不验证 SQL 能跑（那需要真实 MySQL），只验证"条件没有被删掉"。</p>
 */
class ConditionalUpdateSqlContractTest {

    private static String sqlOf(Class<?> type, String methodName) throws Exception {
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                Update update = m.getAnnotation(Update.class);
                assertThat(update).as("%s#%s 必须标注 @Update", type.getSimpleName(), methodName).isNotNull();
                // 统一空白，避免换行/缩进导致断言脆弱
                return String.join(" ", update.value()).replaceAll("\\s+", " ").trim();
            }
        }
        throw new AssertionError("找不到方法 " + type.getSimpleName() + "#" + methodName);
    }

    @Test
    @DisplayName("商品锁定必须是 UPDATE ... WHERE id=? AND status=在售")
    void mark_sold_is_conditional_on_current_status() throws Exception {
        String sql = sqlOf(ProductRepository.class, "markSoldIfOnSale");

        assertThat(sql).contains("UPDATE products");
        assertThat(sql).contains("SET status = #{soldStatus}");
        assertThat(sql)
                .as("WHERE 里必须同时有 id 与 status 条件，否则并发抢购会出两张订单")
                .contains("WHERE id = #{id} AND status = #{onSaleStatus}");
    }

    @Test
    @DisplayName("订单状态推进必须带 status=旧状态 的条件")
    void order_status_update_is_conditional() throws Exception {
        String sql = sqlOf(OrderRepository.class, "updateStatusIfMatch");

        assertThat(sql).contains("UPDATE orders");
        assertThat(sql).contains("SET status = #{newStatus}");
        assertThat(sql).contains("WHERE id = #{id} AND status = #{expectedStatus}");
    }

    @Test
    @DisplayName("支付落库必须在同一条语句里带条件与流水字段")
    void pay_update_is_conditional_and_writes_trade_fields_atomically() throws Exception {
        String sql = sqlOf(OrderRepository.class, "markPaidIfPending");

        assertThat(sql).contains("WHERE id = #{id} AND status = #{expectedStatus}");
        // 状态与流水字段同一条语句写入：拆成两条会留下"状态已变但交易号未落库"的中间态
        assertThat(sql).contains("payment_method = #{payMethod}");
        assertThat(sql).contains("transaction_id = #{tradeNo}");
        assertThat(sql).contains("paid_time = NOW()");
    }
}
