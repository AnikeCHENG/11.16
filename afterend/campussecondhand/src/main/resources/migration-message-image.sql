-- ===========================================================================
-- 聊天图片消息（migration-message-image.sql）
--
-- messages 新增：
--   msg_type  消息类型：0=文本（默认），1=图片
--   caption   图片消息的配文（选填，最长 200 字，业务层校验）
--
-- 存量消息自动归为文本（NOT NULL DEFAULT 0），读取路径无需条件处理。
-- 图片消息的 content 字段复用为图片路径（/uploads/yyyy/MM/<32位hex>.<ext>），
-- 格式校验在 MessageService#send 中完成，不新增列。
-- ===========================================================================
ALTER TABLE messages
    ADD COLUMN msg_type TINYINT NOT NULL DEFAULT 0 COMMENT '消息类型：0文本 1图片' AFTER content;

ALTER TABLE messages
    ADD COLUMN caption VARCHAR(200) DEFAULT NULL COMMENT '图片消息配文' AFTER msg_type;
