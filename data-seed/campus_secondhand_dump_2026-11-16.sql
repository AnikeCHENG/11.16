-- MySQL dump 10.13  Distrib 8.0.28, for Win64 (x86_64)
--
-- Host: localhost    Database: campus_secondhand
-- ------------------------------------------------------
-- Server version	8.0.28

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `account_flow`
--

DROP TABLE IF EXISTS `account_flow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `account_flow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '账变用户ID',
  `order_id` bigint DEFAULT NULL COMMENT '关联订单ID',
  `order_no` varchar(50) DEFAULT NULL COMMENT '订单号（冗余便于对账）',
  `direction` varchar(10) NOT NULL COMMENT '方向：IN 收入 / OUT 支出',
  `amount` decimal(10,2) NOT NULL COMMENT '本次变动金额（正数，方向见 direction）',
  `balance_after` decimal(12,2) NOT NULL COMMENT '记账后该用户余额快照',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记账时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_direction` (`order_id`,`direction`),
  KEY `idx_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资金流水（卖家收入/平台服务费等）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `account_flow`
--

LOCK TABLES `account_flow` WRITE;
/*!40000 ALTER TABLE `account_flow` DISABLE KEYS */;
/*!40000 ALTER TABLE `account_flow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `browse_history`
--

DROP TABLE IF EXISTS `browse_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `browse_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `last_view_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近浏览时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`,`product_id`),
  KEY `idx_user_time` (`user_id`,`last_view_time`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='浏览历史表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `browse_history`
--

LOCK TABLES `browse_history` WRITE;
/*!40000 ALTER TABLE `browse_history` DISABLE KEYS */;
INSERT INTO `browse_history` VALUES (1,7,403,'2026-11-15 16:58:35.214'),(2,8,403,'2026-11-15 16:58:35.326'),(3,9,406,'2026-11-15 16:59:40.615'),(4,10,406,'2026-11-15 16:59:40.708'),(5,11,303,'2026-11-15 18:33:47.716'),(6,11,305,'2026-11-16 13:06:53.311'),(7,3,305,'2026-11-16 13:08:03.050'),(8,3,303,'2026-11-16 13:17:15.581');
/*!40000 ALTER TABLE `browse_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cart`
--

DROP TABLE IF EXISTS `cart`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cart` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`,`product_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cart`
--

LOCK TABLES `cart` WRITE;
/*!40000 ALTER TABLE `cart` DISABLE KEYS */;
/*!40000 ALTER TABLE `cart` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `categories`
--

DROP TABLE IF EXISTS `categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `categories` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(50) NOT NULL COMMENT '分类名称',
  `code` varchar(50) NOT NULL COMMENT '分类标识（products.category 存的即此值）',
  `description` varchar(200) DEFAULT NULL COMMENT '分类描述',
  `parent_id` bigint DEFAULT NULL COMMENT '父分类ID',
  `icon` varchar(500) DEFAULT NULL COMMENT '分类图标',
  `sort_order` int DEFAULT '0' COMMENT '排序权重',
  `status` int DEFAULT '1' COMMENT '状态：1启用，0禁用',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_categories_code` (`code`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分类表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `categories`
--

LOCK TABLES `categories` WRITE;
/*!40000 ALTER TABLE `categories` DISABLE KEYS */;
INSERT INTO `categories` VALUES (1,'图书教材','books','二手图书、教材、考试资料',NULL,NULL,1,1,'2026-11-11 22:29:57','2026-11-14 10:49:00'),(2,'数码电子','electronics','手机、电脑、平板、耳机等',NULL,NULL,2,1,'2026-11-11 22:29:57','2026-11-14 10:49:00'),(3,'生活用品','living','日常用品、宿舍用品',NULL,NULL,3,1,'2026-11-11 22:29:57','2026-11-14 10:49:00'),(4,'服饰鞋包','clothing','衣服、鞋子、包包',NULL,NULL,4,1,'2026-11-11 22:29:57','2026-11-14 10:49:00'),(5,'运动户外','sports','运动器材、户外装备',NULL,NULL,5,1,'2026-11-11 22:29:57','2026-11-14 10:49:00'),(6,'美妆护肤','beauty','化妆品、护肤品',NULL,NULL,6,1,'2026-11-11 22:29:57','2026-11-14 10:49:00'),(7,'学习用品','stationery','文具、学习工具',NULL,NULL,7,1,'2026-11-11 22:29:57','2026-11-14 10:49:00'),(8,'其他','other','其他闲置物品',NULL,NULL,8,1,'2026-11-11 22:29:57','2026-11-14 10:49:00'),(9,'游戏数码','gaming','游戏机、手柄及数码配件',NULL,NULL,9,1,'2026-11-14 10:49:00','2026-11-14 10:49:00'),(10,'出行工具','transport','自行车、电动车及出行装备',NULL,NULL,10,1,'2026-11-14 10:49:00','2026-11-14 10:49:00');
/*!40000 ALTER TABLE `categories` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `comments`
--

DROP TABLE IF EXISTS `comments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `comments` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `post_id` bigint NOT NULL COMMENT '动态/商品ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `parent_id` bigint DEFAULT NULL COMMENT '父评论ID',
  `content` text NOT NULL COMMENT '评论内容',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `comments`
--

LOCK TABLES `comments` WRITE;
/*!40000 ALTER TABLE `comments` DISABLE KEYS */;
/*!40000 ALTER TABLE `comments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `credit_log`
--

DROP TABLE IF EXISTS `credit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `credit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '被加/扣分的用户',
  `event_type` varchar(30) NOT NULL COMMENT '事件类型，见 CreditEvent',
  `biz_id` bigint NOT NULL COMMENT '业务ID：订单ID/举报ID/用户ID',
  `delta` int NOT NULL COMMENT '分值变动，可为负',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_event_biz` (`user_id`,`event_type`,`biz_id`),
  KEY `idx_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='信用分变动流水';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `credit_log`
--

LOCK TABLES `credit_log` WRITE;
/*!40000 ALTER TABLE `credit_log` DISABLE KEYS */;
INSERT INTO `credit_log` VALUES (1,5,'STUDENT_VERIFIED',5,10,'完成学生认证','2026-11-13 22:34:55');
/*!40000 ALTER TABLE `credit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `favorites`
--

DROP TABLE IF EXISTS `favorites`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `favorites` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`,`product_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `favorites`
--

LOCK TABLES `favorites` WRITE;
/*!40000 ALTER TABLE `favorites` DISABLE KEYS */;
/*!40000 ALTER TABLE `favorites` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `like_record`
--

DROP TABLE IF EXISTS `like_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `like_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `post_id` bigint NOT NULL COMMENT '动态/商品ID',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_post` (`user_id`,`post_id`),
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点赞记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `like_record`
--

LOCK TABLES `like_record` WRITE;
/*!40000 ALTER TABLE `like_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `like_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `messages`
--

DROP TABLE IF EXISTS `messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `messages` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sender_id` bigint NOT NULL COMMENT '发送者ID',
  `receiver_id` bigint NOT NULL COMMENT '接收者ID',
  `product_id` bigint DEFAULT NULL COMMENT '关联商品ID',
  `content` text COMMENT '消息内容',
  `msg_type` tinyint NOT NULL DEFAULT '0' COMMENT '消息类型：0文本 1图片',
  `caption` varchar(200) DEFAULT NULL COMMENT '图片消息配文',
  `is_read` int DEFAULT '0' COMMENT '是否已读：0未读，1已读',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_sender_id` (`sender_id`),
  KEY `idx_receiver_id` (`receiver_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_is_read` (`is_read`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `messages`
--

LOCK TABLES `messages` WRITE;
/*!40000 ALTER TABLE `messages` DISABLE KEYS */;
INSERT INTO `messages` VALUES (5,11,3,303,'你好，在吗？',0,NULL,1,'2026-11-15 18:33:52'),(6,11,3,305,'你好，在吗？',0,NULL,1,'2026-11-16 13:06:57'),(7,3,11,NULL,'/uploads/2026/11/69c37ed2b51f4289be8e83ddf762f154.png',1,NULL,1,'2026-11-16 13:07:45');
/*!40000 ALTER TABLE `messages` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(50) NOT NULL COMMENT '订单号',
  `buyer_id` bigint NOT NULL COMMENT '买家ID',
  `seller_id` bigint NOT NULL COMMENT '卖家ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `order_title` varchar(100) DEFAULT NULL COMMENT '商品标题快照',
  `order_image` mediumtext COMMENT '商品首图快照(base64)',
  `price` decimal(10,2) NOT NULL COMMENT '成交价',
  `shipping_fee` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '运费(校内自提免运费)',
  `status` int DEFAULT '0' COMMENT '状态：0待支付 1待发货 2待收货 3已完成 4已取消',
  `refund_from_status` int DEFAULT NULL COMMENT '申请退款前的订单状态：1待发货 2待收货',
  `refund_reason` varchar(200) DEFAULT NULL COMMENT '买家退款原因',
  `refund_reply` varchar(200) DEFAULT NULL COMMENT '卖家/管理员处理说明',
  `refund_apply_time` datetime DEFAULT NULL COMMENT '退款申请时间',
  `refund_time` datetime DEFAULT NULL COMMENT '退款完成/驳回时间',
  `refund_amount` decimal(10,2) DEFAULT NULL COMMENT '退款金额：成交价+运费',
  `payment_method` varchar(50) DEFAULT NULL COMMENT '支付方式',
  `transaction_id` varchar(100) DEFAULT NULL COMMENT '交易流水号',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `paid_time` datetime DEFAULT NULL COMMENT '支付时间',
  `shipped_time` datetime DEFAULT NULL COMMENT '卖家发货时间（自动确认收货计时起点）',
  `expire_time` datetime DEFAULT NULL COMMENT '支付截止时间',
  `completed_time` datetime DEFAULT NULL COMMENT '完成时间',
  `service_fee` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '平台服务费(卖家承担)',
  `seller_income` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '卖家应收金额',
  `buyer_hidden` tinyint(1) NOT NULL DEFAULT '0' COMMENT '买家是否从自己的订单列表隐藏：0否 1是（订单行保留）',
  `seller_hidden` tinyint(1) NOT NULL DEFAULT '0' COMMENT '卖家是否从自己的订单列表隐藏：0否 1是（订单行保留）',
  `active_product_id` bigint GENERATED ALWAYS AS ((case when (`status` not in (4,6)) then `product_id` else NULL end)) STORED COMMENT '未取消、未退款订单的商品ID；仅供 uk_active_product 使用，业务不读',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_active_product` (`active_product_id`),
  KEY `idx_buyer_id` (`buyer_id`),
  KEY `idx_seller_id` (`seller_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_status` (`status`),
  KEY `idx_status_shipped` (`status`,`shipped_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `orders`
--

LOCK TABLES `orders` WRITE;
/*!40000 ALTER TABLE `orders` DISABLE KEYS */;
/*!40000 ALTER TABLE `orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `payment_record`
--

DROP TABLE IF EXISTS `payment_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(50) NOT NULL COMMENT '订单编号(冗余便于对账)',
  `amount` decimal(10,2) NOT NULL COMMENT '支付金额(买家实付,不含卖家承担的服务费)',
  `pay_method` varchar(50) NOT NULL COMMENT '支付方式:余额/支付宝/微信',
  `trade_no` varchar(100) NOT NULL COMMENT '渠道流水号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_no` (`trade_no`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付流水表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `payment_record`
--

LOCK TABLES `payment_record` WRITE;
/*!40000 ALTER TABLE `payment_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `payment_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `posts`
--

DROP TABLE IF EXISTS `posts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `posts` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '动态ID',
  `user_id` bigint NOT NULL COMMENT '发布者ID',
  `type` varchar(20) NOT NULL COMMENT '动态类型：SELL出售 SEEK求购 FREE免费送 WARN避雷 CHAT闲聊',
  `content` text NOT NULL COMMENT '动态正文',
  `tags` varchar(255) DEFAULT NULL COMMENT '标签，逗号分隔',
  `images` mediumtext COMMENT '图片，逗号分隔(Base64或URL)',
  `product_id` bigint DEFAULT NULL COMMENT '关联商品ID；仅SELL/FREE携带productInfo时非空',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  PRIMARY KEY (`id`),
  KEY `idx_created` (`created_time`),
  KEY `idx_type_created` (`type`,`created_time`),
  KEY `idx_user_type` (`user_id`,`type`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大厅动态';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `posts`
--

LOCK TABLES `posts` WRITE;
/*!40000 ALTER TABLE `posts` DISABLE KEYS */;
INSERT INTO `posts` VALUES (1,1,'SELL','毕业清仓：九成新自行车，车况良好，附送车锁和打气筒。东校区自提，可先试骑。','自行车,毕业清仓',NULL,1,'2026-11-11 20:29:58'),(2,3,'SEEK','求购 2026 年线性代数真题及解析，任意版本都行，最好带完整答案。考研复试用，图书馆那几本答案太散了。','教材,考研',NULL,NULL,'2026-11-11 17:29:58'),(3,1,'CHAT','毕业季准备清空宿舍，有没有人想一起组个「闲置互换」小组？把用不上的东西换成想要的，比扔掉划算。','毕业季,闲置互换',NULL,NULL,'2026-11-10 22:29:58'),(4,4,'WARN','提醒：这周有人卖二手平板虚标成色，描述写着「全新」实际有明显划痕。平板和耳机这类贵重物品，尽量校内面交当面验机，别只看照片就转账。','校园避雷,面交提醒',NULL,NULL,'2026-11-09 22:29:58'),(5,1,'FREE','搬宿舍剩两盏台灯，灯罩完好、光线柔和，直接送给需要的同学。西校区宿舍楼下自提，数量有限先到先得。','免费送,台灯',NULL,2,'2026-11-08 22:29:58'),(6,6,'SEEK','求购一个罗技鼠标，G102 或同价位都行，主要用来写代码，要求滚轮不飘。预算 80 以内。','数码,鼠标',NULL,NULL,'2026-11-07 22:29:58'),(7,1,'CHAT','想问下大家平时怎么处理毕业季的书籍？我这边教材都太新了，闲置卖掉价格低，送人又不舍得分，有没有人一起打包处理的？','毕业季,教材',NULL,NULL,'2026-11-06 22:29:58'),(8,3,'SELL','出宿舍闲置小台灯，护眼款可调亮度，用了半年没什么磕碰。价格好商量，能自提优先。','台灯,宿舍',NULL,8,'2026-11-05 22:29:58');
/*!40000 ALTER TABLE `posts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `products`
--

DROP TABLE IF EXISTS `products`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `products` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '发布者ID',
  `title` varchar(100) NOT NULL COMMENT '商品标题',
  `description` text COMMENT '商品描述',
  `price` decimal(10,2) NOT NULL COMMENT '价格',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '原价',
  `category` varchar(50) DEFAULT NULL COMMENT '分类名称',
  `condition_level` tinyint NOT NULL DEFAULT '2' COMMENT '成色等级 0全新 1=99新 2=95新 3=9成新 4=8成新及以下',
  `flaw_description` varchar(255) DEFAULT NULL COMMENT '瑕疵描述，如屏幕划痕、电池老化；为空表示无明显瑕疵',
  `images` mediumtext COMMENT '图片(逗号分隔Base64或URL)',
  `view_count` int DEFAULT '0' COMMENT '浏览量',
  `status` int DEFAULT '1' COMMENT '商品状态：0下架，1在售，2已售出',
  `review_status` tinyint NOT NULL DEFAULT '1' COMMENT '0=待审核 1=已通过 2=未通过',
  `review_reason` varchar(255) DEFAULT NULL COMMENT '审核拒绝理由',
  `reviewed_by` bigint DEFAULT NULL COMMENT '审核人（管理员）ID',
  `reviewed_time` datetime DEFAULT NULL COMMENT '审核时间',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `sold_time` datetime DEFAULT NULL COMMENT '售出时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_category` (`category`),
  KEY `idx_status` (`status`),
  KEY `idx_products_status_condition` (`status`,`condition_level`),
  KEY `idx_products_review` (`review_status`,`created_time`)
) ENGINE=InnoDB AUTO_INCREMENT=409 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `products`
--

LOCK TABLES `products` WRITE;
/*!40000 ALTER TABLE `products` DISABLE KEYS */;
INSERT INTO `products` VALUES (201,3,'空白笔记本配签字笔','空白笔记本配签字笔. Condition: brand new, never opened. In-person trade and inspection welcome, price slightly negotiable.',52.84,89.83,'books',0,'brand new, never opened','/uploads/2026/11/a87254555cc54e35a22ba65d252742aa.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(202,3,'课堂笔记本全新未拆封','课堂笔记本全新未拆封. Condition: light signs of use, fully working. In-person trade and inspection welcome, price slightly negotiable.',49.10,73.65,'books',1,'light signs of use, fully working','/uploads/2026/11/a87254555cc54e35a22ba65d252742aa.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(203,3,'考研课堂笔记手写本','考研课堂笔记手写本. Condition: brand new, never opened. In-person trade and inspection welcome, price slightly negotiable.',38.89,42.78,'books',0,'brand new, never opened','/uploads/2026/11/a87254555cc54e35a22ba65d252742aa.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(204,3,'高等数学黑板板书笔记','高等数学黑板板书笔记. Condition: minor cosmetic wear. In-person trade and inspection welcome, price slightly negotiable.',37.58,41.34,'books',2,'minor cosmetic wear','/uploads/2026/11/d86546de134594865f4ce9ccb73ea966.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(205,3,'考研数学真题详细讲解','考研数学真题详细讲解. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',29.23,32.15,'books',4,'fair condition, scratches and marks','/uploads/2026/11/d86546de134594865f4ce9ccb73ea966.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(206,3,'线性代数重点归纳笔记','线性代数重点归纳笔记. Condition: light signs of use, fully working. In-person trade and inspection welcome, price slightly negotiable.',50.36,55.40,'books',1,'light signs of use, fully working','/uploads/2026/11/d86546de134594865f4ce9ccb73ea966.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(207,3,'摊开的专业教材有笔记','摊开的专业教材有笔记. Condition: minor cosmetic wear. In-person trade and inspection welcome, price slightly negotiable.',21.44,40.74,'books',2,'minor cosmetic wear','/uploads/2026/11/08b2cc2b4df833448f8ed4cd3229bbba.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(208,3,'考研英语核心词汇书','考研英语核心词汇书. Condition: light signs of use, fully working. In-person trade and inspection welcome, price slightly negotiable.',41.71,58.39,'books',1,'light signs of use, fully working','/uploads/2026/11/08b2cc2b4df833448f8ed4cd3229bbba.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(209,3,'大学计算机基础教材','大学计算机基础教材. Condition: minor cosmetic wear. In-person trade and inspection welcome, price slightly negotiable.',38.70,46.44,'books',2,'minor cosmetic wear','/uploads/2026/11/08b2cc2b4df833448f8ed4cd3229bbba.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(210,3,'Cybook Opus 电子书阅读器','Cybook Opus 电子书阅读器. Condition: minor cosmetic wear. In-person trade and inspection welcome, price slightly negotiable.',70.89,85.07,'books',2,'minor cosmetic wear','/uploads/2026/11/688e3076b01b557769ee504479e6f081.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(211,3,'二手电纸书阅读器带原装保护套','二手电纸书阅读器带原装保护套. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',122.97,147.56,'books',3,'visible wear, works perfectly','/uploads/2026/11/688e3076b01b557769ee504479e6f081.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(212,3,'墨水屏阅读器 适合看PDF','墨水屏阅读器 适合看PDF. Condition: minor cosmetic wear. In-person trade and inspection welcome, price slightly negotiable.',103.34,175.68,'books',2,'minor cosmetic wear','/uploads/2026/11/688e3076b01b557769ee504479e6f081.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(213,3,'白色智能手机屏幕无划痕','白色智能手机屏幕无划痕. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',314.12,565.42,'electronics',3,'visible wear, works perfectly','/uploads/2026/11/c623af0aead4b59155a30382fc3106b7.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(214,3,'备用机电池健康良好','备用机电池健康良好. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',1650.76,1980.91,'electronics',3,'visible wear, works perfectly','/uploads/2026/11/c623af0aead4b59155a30382fc3106b7.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(215,3,'自用手机带原装保护壳','自用手机带原装保护壳. Condition: light signs of use, fully working. In-person trade and inspection welcome, price slightly negotiable.',1572.98,2674.07,'electronics',1,'light signs of use, fully working','/uploads/2026/11/c623af0aead4b59155a30382fc3106b7.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(216,3,'折叠屏手机 屏幕无折痕','折叠屏手机 屏幕无折痕. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',618.40,742.08,'electronics',4,'fair condition, scratches and marks','/uploads/2026/11/c623af0aead4b59155a30382fc3106b7.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(217,3,'轻薄笔记本电脑性能正常','轻薄笔记本电脑性能正常. Condition: brand new, never opened. In-person trade and inspection welcome, price slightly negotiable.',1189.71,2260.45,'electronics',0,'brand new, never opened','/uploads/2026/11/d2646473548a4ef71c6a5da1979013b2.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(218,3,'上课用的笔记本已升级内存','上课用的笔记本已升级内存. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',851.10,1617.09,'electronics',4,'fair condition, scratches and marks','/uploads/2026/11/d2646473548a4ef71c6a5da1979013b2.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(219,3,'MacBook笔记本循环次数低','MacBook笔记本循环次数低. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',2111.97,4012.74,'electronics',3,'visible wear, works perfectly','/uploads/2026/11/beccfb36755e9da011f7b10376a8f719.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(220,3,'苹果笔记本配件齐全','苹果笔记本配件齐全. Condition: minor cosmetic wear. In-person trade and inspection welcome, price slightly negotiable.',2706.53,4330.45,'electronics',2,'minor cosmetic wear','/uploads/2026/11/beccfb36755e9da011f7b10376a8f719.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(221,3,'头戴式耳机音质完好','头戴式耳机音质完好. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',354.02,389.42,'electronics',4,'fair condition, scratches and marks','/uploads/2026/11/d347e7293df976bbdf4a9b1ac181ada0.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(222,3,'降噪头戴耳机带收纳袋','降噪头戴耳机带收纳袋. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',199.90,379.81,'electronics',4,'fair condition, scratches and marks','/uploads/2026/11/d347e7293df976bbdf4a9b1ac181ada0.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(223,3,'书架音箱一对含接线','书架音箱一对含接线. Condition: light signs of use, fully working. In-person trade and inspection welcome, price slightly negotiable.',390.03,741.06,'electronics',1,'light signs of use, fully working','/uploads/2026/11/97a8d1d53d19b442589f8e670e9296df.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(224,3,'桌面音箱宿舍放歌用','桌面音箱宿舍放歌用. Condition: light signs of use, fully working. In-person trade and inspection welcome, price slightly negotiable.',212.49,339.98,'electronics',1,'light signs of use, fully working','/uploads/2026/11/97a8d1d53d19b442589f8e670e9296df.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(225,3,'蓝牙音箱 续航正常','蓝牙音箱 续航正常. Condition: brand new, never opened. In-person trade and inspection welcome, price slightly negotiable.',526.84,684.89,'electronics',0,'brand new, never opened','/uploads/2026/11/97a8d1d53d19b442589f8e670e9296df.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(226,3,'陶瓷马克杯无缺口','陶瓷马克杯无缺口. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',43.75,48.13,'living',4,'fair condition, scratches and marks','/uploads/2026/11/d31343802cb4947689c6773fa0135bd8.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(227,3,'情侣马克杯送朋友','情侣马克杯送朋友. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',17.82,28.51,'living',3,'visible wear, works perfectly','/uploads/2026/11/d31343802cb4947689c6773fa0135bd8.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(228,3,'香薰蜡烛未点燃','香薰蜡烛未点燃. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',68.71,75.58,'living',3,'visible wear, works perfectly','/uploads/2026/11/43b466221dbc431b80a2418c6d0d759b.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(229,3,'助眠香薰蜡烛礼盒装','助眠香薰蜡烛礼盒装. Condition: brand new, never opened. In-person trade and inspection welcome, price slightly negotiable.',39.46,71.03,'living',0,'brand new, never opened','/uploads/2026/11/43b466221dbc431b80a2418c6d0d759b.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(230,3,'纯棉毛巾三条装','纯棉毛巾三条装. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',33.93,47.50,'living',3,'visible wear, works perfectly','/uploads/2026/11/60179bedd311078af55499f487ec0ba3.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(231,3,'手工皂温和不刺激','手工皂温和不刺激. Condition: brand new, never opened. In-person trade and inspection welcome, price slightly negotiable.',53.00,90.10,'living',0,'brand new, never opened','/uploads/2026/11/44d60f0e28807838a28e2790426ca8bc.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(232,3,'精华液专柜剩余小样','精华液专柜剩余小样. Condition: light signs of use, fully working. In-person trade and inspection welcome, price slightly negotiable.',80.07,144.13,'living',1,'light signs of use, fully working','/uploads/2026/11/9b0acddbe8c40bf280062c02ab99c0b4.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(233,3,'布艺沙发搬家出','布艺沙发搬家出. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',372.05,446.46,'living',3,'visible wear, works perfectly','/uploads/2026/11/b06022d4b5e425689ad79c19d39cdabb.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(234,3,'搬家纸箱五个装','搬家纸箱五个装. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',28.47,34.16,'living',4,'fair condition, scratches and marks','/uploads/2026/11/2d7644518ad81f8742696af339560351.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(235,3,'半自动咖啡机宿舍神器','半自动咖啡机宿舍神器. Condition: brand new, never opened. In-person trade and inspection welcome, price slightly negotiable.',315.97,537.15,'living',0,'brand new, never opened','/uploads/2026/11/13ce162c92c3c15691c8a1aac354cefa.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(236,3,'烘焙咖啡豆整袋未拆','烘焙咖啡豆整袋未拆. Condition: brand new, never opened. In-person trade and inspection welcome, price slightly negotiable.',86.28,112.16,'living',0,'brand new, never opened','/uploads/2026/11/a5b53b3074d0533d73d4398cbf5d4001.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(237,3,'手冲咖啡壶套装','手冲咖啡壶套装. Condition: minor cosmetic wear. In-person trade and inspection welcome, price slightly negotiable.',28.73,48.84,'living',2,'minor cosmetic wear','/uploads/2026/11/d6c85fbad8896f2843c999f912356465.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(238,3,'圣诞礼盒未拆封','圣诞礼盒未拆封. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',89.40,169.86,'living',4,'fair condition, scratches and marks','/uploads/2026/11/60a7b503812369b6b9b35330cc1f9372.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(239,3,'生日礼物包装盒全新','生日礼物包装盒全新. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',87.09,121.93,'living',4,'fair condition, scratches and marks','/uploads/2026/11/60a7b503812369b6b9b35330cc1f9372.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(240,3,'宿舍桌面收纳箱','宿舍桌面收纳箱. Condition: brand new, never opened. In-person trade and inspection welcome, price slightly negotiable.',41.93,79.67,'living',0,'brand new, never opened','/uploads/2026/11/6d58aee9941d2c4166bc9e115ea94b39.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(241,3,'纯棉短袖T恤M码','纯棉短袖T恤M码. Condition: minor cosmetic wear. In-person trade and inspection welcome, price slightly negotiable.',61.10,91.65,'clothing',2,'minor cosmetic wear','/uploads/2026/11/ed890766acdadd3036a74449c1639cbd.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(242,3,'印花短袖全新吊牌在','印花短袖全新吊牌在. Condition: brand new, never opened. In-person trade and inspection welcome, price slightly negotiable.',78.06,109.28,'clothing',0,'brand new, never opened','/uploads/2026/11/ed890766acdadd3036a74449c1639cbd.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(243,3,'白色基础款T恤两件装','白色基础款T恤两件装. Condition: minor cosmetic wear. In-person trade and inspection welcome, price slightly negotiable.',101.71,183.08,'clothing',2,'minor cosmetic wear','/uploads/2026/11/ed890766acdadd3036a74449c1639cbd.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(244,3,'白色运动鞋42码','白色运动鞋42码. Condition: minor cosmetic wear. In-person trade and inspection welcome, price slightly negotiable.',254.98,407.97,'clothing',2,'minor cosmetic wear','/uploads/2026/11/0dc812327743c01c425a9320a628db53.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(245,3,'休闲板鞋穿过两次','休闲板鞋穿过两次. Condition: brand new, never opened. In-person trade and inspection welcome, price slightly negotiable.',206.77,330.83,'clothing',0,'brand new, never opened','/uploads/2026/11/0dc812327743c01c425a9320a628db53.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(246,3,'厚底老爹鞋 尺码偏小','厚底老爹鞋 尺码偏小. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',257.13,282.84,'clothing',4,'fair condition, scratches and marks','/uploads/2026/11/0dc812327743c01c425a9320a628db53.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(247,3,'真皮夹克L码','真皮夹克L码. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',183.05,219.66,'clothing',4,'fair condition, scratches and marks','/uploads/2026/11/3adc8b49893c62ba04980d1c84645029.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(248,3,'黑色皮夹克换季出','黑色皮夹克换季出. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',124.72,162.14,'clothing',3,'visible wear, works perfectly','/uploads/2026/11/3adc8b49893c62ba04980d1c84645029.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(249,3,'牛仔外套 全新','牛仔外套 全新. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',170.12,255.18,'clothing',3,'visible wear, works perfectly','/uploads/2026/11/3adc8b49893c62ba04980d1c84645029.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(250,3,'棉麻连衣裙春秋款','棉麻连衣裙春秋款. Condition: brand new, never opened. In-person trade and inspection welcome, price slightly negotiable.',158.41,190.09,'clothing',0,'brand new, never opened','/uploads/2026/11/188164c94dfa43973d45050eedcf7d26.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(251,3,'碎花连衣裙全新','碎花连衣裙全新. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',155.13,217.18,'clothing',3,'visible wear, works perfectly','/uploads/2026/11/188164c94dfa43973d45050eedcf7d26.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(252,3,'学院风针织连衣裙','学院风针织连衣裙. Condition: light signs of use, fully working. In-person trade and inspection welcome, price slightly negotiable.',221.89,399.40,'clothing',1,'light signs of use, fully working','/uploads/2026/11/188164c94dfa43973d45050eedcf7d26.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(253,3,'简约风衣外套 九成新','简约风衣外套 九成新. Condition: brand new, never opened. In-person trade and inspection welcome, price slightly negotiable.',174.95,244.93,'clothing',0,'brand new, never opened','/uploads/2026/11/188164c94dfa43973d45050eedcf7d26.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(254,3,'山地自行车变速正常','山地自行车变速正常. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',757.07,1438.43,'transport',3,'visible wear, works perfectly','/uploads/2026/11/c148fb09202fb259758cf12c8586d4c2.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(255,3,'通勤自行车带车锁','通勤自行车带车锁. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',834.90,1502.82,'transport',4,'fair condition, scratches and marks','/uploads/2026/11/c148fb09202fb259758cf12c8586d4c2.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(256,3,'折叠自行车宿舍可放','折叠自行车宿舍可放. Condition: light signs of use, fully working. In-person trade and inspection welcome, price slightly negotiable.',226.96,249.66,'transport',1,'light signs of use, fully working','/uploads/2026/11/c148fb09202fb259758cf12c8586d4c2.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(257,3,'公路自行车 弯把款','公路自行车 弯把款. Condition: light signs of use, fully working. In-person trade and inspection welcome, price slightly negotiable.',472.33,614.03,'transport',1,'light signs of use, fully working','/uploads/2026/11/c148fb09202fb259758cf12c8586d4c2.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(258,3,'军用双肩包容量大','军用双肩包容量大. Condition: minor cosmetic wear. In-person trade and inspection welcome, price slightly negotiable.',298.80,418.32,'transport',2,'minor cosmetic wear','/uploads/2026/11/ea3c76cc77e158282b4159e0c7e93a4e.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(259,3,'户外背包徒步用','户外背包徒步用. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',260.17,338.22,'transport',4,'fair condition, scratches and marks','/uploads/2026/11/ea3c76cc77e158282b4159e0c7e93a4e.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(260,3,'电脑背包隔层齐全','电脑背包隔层齐全. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',85.40,153.72,'transport',4,'fair condition, scratches and marks','/uploads/2026/11/ea3c76cc77e158282b4159e0c7e93a4e.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(261,3,'大容量旅行包 防水','大容量旅行包 防水. Condition: minor cosmetic wear. In-person trade and inspection welcome, price slightly negotiable.',165.08,214.60,'transport',2,'minor cosmetic wear','/uploads/2026/11/ea3c76cc77e158282b4159e0c7e93a4e.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(262,3,'婴儿推车折叠收纳','婴儿推车折叠收纳. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',249.21,473.50,'transport',4,'fair condition, scratches and marks','/uploads/2026/11/c47e22111a06f51fe5f77c27e6c1ad3f.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(263,3,'轻便婴儿车可登机','轻便婴儿车可登机. Condition: light signs of use, fully working. In-person trade and inspection welcome, price slightly negotiable.',196.39,314.22,'transport',1,'light signs of use, fully working','/uploads/2026/11/c47e22111a06f51fe5f77c27e6c1ad3f.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(264,3,'医用助行器可调节','医用助行器可调节. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',259.77,311.72,'transport',4,'fair condition, scratches and marks','/uploads/2026/11/4f29bd352c778d501f63fc8a625ddf96.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(265,3,'折叠助行车 送老人','折叠助行车 送老人. Condition: minor cosmetic wear. In-person trade and inspection welcome, price slightly negotiable.',240.82,457.56,'transport',2,'minor cosmetic wear','/uploads/2026/11/4f29bd352c778d501f63fc8a625ddf96.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(266,3,'积木塔拼装玩具缺件','积木塔拼装玩具缺件. Condition: light signs of use, fully working. In-person trade and inspection welcome, price slightly negotiable.',66.53,79.84,'gaming',1,'light signs of use, fully working','/uploads/2026/11/9fde1319fbd4e72969fcd32a8af0c151.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(267,3,'儿童积木套装全新','儿童积木套装全新. Condition: light signs of use, fully working. In-person trade and inspection welcome, price slightly negotiable.',65.28,124.03,'gaming',1,'light signs of use, fully working','/uploads/2026/11/9fde1319fbd4e72969fcd32a8af0c151.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(268,3,'磁力片拼装玩具','磁力片拼装玩具. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',81.34,122.01,'gaming',3,'visible wear, works perfectly','/uploads/2026/11/9fde1319fbd4e72969fcd32a8af0c151.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(269,3,'木质小火车带轨道','木质小火车带轨道. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',161.24,177.36,'gaming',3,'visible wear, works perfectly','/uploads/2026/11/779b72f46f9d8a44dfdba4172fa9e0fe.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(270,3,'儿童轨道火车玩具','儿童轨道火车玩具. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',110.95,177.52,'gaming',3,'visible wear, works perfectly','/uploads/2026/11/779b72f46f9d8a44dfdba4172fa9e0fe.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(271,3,'遥控赛车 电池全新','遥控赛车 电池全新. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',154.60,247.36,'gaming',3,'visible wear, works perfectly','/uploads/2026/11/779b72f46f9d8a44dfdba4172fa9e0fe.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(272,3,'木吉他民谣款带琴包','木吉他民谣款带琴包. Condition: brand new, never opened. In-person trade and inspection welcome, price slightly negotiable.',219.37,329.06,'gaming',0,'brand new, never opened','/uploads/2026/11/cfcc4108c5038e7aa3d8d5d2ffeae0be.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(273,3,'吉他初学入门款','吉他初学入门款. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',337.88,574.40,'gaming',4,'fair condition, scratches and marks','/uploads/2026/11/cfcc4108c5038e7aa3d8d5d2ffeae0be.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(274,3,'哑铃一对可调重量','哑铃一对可调重量. Condition: brand new, never opened. In-person trade and inspection welcome, price slightly negotiable.',85.27,136.43,'gaming',0,'brand new, never opened','/uploads/2026/11/52a98cf968ca4ffb6c523d7758e32ba1.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(275,3,'宿舍健身小哑铃','宿舍健身小哑铃. Condition: light signs of use, fully working. In-person trade and inspection welcome, price slightly negotiable.',158.99,174.89,'gaming',1,'light signs of use, fully working','/uploads/2026/11/52a98cf968ca4ffb6c523d7758e32ba1.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(276,3,'弹力带健身套装','弹力带健身套装. Condition: minor cosmetic wear. In-person trade and inspection welcome, price slightly negotiable.',95.80,172.44,'gaming',2,'minor cosmetic wear','/uploads/2026/11/52a98cf968ca4ffb6c523d7758e32ba1.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(277,3,'加厚瑜伽垫防滑','加厚瑜伽垫防滑. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',116.92,187.07,'gaming',4,'fair condition, scratches and marks','/uploads/2026/11/24f2001210760f46a6e55f90d914cf8d.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(278,3,'便携瑜伽垫带背带','便携瑜伽垫带背带. Condition: light signs of use, fully working. In-person trade and inspection welcome, price slightly negotiable.',115.28,219.03,'gaming',1,'light signs of use, fully working','/uploads/2026/11/24f2001210760f46a6e55f90d914cf8d.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(279,3,'福特福克斯二手代售','福特福克斯二手代售. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',413.38,661.41,'transport',4,'fair condition, scratches and marks','/uploads/2026/11/c91cfabb3b369529d04927296cf0300d.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(280,3,'家用两厢轿车有保养记录','家用两厢轿车有保养记录. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',340.75,647.42,'transport',4,'fair condition, scratches and marks','/uploads/2026/11/c91cfabb3b369529d04927296cf0300d.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(281,3,'家用冲击钻带钻头','家用冲击钻带钻头. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',204.29,265.58,'living',4,'fair condition, scratches and marks','/uploads/2026/11/c59fbb524fef41e5c54654c11653190d.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(282,3,'电动打孔器装修用','电动打孔器装修用. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',215.35,366.10,'living',4,'fair condition, scratches and marks','/uploads/2026/11/c59fbb524fef41e5c54654c11653190d.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(283,3,'手动工具套装 齐全','手动工具套装 齐全. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',132.80,225.76,'living',4,'fair condition, scratches and marks','/uploads/2026/11/c59fbb524fef41e5c54654c11653190d.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(284,3,'绿篱修剪机园艺工具','绿篱修剪机园艺工具. Condition: minor cosmetic wear. In-person trade and inspection welcome, price slightly negotiable.',188.44,339.19,'living',2,'minor cosmetic wear','/uploads/2026/11/d43e121db63c9319edf15cb2fb436058.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(285,3,'电动修枝剪家用','电动修枝剪家用. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',153.42,230.13,'living',4,'fair condition, scratches and marks','/uploads/2026/11/d43e121db63c9319edf15cb2fb436058.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(286,3,'车载吸尘器 闲置出','车载吸尘器 闲置出. Condition: light signs of use, fully working. In-person trade and inspection welcome, price slightly negotiable.',148.07,251.72,'living',1,'light signs of use, fully working','/uploads/2026/11/d43e121db63c9319edf15cb2fb436058.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(287,3,'洗车高压水枪 家用','洗车高压水枪 家用. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',230.89,415.60,'living',3,'visible wear, works perfectly','/uploads/2026/11/d43e121db63c9319edf15cb2fb436058.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(288,3,'实木工作台带工具','实木工作台带工具. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',237.97,261.77,'living',4,'fair condition, scratches and marks','/uploads/2026/11/378dd0b256289a7bb47d97b2fb2235ec.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(289,3,'折叠工作台DIY用','折叠工作台DIY用. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',117.07,199.02,'living',3,'visible wear, works perfectly','/uploads/2026/11/378dd0b256289a7bb47d97b2fb2235ec.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(290,3,'车库置物架 闲置出','车库置物架 闲置出. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',243.62,267.98,'living',3,'visible wear, works perfectly','/uploads/2026/11/378dd0b256289a7bb47d97b2fb2235ec.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(291,3,'拉布拉多幼犬找领养','拉布拉多幼犬找领养. Condition: minor cosmetic wear. In-person trade and inspection welcome, price slightly negotiable.',12.44,17.42,'other',2,'minor cosmetic wear','/uploads/2026/11/51ef9a40c7cd59b1d1d027445f9ccceb.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(292,3,'金毛犬三个月有疫苗本','金毛犬三个月有疫苗本. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',10.66,11.73,'other',4,'fair condition, scratches and marks','/uploads/2026/11/51ef9a40c7cd59b1d1d027445f9ccceb.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(293,3,'橘猫幼崽 免费送','橘猫幼崽 免费送. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',2.87,4.30,'other',4,'fair condition, scratches and marks','/uploads/2026/11/51ef9a40c7cd59b1d1d027445f9ccceb.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(294,3,'旧课桌两张 免费送','旧课桌两张 免费送. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',1.97,2.17,'other',4,'fair condition, scratches and marks','/uploads/2026/11/51ef9a40c7cd59b1d1d027445f9ccceb.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(295,3,'猫爬架送猫友','猫爬架送猫友. Condition: brand new, never opened. In-person trade and inspection welcome, price slightly negotiable.',4.31,7.33,'other',0,'brand new, never opened','/uploads/2026/11/976ca23d2f156705726b8f504f734513.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(296,3,'实木猫爬架自提','实木猫爬架自提. Condition: visible wear, works perfectly. In-person trade and inspection welcome, price slightly negotiable.',25.75,38.62,'other',3,'visible wear, works perfectly','/uploads/2026/11/976ca23d2f156705726b8f504f734513.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(297,3,'猫砂盆 全新未拆','猫砂盆 全新未拆. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',2.01,3.42,'other',4,'fair condition, scratches and marks','/uploads/2026/11/976ca23d2f156705726b8f504f734513.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(298,3,'独栋住宅低价急出','独栋住宅低价急出. Condition: minor cosmetic wear. In-person trade and inspection welcome, price slightly negotiable.',34.88,48.83,'other',2,'minor cosmetic wear','/uploads/2026/11/97e076e8f4e0cf1509bb109432855f71.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(299,3,'郊区小产权房诚意转让','郊区小产权房诚意转让. Condition: fair condition, scratches and marks. In-person trade and inspection welcome, price slightly negotiable.',3.98,6.37,'other',4,'fair condition, scratches and marks','/uploads/2026/11/97e076e8f4e0cf1509bb109432855f71.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(300,3,'空房间带家具 免费住','空房间带家具 免费住. Condition: light signs of use, fully working. In-person trade and inspection welcome, price slightly negotiable.',39.75,47.70,'other',1,'light signs of use, fully working','/uploads/2026/11/97e076e8f4e0cf1509bb109432855f71.webp',0,1,1,NULL,NULL,NULL,'2026-11-12 09:04:24','2026-11-12 09:04:24',NULL),(303,3,'时令食谱电子书全新','时令食谱电子书全新。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',43.24,64.86,'books',2,'有明显外观磨损 功能正常','/uploads/2026/11/3bbbee53e71d4d58970b7c3c0bc3a2a0.jpg',3,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(304,3,'记账本带明细页','记账本带明细页。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',28.64,40.10,'books',2,'有明显外观磨损 功能正常','/uploads/2026/11/a87254555cc54e35a22ba65d252742aa.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(305,3,'高等数学上下册教材','高等数学上下册教材。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',42.63,46.89,'books',1,'轻微使用痕迹 功能完好','/uploads/2026/11/d86546de134594865f4ce9ccb73ea966.webp',2,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(306,3,'编译原理课程讲义','编译原理课程讲义。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',18.78,24.41,'books',4,'成色一般 有划痕与磕碰','/uploads/2026/11/08b2cc2b4df833448f8ed4cd3229bbba.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(307,3,'Kindle 电子书阅读器备用','Kindle 电子书阅读器备用。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',27.17,43.47,'books',1,'轻微使用痕迹 功能完好','/uploads/2026/11/688e3076b01b557769ee504479e6f081.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(308,3,'编织数据线两米装','编织数据线两米装。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',1805.69,3430.81,'electronics',2,'有明显外观磨损 功能正常','/uploads/2026/11/8e77fd440c084df78c7a566b3863d62a.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(309,3,'黑色磨砂手机壳九成新','黑色磨砂手机壳九成新。全新未拆封。支持当面验货自提，价格可小刀诚心出。',2783.67,4732.24,'electronics',0,'全新未拆封','/uploads/2026/11/a813de410ab84bea8871923c1c9b3ab1.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(310,3,'淡紫色软壳换机出','淡紫色软壳换机出。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',2522.86,5045.72,'electronics',2,'有明显外观磨损 功能正常','/uploads/2026/11/36890b9163fd46bdbfdfb9501fb5a9af.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(311,3,'天蓝色手机壳全新','天蓝色手机壳全新。全新未拆封。支持当面验货自提，价格可小刀诚心出。',2543.03,4831.76,'electronics',0,'全新未拆封','/uploads/2026/11/dfeb495b5914443596e5887ea1f06ab7.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(312,3,'云白色无线耳机电量足','云白色无线耳机电量足。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',2521.07,3277.39,'electronics',4,'成色一般 有划痕与磕碰','/uploads/2026/11/eff2f6bceff745b39435dbd5c9d6809b.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(313,3,'黑色无线耳机功能正常','黑色无线耳机功能正常。全新未拆封。支持当面验货自提，价格可小刀诚心出。',2045.97,3273.55,'electronics',0,'全新未拆封','/uploads/2026/11/bf406a058c7540beb66201017a063fe8.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(314,3,'藏蓝无线耳机续航久','藏蓝无线耳机续航久。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',2005.34,3208.54,'electronics',4,'成色一般 有划痕与磕碰','/uploads/2026/11/3bc0e6228996489ab0324fc2e4ead7e5.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(315,3,'二手手机成色良好带原装线','二手手机成色良好带原装线。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',1400.01,1680.01,'electronics',2,'有明显外观磨损 功能正常','/uploads/2026/11/c623af0aead4b59155a30382fc3106b7.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(316,3,'游戏本 显卡性能在线','游戏本 显卡性能在线。使用痕迹较多 性能完好。支持当面验货自提，价格可小刀诚心出。',1298.69,1948.04,'electronics',3,'使用痕迹较多 性能完好','/uploads/2026/11/d2646473548a4ef71c6a5da1979013b2.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(317,3,'学习用笔记本 电池健康','学习用笔记本 电池健康。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',887.73,1420.37,'electronics',1,'轻微使用痕迹 功能完好','/uploads/2026/11/beccfb36755e9da011f7b10376a8f719.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(318,3,'入耳式耳机 带充电盒','入耳式耳机 带充电盒。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',1910.45,2674.63,'electronics',2,'有明显外观磨损 功能正常','/uploads/2026/11/d347e7293df976bbdf4a9b1ac181ada0.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(319,3,'便携蓝牙音箱 防水','便携蓝牙音箱 防水。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',699.95,1329.90,'electronics',1,'轻微使用痕迹 功能完好','/uploads/2026/11/97a8d1d53d19b442589f8e670e9296df.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(320,3,'帆布托特包大容量','帆布托特包大容量。使用痕迹较多 性能完好。支持当面验货自提，价格可小刀诚心出。',222.88,267.46,'transport',3,'使用痕迹较多 性能完好','/uploads/2026/11/4864de7c7e9b40b5aff73db28ecb7198.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(321,3,'黑色卷口双肩包','黑色卷口双肩包。全新未拆封。支持当面验货自提，价格可小刀诚心出。',132.38,158.86,'transport',0,'全新未拆封','/uploads/2026/11/2b67941d1538406885a41af03ddfe0a5.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(322,3,'橄榄绿卷口背包','橄榄绿卷口背包。全新未拆封。支持当面验货自提，价格可小刀诚心出。',73.54,139.73,'transport',0,'全新未拆封','/uploads/2026/11/c80c0a0701e149bca5b3718c821c1e8d.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(323,3,'铁锈红卷口背包','铁锈红卷口背包。全新未拆封。支持当面验货自提，价格可小刀诚心出。',51.37,97.60,'transport',0,'全新未拆封','/uploads/2026/11/d00d3c0edc08455cb0b304f0d9a7c3ec.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(324,3,'山地车 骑行代步','山地车 骑行代步。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',56.35,90.16,'transport',4,'成色一般 有划痕与磕碰','/uploads/2026/11/c148fb09202fb259758cf12c8586d4c2.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(325,3,'双肩包 带电脑隔层','双肩包 带电脑隔层。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',272.11,353.74,'transport',4,'成色一般 有划痕与磕碰','/uploads/2026/11/ea3c76cc77e158282b4159e0c7e93a4e.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(326,3,'折叠推车 出行方便','折叠推车 出行方便。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',58.32,64.15,'transport',4,'成色一般 有划痕与磕碰','/uploads/2026/11/c47e22111a06f51fe5f77c27e6c1ad3f.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(327,3,'轻便助行器 调节灵活','轻便助行器 调节灵活。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',238.41,381.46,'transport',1,'轻微使用痕迹 功能完好','/uploads/2026/11/4f29bd352c778d501f63fc8a625ddf96.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(328,3,'小型两厢车 手续齐全','小型两厢车 手续齐全。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',162.99,228.19,'transport',1,'轻微使用痕迹 功能完好','/uploads/2026/11/c91cfabb3b369529d04927296cf0300d.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(329,3,'软木瑜伽垫全新','软木瑜伽垫全新。全新未拆封。支持当面验货自提，价格可小刀诚心出。',40.35,44.39,'gaming',0,'全新未拆封','/uploads/2026/11/b483914235524d5f9033399639810604.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(330,3,'积木 拼装小套装','积木 拼装小套装。使用痕迹较多 性能完好。支持当面验货自提，价格可小刀诚心出。',150.19,270.34,'gaming',3,'使用痕迹较多 性能完好','/uploads/2026/11/9fde1319fbd4e72969fcd32a8af0c151.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(331,3,'木质小火车 带轨道','木质小火车 带轨道。全新未拆封。支持当面验货自提，价格可小刀诚心出。',204.89,368.80,'gaming',0,'全新未拆封','/uploads/2026/11/779b72f46f9d8a44dfdba4172fa9e0fe.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(332,3,'尤克里里 入门款','尤克里里 入门款。使用痕迹较多 性能完好。支持当面验货自提，价格可小刀诚心出。',198.23,317.17,'gaming',3,'使用痕迹较多 性能完好','/uploads/2026/11/cfcc4108c5038e7aa3d8d5d2ffeae0be.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(333,3,'哑铃 家用可调节','哑铃 家用可调节。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',194.03,252.24,'gaming',2,'有明显外观磨损 功能正常','/uploads/2026/11/52a98cf968ca4ffb6c523d7758e32ba1.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(334,3,'加厚防滑瑜伽垫','加厚防滑瑜伽垫。全新未拆封。支持当面验货自提，价格可小刀诚心出。',218.59,349.74,'gaming',0,'全新未拆封','/uploads/2026/11/24f2001210760f46a6e55f90d914cf8d.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(335,3,'黑色圆领短袖纯棉','黑色圆领短袖纯棉。全新未拆封。支持当面验货自提，价格可小刀诚心出。',283.94,567.88,'clothing',0,'全新未拆封','/uploads/2026/11/fb06b550c31a4f3395c6e70fa063a38f.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(336,3,'墨绿圆领短袖','墨绿圆领短袖。使用痕迹较多 性能完好。支持当面验货自提，价格可小刀诚心出。',250.91,351.27,'clothing',3,'使用痕迹较多 性能完好','/uploads/2026/11/5f6eef0db5914adf8e89856f26f972cb.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(337,3,'麻灰圆领短袖','麻灰圆领短袖。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',216.80,368.56,'clothing',2,'有明显外观磨损 功能正常','/uploads/2026/11/fb7515216b4149469b7ad695a1299208.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(338,3,'藏青圆领短袖','藏青圆领短袖。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',253.92,330.10,'clothing',2,'有明显外观磨损 功能正常','/uploads/2026/11/af3393e4ce884e0b9824d0c075131921.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(339,3,'白色圆领短袖全新','白色圆领短袖全新。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',306.94,429.72,'clothing',2,'有明显外观磨损 功能正常','/uploads/2026/11/325d126b936949bb814ecd674929a449.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(340,3,'炭灰连帽卫衣全新','炭灰连帽卫衣全新。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',170.76,239.06,'clothing',2,'有明显外观磨损 功能正常','/uploads/2026/11/3b29e1b6e9a4495db54a8911401af508.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(341,3,'藕粉连帽卫衣','藕粉连帽卫衣。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',338.02,574.63,'clothing',4,'成色一般 有划痕与磕碰','/uploads/2026/11/6d869f95e0fa425c8a98c1718b2d5cf7.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(342,3,'燕麦色连帽卫衣','燕麦色连帽卫衣。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',248.88,298.66,'clothing',1,'轻微使用痕迹 功能完好','/uploads/2026/11/54de39c1e4124d81aed169542a8278d2.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(343,3,'亚麻长袖衬衫全新','亚麻长袖衬衫全新。使用痕迹较多 性能完好。支持当面验货自提，价格可小刀诚心出。',166.84,317.00,'clothing',3,'使用痕迹较多 性能完好','/uploads/2026/11/922282592ad247cab2064cd44abfbea2.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(344,3,'衬衫做工好九成新','衬衫做工好九成新。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',40.09,60.14,'clothing',4,'成色一般 有划痕与磕碰','/uploads/2026/11/369c3a06ade4468c903c3291a9d5d9c5.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(345,3,'美利奴羊毛袜五双装','美利奴羊毛袜五双装。全新未拆封。支持当面验货自提，价格可小刀诚心出。',354.24,637.63,'clothing',0,'全新未拆封','/uploads/2026/11/bdadcaaa076c4d4a9e09491e5c5c017d.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(346,3,'黑色罗纹针织帽','黑色罗纹针织帽。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',363.05,726.10,'clothing',1,'轻微使用痕迹 功能完好','/uploads/2026/11/63d00048bc8f4ef0b24d24c863be3ff5.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(347,3,'酒红色针织帽','酒红色针织帽。使用痕迹较多 性能完好。支持当面验货自提，价格可小刀诚心出。',73.33,124.66,'clothing',3,'使用痕迹较多 性能完好','/uploads/2026/11/c94724dab7374c6f8ffa6a00d45d3bbc.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(348,3,'姜黄色罗纹帽','姜黄色罗纹帽。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',279.02,306.92,'clothing',4,'成色一般 有划痕与磕碰','/uploads/2026/11/cd144ade76c54c43b56b458d227bf18c.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(349,3,'灰绿色针织帽','灰绿色针织帽。使用痕迹较多 性能完好。支持当面验货自提，价格可小刀诚心出。',240.51,336.71,'clothing',3,'使用痕迹较多 性能完好','/uploads/2026/11/95d6438bf6454c969eb21988f11594db.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(350,3,'透明框圆框墨镜','透明框圆框墨镜。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',366.86,403.55,'clothing',2,'有明显外观磨损 功能正常','/uploads/2026/11/f88d5158b6694cf8bde73f0ebe8f1045.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(351,3,'黑框圆框墨镜','黑框圆框墨镜。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',113.72,216.07,'clothing',2,'有明显外观磨损 功能正常','/uploads/2026/11/8eeafbead2274c1685ad7705c16e4add.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(352,3,'玳瑁色圆框墨镜','玳瑁色圆框墨镜。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',238.93,406.18,'clothing',1,'轻微使用痕迹 功能完好','/uploads/2026/11/89f7e86d6a2c42d585ebe8a47640df4a.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(353,3,'黑色一字拖全新','黑色一字拖全新。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',94.05,178.70,'clothing',2,'有明显外观磨损 功能正常','/uploads/2026/11/17b3a52fe9ff49b8b90cdad7e9b9eefe.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(354,3,'米色一字拖','米色一字拖。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',233.54,303.60,'clothing',1,'轻微使用痕迹 功能完好','/uploads/2026/11/7b3f1d91c20545178202195ee40e35c4.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(355,3,'黑色缓震跑鞋42码','黑色缓震跑鞋42码。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',236.55,283.86,'clothing',4,'成色一般 有划痕与磕碰','/uploads/2026/11/220af8bbd2c8467881916ef5fb0a0641.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(356,3,'珊瑚粉跑鞋女款','珊瑚粉跑鞋女款。使用痕迹较多 性能完好。支持当面验货自提，价格可小刀诚心出。',332.02,664.04,'clothing',3,'使用痕迹较多 性能完好','/uploads/2026/11/1c4df82edc9d4b5f9e523c4af020019a.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(357,3,'灰蓝跑鞋41码','灰蓝跑鞋41码。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',157.14,267.14,'clothing',2,'有明显外观磨损 功能正常','/uploads/2026/11/2d4c32d07e33404a9bbad730564e4473.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(358,3,'印花短袖 全新吊牌','印花短袖 全新吊牌。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',284.02,426.03,'clothing',2,'有明显外观磨损 功能正常','/uploads/2026/11/ed890766acdadd3036a74449c1639cbd.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(359,3,'休闲运动鞋 九成新','休闲运动鞋 九成新。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',347.48,660.21,'clothing',1,'轻微使用痕迹 功能完好','/uploads/2026/11/0dc812327743c01c425a9320a628db53.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(360,3,'牛仔夹克 换季出','牛仔夹克 换季出。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',198.75,337.88,'clothing',1,'轻微使用痕迹 功能完好','/uploads/2026/11/3adc8b49893c62ba04980d1c84645029.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(361,3,'碎花连衣裙 春夏款','碎花连衣裙 春夏款。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',191.01,210.11,'clothing',4,'成色一般 有划痕与磕碰','/uploads/2026/11/188164c94dfa43973d45050eedcf7d26.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(362,3,'西式早餐餐具套装','西式早餐餐具套装。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',188.23,376.46,'living',2,'有明显外观磨损 功能正常','/uploads/2026/11/509c18fa81494f4f8c8053ddd08547b0.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(363,3,'法压壶替换盖全新','法压壶替换盖全新。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',224.80,382.16,'living',1,'轻微使用痕迹 功能完好','/uploads/2026/11/a84d0433a2a94d4c80a4db2ef69b4f28.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(364,3,'玻璃法压咖啡壶','玻璃法压咖啡壶。全新未拆封。支持当面验货自提，价格可小刀诚心出。',150.19,270.34,'living',0,'全新未拆封','/uploads/2026/11/454f3187fd8f491e94d7ef74be6d6efb.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(365,3,'法压壶壶身细节完好','法压壶壶身细节完好。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',25.92,28.51,'living',4,'成色一般 有划痕与磕碰','/uploads/2026/11/1a34cce25560423b97812094e1af1784.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(366,3,'手工陶瓷花瓶','手工陶瓷花瓶。全新未拆封。支持当面验货自提，价格可小刀诚心出。',236.55,354.83,'living',0,'全新未拆封','/uploads/2026/11/16cc9226b7a643e2906d2cda711cf383.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(367,3,'中深烘焙咖啡豆整袋','中深烘焙咖啡豆整袋。全新未拆封。支持当面验货自提，价格可小刀诚心出。',87.96,140.74,'living',0,'全新未拆封','/uploads/2026/11/5a631f4b558e4d1296f3feb21a21bd1c.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(368,3,'保湿精华液剩小样','保湿精华液剩小样。全新未拆封。支持当面验货自提，价格可小刀诚心出。',53.83,64.60,'living',0,'全新未拆封','/uploads/2026/11/1e68e9637efb4a969ff2a0d30f0ed53d.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(369,3,'炭灰棉麻抱枕套','炭灰棉麻抱枕套。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',70.18,112.29,'living',4,'成色一般 有划痕与磕碰','/uploads/2026/11/fef1b62e02c04203840c8118ace0584f.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(370,3,'陶土色抱枕套','陶土色抱枕套。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',153.36,168.70,'living',1,'轻微使用痕迹 功能完好','/uploads/2026/11/59dc1a999fb4456da06bc02bd376da12.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(371,3,'象牙白抱枕套全新','象牙白抱枕套全新。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',226.33,384.76,'living',4,'成色一般 有划痕与磕碰','/uploads/2026/11/7953aaac23fb42dc80d30515fdd4451d.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(372,3,'润唇膏三支装','润唇膏三支装。全新未拆封。支持当面验货自提，价格可小刀诚心出。',90.26,117.34,'living',0,'全新未拆封','/uploads/2026/11/f9d0fb95395c4f5a9b535cc53588ce41.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(373,3,'散装茶试用装多种','散装茶试用装多种。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',116.44,209.59,'living',4,'成色一般 有划痕与磕碰','/uploads/2026/11/1eb6772c6f9f4dd084cce563bc306b3d.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(374,3,'橄榄木砧板全新','橄榄木砧板全新。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',94.60,104.06,'living',1,'轻微使用痕迹 功能完好','/uploads/2026/11/345d5cc9b85a419f92cd1566cf46fcbf.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(375,3,'大豆蜡香薰蜡烛','大豆蜡香薰蜡烛。使用痕迹较多 性能完好。支持当面验货自提，价格可小刀诚心出。',221.27,442.54,'living',3,'使用痕迹较多 性能完好','/uploads/2026/11/7121e99d4f5841e9bb922e3f0867e82f.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(376,3,'深蓝陶质马克杯','深蓝陶质马克杯。使用痕迹较多 性能完好。支持当面验货自提，价格可小刀诚心出。',174.41,209.29,'living',3,'使用痕迹较多 性能完好','/uploads/2026/11/f2955f86b11a475b96144487bc275e66.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(377,3,'灰绿陶杯九成新','灰绿陶杯九成新。全新未拆封。支持当面验货自提，价格可小刀诚心出。',141.67,283.34,'living',0,'全新未拆封','/uploads/2026/11/0d63f21bd768465cb4886dd6923ac3d0.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(378,3,'斑点白陶杯全新','斑点白陶杯全新。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',231.89,463.78,'living',4,'成色一般 有划痕与磕碰','/uploads/2026/11/10c7e6d8dbb64ca0add68dcb0b0c98bd.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(379,3,'陶土色手工杯','陶土色手工杯。使用痕迹较多 性能完好。支持当面验货自提，价格可小刀诚心出。',192.53,385.06,'living',3,'使用痕迹较多 性能完好','/uploads/2026/11/90be27b5569f403892c82ce5fbaf09ae.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(380,3,'陶瓷杯 无缺口','陶瓷杯 无缺口。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',120.58,229.10,'living',1,'轻微使用痕迹 功能完好','/uploads/2026/11/d31343802cb4947689c6773fa0135bd8.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(381,3,'香薰蜡烛 未点燃','香薰蜡烛 未点燃。全新未拆封。支持当面验货自提，价格可小刀诚心出。',223.06,356.90,'living',0,'全新未拆封','/uploads/2026/11/43b466221dbc431b80a2418c6d0d759b.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(382,3,'纯棉毛巾 两条装','纯棉毛巾 两条装。全新未拆封。支持当面验货自提，价格可小刀诚心出。',184.72,221.66,'living',0,'全新未拆封','/uploads/2026/11/60179bedd311078af55499f487ec0ba3.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(383,3,'温和洁面 全新','温和洁面 全新。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',100.54,110.59,'living',2,'有明显外观磨损 功能正常','/uploads/2026/11/44d60f0e28807838a28e2790426ca8bc.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(384,3,'精华小样 分装','精华小样 分装。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',135.40,243.72,'living',4,'成色一般 有划痕与磕碰','/uploads/2026/11/9b0acddbe8c40bf280062c02ab99c0b4.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(385,3,'桌面收纳盒 整齐','桌面收纳盒 整齐。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',16.91,33.82,'living',4,'成色一般 有划痕与磕碰','/uploads/2026/11/b06022d4b5e425689ad79c19d39cdabb.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(386,3,'搬家纸箱 四个装','搬家纸箱 四个装。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',165.32,314.11,'living',4,'成色一般 有划痕与磕碰','/uploads/2026/11/2d7644518ad81f8742696af339560351.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(387,3,'半自动咖啡机 宿舍用','半自动咖啡机 宿舍用。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',118.78,178.17,'living',1,'轻微使用痕迹 功能完好','/uploads/2026/11/13ce162c92c3c15691c8a1aac354cefa.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(388,3,'烘焙咖啡豆整袋新烘焙','烘焙咖啡豆整袋未拆。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',99.40,139.16,'living',4,'成色一般 有划痕与磕碰','/uploads/2026/11/a5b53b3074d0533d73d4398cbf5d4001.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:22:29',NULL),(389,3,'咖啡拉花器具一套','咖啡拉花器具一套。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',62.05,74.46,'living',4,'成色一般 有划痕与磕碰','/uploads/2026/11/d6c85fbad8896f2843c999f912356465.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(390,3,'咖啡兑换券 转让','咖啡兑换券 转让。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',171.21,205.45,'living',4,'成色一般 有划痕与磕碰','/uploads/2026/11/60a7b503812369b6b9b35330cc1f9372.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(391,3,'周末市集摊位物料','周末市集摊位物料。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',66.36,119.45,'living',4,'成色一般 有划痕与磕碰','/uploads/2026/11/6d58aee9941d2c4166bc9e115ea94b39.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(392,3,'家用冲击钻带钻头套装','家用冲击钻带钻头。全新未拆封。支持当面验货自提，价格可小刀诚心出。',208.21,374.78,'living',0,'全新未拆封','/uploads/2026/11/c59fbb524fef41e5c54654c11653190d.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:22:29',NULL),(393,3,'园艺修枝工具一套','园艺修枝工具一套。使用痕迹较多 性能完好。支持当面验货自提，价格可小刀诚心出。',180.08,216.10,'living',3,'使用痕迹较多 性能完好','/uploads/2026/11/d43e121db63c9319edf15cb2fb436058.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(394,3,'实木工作台 带工具','实木工作台 带工具。全新未拆封。支持当面验货自提，价格可小刀诚心出。',173.67,329.97,'living',0,'全新未拆封','/uploads/2026/11/378dd0b256289a7bb47d97b2fb2235ec.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(395,3,'珐琅徽章一套八枚','珐琅徽章一套八枚。全新未拆封。支持当面验货自提，价格可小刀诚心出。',23.12,36.99,'other',0,'全新未拆封','/uploads/2026/11/5bd11a0a30174b95bf2dd61ab763eaed.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(396,3,'限量版画装裱好','限量版画装裱好。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',66.82,100.23,'other',2,'有明显外观磨损 功能正常','/uploads/2026/11/1785c67e6f7b404796fd330b229348f3.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(397,3,'礼品包装服务可预约','礼品包装服务可预约。成色一般 有划痕与磕碰。支持当面验货自提，价格可小刀诚心出。',75.97,151.94,'other',4,'成色一般 有划痕与磕碰','/uploads/2026/11/6cc7b1b8308d41eca7ee65f8938a99d5.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(398,3,'深棕真皮钱包无磨损','深棕真皮钱包无磨损。轻微使用痕迹 功能完好。支持当面验货自提，价格可小刀诚心出。',211.15,295.61,'other',1,'轻微使用痕迹 功能完好','/uploads/2026/11/fc04c599a6774c739587d9da1b5da6c2.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(399,3,'浅棕卡包多卡位','浅棕卡包多卡位。全新未拆封。支持当面验货自提，价格可小刀诚心出。',210.62,252.74,'other',0,'全新未拆封','/uploads/2026/11/05cf6275d4874f478ae8bad73108da98.jpg',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(400,3,'金毛犬找领养 有疫苗本','金毛犬找领养 有疫苗本。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',97.00,145.50,'other',2,'有明显外观磨损 功能正常','/uploads/2026/11/51ef9a40c7cd59b1d1d027445f9ccceb.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:21:27',NULL),(401,3,'实木猫爬架带猫窝自提','实木猫爬架自提。使用痕迹较多 性能完好。支持当面验货自提，价格可小刀诚心出。',232.42,255.66,'other',3,'使用痕迹较多 性能完好','/uploads/2026/11/976ca23d2f156705726b8f504f734513.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:22:29',NULL),(402,3,'独栋住宅带院子低价急出','独栋住宅低价急出。有明显外观磨损 功能正常。支持当面验货自提，价格可小刀诚心出。',131.15,144.27,'other',2,'有明显外观磨损 功能正常','/uploads/2026/11/97e076e8f4e0cf1509bb109432855f71.webp',0,1,1,NULL,NULL,NULL,'2026-11-13 00:21:27','2026-11-13 00:22:30',NULL);
/*!40000 ALTER TABLE `products` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `report`
--

DROP TABLE IF EXISTS `report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '举报ID',
  `reporter_id` bigint NOT NULL COMMENT '举报人ID',
  `target_id` bigint NOT NULL COMMENT '被举报对象ID',
  `target_type` varchar(10) NOT NULL COMMENT '被举报类型：PRODUCT商品 USER用户',
  `reason` varchar(100) NOT NULL COMMENT '举报原因，如虚假宣传/违禁品/假冒伪劣/辱骂',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '处理状态：0待处理 1已处理并处罚 2已驳回',
  `admin_remark` varchar(500) DEFAULT NULL COMMENT '管理员处理备注，内含处罚动作留痕快照',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '举报时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_create` (`status`,`create_time`),
  KEY `idx_reporter_target` (`reporter_id`,`target_type`,`target_id`,`create_time`),
  KEY `idx_target_type` (`target_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户举报';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `report`
--

LOCK TABLES `report` WRITE;
/*!40000 ALTER TABLE `report` DISABLE KEYS */;
/*!40000 ALTER TABLE `report` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `review`
--

DROP TABLE IF EXISTS `review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `review` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `order_id` bigint NOT NULL COMMENT '订单ID，一单一评',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `reviewer_id` bigint NOT NULL COMMENT '评价人（买家）',
  `target_id` bigint NOT NULL COMMENT '被评价人（卖家）',
  `rating` tinyint NOT NULL COMMENT '评分1-5',
  `content` varchar(255) DEFAULT NULL COMMENT '评价内容，50字以内',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_target_id` (`target_id`),
  KEY `idx_reviewer_id` (`reviewer_id`),
  KEY `idx_review_target_rating` (`target_id`,`rating`),
  CONSTRAINT `fk_review_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单评价';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `review`
--

LOCK TABLES `review` WRITE;
/*!40000 ALTER TABLE `review` DISABLE KEYS */;
/*!40000 ALTER TABLE `review` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `share_record`
--

DROP TABLE IF EXISTS `share_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `share_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `post_id` bigint NOT NULL COMMENT '动态/商品ID',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '分享时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分享记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `share_record`
--

LOCK TABLES `share_record` WRITE;
/*!40000 ALTER TABLE `share_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `share_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `student_verification`
--

DROP TABLE IF EXISTS `student_verification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_verification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `user_id` bigint NOT NULL COMMENT '申请人ID，关联 users.id',
  `method` varchar(50) NOT NULL DEFAULT 'alipay_student_screenshot' COMMENT '认证方式，当前仅支付宝截图辅助认证',
  `real_name` varchar(50) NOT NULL COMMENT '真实姓名，列表页展示时脱敏',
  `school` varchar(100) NOT NULL COMMENT '学校名称',
  `student_no` varchar(20) DEFAULT NULL COMMENT '学号，选填；列表页展示时脱敏',
  `enrollment_year` smallint NOT NULL COMMENT '入学年份，如 2023',
  `department` varchar(100) DEFAULT NULL COMMENT '学院或专业，选填',
  `proof_image` varchar(255) NOT NULL COMMENT '支付宝学生认证结果截图路径，形如 /uploads/2026/11/xxx.jpg',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注说明，选填',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending待审核 approved审核通过 rejected审核拒绝 expired已过期',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '审核拒绝理由，用户端可见',
  `reviewed_by` bigint DEFAULT NULL COMMENT '审核管理员ID，关联 users.id',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `reviewed_at` datetime DEFAULT NULL COMMENT '审核时间',
  `expires_at` datetime DEFAULT NULL COMMENT '认证有效期截止时间，审核通过时写入',
  PRIMARY KEY (`id`),
  KEY `idx_user_created` (`user_id`,`created_at`),
  KEY `idx_status_created` (`status`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生认证申请（支付宝学生认证截图辅助认证）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student_verification`
--

LOCK TABLES `student_verification` WRITE;
/*!40000 ALTER TABLE `student_verification` DISABLE KEYS */;
INSERT INTO `student_verification` VALUES (13,28,'alipay_student_screenshot','示例学生','示例大学','202600000001',2024,'软件工程','12149fe003854d9ca244dd3682d3d9a8.png',NULL,'pending',NULL,NULL,'2026-11-15 17:46:19',NULL,NULL);
/*!40000 ALTER TABLE `student_verification` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `password` varchar(255) NOT NULL COMMENT '密码',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `avatar` mediumtext COMMENT '头像(Base64 Data URL)',
  `bio` varchar(255) DEFAULT NULL COMMENT '个人简介',
  `location` varchar(100) DEFAULT NULL COMMENT '所在位置',
  `qq` varchar(20) DEFAULT NULL COMMENT '联系QQ',
  `wechat` varchar(50) DEFAULT NULL COMMENT '联系微信',
  `status` int DEFAULT '1' COMMENT '状态：1正常，0禁用',
  `role` int DEFAULT '0' COMMENT '角色：0普通用户，1管理员',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int DEFAULT '0' COMMENT '逻辑删除：0未删除，1已删除',
  `balance` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '账户余额（元）：确认收货后由 seller_income 计入',
  `credit_score` int NOT NULL DEFAULT '100' COMMENT '信用分：初始 100，范围 0~150',
  `is_student_verified` int DEFAULT '0',
  `student_no` varchar(20) DEFAULT NULL COMMENT '学号',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名，仅认证时填写',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  UNIQUE KEY `uk_student_no` (`student_no`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'smoke_user2','smoke2@test.local','$2a$10$h9HpkjTxqng0YFhDpZ6kjO9Gjnw5wWoDTCHAotN0Ms4ShgF4yKgPO',NULL,NULL,NULL,NULL,NULL,NULL,1,1,'2026-11-11 23:26:06','2026-11-12 08:23:13',0,0.00,100,0,NULL,NULL),(3,'admin','admin@campus.local','$2a$10$IOmaAQdEnA8OlsrE28r5LOq7EgJ.8ucn.WJhD4xJvDQ66vZwtklyq',NULL,NULL,NULL,NULL,NULL,NULL,1,1,'2026-11-12 08:28:40','2026-11-13 22:30:42',0,0.00,100,0,NULL,NULL),(11,'admin1','admin1@campus.local','$2a$10$7qY6PzFz8y07wyCJwiFjqefdw9B7uABXvZVq/bEdKNS7D2CtiNir.',NULL,NULL,NULL,NULL,NULL,NULL,1,1,'2026-11-15 17:11:03','2026-11-15 17:11:10',0,0.00,100,0,NULL,NULL),(28,'demo_student','demo_student@campus.local','$2a$10$h.zi8HgP9QNZOJq.XlU7V.ghUtdBImA5GRDHEQgu4nYv9brNycGVe',NULL,NULL,NULL,NULL,NULL,NULL,1,0,'2026-11-15 17:46:03','2026-11-15 17:46:03',0,0.00,100,0,NULL,NULL);
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-16 17:10:39
