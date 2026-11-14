-- ============================================================================
-- 学生认证申请表：支付宝学生认证截图辅助认证
--
-- 背景：users 表只有 is_student_verified / student_no / real_name 三个"结果"字段，
-- 认证是即交即过的模拟校验，没有申请记录，也没有待审核状态。本表为
-- 「支付宝学生认证截图」这一辅助认证方式提供申请 → 人工审核的留痕，
-- 不改动 users 表的任何字段，原有学号认证链路完全不受影响。
--
-- 状态用字符串而非 TINYINT：pending / approved / rejected / expired 需要原样
-- 暴露给前端与后台，字符串可直接对照需求文档，避免前后端各记一套映射。
--
-- 注意：与 report / review 一致，本表没有逻辑删除列 deleted；
-- MyBatis-Plus 的逻辑删除配置只作用于带 deleted 字段的实体。
-- 建表语句幂等（IF NOT EXISTS），但重复执行无意义——表已存在时不会补列。
-- ============================================================================

CREATE TABLE IF NOT EXISTS student_verification (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '申请ID',
    user_id BIGINT NOT NULL COMMENT '申请人ID，关联 users.id',
    method VARCHAR(50) NOT NULL DEFAULT 'alipay_student_screenshot' COMMENT '认证方式，当前仅支付宝截图辅助认证',
    real_name VARCHAR(50) NOT NULL COMMENT '真实姓名，列表页展示时脱敏',
    school VARCHAR(100) NOT NULL COMMENT '学校名称',
    student_no VARCHAR(20) DEFAULT NULL COMMENT '学号，选填；列表页展示时脱敏',
    enrollment_year SMALLINT NOT NULL COMMENT '入学年份，如 2023',
    department VARCHAR(100) DEFAULT NULL COMMENT '学院或专业，选填',
    proof_image VARCHAR(255) NOT NULL COMMENT '支付宝学生认证结果截图路径，形如 /uploads/2026/11/xxx.jpg',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注说明，选填',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending待审核 approved审核通过 rejected审核拒绝 expired已过期',
    reject_reason VARCHAR(500) DEFAULT NULL COMMENT '审核拒绝理由，用户端可见',
    reviewed_by BIGINT DEFAULT NULL COMMENT '审核管理员ID，关联 users.id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    reviewed_at DATETIME DEFAULT NULL COMMENT '审核时间',
    expires_at DATETIME DEFAULT NULL COMMENT '认证有效期截止时间，审核通过时写入',
    PRIMARY KEY (id),

    -- 用户端「我的最新一条申请」按 user_id + 时间倒序查
    KEY idx_user_created (user_id, created_at),
    -- 管理端默认按 status=pending + 提交时间正序处理
    KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生认证申请（支付宝学生认证截图辅助认证）';
