-- ============================================================================
-- 卖家余额与资金流水（P1-10）
--
-- 背景：orders 里有 seller_income（卖家应收）与 service_fee（平台服务费），
--       但确认收货只把订单状态改成「已完成」，钱从来没有真正"到账"——
--       既没有余额字段，也没有流水表，答辩时只能算"模拟担保"。
--
-- 做法：
--   1. users 加 balance，确认收货时在同一事务里把 seller_income 计入；
--   2. 新增 account_flow（资金流水），每次记账都留痕，便于对账与展示。
--
-- uk_order_direction 是这里最关键的一行：同一张订单的同一方向只允许记一次账。
-- 确认收货虽然已是条件 UPDATE（重复请求只会成功一次），但数据库这道唯一键
-- 才是"钱不会被记两遍"的最终保证——应用层判断永远可能被绕过或将来改错。
--
-- !! 本文件是纯增量、带默认值的安全变更：代码尚未接入时执行也没有副作用。
--
-- 执行方式：
--   mysql -uroot -p campus_secondhand < migration-seller-balance.sql
-- ============================================================================

-- 1) 卖家余额。DECIMAL(12,2) 与金额字段口径一致，不用 FLOAT/DOUBLE。
ALTER TABLE users
    ADD COLUMN balance DECIMAL(12,2) NOT NULL DEFAULT 0.00
        COMMENT '账户余额（元）：确认收货后由 seller_income 计入';

-- 2) 资金流水
CREATE TABLE IF NOT EXISTS account_flow (
    id            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT         NOT NULL                COMMENT '账变用户ID',
    order_id      BIGINT         DEFAULT NULL            COMMENT '关联订单ID',
    order_no      VARCHAR(50)    DEFAULT NULL            COMMENT '订单号（冗余便于对账）',
    direction     VARCHAR(10)    NOT NULL                COMMENT '方向：IN 收入 / OUT 支出',
    amount        DECIMAL(10,2)  NOT NULL                COMMENT '本次变动金额（正数，方向见 direction）',
    balance_after DECIMAL(12,2)  NOT NULL                COMMENT '记账后该用户余额快照',
    remark        VARCHAR(200)   DEFAULT NULL            COMMENT '备注',
    create_time   DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '记账时间',
    PRIMARY KEY (id),
    -- 一单一行一方向：重复确认收货不会记第二笔账
    UNIQUE KEY uk_order_direction (order_id, direction),
    KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金流水（卖家收入/平台服务费等）';

-- 3) 核对
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'balance';

SHOW TABLES LIKE 'account_flow';

-- 回滚（如需）：
--   DROP TABLE IF EXISTS account_flow;
--   ALTER TABLE users DROP COLUMN balance;
