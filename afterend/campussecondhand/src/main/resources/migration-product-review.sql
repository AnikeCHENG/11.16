-- ============================================================================
-- 商品人工审核：products 增加审核状态与审核留痕
--
-- 设计说明：
--   * status（0下架 / 1在售 / 2已售出）语义保持不变，审核是独立维度。
--   * 新发布商品由 ProductService#create 写为 review_status=0 + status=0（下架），
--     复用既有的「下架商品不进大厅/购物车/下单」逻辑，读取路径无需改动。
--   * review_status=1（已通过）+ status=1（在售）才对外可见。
--   * 存量商品默认 1（已通过），保证上线后不会突然全部消失。
--
-- 注意：本文件不会被 spring.sql.init 自动加载，需要手工执行（一次即可）。
--   mysql -uroot -p --default-character-set=utf8mb4 campus_secondhand < migration-product-review.sql
-- ============================================================================

ALTER TABLE products
    ADD COLUMN review_status TINYINT NOT NULL DEFAULT 1
        COMMENT '0=待审核 1=已通过 2=未通过' AFTER status,
    ADD COLUMN review_reason VARCHAR(255) NULL
        COMMENT '审核拒绝理由' AFTER review_status,
    ADD COLUMN reviewed_by BIGINT NULL
        COMMENT '审核人（管理员）ID' AFTER review_reason,
    ADD COLUMN reviewed_time DATETIME NULL
        COMMENT '审核时间' AFTER reviewed_by,
    ADD INDEX idx_products_review (review_status, created_time);

-- 存量商品一律视为已通过（列的 DEFAULT 已保证，这里显式兜底一次）
UPDATE products SET review_status = 1 WHERE review_status IS NULL;

-- 核对：应只有 1
SELECT review_status, COUNT(*) AS cnt FROM products GROUP BY review_status;
