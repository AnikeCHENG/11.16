-- ============================================================================
-- 订单「各自隐藏」标记（P1-3）
--
-- 背景：DELETE /api/orders/{id} 是硬删除——申请 P0 修复后已限制为
--       「仅已完成/已取消」可删，但删除仍然不可逆：买家清理自己的订单列表
--       会把卖家的交易凭证一起抹掉（反之亦然）。订单既是对账依据，
--       也是纠纷时唯一的证据，不该因为一方想清爽列表就消失。
--
-- 做法：加买家/卖家两个隐藏标记，用户"删除"只置自己的标记，
--       订单行永久保留；报表与对账仍能查到全量。
--
-- !! 本文件是纯增量、可空、带默认值的安全变更：
--    代码尚未接入这两列（当前无任何 Java 代码读写 buyer_hidden/seller_hidden），
--    执行后不会改变任何现有行为；订单删除仍是硬删除（仅限已完成/已取消/已退款）。
--    保留此迁移是为后续实现「各自隐藏」预留，执行与否都不影响现有功能。
--
-- 执行方式：
--   mysql -uroot -p campus_secondhand < migration-order-hide-flags.sql
-- ============================================================================

ALTER TABLE orders
    ADD COLUMN buyer_hidden TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '买家是否从自己的订单列表隐藏：0否 1是（订单行保留）',
    ADD COLUMN seller_hidden TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '卖家是否从自己的订单列表隐藏：0否 1是（订单行保留）';

-- 核对
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'orders'
  AND COLUMN_NAME IN ('buyer_hidden', 'seller_hidden');

-- 回滚（如需）：
--   ALTER TABLE orders DROP COLUMN buyer_hidden, DROP COLUMN seller_hidden;
