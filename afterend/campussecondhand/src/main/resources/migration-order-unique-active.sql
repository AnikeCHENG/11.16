-- ============================================================================
-- 订单唯一性约束：一件商品同时只能有一张「未取消」订单（P1-11）
--
-- 背景：orders 表只有 uk_order_no（订单号唯一），product_id 上仅有一个普通索引
--       idx_product_id。数据库层面完全允许同一件商品存在多张待支付订单——
--       "一件商品只能卖一次"此前只靠 OrderService 的条件 UPDATE 保证。
--       应用层保护是对的，但缺少数据库这道兜底：任何绕过该方法的写入
--       （以后新加的批量接口、人工修数据、脚本）都能造出重复订单。
--
-- 做法：加一个 STORED 生成列，订单未取消时等于 product_id，已取消时为 NULL，
--       再对它建唯一索引。MySQL 的唯一索引允许多个 NULL，于是：
--         - 同一商品存在两张未取消订单  -> 唯一键冲突，写不进去
--         - 同一商品有多张已取消订单     -> 全为 NULL，不冲突（用户可以反复下单/取消）
--
-- 生成列对应用完全透明：Order 实体里没有这个字段，MyBatis-Plus 的
-- insert/update 不会碰它，值由 MySQL 自己算。
--
-- !! 执行顺序：必须先跑第 1 步查重，确认结果为空再执行第 2 步。
-- !! 这不是幂等脚本，重复执行会报 "Duplicate column name"（属正常）。
--
-- 执行方式：
--   mysql -uroot -p campus_secondhand < migration-order-unique-active.sql
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 第 1 步：查重。必须返回 0 行，否则先人工处理（每行一张订单，
--         建议只保留最早那张，其余按业务决定取消或删除）。
-- ---------------------------------------------------------------------------
SELECT product_id,
       COUNT(*) AS active_orders,
       GROUP_CONCAT(id ORDER BY id) AS order_ids
FROM orders
WHERE status <> 4          -- 4 = 已取消
GROUP BY product_id
HAVING COUNT(*) > 1;

-- ---------------------------------------------------------------------------
-- 第 2 步：加生成列 + 唯一索引（第 1 步结果为空时才执行）
-- ---------------------------------------------------------------------------
ALTER TABLE orders
    ADD COLUMN active_product_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN status <> 4 THEN product_id ELSE NULL END) STORED
        COMMENT '未取消订单的商品ID；仅供 uk_active_product 使用，业务不读',
    ADD UNIQUE KEY uk_active_product (active_product_id);

-- ---------------------------------------------------------------------------
-- 第 3 步：核对索引确实建上
-- ---------------------------------------------------------------------------
SHOW INDEX FROM orders WHERE Key_name = 'uk_active_product';

-- 回滚（如需）：
--   ALTER TABLE orders DROP INDEX uk_active_product, DROP COLUMN active_product_id;
