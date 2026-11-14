-- ============================================================================
-- 信用分（Credit Score）
--
-- 背景：平台只有「封号」这一种一刀切的处罚，缺乏渐进式约束。信用分把
--       认证、成交、评价、超时、举报串成一条可展示、可解释的激励链路，
--       并为「低分用户限权」提供数据基础。
--
-- 做法：
--   1. users 加 credit_score，初始 100，范围 0~150；
--   2. 新增 credit_log（信用流水），每一次加/扣分都留痕，个人中心可回放。
--
-- uk_user_event_biz 是这里最关键的一行：同一事件（同一用户 + 同一类型 +
-- 同一业务ID）只允许记一次分。应用层的幂等判断永远可能被绕过或将来改错，
-- 数据库这道唯一键才是「分不会被记两遍」的最终保证。
--
-- 为什么用原生 SQL 而不是给 User 实体加字段：实体一旦多一个字段，
-- MyBatis-Plus 的每条 selectById/selectPage 都会把它带进 SQL，迁移没跑
-- 之前登录、订单列表全会抛 Unknown column。理由同 migration-seller-balance.sql。
--
-- !! 第 1 步 ALTER 不幂等：重复执行会报 Duplicate column name，别跑第二遍。
-- !! 代码未接入时执行也没有副作用（CreditService 全程 fail-open）。
--
-- 执行方式：
--   mysql -uroot -p campus_secondhand < migration-credit-score.sql
-- ============================================================================

-- 1) 信用分字段
ALTER TABLE users
    ADD COLUMN credit_score INT NOT NULL DEFAULT 100
        COMMENT '信用分：初始 100，范围 0~150';

-- 2) 信用流水
CREATE TABLE IF NOT EXISTS credit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL                COMMENT '被加/扣分的用户',
    event_type  VARCHAR(30)  NOT NULL                COMMENT '事件类型，见 CreditEvent',
    biz_id      BIGINT       NOT NULL                COMMENT '业务ID：订单ID/举报ID/用户ID',
    delta       INT          NOT NULL                COMMENT '分值变动，可为负',
    remark      VARCHAR(200) DEFAULT NULL            COMMENT '备注',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (id),
    -- 同一事件的同一业务对象只记一次分
    UNIQUE KEY uk_user_event_biz (user_id, event_type, biz_id),
    KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信用分变动流水';

-- 3) 核对
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'credit_score';

SHOW TABLES LIKE 'credit_log';

-- 回滚（如需）：
--   DROP TABLE IF EXISTS credit_log;
--   ALTER TABLE users DROP COLUMN credit_score;
