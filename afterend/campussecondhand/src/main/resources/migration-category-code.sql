-- ============================================================================
-- 分类管理打通：categories 增加 code（英文标识），与 products.category 对齐
--
-- 背景（修复前）：
--   * categories 表存中文名，只被后台分类页读写；
--   * products.category 存的是 books / electronics 这类英文 slug；
--   * 后台按 name 统计商品数 → 永远 0；
--   * 用户端发布/筛选用的是前端硬编码数组，所以后台增删改分类完全没效果。
--
-- 修复思路：
--   * categories 增加 code，商品继续存 slug（零迁移存量商品）；
--   * 后台列表按 code 统计商品数；改名不影响商品归属；
--   * 用户端发布下拉、筛选 chips 改为读取 /api/categories。
--
-- 注意：本文件不会被 spring.sql.init 自动加载，需要手工执行（一次即可）。
--   mysql -uroot -p --default-character-set=utf8mb4 campus_secondhand < migration-category-code.sql
-- ============================================================================

ALTER TABLE categories
    ADD COLUMN code VARCHAR(50) NULL COMMENT '分类标识（products.category 存的即此值）' AFTER name;

-- 1. 按现有中文名回填标识（同时兼容本项目历史用过的几种叫法）
UPDATE categories SET code = CASE name
    WHEN '图书教材' THEN 'books'
    WHEN '教材书籍' THEN 'books'
    WHEN '数码电子' THEN 'electronics'
    WHEN '电子产品' THEN 'electronics'
    WHEN '生活用品' THEN 'living'
    WHEN '服饰鞋包' THEN 'clothing'
    WHEN '服饰鞋帽' THEN 'clothing'
    WHEN '运动户外' THEN 'sports'
    WHEN '美妆护肤' THEN 'beauty'
    WHEN '美妆个护' THEN 'beauty'
    WHEN '学习用品' THEN 'stationery'
    WHEN '其他' THEN 'other'
END
WHERE code IS NULL;

-- 2. 商品里已在使用、但分类表缺失的两个 slug，补建分类记录
INSERT INTO categories (name, code, description, sort_order, status, created_time, updated_time)
SELECT '游戏数码', 'gaming', '游戏机、手柄及数码配件', 9, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE code = 'gaming');

INSERT INTO categories (name, code, description, sort_order, status, created_time, updated_time)
SELECT '出行工具', 'transport', '自行车、电动车及出行装备', 10, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE code = 'transport');

-- 3. 兜底：仍未匹配到标识的历史/自定义分类，生成 cat_<id>，避免 NOT NULL 失败
UPDATE categories SET code = CONCAT('cat_', id) WHERE code IS NULL OR code = '';

-- 4. 收紧约束：标识必填且唯一
ALTER TABLE categories
    MODIFY code VARCHAR(50) NOT NULL COMMENT '分类标识（products.category 存的即此值）',
    ADD UNIQUE KEY uk_categories_code (code);

-- 核对：每个分类的真实商品数（此前显示为 0 的根因即按 name 统计）
SELECT c.id, c.code, c.name, c.status,
       (SELECT COUNT(*) FROM products p WHERE p.category = c.code) AS product_count
FROM categories c
ORDER BY c.sort_order, c.id;
