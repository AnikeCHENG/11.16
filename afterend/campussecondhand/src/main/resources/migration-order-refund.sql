-- ============================================================================
-- 退款/售后 + 自动确认收货（批次 B）
--
-- 背景：
--   1. 此前已支付订单没有任何退款出口：OrderStatus 只有 0-4，
--      cancelOrder 只允许「待支付」，买家付完钱一旦反悔就无路可走；
--      首页却写着「7 天无理由退换」，属于文案承诺与功能不一致。
--   2. 卖家发货后只能等买家手动确认收货；买家不点，钱永远不进卖家账户。
--
-- 本迁移做三件事：
--   1. orders 增加发货时间与退款快照字段；
--   2. 新增「退款中(5) / 已退款(6)」两个状态所需的字段（状态值本身由枚举控制）；
--   3. 重建 uk_active_product：已退款订单必须释放商品，否则唯一索引会把
--      商品永久锁死（生成列原表达式是 status <> 4，把 6 也算作活跃订单）。
--
-- 退款范围：仅「确认收货前」（状态 1/2）。确认收货时卖家才入账，
-- 因此退款不需要从卖家余额扣回；完成后订单不进入退款流程。
--
-- !! 非幂等脚本，请勿重复执行（项目尚未引入 Flyway）
-- !! 执行前请先备份 orders / users / account_flow / payment_record
-- 执行方式：
--   mysql -uroot -p campus_secondhand < migration-order-refund.sql
-- ============================================================================

-- 1) 发货时间与退款字段
--    shipped_time 用于「发货后 N 天自动确认收货」的扫描口径
--    refund_from_status 记录申请前状态（1/2），拒绝退款时按原状态回退
--    不能固定回退到 1 或 2，否则待收货的订单会被误改回待发货
ALTER TABLE orders
    ADD COLUMN shipped_time DATETIME NULL COMMENT '卖家发货时间（自动确认收货计时起点）' AFTER paid_time,
    ADD COLUMN refund_from_status INT NULL COMMENT '申请退款前的订单状态：1待发货 2待收货' AFTER status,
    ADD COLUMN refund_reason VARCHAR(200) NULL COMMENT '买家退款原因' AFTER refund_from_status,
    ADD COLUMN refund_reply VARCHAR(200) NULL COMMENT '卖家/管理员处理说明' AFTER refund_reason,
    ADD COLUMN refund_apply_time DATETIME NULL COMMENT '退款申请时间' AFTER refund_reply,
    ADD COLUMN refund_time DATETIME NULL COMMENT '退款完成/驳回时间' AFTER refund_apply_time,
    ADD COLUMN refund_amount DECIMAL(10,2) NULL COMMENT '退款金额：成交价+运费' AFTER refund_time,
    ADD KEY idx_status_shipped (status, shipped_time);

-- 2) 历史「待收货」订单补发货时间：没有该值会导致自动确认任务永远跳过它们
--    用 updated_time 兜底，因为发货是这类订单最后一次状态变更
UPDATE orders
SET shipped_time = COALESCE(paid_time, updated_time, created_time, NOW())
WHERE status = 2
  AND shipped_time IS NULL;

-- 3) 重建活跃订单唯一约束：已退款(6)不再占用商品
--    先删索引再删列（生成列被索引引用，顺序不能反）
ALTER TABLE orders
    DROP INDEX uk_active_product,
    DROP COLUMN active_product_id;

ALTER TABLE orders
    ADD COLUMN active_product_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN status NOT IN (4, 6) THEN product_id ELSE NULL END) STORED
        COMMENT '未取消、未退款订单的商品ID；仅供 uk_active_product 使用，业务不读',
    ADD UNIQUE KEY uk_active_product (active_product_id);

-- 4) 核对
SHOW COLUMNS FROM orders
WHERE Field IN ('shipped_time', 'refund_from_status', 'refund_reason', 'refund_reply',
                'refund_apply_time', 'refund_time', 'refund_amount', 'active_product_id');

SHOW INDEX FROM orders WHERE Key_name IN ('uk_active_product', 'idx_status_shipped');

-- 回滚（如需）：
--   ALTER TABLE orders DROP INDEX uk_active_product, DROP COLUMN active_product_id;
--   ALTER TABLE orders
--       ADD COLUMN active_product_id BIGINT
--           GENERATED ALWAYS AS (CASE WHEN status <> 4 THEN product_id ELSE NULL END) STORED,
--       ADD UNIQUE KEY uk_active_product (active_product_id);
--   ALTER TABLE orders
--       DROP INDEX idx_status_shipped,
--       DROP COLUMN shipped_time, DROP COLUMN refund_from_status,
--       DROP COLUMN refund_reason, DROP COLUMN refund_reply,
--       DROP COLUMN refund_apply_time, DROP COLUMN refund_time,
--       DROP COLUMN refund_amount;
