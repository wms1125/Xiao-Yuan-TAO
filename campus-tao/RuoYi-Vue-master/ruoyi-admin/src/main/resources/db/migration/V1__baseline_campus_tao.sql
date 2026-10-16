-- ============================================================
-- 校园淘 V1 基线（Flyway）
-- 来源：sql/campus_tao_full.sql（v1.0.15 合并快照，mysqldump 产出）等价转化
-- 转化动作：仅移除脚本内的 CREATE DATABASE / USE（目标库由连接串决定）。
-- 历史手工增量 v1.0.5 / v1.0.7 / v1.0.15 的内容已合并进本基线；
-- 版本化增量自 V2 起由 Flyway 接管。
-- 回滚脚本：db/rollback/U1__baseline_campus_tao.sql
-- ============================================================

-- MySQL dump 10.13  Distrib 8.0.40, for Win64 (x86_64)
--
-- Host: localhost    Database: campus_tao
-- ------------------------------------------------------
-- Server version	8.0.40

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
-- Current Database: `campus_tao`
--



--
-- Table structure for table `campus_account`
--

DROP TABLE IF EXISTS `campus_account`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campus_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '账户ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `user_name` varchar(50) DEFAULT '' COMMENT '用户名',
  `balance` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '可用余额',
  `freeze` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '冻结金额(支付中)',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付沙箱账户';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `campus_account`
--

LOCK TABLES `campus_account` WRITE;
/*!40000 ALTER TABLE `campus_account` DISABLE KEYS */;
INSERT INTO `campus_account` VALUES (1,1,'admin',1000.00,0.00,'2026-10-15 09:30:00'),(2,2,'ry',500.00,0.00,'2026-10-15 09:30:00'),(3,3,'liyutong',860.00,0.00,'2026-10-15 09:30:00'),(4,4,'wanghao',420.00,0.00,'2026-10-15 09:30:00'),(5,5,'zhaomin',1150.00,0.00,'2026-10-15 09:30:00'),(6,6,'chenxinyi',300.00,0.00,'2026-10-15 09:30:00'),(7,7,'zhoukai',680.00,0.00,'2026-10-15 09:30:00'),(8,8,'liuzhiyuan',95.00,0.00,'2026-10-15 09:30:00');
/*!40000 ALTER TABLE `campus_account` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `campus_aftersale`
--

DROP TABLE IF EXISTS `campus_aftersale`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campus_aftersale` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '售后ID',
  `aftersale_no` varchar(32) NOT NULL COMMENT '售后单号',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `user_id` bigint NOT NULL COMMENT '申请人ID',
  `user_name` varchar(50) DEFAULT '' COMMENT '申请人',
  `type` tinyint NOT NULL DEFAULT '1' COMMENT '类型 1仅退款 2退货退款',
  `reason` varchar(255) DEFAULT '' COMMENT '原因',
  `refund_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '退款金额',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态 0待审核 1通过 2拒绝 3已完成',
  `handler` varchar(50) DEFAULT '' COMMENT '处理人',
  `handle_remark` varchar(255) DEFAULT '' COMMENT '处理备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='售后';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `campus_aftersale`
--

LOCK TABLES `campus_aftersale` WRITE;
/*!40000 ALTER TABLE `campus_aftersale` DISABLE KEYS */;
INSERT INTO `campus_aftersale` VALUES (1,'AS202610091005001',22,'CT202610052015001',8,'liuzhiyuan',2,'耳机左声道有轻微杂音，与商品描述不符，申请退货退款。',950.00,0,'','','2026-10-09 10:05:00',NULL),(2,'AS202609111000002',23,'CT202609081433001',4,'wanghao',2,'吹风机风力比描述偏小，宿舍用不太够，申请退货退款。',129.00,3,'admin','已核实买家提交的视频，同意退货退款；商品不影响二次销售，退款已退回沙箱余额。','2026-09-11 10:00:00','2026-09-11 09:30:00'),(3,'AS202610061000003',24,'CT202610031830001',7,'zhoukai',1,'阅读器屏幕右下角有轻微亮点，申请仅退款（商品留用）。',380.00,3,'admin','亮点情况属实，经与买卖双方协商按仅退款处理，商品由买家留用。','2026-10-06 10:00:00','2026-10-06 10:15:00'),(4,'AS202610011000004',6,'CT202609271830001',7,'zhoukai',2,'收到后发现边框有一处磕碰，申请退货退款。',1650.00,2,'admin','经核实磕碰为快递运输造成，已协调卖家补偿差价，本次退货退款申请不予受理。','2026-10-01 10:00:00','2026-10-01 09:20:00');
/*!40000 ALTER TABLE `campus_aftersale` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `campus_cart`
--

DROP TABLE IF EXISTS `campus_cart`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campus_cart` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '购物车ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `user_name` varchar(50) DEFAULT '' COMMENT '用户名',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '数量',
  `checked` tinyint NOT NULL DEFAULT '1' COMMENT '是否勾选(0否1是)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `campus_cart`
--

LOCK TABLES `campus_cart` WRITE;
/*!40000 ALTER TABLE `campus_cart` DISABLE KEYS */;
INSERT INTO `campus_cart` VALUES (1,2,'ry',1,1,1,'2026-10-14 20:10:00','2026-10-14 20:10:00'),(2,2,'ry',5,1,1,'2026-10-15 08:20:00','2026-10-15 08:20:00'),(3,3,'liyutong',12,1,1,'2026-10-13 19:05:00','2026-10-13 19:05:00'),(4,3,'liyutong',46,1,1,'2026-10-13 19:08:00','2026-10-13 19:08:00'),(5,4,'wanghao',30,1,1,'2026-10-12 12:30:00','2026-10-12 12:30:00'),(6,4,'wanghao',43,1,0,'2026-10-12 12:32:00','2026-10-12 12:32:00'),(7,5,'zhaomin',45,1,1,'2026-10-14 21:15:00','2026-10-14 21:15:00'),(8,6,'chenxinyi',39,1,1,'2026-10-14 22:40:00','2026-10-14 22:40:00'),(9,6,'chenxinyi',40,1,1,'2026-10-14 22:41:00','2026-10-14 22:41:00'),(10,7,'zhoukai',29,1,0,'2026-10-11 10:05:00','2026-10-11 10:05:00'),(11,8,'liuzhiyuan',49,1,1,'2026-10-15 09:10:00','2026-10-15 09:10:00'),(12,8,'liuzhiyuan',27,1,1,'2026-10-15 09:12:00','2026-10-15 09:12:00');
/*!40000 ALTER TABLE `campus_cart` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `campus_category`
--

DROP TABLE IF EXISTS `campus_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campus_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `name` varchar(50) NOT NULL COMMENT '分类名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父分类ID(0=一级)',
  `sort` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品分类';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `campus_category`
--

LOCK TABLES `campus_category` WRITE;
/*!40000 ALTER TABLE `campus_category` DISABLE KEYS */;
INSERT INTO `campus_category` VALUES (1,'教材书籍',0,1,'2026-08-20 09:00:00','2026-08-20 09:00:00'),(2,'考研考证',0,2,'2026-08-20 09:00:00','2026-08-20 09:00:00'),(3,'数码电子',0,3,'2026-08-20 09:00:00','2026-08-20 09:00:00'),(4,'宿舍电器',0,4,'2026-08-20 09:00:00','2026-08-20 09:00:00'),(5,'运动户外',0,5,'2026-08-20 09:00:00','2026-08-20 09:00:00'),(6,'代步工具',0,6,'2026-08-20 09:00:00','2026-08-20 09:00:00'),(7,'乐器文娱',0,7,'2026-08-20 09:00:00','2026-08-20 09:00:00'),(8,'生活百货',0,8,'2026-08-20 09:00:00','2026-08-20 09:00:00'),(9,'美妆个护',0,9,'2026-08-20 09:00:00','2026-08-20 09:00:00'),(10,'票券卡类',0,10,'2026-08-20 09:00:00','2026-08-20 09:00:00');
/*!40000 ALTER TABLE `campus_category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `campus_logistics_trace`
--

DROP TABLE IF EXISTS `campus_logistics_trace`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campus_logistics_trace` (
  `trace_id` bigint NOT NULL AUTO_INCREMENT COMMENT '轨迹ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `logistics_no` varchar(64) NOT NULL DEFAULT '' COMMENT '物流单号',
  `carrier` varchar(32) DEFAULT '' COMMENT '承运商编码',
  `status_label` varchar(50) DEFAULT '' COMMENT '轨迹状态标签',
  `context` varchar(255) DEFAULT '' COMMENT '轨迹描述',
  `trace_time` datetime DEFAULT NULL COMMENT '轨迹发生时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (`trace_id`),
  UNIQUE KEY `uk_trace` (`order_no`,`logistics_no`,`status_label`,`context`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流轨迹节点（承运商Provider查询结果落库）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `campus_logistics_trace`
--

LOCK TABLES `campus_logistics_trace` WRITE;
/*!40000 ALTER TABLE `campus_logistics_trace` DISABLE KEYS */;
INSERT INTO `campus_logistics_trace` VALUES (1,'CT202610121340001','YT7788990011203','YT','已揽收','圆通速递已揽收，包裹正在发往本地分拨中心。','2026-10-12 19:05:00','2026-10-12 19:05:00'),(2,'CT202610121340001','YT7788990011203','YT','运输中','快件已到达校区所在城市分拨中心，正在发往下一站。','2026-10-13 02:05:00','2026-10-13 02:05:00'),(3,'CT202610121508001','ZTO6677889900103','ZTO','已揽收','中通快递已揽收，包裹正在发往本地分拨中心。','2026-10-13 10:30:00','2026-10-13 10:30:00'),(4,'CT202610121508001','ZTO6677889900103','ZTO','运输中','快件已到达校区所在城市分拨中心，正在发往下一站。','2026-10-13 17:30:00','2026-10-13 17:30:00'),(5,'CT202610131825001','SF1020304050605','SF','已揽收','顺丰速运已揽收，包裹正在发往本地分拨中心。','2026-10-14 11:15:00','2026-10-14 11:15:00'),(6,'CT202610131825001','SF1020304050605','SF','运输中','快件已到达校区所在城市分拨中心，正在发往下一站。','2026-10-14 18:15:00','2026-10-14 18:15:00'),(7,'CT202610140937001','JD0987654321104','JD','已揽收','京东物流已揽收，包裹正在发往本地分拨中心。','2026-10-14 17:40:00','2026-10-14 17:40:00'),(8,'CT202610140937001','JD0987654321104','JD','运输中','快件已到达校区所在城市分拨中心，正在发往下一站。','2026-10-15 00:40:00','2026-10-15 00:40:00'),(9,'CT202609271830001','SF1020304050603','SF','已揽收','顺丰速运已揽收，包裹正在发往本地分拨中心。','2026-09-28 10:05:00','2026-09-28 10:05:00'),(10,'CT202609271830001','SF1020304050603','SF','运输中','快件已到达校区所在城市分拨中心，正在发往下一站。','2026-09-28 17:05:00','2026-09-28 17:05:00'),(11,'CT202609271830001','SF1020304050603','SF','派送中','快件已到达校园快递驿站，正在派送中。','2026-09-30 08:30:00','2026-09-30 08:30:00'),(12,'CT202609271830001','SF1020304050603','SF','已签收','快件已由本人签收，感谢使用顺丰速运。','2026-09-30 16:20:00','2026-09-30 16:20:00'),(13,'CT202610091056001','JD0987654321103','JD','已揽收','京东物流已揽收，包裹正在发往本地分拨中心。','2026-10-09 17:20:00','2026-10-09 17:20:00'),(14,'CT202610091056001','JD0987654321103','JD','运输中','快件已到达校区所在城市分拨中心，正在发往下一站。','2026-10-10 00:20:00','2026-10-10 00:20:00'),(15,'CT202610091056001','JD0987654321103','JD','派送中','快件已到达校园快递驿站，正在派送中。','2026-10-11 08:30:00','2026-10-11 08:30:00'),(16,'CT202610091056001','JD0987654321103','JD','已签收','快件已由本人签收，感谢使用京东物流。','2026-10-11 09:15:00','2026-10-11 09:15:00');
/*!40000 ALTER TABLE `campus_logistics_trace` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `campus_notice`
--

DROP TABLE IF EXISTS `campus_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campus_notice` (
  `notice_id` bigint NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `user_id` bigint NOT NULL COMMENT '接收用户ID',
  `type` varchar(20) NOT NULL DEFAULT 'order' COMMENT '类型 order订单 create/ship发货/aftersale售后/system系统',
  `title` varchar(100) NOT NULL DEFAULT '' COMMENT '标题',
  `content` varchar(500) NOT NULL DEFAULT '' COMMENT '内容',
  `biz_no` varchar(32) DEFAULT '' COMMENT '关联业务号（订单号/售后单号）',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '是否已读 0未读 1已读',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  PRIMARY KEY (`notice_id`),
  KEY `idx_user_read` (`user_id`,`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知（面向用户的订单/售后消息）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `campus_notice`
--

LOCK TABLES `campus_notice` WRITE;
/*!40000 ALTER TABLE `campus_notice` DISABLE KEYS */;
INSERT INTO `campus_notice` VALUES (1,2,'order','订单支付成功','订单 CT202610031203001 已支付成功，金额 60.00 元，卖家正在准备发货。','CT202610031203001',1,'2026-10-03 12:15:00','2026-10-03 12:40:00'),(2,2,'ship','订单已发货','订单 CT202610031203001 已发货，物流单号 YT7788990011202，请留意收货。','CT202610031203001',1,'2026-10-03 17:40:00','2026-10-03 19:05:00'),(3,3,'order','订单已完成','订单 CT202610051710001 已确认收货，别忘了给卖家一个评价哦。','CT202610051710001',1,'2026-10-08 15:05:00','2026-10-08 20:30:00'),(4,6,'ship','订单已发货','订单 CT202610121340001 已发货，物流单号 YT7788990011203，请留意收货。','CT202610121340001',0,'2026-10-12 18:05:00',NULL),(5,7,'ship','订单已发货','订单 CT202610121508001 已发货，物流单号 ZTO6677889900103，请留意收货。','CT202610121508001',0,'2026-10-13 09:30:00',NULL),(6,8,'ship','订单已发货','订单 CT202610131825001 已发货，物流单号 SF1020304050605，请留意收货。','CT202610131825001',0,'2026-10-14 10:15:00',NULL),(7,8,'aftersale','售后申请已提交','售后单 AS202610091005001 已提交（退货退款），管理员会在 1 个工作日内处理。','AS202610091005001',0,'2026-10-09 10:05:00',NULL),(8,3,'order','订单已支付','订单 CT202610141602001 已支付成功，金额 750.00 元，卖家正在准备发货。','CT202610141602001',0,'2026-10-14 16:10:00',NULL),(9,4,'aftersale','退款已完成','售后单 AS202609111000002 审核通过，退款 129.00 元已退回沙箱余额。','AS202609111000002',1,'2026-09-11 09:30:00','2026-09-11 12:15:00'),(10,7,'aftersale','退款已完成','售后单 AS202610061000003 审核通过，退款 380.00 元已退回沙箱余额。','AS202610061000003',0,'2026-10-06 10:15:00',NULL),(11,7,'aftersale','售后申请未通过','售后单 AS202610011000004 未通过审核：经核实磕碰为快递运输造成，已协调卖家补偿差价，本次退货退款申请不予受理。','AS202610011000004',1,'2026-10-01 09:20:00','2026-10-01 18:40:00'),(12,8,'order','订单已自动取消','订单 CT202610150630001 因超时未支付已被自动取消，占用库存已释放。','CT202610150630001',1,'2026-10-15 07:00:00','2026-10-15 08:20:00'),(13,5,'system','校园淘使用提示','发布闲置请如实描述成色与瑕疵，交易建议在校内公共场所当面验货。','',0,'2026-09-01 10:00:00',NULL),(14,7,'order','订单待支付提醒','您提交的订单尚未支付，请在 30 分钟内完成付款，超时系统将自动取消并释放库存。','',0,DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR),NULL);
/*!40000 ALTER TABLE `campus_notice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `campus_notify_log`
--

DROP TABLE IF EXISTS `campus_notify_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campus_notify_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '留痕ID',
  `user_id` bigint NOT NULL COMMENT '接收用户ID',
  `channel` varchar(20) NOT NULL COMMENT '渠道 sms短信/email邮件',
  `target` varchar(100) DEFAULT '' COMMENT '接收目标（手机号/邮箱）',
  `type` varchar(20) DEFAULT '' COMMENT '业务类型 order/ship/aftersale/system',
  `title` varchar(100) DEFAULT '' COMMENT '标题',
  `content` varchar(500) DEFAULT '' COMMENT '内容',
  `biz_no` varchar(32) DEFAULT '' COMMENT '关联业务号（订单号/售后单号）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '结果 0失败 1成功 2未启用(已降级为仅站内通知)',
  `response` varchar(500) DEFAULT '' COMMENT '网关响应摘要',
  `error_msg` varchar(500) DEFAULT '' COMMENT '失败原因',
  `cost_ms` bigint DEFAULT '0' COMMENT '耗时(毫秒)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_channel_status` (`channel`,`status`),
  KEY `idx_biz_no` (`biz_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知外呼留痕（短信/邮件通道发送记录）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `campus_notify_log`
--

LOCK TABLES `campus_notify_log` WRITE;
/*!40000 ALTER TABLE `campus_notify_log` DISABLE KEYS */;
INSERT INTO `campus_notify_log` VALUES (1,2,'sms','15666666666','order','订单支付成功','订单 CT202610031203001 已支付成功，金额 60.00 元，卖家正在准备发货。','CT202610031203001',2,'','通道未配置，已降级为仅站内通知',0,'2026-10-14 20:05:00'),(2,2,'email','ry@qq.com','order','订单支付成功','订单 CT202610031203001 已支付成功，金额 60.00 元，卖家正在准备发货。','CT202610031203001',2,'','通道未配置，已降级为仅站内通知',0,'2026-10-15 20:05:00'),(3,3,'sms','13910000003','ship','订单已发货','订单 CT202610051710001 已发货，物流单号 ZTO6677889900102，请注意查收。','CT202610051710001',2,'','通道未配置，已降级为仅站内通知',0,'2026-10-16 20:05:00'),(4,3,'email','liyutong@stu.edu.cn','ship','订单已发货','订单 CT202610051710001 已发货，物流单号 ZTO6677889900102，请注意查收。','CT202610051710001',2,'','通道未配置，已降级为仅站内通知',0,'2026-10-17 20:05:00'),(5,6,'sms','13910000006','ship','订单已发货','订单 CT202610121340001 已发货，物流单号 YT7788990011203，请注意查收。','CT202610121340001',2,'','通道未配置，已降级为仅站内通知',0,'2026-10-18 20:05:00'),(6,8,'sms','13910000008','aftersale','售后申请已提交','售后单 AS202610091005001 已提交，管理员会在 1 个工作日内处理。','AS202610091005001',2,'','通道未配置，已降级为仅站内通知',0,'2026-10-19 20:05:00'),(7,7,'email','zhoukai@stu.edu.cn','aftersale','退款已完成','售后单 AS202610061000003 审核通过，退款 380.00 元已退回沙箱余额。','AS202610061000003',2,'','通道未配置，已降级为仅站内通知',0,'2026-10-20 20:05:00'),(8,5,'sms','13910000005','system','校园淘使用提示','发布闲置请如实描述成色，交易尽量在校内公共场所当面验货。','',2,'','通道未配置，已降级为仅站内通知',0,'2026-10-21 20:05:00');
/*!40000 ALTER TABLE `campus_notify_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `campus_order`
--

DROP TABLE IF EXISTS `campus_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campus_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号(唯一)',
  `user_id` bigint NOT NULL COMMENT '买家ID',
  `user_name` varchar(50) DEFAULT '' COMMENT '买家名',
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '订单总额',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态 0待付款 1已付款 2已发货 3已完成 4已取消 5退款中 6已退款',
  `receiver_name` varchar(50) DEFAULT '' COMMENT '收货人',
  `receiver_phone` varchar(20) DEFAULT '' COMMENT '收货电话',
  `address` varchar(200) DEFAULT '' COMMENT '收货地址',
  `remark` varchar(255) DEFAULT '' COMMENT '买家备注',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `ship_time` datetime DEFAULT NULL COMMENT '发货时间',
  `logistics_no` varchar(64) DEFAULT '' COMMENT '物流单号',
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `campus_order`
--

LOCK TABLES `campus_order` WRITE;
/*!40000 ALTER TABLE `campus_order` DISABLE KEYS */;
INSERT INTO `campus_order` VALUES (1,'CT202609121012001',2,'ry',82.00,3,'林俊','15666666666','东区6号宿舍楼302室（可放菜鸟驿站）','尽快发货，谢谢','2026-09-12 10:20:00','2026-09-12 10:45:00','SF1020304050601','2026-09-14 19:30:00',NULL,'2026-09-12 10:12:00','2026-09-14 19:30:00'),(2,'CT202609151930001',3,'liyutong',95.00,3,'李雨桐','13910000003','西区9号宿舍楼412室','宿舍楼下取货，方便的话今晚就可以','2026-09-15 19:35:00','2026-09-16 09:10:00','JD0987654321101','2026-09-17 12:20:00',NULL,'2026-09-15 19:30:00','2026-09-17 12:20:00'),(3,'CT202609181422001',4,'wanghao',55.00,3,'王浩','13910000004','南区3号宿舍楼215室（快递驿站自提）','考研党，希望书里笔记别擦掉','2026-09-18 14:30:00','2026-09-18 17:05:00','YT7788990011201','2026-09-20 10:15:00',NULL,'2026-09-18 14:22:00','2026-09-20 10:15:00'),(4,'CT202609211645001',5,'zhaomin',77.00,3,'赵敏','13910000005','西区7号宿舍楼508室','周末约球用，麻烦包装结实点','2026-09-21 16:50:00','2026-09-22 08:40:00','ZTO6677889900101','2026-09-23 20:05:00',NULL,'2026-09-21 16:45:00','2026-09-23 20:05:00'),(5,'CT202609240911001',6,'chenxinyi',33.00,3,'陈心怡','13910000006','北区4号宿舍楼306室','','2026-09-24 09:20:00','2026-09-24 15:30:00','SF1020304050602','2026-09-26 11:40:00',NULL,'2026-09-24 09:11:00','2026-09-26 11:40:00'),(6,'CT202609271830001',7,'zhoukai',1650.00,3,'周恺','13910000007','东区2号宿舍楼118室（快递驿站自提）','麻烦确认下电池健康度，谢谢','2026-09-27 18:45:00','2026-09-28 09:05:00','SF1020304050603','2026-09-30 16:20:00',NULL,'2026-09-27 18:30:00','2026-09-30 16:20:00'),(7,'CT202610010845001',8,'liuzhiyuan',220.00,3,'刘志远','13910000008','南区1号宿舍楼601室','送的东西都在吧？','2026-10-01 08:55:00','2026-10-01 14:10:00','JD0987654321102','2026-10-03 09:35:00',NULL,'2026-10-01 08:45:00','2026-10-03 09:35:00'),(8,'CT202610031203001',2,'ry',60.00,3,'林俊','15666666666','东区6号宿舍楼302室（可放菜鸟驿站）','搬宿舍急用，能今天就发吗','2026-10-03 12:15:00','2026-10-03 17:40:00','YT7788990011202','2026-10-05 10:50:00',NULL,'2026-10-03 12:03:00','2026-10-05 10:50:00'),(9,'CT202610051710001',3,'liyutong',104.00,3,'李雨桐','13910000003','西区9号宿舍楼412室','','2026-10-05 17:25:00','2026-10-06 09:20:00','ZTO6677889900102','2026-10-08 15:05:00',NULL,'2026-10-05 17:10:00','2026-10-08 15:05:00'),(10,'CT202610072058001',4,'wanghao',1100.00,3,'王浩','13910000004','南区3号宿舍楼215室（快递驿站自提）','吉他能不能帮我调好弦再寄','2026-10-07 21:10:00','2026-10-08 10:35:00','SF1020304050604','2026-10-10 12:45:00',NULL,'2026-10-07 20:58:00','2026-10-10 12:45:00'),(11,'CT202610091056001',5,'zhaomin',680.00,3,'赵敏','13910000005','西区7号宿舍楼508室','自行车和平衡车一起去南区自提可以吗','2026-10-09 11:05:00','2026-10-09 16:20:00','JD0987654321103','2026-10-11 09:15:00',NULL,'2026-10-09 10:56:00','2026-10-11 09:15:00'),(12,'CT202610121340001',6,'chenxinyi',480.00,2,'陈心怡','13910000006','北区4号宿舍楼306室','校内可以自提，什么时候方便','2026-10-12 13:50:00','2026-10-12 18:05:00','YT7788990011203',NULL,NULL,'2026-10-12 13:40:00','2026-10-12 13:50:00'),(13,'CT202610121508001',7,'zhoukai',45.00,2,'周恺','13910000007','东区2号宿舍楼118室（快递驿站自提）','锅麻烦擦干净一点再寄','2026-10-12 15:20:00','2026-10-13 09:30:00','ZTO6677889900103',NULL,NULL,'2026-10-12 15:08:00','2026-10-12 15:20:00'),(14,'CT202610131825001',8,'liuzhiyuan',88.00,2,'刘志远','13910000008','南区1号宿舍楼601室','四本一起发，谢谢','2026-10-13 18:35:00','2026-10-14 10:15:00','SF1020304050605',NULL,NULL,'2026-10-13 18:25:00','2026-10-13 18:35:00'),(15,'CT202610140937001',2,'ry',120.00,2,'林俊','15666666666','东区6号宿舍楼302室（可放菜鸟驿站）','小冰箱有点重，麻烦包厚一点','2026-10-14 09:45:00','2026-10-14 16:40:00','JD0987654321104',NULL,NULL,'2026-10-14 09:37:00','2026-10-14 09:45:00'),(16,'CT202610141602001',3,'liyutong',750.00,1,'李雨桐','13910000003','西区9号宿舍楼412室','约个时间校内试骑一下','2026-10-14 16:10:00',NULL,'',NULL,NULL,'2026-10-14 16:02:00','2026-10-14 16:10:00'),(17,'CT202610141120001',5,'zhaomin',60.00,1,'赵敏','13910000005','西区7号宿舍楼508室','真题答案册一定要带上','2026-10-14 11:30:00',NULL,'',NULL,NULL,'2026-10-14 11:20:00','2026-10-14 11:30:00'),(18,'CT202610141945001',6,'chenxinyi',380.00,1,'陈心怡','13910000006','北区4号宿舍楼306室','麻烦把小票一起寄过来，谢谢','2026-10-14 19:50:00',NULL,'',NULL,NULL,'2026-10-14 19:45:00','2026-10-14 19:50:00'),(19,CONCAT('CT',DATE_FORMAT(DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR),'%Y%m%d%H%i%s'),'1'),7,'zhoukai',260.00,0,'周恺','13910000007','东区2号宿舍楼118室（快递驿站自提）','先下单占着，明天发生活费就付，麻烦帮我留一下',NULL,NULL,'',NULL,NULL,DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR),DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR)),(20,'CT202610150630001',8,'liuzhiyuan',85.00,4,'刘志远','13910000008','南区1号宿舍楼601室','',NULL,NULL,'',NULL,'2026-10-15 07:00:00','2026-10-15 06:30:00','2026-10-15 07:00:00'),(21,'CT202609301602001',4,'wanghao',35.00,4,'王浩','13910000004','南区3号宿舍楼215室（快递驿站自提）','想想还是不用了',NULL,NULL,'',NULL,'2026-09-30 18:00:00','2026-09-30 16:02:00','2026-09-30 18:00:00'),(22,'CT202610052015001',8,'liuzhiyuan',950.00,5,'刘志远','13910000008','南区1号宿舍楼601室','希望能当面验货','2026-10-05 20:25:00','2026-10-06 09:40:00','YT7788990011204','2026-10-08 11:20:00',NULL,'2026-10-05 20:15:00','2026-10-08 11:20:00'),(23,'CT202609081433001',4,'wanghao',129.00,6,'王浩','13910000004','南区3号宿舍楼215室（快递驿站自提）','','2026-09-08 14:45:00','2026-09-08 18:20:00','ZTO6677889900104','2026-09-10 10:05:00',NULL,'2026-09-08 14:33:00','2026-09-10 10:05:00'),(24,'CT202610031830001',7,'zhoukai',380.00,6,'周恺','13910000007','东区2号宿舍楼118室（快递驿站自提）','屏幕一定要无亮点，介意勿发','2026-10-03 18:40:00','2026-10-04 09:15:00','SF1020304050606','2026-10-05 15:30:00',NULL,'2026-10-03 18:30:00','2026-10-05 15:30:00');
/*!40000 ALTER TABLE `campus_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `campus_order_item`
--

DROP TABLE IF EXISTS `campus_order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campus_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单项ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `product_title` varchar(200) DEFAULT '' COMMENT '商品标题快照',
  `image` varchar(500) DEFAULT '' COMMENT '商品图片快照',
  `price` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '成交单价',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '数量',
  `subtotal` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '小计',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单项';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `campus_order_item`
--

LOCK TABLES `campus_order_item` WRITE;
/*!40000 ALTER TABLE `campus_order_item` DISABLE KEYS */;
INSERT INTO `campus_order_item` VALUES (1,1,'CT202609121012001',6,'《操作系统概念》第9版（恐龙书）','/campus-img/books.jpg',42.00,1,42.00),(2,1,'CT202609121012001',12,'中学教师资格证 综合素质教材+真题','/campus-img/exam.jpg',40.00,1,40.00),(3,2,'CT202609151930001',15,'罗技 G304 无线游戏鼠标','/campus-img/pcacc.jpg',95.00,1,95.00),(4,3,'CT202609181422001',9,'肖秀荣考研政治1000题（2026版）','/campus-img/exam.jpg',20.00,1,20.00),(5,3,'CT202609181422001',10,'考研英语二 张剑黄皮书 真题解析','/campus-img/exam.jpg',35.00,1,35.00),(6,4,'CT202609211645001',26,'迪卡侬 羽毛球拍双拍套装','/campus-img/sports.jpg',65.00,1,65.00),(7,4,'CT202609211645001',30,'负重跳绳 + 护腕（套装）','/campus-img/sports.jpg',12.00,1,12.00),(8,5,'CT202609240911001',2,'《高等数学》同济第七版 上下册（合售）','/campus-img/books.jpg',25.00,1,25.00),(9,5,'CT202609240911001',3,'《线性代数》同济第六版','/campus-img/books.jpg',8.00,1,8.00),(10,6,'CT202609271830001',14,'iPad 2022款 64G WiFi版 深空灰','/campus-img/tablet.jpg',1650.00,1,1650.00),(11,7,'CT202610010845001',37,'尤克里里 23寸单板','/campus-img/instrument.jpg',180.00,1,180.00),(12,7,'CT202610010845001',42,'美的 迷你电吹风 1200W','/campus-img/living.jpg',40.00,1,40.00),(13,8,'CT202610031203001',41,'可折叠收纳箱 三件套','/campus-img/living.jpg',25.00,1,25.00),(14,8,'CT202610031203001',43,'保温杯 500ml 316不锈钢','/campus-img/living.jpg',35.00,1,35.00),(15,9,'CT202610051710001',22,'小米 台灯Pro 护眼版','/campus-img/appliance.jpg',89.00,1,89.00),(16,9,'CT202610051710001',23,'挂脖小风扇（三档风力）','/campus-img/appliance.jpg',15.00,1,15.00),(17,10,'CT202610072058001',35,'雅马哈 F310 民谣吉他','/campus-img/instrument.jpg',420.00,1,420.00),(18,10,'CT202610072058001',36,'卡西欧 CT-X700 电子琴（61键）','/campus-img/instrument.jpg',680.00,1,680.00),(19,11,'CT202610091056001',32,'永久 26寸通勤自行车','/campus-img/vehicle.jpg',260.00,1,260.00),(20,11,'CT202610091056001',34,'九号 电动平衡车','/campus-img/vehicle.jpg',420.00,1,420.00),(21,12,'CT202610121340001',31,'迪卡侬 20寸折叠山地车','/campus-img/vehicle.jpg',480.00,1,480.00),(22,13,'CT202610121508001',21,'小熊 迷你电煮锅 1.2L（带蒸屉）','/campus-img/appliance.jpg',45.00,1,45.00),(23,14,'CT202610131825001',11,'计算机408 王道考研全套（4本）','/campus-img/exam.jpg',88.00,1,88.00),(24,15,'CT202610140937001',24,'桌面迷你小冰箱 6L','/campus-img/appliance.jpg',120.00,1,120.00),(25,16,'CT202610141602001',33,'小米电动滑板车 1S','/campus-img/vehicle.jpg',750.00,1,750.00),(26,17,'CT202610141120001',13,'雅思词汇（乱序版）+ 剑桥真题11-18','/campus-img/exam.jpg',60.00,1,60.00),(27,18,'CT202610141945001',44,'兰蔻 小黑瓶精华 30ml（专柜正装）','/campus-img/beauty.jpg',380.00,1,380.00),(28,19,CONCAT('CT',DATE_FORMAT(DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR),'%Y%m%d%H%i%s'),'1'),38,'得胜 电容麦克风+声卡套装','/campus-img/instrument.jpg',260.00,1,260.00),(29,20,'CT202610150630001',46,'珂润 润浸保湿乳液 120ml','/campus-img/beauty.jpg',85.00,1,85.00),(30,21,'CT202609301602001',28,'Keep 瑜伽垫 加厚8mm','/campus-img/sports.jpg',35.00,1,35.00),(31,22,'CT202610052015001',19,'索尼 WH-1000XM4 头戴式降噪耳机','/campus-img/pcacc.jpg',950.00,1,950.00),(32,23,'CT202609081433001',45,'负离子吹风机（戴森平替款）','/campus-img/beauty.jpg',129.00,1,129.00),(33,24,'CT202610031830001',16,'Kindle Paperwhite 4 8G 电子书阅读器','/campus-img/tablet.jpg',380.00,1,380.00);
/*!40000 ALTER TABLE `campus_order_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `campus_product`
--

DROP TABLE IF EXISTS `campus_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campus_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `category_id` bigint DEFAULT '0' COMMENT '分类ID',
  `title` varchar(200) NOT NULL COMMENT '商品标题',
  `price` decimal(10,2) NOT NULL COMMENT '售价',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '原价',
  `condition_level` int DEFAULT '5' COMMENT '成色(1-5,5=最新)',
  `description` varchar(2000) DEFAULT '' COMMENT '描述',
  `images` varchar(1000) DEFAULT '' COMMENT '图片URL,逗号分隔',
  `stock` int NOT NULL DEFAULT '0' COMMENT '库存',
  `seller_id` bigint DEFAULT '0' COMMENT '卖家用户ID',
  `seller_name` varchar(50) DEFAULT '' COMMENT '卖家昵称',
  `status` tinyint DEFAULT '1' COMMENT '状态(0下架,1上架)',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='二手商品';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `campus_product`
--

LOCK TABLES `campus_product` WRITE;
/*!40000 ALTER TABLE `campus_product` DISABLE KEYS */;
INSERT INTO `campus_product` VALUES (1,1,'《数据结构（C语言版）》严蔚敏 第2版',18.00,45.00,2,'大二上学期用过一学期，书页干净平整，仅前两章有少量铅笔标注（已擦除）。适合课程配套或考研打基础。','/campus-img/books.jpg',2,3,'liyutong',1,0,'2026-08-25 13:17:00','2026-08-25 13:17:00'),(2,1,'《高等数学》同济第七版 上下册（合售）',25.00,89.00,2,'上下册一起出，附课后习题册。上册目录页有折痕，正文笔记工整，适合期末复习。','/campus-img/books.jpg',1,3,'liyutong',1,0,'2026-09-01 18:34:00','2026-09-01 18:34:00'),(3,1,'《线性代数》同济第六版',8.00,29.00,3,'课堂笔记记了大半本，书角有轻微卷边，不影响阅读。低价出，介意勿拍。','/campus-img/books.jpg',1,4,'wanghao',1,0,'2026-08-20 09:51:00','2026-08-20 09:51:00'),(4,1,'《大学英语综合教程》1-4册（成套）',30.00,120.00,2,'四册成套出，听力光盘只找到第3册，其余正常。书内有课文生词标注。','/campus-img/books.jpg',2,5,'zhaomin',1,0,'2026-08-27 14:08:00','2026-08-27 14:08:00'),(5,1,'《C Primer Plus》中文版 第6版',35.00,89.00,2,'自学C语言买的正版，只翻过前1/3，后面几乎全新。页码完整无缺页。','/campus-img/books.jpg',2,4,'wanghao',1,0,'2026-09-03 19:25:00','2026-09-03 19:25:00'),(6,1,'《操作系统概念》第9版（恐龙书）',42.00,128.00,2,'考研复习时买的，书内夹了一份手写重点提纲，可一并赠送。','/campus-img/books.jpg',1,6,'chenxinyi',1,0,'2026-08-22 10:42:00','2026-08-22 10:42:00'),(7,1,'《计算机网络：自顶向下方法》第8版',38.00,99.00,3,'有荧光笔标记，重点章节划得比较清楚，书脊完好。','/campus-img/books.jpg',2,3,'liyutong',1,0,'2026-08-29 15:59:00','2026-08-29 15:59:00'),(8,2,'张宇考研数学基础30讲（2026版）',45.00,168.00,2,'2026版全套，只跟了前5讲，其余讲义未拆封。赠配套视频兑换码（未使用）。','/campus-img/exam.jpg',2,3,'liyutong',1,0,'2026-09-05 20:16:00','2026-09-05 20:16:00'),(9,2,'肖秀荣考研政治1000题（2026版）',20.00,55.00,2,'刷过一轮，答案册有铅笔对勾，可以擦掉再刷。题册无缺页。','/campus-img/exam.jpg',1,5,'zhaomin',1,0,'2026-08-24 11:33:00','2026-08-24 11:33:00'),(10,2,'考研英语二 张剑黄皮书 真题解析',35.00,119.00,3,'含2015-2024年真题解析，我的笔记都记在附赠的手写本上，不写在书上。','/campus-img/exam.jpg',1,5,'zhaomin',1,0,'2026-08-31 16:50:00','2026-08-31 16:50:00'),(11,2,'计算机408 王道考研全套（4本）',88.00,320.00,2,'数据结构、操作系统、计算机组成原理、计算机网络四本齐全，无缺页无涂画。','/campus-img/exam.jpg',1,7,'zhoukai',1,0,'2026-08-19 21:07:00','2026-08-19 21:07:00'),(12,2,'中学教师资格证 综合素质教材+真题',40.00,130.00,1,'全新未拆，报名后改考别的科目了，低价转给需要的同学。','/campus-img/exam.jpg',1,4,'wanghao',1,0,'2026-08-26 12:24:00','2026-08-26 12:24:00'),(13,2,'雅思词汇（乱序版）+ 剑桥真题11-18',60.00,260.00,2,'词汇书全新未写，剑桥真题做过3套（另外5套空白），答案册齐全。','/campus-img/exam.jpg',1,8,'liuzhiyuan',1,0,'2026-09-02 17:41:00','2026-09-02 17:41:00'),(14,3,'iPad 2022款 64G WiFi版 深空灰',1650.00,2599.00,2,'大一买的，平时只用来记笔记和追剧，电池健康度92%，屏幕无划痕，含原装充电器和保护壳。','/campus-img/tablet.jpg',0,4,'wanghao',0,0,'2026-08-21 08:58:00','2026-08-21 08:58:00'),(15,3,'罗技 G304 无线游戏鼠标',95.00,249.00,3,'用了一年多，左右键和微动都正常，脚贴有磨损，送两节新电池。','/campus-img/pcacc.jpg',0,4,'wanghao',0,0,'2026-08-28 13:15:00','2026-08-28 13:15:00'),(16,3,'Kindle Paperwhite 4 8G 电子书阅读器',380.00,998.00,3,'看书用，屏幕无划痕，背光均匀，附原装保护套。电池续航正常。','/campus-img/tablet.jpg',1,6,'chenxinyi',1,0,'2026-09-04 18:32:00','2026-09-04 18:32:00'),(17,3,'绿联 65W 氮化镓充电器（双口）',55.00,129.00,2,'毕业清仓，只用过几次，双口同时输出正常，线材另送一条。','/campus-img/pcacc.jpg',1,6,'chenxinyi',1,0,'2026-08-23 09:49:00','2026-08-23 09:49:00'),(18,3,'小米移动电源3 10000mAh',45.00,89.00,3,'外壳有轻微使用痕迹，容量输出正常，支持双向快充。','/campus-img/pcacc.jpg',1,2,'ry',1,0,'2026-08-30 14:06:00','2026-08-30 14:06:00'),(19,3,'索尼 WH-1000XM4 头戴式降噪耳机',950.00,2299.00,2,'图书馆降噪神器，耳罩无脱皮、无掉皮，含原包装盒和收纳袋。','/campus-img/pcacc.jpg',0,6,'chenxinyi',0,0,'2026-08-18 19:23:00','2026-08-18 19:23:00'),(20,3,'雷柏 V500 Pro 机械键盘（青轴）',78.00,199.00,4,'青轴声音偏大，宿舍环境介意的同学慎拍。键帽有轻微打油，轴体手感正常。','/campus-img/pcacc.jpg',1,4,'wanghao',1,0,'2026-08-25 10:40:00','2026-08-25 10:40:00'),(21,4,'小熊 迷你电煮锅 1.2L（带蒸屉）',45.00,129.00,3,'宿舍煮面煮粥用，带蒸屉和电源线，已清洁消毒。功率达标不跳闸。','/campus-img/appliance.jpg',0,5,'zhaomin',0,0,'2026-09-01 15:57:00','2026-09-01 15:57:00'),(22,4,'小米 台灯Pro 护眼版',89.00,199.00,2,'考研自习用，无频闪、无可视频闪，含说明书和原装电源。','/campus-img/appliance.jpg',0,5,'zhaomin',0,0,'2026-08-20 20:14:00','2026-08-20 20:14:00'),(23,4,'挂脖小风扇（三档风力）',15.00,49.00,3,'夏天军训和自习用，三档风力正常，续航约4小时，已擦拭消毒。','/campus-img/appliance.jpg',0,8,'liuzhiyuan',0,0,'2026-08-27 11:31:00','2026-08-27 11:31:00'),(24,4,'桌面迷你小冰箱 6L',120.00,299.00,2,'放饮料和面膜用，制冷正常，送电源适配器。宿舍桌面刚好放得下。','/campus-img/appliance.jpg',0,7,'zhoukai',0,0,'2026-09-03 16:48:00','2026-09-03 16:48:00'),(25,4,'小熊 加湿器 4L 大容量',55.00,159.00,3,'冬天宿舍开暖气用，静音款，滤芯上周刚换新的。','/campus-img/appliance.jpg',1,5,'zhaomin',1,0,'2026-08-22 21:05:00','2026-08-22 21:05:00'),(26,5,'迪卡侬 羽毛球拍双拍套装',65.00,159.00,3,'双拍套装含3个球和拍包，拉线张力正常，手胶换过一次。','/campus-img/sports.jpg',0,6,'chenxinyi',0,0,'2026-08-29 12:22:00','2026-08-29 12:22:00'),(27,5,'李宁 7号PU篮球',50.00,149.00,3,'打了一年，气密性良好，附赠气针。手感偏软，适合外场。','/campus-img/sports.jpg',1,6,'chenxinyi',1,0,'2026-09-05 17:39:00','2026-09-05 17:39:00'),(28,5,'Keep 瑜伽垫 加厚8mm',35.00,99.00,2,'用过五六次，已用酒精擦洗消毒，无破损无掉渣。','/campus-img/sports.jpg',1,7,'zhoukai',1,0,'2026-08-24 08:56:00','2026-08-24 08:56:00'),(29,5,'阿迪达斯 运动双肩包（可放15.6寸电脑）',69.00,249.00,3,'背过一学期，拉链顺滑，无明显破损，笔记本隔层可放15.6寸电脑。','/campus-img/sports.jpg',1,8,'liuzhiyuan',1,0,'2026-08-31 13:13:00','2026-08-31 13:13:00'),(30,5,'负重跳绳 + 护腕（套装）',12.00,39.00,2,'减脂期买的，几乎全新，绳长可调，送一副护腕。','/campus-img/sports.jpg',2,6,'chenxinyi',1,0,'2026-08-19 18:30:00','2026-08-19 18:30:00'),(31,6,'迪卡侬 20寸折叠山地车',480.00,1299.00,3,'校园代步用，变速和刹车都正常，附车锁。毕业转让，校内可试骑。','/campus-img/vehicle.jpg',0,2,'ry',0,0,'2026-08-26 09:47:00','2026-08-26 09:47:00'),(32,6,'永久 26寸通勤自行车',260.00,799.00,4,'骑了两年，刹车刚换新，车架有一处掉漆，需自提，校内可看车。','/campus-img/vehicle.jpg',0,8,'liuzhiyuan',0,0,'2026-09-02 14:04:00','2026-09-02 14:04:00'),(33,6,'小米电动滑板车 1S',750.00,1999.00,3,'续航约20km，电池健康，含充电器。校内可试骑，介意电量衰减的慎拍。','/campus-img/vehicle.jpg',0,2,'ry',0,0,'2026-08-21 19:21:00','2026-08-21 19:21:00'),(34,6,'九号 电动平衡车',420.00,1299.00,4,'闲置较久，电池需定期充电保养，功能正常，附原装充电器。','/campus-img/vehicle.jpg',0,7,'zhoukai',0,0,'2026-08-28 10:38:00','2026-08-28 10:38:00'),(35,7,'雅马哈 F310 民谣吉他',420.00,899.00,3,'入门练习用，音准正常，琴颈无变形，送变调夹和琴包。','/campus-img/instrument.jpg',0,7,'zhoukai',0,0,'2026-09-04 15:55:00','2026-09-04 15:55:00'),(36,7,'卡西欧 CT-X700 电子琴（61键）',680.00,1599.00,3,'61键，含琴架和原装电源，宿舍放不下才出。所有琴键力度响应正常。','/campus-img/instrument.jpg',0,7,'zhoukai',0,0,'2026-08-23 20:12:00','2026-08-23 20:12:00'),(37,7,'尤克里里 23寸单板',180.00,499.00,2,'买来学了两周就吃灰了，几乎全新，送教程书和调音器。','/campus-img/instrument.jpg',0,3,'liyutong',0,0,'2026-08-30 11:29:00','2026-08-30 11:29:00'),(38,7,'得胜 电容麦克风+声卡套装',260.00,699.00,2,'录播客用，全套线材齐全，麦克风支架和防喷罩都在。','/campus-img/instrument.jpg',0,8,'liuzhiyuan',0,0,'2026-08-18 16:46:00','2026-08-18 16:46:00'),(39,8,'宿舍床上书桌（折叠款）',55.00,149.00,3,'桌面有两处轻微划痕，承重正常，折叠后不占地方。','/campus-img/living.jpg',1,8,'liuzhiyuan',1,0,'2026-08-25 21:03:00','2026-08-25 21:03:00'),(40,8,'遮光窗帘 1.5×2m（宿舍专用）',30.00,89.00,2,'遮光效果好，白天睡觉基本看不到光，含挂钩和绑带。','/campus-img/living.jpg',1,8,'liuzhiyuan',1,0,'2026-09-01 12:20:00','2026-09-01 12:20:00'),(41,8,'可折叠收纳箱 三件套',25.00,79.00,2,'搬宿舍用完后闲置，三个尺寸成套，无破损无异味。','/campus-img/living.jpg',0,5,'zhaomin',0,0,'2026-08-20 17:37:00','2026-08-20 17:37:00'),(42,8,'美的 迷你电吹风 1200W',40.00,119.00,3,'低功率适合宿舍使用，线材完好，两档风速正常。','/campus-img/living.jpg',0,6,'chenxinyi',0,0,'2026-08-27 08:54:00','2026-08-27 08:54:00'),(43,8,'保温杯 500ml 316不锈钢',35.00,129.00,1,'全新未使用，收到重复礼物，所以低价出。附原装包装盒。','/campus-img/living.jpg',1,5,'zhaomin',1,0,'2026-09-03 13:11:00','2026-09-03 13:11:00'),(44,9,'兰蔻 小黑瓶精华 30ml（专柜正装）',380.00,760.00,1,'全新未开封，专柜小票还在，2027年到期。非临期，假一赔十。','/campus-img/beauty.jpg',0,3,'liyutong',0,0,'2026-08-22 18:28:00','2026-08-22 18:28:00'),(45,9,'负离子吹风机（戴森平替款）',129.00,399.00,2,'用过两次，风力正常，含集风嘴和柔风嘴，包装盒在。','/campus-img/beauty.jpg',1,6,'chenxinyi',1,0,'2026-08-29 09:45:00','2026-08-29 09:45:00'),(46,9,'珂润 润浸保湿乳液 120ml',85.00,179.00,1,'全新，多买了一瓶，适合干敏皮，2027年到期。','/campus-img/beauty.jpg',2,5,'zhaomin',1,0,'2026-09-05 14:02:00','2026-09-05 14:02:00'),(47,10,'校园健身房月卡（10.15-11.15）',80.00,150.00,1,'本人转专业后用不上，可陪同到前台办理过户，仅限本校学生。','/campus-img/card.jpg',1,1,'admin',1,0,'2026-08-24 19:19:00','2026-08-24 19:19:00'),(48,10,'学校游泳馆次卡（剩15次）',100.00,300.00,1,'原20次卡剩15次，有效期到今年年底，可随时去游。','/campus-img/card.jpg',1,1,'admin',1,0,'2026-08-31 10:36:00','2026-08-31 10:36:00'),(49,10,'电影兑换券 6张（不限影片）',90.00,180.00,1,'学校发的福利券，不限影片和场次，2026年底到期，6张打包出。','/campus-img/card.jpg',6,2,'ry',1,0,'2026-08-19 15:53:00','2026-08-19 15:53:00');
/*!40000 ALTER TABLE `campus_product` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `campus_review`
--

DROP TABLE IF EXISTS `campus_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campus_review` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `user_id` bigint NOT NULL COMMENT '评价人ID',
  `user_name` varchar(50) DEFAULT '' COMMENT '评价人',
  `rating` tinyint NOT NULL DEFAULT '5' COMMENT '评分 1-5',
  `content` varchar(500) DEFAULT '' COMMENT '评价内容',
  `images` varchar(500) DEFAULT '' COMMENT '评价图片',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评价';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `campus_review`
--

LOCK TABLES `campus_review` WRITE;
/*!40000 ALTER TABLE `campus_review` DISABLE KEYS */;
INSERT INTO `campus_review` VALUES (1,1,6,2,'ry',5,'书和描述一致，还附赠了手写提纲，考研复习帮了大忙。','','2026-09-15 20:00:00'),(2,1,12,2,'ry',5,'全新未拆封，比书店便宜一半，卖家发货也快。','','2026-09-15 11:00:00'),(3,2,15,3,'liyutong',5,'鼠标反应灵敏，还送了新电池，成色比描述的还好。','','2026-09-18 20:00:00'),(4,3,9,4,'wanghao',4,'题册里确实有铅笔勾过的痕迹，擦干净还能继续刷，可以接受。','','2026-09-21 11:00:00'),(5,3,10,4,'wanghao',5,'真题解析很全，笔记在单独的本子上，不影响使用，很划算。','','2026-09-21 20:00:00'),(6,4,26,5,'zhaomin',5,'球拍和球都挺新的，周末约球正好用上，卖家还多送了一个球。','','2026-09-24 11:00:00'),(7,4,30,5,'zhaomin',4,'跳绳没问题，护腕有点旧，不过这个价格也值了。','','2026-09-24 20:00:00'),(8,5,2,6,'chenxinyi',5,'上下册都在，笔记记得很整齐，期末复习帮了大忙。','','2026-09-27 11:00:00'),(9,5,3,6,'chenxinyi',4,'书角有点卷，内容没问题，八块钱很划算了。','','2026-09-27 20:00:00'),(10,6,14,7,'zhoukai',5,'iPad 成色很好，电池健康度确实是92%，卖家还贴了膜，值。','','2026-10-01 11:00:00'),(11,7,37,8,'liuzhiyuan',5,'尤克里里音色不错，教程和调音器都送了，新手够用。','','2026-10-04 20:00:00'),(12,7,42,8,'liuzhiyuan',4,'吹风风力一般，但宿舍用正好不跳闸，线材完好。','','2026-10-04 11:00:00'),(13,8,41,2,'ry',5,'收纳箱三个尺寸都有，搬宿舍装东西很方便，没有异味。','','2026-10-06 20:00:00'),(14,8,43,2,'ry',5,'全新未拆，包装完好，比双十一还便宜，谢谢卖家。','','2026-10-06 11:00:00'),(15,9,22,3,'liyutong',5,'台灯没有频闪，晚上自习眼睛不酸了，说明书也在。','','2026-10-09 20:00:00'),(16,9,23,3,'liyutong',5,'风扇三档风力够用，军训那几天全靠它了。','','2026-10-09 11:00:00'),(17,10,35,4,'wanghao',5,'吉他很新，音准没问题，卖家还帮我调了一次弦。','','2026-10-11 20:00:00'),(18,10,36,4,'wanghao',4,'电子琴所有键都能用，琴架有点旧，整体满意。','','2026-10-11 11:00:00'),(19,11,32,5,'zhaomin',4,'车架掉漆的地方和描述一致，刹车确实是新换的，能骑。','','2026-10-12 20:00:00'),(20,11,34,5,'zhaomin',5,'平衡车功能正常，卖家教我怎么保养电池，很耐心。','','2026-10-12 11:00:00');
/*!40000 ALTER TABLE `campus_review` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gen_table`
--

DROP TABLE IF EXISTS `gen_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gen_table` (
  `table_id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '表名称',
  `table_comment` varchar(500) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '表描述',
  `sub_table_name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联子表的表名',
  `sub_table_fk_name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '子表关联的外键名',
  `class_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '实体类名称',
  `tpl_category` varchar(200) COLLATE utf8mb4_general_ci DEFAULT 'crud' COMMENT '使用的模板（crud单表操作 tree树表操作）',
  `tpl_web_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '前端模板类型（element-ui模版 element-plus模版）',
  `package_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成包路径',
  `module_name` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成模块名',
  `business_name` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成业务名',
  `function_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成功能名',
  `function_author` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成功能作者',
  `form_col_num` int DEFAULT '1' COMMENT '表单布局（单列 双列 三列）',
  `gen_type` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '生成代码方式（0zip压缩包 1自定义路径）',
  `gen_path` varchar(200) COLLATE utf8mb4_general_ci DEFAULT '/' COMMENT '生成路径（不填默认项目路径）',
  `options` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '其它生成选项',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成业务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gen_table`
--

LOCK TABLES `gen_table` WRITE;
/*!40000 ALTER TABLE `gen_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `gen_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gen_table_column`
--

DROP TABLE IF EXISTS `gen_table_column`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gen_table_column` (
  `column_id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_id` bigint DEFAULT NULL COMMENT '归属表编号',
  `column_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '列名称',
  `column_comment` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '列描述',
  `column_type` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '列类型',
  `java_type` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'JAVA类型',
  `java_field` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'JAVA字段名',
  `is_pk` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否主键（1是）',
  `is_increment` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否自增（1是）',
  `is_required` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否必填（1是）',
  `is_insert` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否为插入字段（1是）',
  `is_edit` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否编辑字段（1是）',
  `is_list` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否列表字段（1是）',
  `is_query` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否查询字段（1是）',
  `query_type` varchar(200) COLLATE utf8mb4_general_ci DEFAULT 'EQ' COMMENT '查询方式（等于、不等于、大于、小于、范围）',
  `html_type` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  `dict_type` varchar(200) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',
  `sort` int DEFAULT NULL COMMENT '排序',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`column_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成业务表字段';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gen_table_column`
--

LOCK TABLES `gen_table_column` WRITE;
/*!40000 ALTER TABLE `gen_table_column` DISABLE KEYS */;
/*!40000 ALTER TABLE `gen_table_column` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_blob_triggers`
--

DROP TABLE IF EXISTS `qrtz_blob_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_blob_triggers` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `blob_data` blob COMMENT '存放持久化Trigger对象',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_blob_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Blob类型的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_blob_triggers`
--

LOCK TABLES `qrtz_blob_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_blob_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_blob_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_calendars`
--

DROP TABLE IF EXISTS `qrtz_calendars`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_calendars` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `calendar_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '日历名称',
  `calendar` blob NOT NULL COMMENT '存放持久化calendar对象',
  PRIMARY KEY (`sched_name`,`calendar_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='日历信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_calendars`
--

LOCK TABLES `qrtz_calendars` WRITE;
/*!40000 ALTER TABLE `qrtz_calendars` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_calendars` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_cron_triggers`
--

DROP TABLE IF EXISTS `qrtz_cron_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_cron_triggers` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `cron_expression` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'cron表达式',
  `time_zone_id` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '时区',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_cron_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Cron类型的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_cron_triggers`
--

LOCK TABLES `qrtz_cron_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_cron_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_cron_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_fired_triggers`
--

DROP TABLE IF EXISTS `qrtz_fired_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_fired_triggers` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `entry_id` varchar(95) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器实例id',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `instance_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器实例名',
  `fired_time` bigint NOT NULL COMMENT '触发的时间',
  `sched_time` bigint NOT NULL COMMENT '定时器制定的时间',
  `priority` int NOT NULL COMMENT '优先级',
  `state` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态',
  `job_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '任务名称',
  `job_group` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '任务组名',
  `is_nonconcurrent` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否并发',
  `requests_recovery` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否接受恢复执行',
  PRIMARY KEY (`sched_name`,`entry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='已触发的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_fired_triggers`
--

LOCK TABLES `qrtz_fired_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_fired_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_fired_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_job_details`
--

DROP TABLE IF EXISTS `qrtz_job_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_job_details` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `job_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务组名',
  `description` varchar(250) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '相关介绍',
  `job_class_name` varchar(250) COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行任务类名称',
  `is_durable` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否持久化',
  `is_nonconcurrent` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否并发',
  `is_update_data` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否更新数据',
  `requests_recovery` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否接受恢复执行',
  `job_data` blob COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`,`job_name`,`job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务详细信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_job_details`
--

LOCK TABLES `qrtz_job_details` WRITE;
/*!40000 ALTER TABLE `qrtz_job_details` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_job_details` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_locks`
--

DROP TABLE IF EXISTS `qrtz_locks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_locks` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `lock_name` varchar(40) COLLATE utf8mb4_general_ci NOT NULL COMMENT '悲观锁名称',
  PRIMARY KEY (`sched_name`,`lock_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='存储的悲观锁信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_locks`
--

LOCK TABLES `qrtz_locks` WRITE;
/*!40000 ALTER TABLE `qrtz_locks` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_locks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_paused_trigger_grps`
--

DROP TABLE IF EXISTS `qrtz_paused_trigger_grps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_paused_trigger_grps` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  PRIMARY KEY (`sched_name`,`trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='暂停的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_paused_trigger_grps`
--

LOCK TABLES `qrtz_paused_trigger_grps` WRITE;
/*!40000 ALTER TABLE `qrtz_paused_trigger_grps` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_paused_trigger_grps` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_scheduler_state`
--

DROP TABLE IF EXISTS `qrtz_scheduler_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_scheduler_state` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `instance_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '实例名称',
  `last_checkin_time` bigint NOT NULL COMMENT '上次检查时间',
  `checkin_interval` bigint NOT NULL COMMENT '检查间隔时间',
  PRIMARY KEY (`sched_name`,`instance_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='调度器状态表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_scheduler_state`
--

LOCK TABLES `qrtz_scheduler_state` WRITE;
/*!40000 ALTER TABLE `qrtz_scheduler_state` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_scheduler_state` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_simple_triggers`
--

DROP TABLE IF EXISTS `qrtz_simple_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_simple_triggers` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `repeat_count` bigint NOT NULL COMMENT '重复的次数统计',
  `repeat_interval` bigint NOT NULL COMMENT '重复的间隔时间',
  `times_triggered` bigint NOT NULL COMMENT '已经触发的次数',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_simple_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='简单触发器的信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_simple_triggers`
--

LOCK TABLES `qrtz_simple_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_simple_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_simple_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_simprop_triggers`
--

DROP TABLE IF EXISTS `qrtz_simprop_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_simprop_triggers` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `str_prop_1` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'String类型的trigger的第一个参数',
  `str_prop_2` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'String类型的trigger的第二个参数',
  `str_prop_3` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'String类型的trigger的第三个参数',
  `int_prop_1` int DEFAULT NULL COMMENT 'int类型的trigger的第一个参数',
  `int_prop_2` int DEFAULT NULL COMMENT 'int类型的trigger的第二个参数',
  `long_prop_1` bigint DEFAULT NULL COMMENT 'long类型的trigger的第一个参数',
  `long_prop_2` bigint DEFAULT NULL COMMENT 'long类型的trigger的第二个参数',
  `dec_prop_1` decimal(13,4) DEFAULT NULL COMMENT 'decimal类型的trigger的第一个参数',
  `dec_prop_2` decimal(13,4) DEFAULT NULL COMMENT 'decimal类型的trigger的第二个参数',
  `bool_prop_1` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Boolean类型的trigger的第一个参数',
  `bool_prop_2` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Boolean类型的trigger的第二个参数',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_simprop_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='同步机制的行锁表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_simprop_triggers`
--

LOCK TABLES `qrtz_simprop_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_simprop_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_simprop_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_triggers`
--

DROP TABLE IF EXISTS `qrtz_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_triggers` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器的名字',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器所属组的名字',
  `job_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_job_details表job_name的外键',
  `job_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_job_details表job_group的外键',
  `description` varchar(250) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '相关介绍',
  `next_fire_time` bigint DEFAULT NULL COMMENT '上一次触发时间（毫秒）',
  `prev_fire_time` bigint DEFAULT NULL COMMENT '下一次触发时间（默认为-1表示不触发）',
  `priority` int DEFAULT NULL COMMENT '优先级',
  `trigger_state` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器状态',
  `trigger_type` varchar(8) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器的类型',
  `start_time` bigint NOT NULL COMMENT '开始时间',
  `end_time` bigint DEFAULT NULL COMMENT '结束时间',
  `calendar_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '日程表名称',
  `misfire_instr` smallint DEFAULT NULL COMMENT '补偿执行的策略',
  `job_data` blob COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  KEY `sched_name` (`sched_name`,`job_name`,`job_group`),
  CONSTRAINT `qrtz_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `job_name`, `job_group`) REFERENCES `qrtz_job_details` (`sched_name`, `job_name`, `job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='触发器详细信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_triggers`
--

LOCK TABLES `qrtz_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `config_id` int NOT NULL AUTO_INCREMENT COMMENT '参数主键',
  `config_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '参数名称',
  `config_key` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '参数键名',
  `config_value` varchar(500) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '参数键值',
  `config_type` char(1) COLLATE utf8mb4_general_ci DEFAULT 'N' COMMENT '系统内置（Y是 N否）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`config_id`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='参数配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_config`
--

LOCK TABLES `sys_config` WRITE;
/*!40000 ALTER TABLE `sys_config` DISABLE KEYS */;
INSERT INTO `sys_config` VALUES (1,'主框架页-默认皮肤样式名称','sys.index.skinName','skin-blue','Y','admin','2026-10-12 09:34:20','',NULL,'蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow'),(2,'用户管理-账号初始密码','sys.user.initPassword','123456','Y','admin','2026-10-12 09:34:20','',NULL,'初始化密码 123456'),(3,'主框架页-侧边栏主题','sys.index.sideTheme','theme-dark','Y','admin','2026-10-12 09:34:20','',NULL,'深色主题theme-dark，浅色主题theme-light'),(4,'账号自助-验证码开关','sys.account.captchaEnabled','false','Y','admin','2026-10-12 09:34:20','',NULL,'是否开启验证码功能（true开启，false关闭）'),(5,'账号自助-是否开启用户注册功能','sys.account.registerUser','false','Y','admin','2026-10-12 09:34:20','',NULL,'是否开启注册用户功能（true开启，false关闭）'),(6,'用户登录-黑名单列表','sys.login.blackIPList','','Y','admin','2026-10-12 09:34:20','',NULL,'设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）'),(7,'用户管理-初始密码修改策略','sys.account.initPasswordModify','1','Y','admin','2026-10-12 09:34:20','',NULL,'0：初始密码修改策略关闭，没有任何提示，1：提醒用户，如果未修改初始密码，则在登录时就会提醒修改密码对话框'),(8,'用户管理-账号密码更新周期','sys.account.passwordValidateDays','0','Y','admin','2026-10-12 09:34:20','',NULL,'密码更新周期（填写数字，数据初始化值为0不限制，若修改必须为大于0小于365的正整数），如果超过这个周期登录系统时，则在登录时就会提醒修改密码对话框'),(9,'用户管理-密码字符范围','sys.account.chrtype','0','Y','admin','2026-10-12 09:34:20','',NULL,'默认任意字符范围，0任意（密码可以输入任意字符），1数字（密码只能为0-9数字），2英文字母（密码只能为a-z和A-Z字母），3字母和数字（密码必须包含字母，数字）,4字母数字和特殊字符（目前支持的特殊字符包括：~!@#$%^&*()-=_+）');
/*!40000 ALTER TABLE `sys_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dept`
--

DROP TABLE IF EXISTS `sys_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dept` (
  `dept_id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门id',
  `parent_id` bigint DEFAULT '0' COMMENT '父部门id',
  `ancestors` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '祖级列表',
  `dept_name` varchar(30) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '部门名称',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `leader` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '负责人',
  `phone` varchar(11) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮箱',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '部门状态（0正常 1停用）',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`dept_id`)
) ENGINE=InnoDB AUTO_INCREMENT=200 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='部门表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dept`
--

LOCK TABLES `sys_dept` WRITE;
/*!40000 ALTER TABLE `sys_dept` DISABLE KEYS */;
INSERT INTO `sys_dept` VALUES (100,0,'0','若依科技',0,'若依','15888888888','ry@qq.com','0','0','admin','2026-10-12 09:34:19','',NULL),(101,100,'0,100','深圳总公司',1,'若依','15888888888','ry@qq.com','0','0','admin','2026-10-12 09:34:19','',NULL),(102,100,'0,100','长沙分公司',2,'若依','15888888888','ry@qq.com','0','0','admin','2026-10-12 09:34:19','',NULL),(103,101,'0,100,101','研发部门',1,'若依','15888888888','ry@qq.com','0','0','admin','2026-10-12 09:34:19','',NULL),(104,101,'0,100,101','市场部门',2,'若依','15888888888','ry@qq.com','0','0','admin','2026-10-12 09:34:19','',NULL),(105,101,'0,100,101','测试部门',3,'若依','15888888888','ry@qq.com','0','0','admin','2026-10-12 09:34:19','',NULL),(106,101,'0,100,101','财务部门',4,'若依','15888888888','ry@qq.com','0','0','admin','2026-10-12 09:34:19','',NULL),(107,101,'0,100,101','运维部门',5,'若依','15888888888','ry@qq.com','0','0','admin','2026-10-12 09:34:19','',NULL),(108,102,'0,100,102','市场部门',1,'若依','15888888888','ry@qq.com','0','0','admin','2026-10-12 09:34:19','',NULL),(109,102,'0,100,102','财务部门',2,'若依','15888888888','ry@qq.com','0','0','admin','2026-10-12 09:34:19','',NULL);
/*!40000 ALTER TABLE `sys_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_data`
--

DROP TABLE IF EXISTS `sys_dict_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_data` (
  `dict_code` bigint NOT NULL AUTO_INCREMENT COMMENT '字典编码',
  `dict_sort` int DEFAULT '0' COMMENT '字典排序',
  `dict_label` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典标签',
  `dict_value` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典键值',
  `dict_type` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',
  `css_class` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '样式属性（其他样式扩展）',
  `list_class` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '表格回显样式',
  `is_default` char(1) COLLATE utf8mb4_general_ci DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_code`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典数据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_data`
--

LOCK TABLES `sys_dict_data` WRITE;
/*!40000 ALTER TABLE `sys_dict_data` DISABLE KEYS */;
INSERT INTO `sys_dict_data` VALUES (1,1,'男','0','sys_user_sex','','','Y','0','admin','2026-10-12 09:34:20','',NULL,'性别男'),(2,2,'女','1','sys_user_sex','','','N','0','admin','2026-10-12 09:34:20','',NULL,'性别女'),(3,3,'未知','2','sys_user_sex','','','N','0','admin','2026-10-12 09:34:20','',NULL,'性别未知'),(4,1,'显示','0','sys_show_hide','','primary','Y','0','admin','2026-10-12 09:34:20','',NULL,'显示菜单'),(5,2,'隐藏','1','sys_show_hide','','danger','N','0','admin','2026-10-12 09:34:20','',NULL,'隐藏菜单'),(6,1,'正常','0','sys_normal_disable','','primary','Y','0','admin','2026-10-12 09:34:20','',NULL,'正常状态'),(7,2,'停用','1','sys_normal_disable','','danger','N','0','admin','2026-10-12 09:34:20','',NULL,'停用状态'),(8,1,'正常','0','sys_job_status','','primary','Y','0','admin','2026-10-12 09:34:20','',NULL,'正常状态'),(9,2,'暂停','1','sys_job_status','','danger','N','0','admin','2026-10-12 09:34:20','',NULL,'停用状态'),(10,1,'默认','DEFAULT','sys_job_group','','','Y','0','admin','2026-10-12 09:34:20','',NULL,'默认分组'),(11,2,'系统','SYSTEM','sys_job_group','','','N','0','admin','2026-10-12 09:34:20','',NULL,'系统分组'),(12,1,'是','Y','sys_yes_no','','primary','Y','0','admin','2026-10-12 09:34:20','',NULL,'系统默认是'),(13,2,'否','N','sys_yes_no','','danger','N','0','admin','2026-10-12 09:34:20','',NULL,'系统默认否'),(14,1,'通知','1','sys_notice_type','','warning','Y','0','admin','2026-10-12 09:34:20','',NULL,'通知'),(15,2,'公告','2','sys_notice_type','','success','N','0','admin','2026-10-12 09:34:20','',NULL,'公告'),(16,1,'正常','0','sys_notice_status','','primary','Y','0','admin','2026-10-12 09:34:20','',NULL,'正常状态'),(17,2,'关闭','1','sys_notice_status','','danger','N','0','admin','2026-10-12 09:34:20','',NULL,'关闭状态'),(18,99,'其他','0','sys_oper_type','','info','N','0','admin','2026-10-12 09:34:20','',NULL,'其他操作'),(19,1,'新增','1','sys_oper_type','','info','N','0','admin','2026-10-12 09:34:20','',NULL,'新增操作'),(20,2,'修改','2','sys_oper_type','','info','N','0','admin','2026-10-12 09:34:20','',NULL,'修改操作'),(21,3,'删除','3','sys_oper_type','','danger','N','0','admin','2026-10-12 09:34:20','',NULL,'删除操作'),(22,4,'授权','4','sys_oper_type','','primary','N','0','admin','2026-10-12 09:34:20','',NULL,'授权操作'),(23,5,'导出','5','sys_oper_type','','warning','N','0','admin','2026-10-12 09:34:20','',NULL,'导出操作'),(24,6,'导入','6','sys_oper_type','','warning','N','0','admin','2026-10-12 09:34:20','',NULL,'导入操作'),(25,7,'强退','7','sys_oper_type','','danger','N','0','admin','2026-10-12 09:34:20','',NULL,'强退操作'),(26,8,'生成代码','8','sys_oper_type','','warning','N','0','admin','2026-10-12 09:34:20','',NULL,'生成操作'),(27,9,'清空数据','9','sys_oper_type','','danger','N','0','admin','2026-10-12 09:34:20','',NULL,'清空操作'),(28,1,'成功','0','sys_common_status','','primary','N','0','admin','2026-10-12 09:34:20','',NULL,'正常状态'),(29,2,'失败','1','sys_common_status','','danger','N','0','admin','2026-10-12 09:34:20','',NULL,'停用状态');
/*!40000 ALTER TABLE `sys_dict_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_type`
--

DROP TABLE IF EXISTS `sys_dict_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type` (
  `dict_id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典主键',
  `dict_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典名称',
  `dict_type` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_id`),
  UNIQUE KEY `dict_type` (`dict_type`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_type`
--

LOCK TABLES `sys_dict_type` WRITE;
/*!40000 ALTER TABLE `sys_dict_type` DISABLE KEYS */;
INSERT INTO `sys_dict_type` VALUES (1,'用户性别','sys_user_sex','0','admin','2026-10-12 09:34:20','',NULL,'用户性别列表'),(2,'菜单状态','sys_show_hide','0','admin','2026-10-12 09:34:20','',NULL,'菜单状态列表'),(3,'系统开关','sys_normal_disable','0','admin','2026-10-12 09:34:20','',NULL,'系统开关列表'),(4,'任务状态','sys_job_status','0','admin','2026-10-12 09:34:20','',NULL,'任务状态列表'),(5,'任务分组','sys_job_group','0','admin','2026-10-12 09:34:20','',NULL,'任务分组列表'),(6,'系统是否','sys_yes_no','0','admin','2026-10-12 09:34:20','',NULL,'系统是否列表'),(7,'通知类型','sys_notice_type','0','admin','2026-10-12 09:34:20','',NULL,'通知类型列表'),(8,'通知状态','sys_notice_status','0','admin','2026-10-12 09:34:20','',NULL,'通知状态列表'),(9,'操作类型','sys_oper_type','0','admin','2026-10-12 09:34:20','',NULL,'操作类型列表'),(10,'系统状态','sys_common_status','0','admin','2026-10-12 09:34:20','',NULL,'登录状态列表');
/*!40000 ALTER TABLE `sys_dict_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job`
--

DROP TABLE IF EXISTS `sys_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job` (
  `job_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `job_name` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '任务名称',
  `job_group` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DEFAULT' COMMENT '任务组名',
  `invoke_target` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调用目标字符串',
  `cron_expression` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT 'cron执行表达式',
  `misfire_policy` varchar(20) COLLATE utf8mb4_general_ci DEFAULT '3' COMMENT '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  `concurrent` char(1) COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '是否并发执行（0允许 1禁止）',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1暂停）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '备注信息',
  PRIMARY KEY (`job_id`,`job_name`,`job_group`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务调度表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_job`
--

LOCK TABLES `sys_job` WRITE;
/*!40000 ALTER TABLE `sys_job` DISABLE KEYS */;
INSERT INTO `sys_job` VALUES (1,'系统默认（无参）','DEFAULT','ryTask.ryNoParams','0/10 * * * * ?','3','1','1','admin','2026-10-12 09:34:20','',NULL,''),(2,'系统默认（有参）','DEFAULT','ryTask.ryParams(\'ry\')','0/15 * * * * ?','3','1','1','admin','2026-10-12 09:34:20','',NULL,''),(3,'系统默认（多参）','DEFAULT','ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)','0/20 * * * * ?','3','1','1','admin','2026-10-12 09:34:20','',NULL,''),(100,'校园淘-超时未支付订单自动取消','DEFAULT','campusOrderTask.autoCancelTimeoutOrder','0 0/1 * * * ?','3','1','0','admin','2026-10-15 09:00:00','',NULL,'每分钟扫描一次：取消超时未支付订单并回补库存，已支付订单不受影响'),(101,'校园淘-物流轨迹自动同步','DEFAULT','campusLogisticsTask.syncShippedTrace','0 0/30 * * * ?','3','1','0','admin','2026-10-15 09:00:00','',NULL,'每30分钟为已发货且有物流单号的订单同步物流轨迹；查询通道未配置时任务自动空转');
/*!40000 ALTER TABLE `sys_job` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job_log`
--

DROP TABLE IF EXISTS `sys_job_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job_log` (
  `job_log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务日志ID',
  `job_name` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务组名',
  `invoke_target` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调用目标字符串',
  `job_message` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '日志信息',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '执行状态（0正常 1失败）',
  `exception_info` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '异常信息',
  `start_time` datetime DEFAULT NULL COMMENT '执行开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '执行结束时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`job_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务调度日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_job_log`
--

LOCK TABLES `sys_job_log` WRITE;
/*!40000 ALTER TABLE `sys_job_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_job_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_logininfor`
--

DROP TABLE IF EXISTS `sys_logininfor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_logininfor` (
  `info_id` bigint NOT NULL AUTO_INCREMENT COMMENT '访问ID',
  `user_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '用户账号',
  `ipaddr` varchar(128) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '登录IP地址',
  `login_location` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '登录地点',
  `browser` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '浏览器类型',
  `os` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '操作系统',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '登录状态（0成功 1失败）',
  `msg` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '提示消息',
  `login_time` datetime DEFAULT NULL COMMENT '访问时间',
  PRIMARY KEY (`info_id`),
  KEY `idx_sys_logininfor_s` (`status`),
  KEY `idx_sys_logininfor_lt` (`login_time`)
) ENGINE=InnoDB AUTO_INCREMENT=110 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统访问记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_logininfor`
--

LOCK TABLES `sys_logininfor` WRITE;
/*!40000 ALTER TABLE `sys_logininfor` DISABLE KEYS */;
INSERT INTO `sys_logininfor` VALUES (100,'admin','127.0.0.1','内网IP','Curl 8.21.0','','1','验证码已失效','2026-10-13 17:55:43'),(101,'admin','127.0.0.1','内网IP','Curl 8.21.0','','0','登录成功','2026-10-13 17:57:42'),(102,'admin','127.0.0.1','内网IP','Python-urllib 3.13','','0','登录成功','2026-10-13 18:10:47'),(103,'admin','127.0.0.1','内网IP','Python-urllib 3.13','','0','登录成功','2026-10-13 18:11:14'),(104,'admin','127.0.0.1','内网IP','Python-urllib 3.13','','0','登录成功','2026-10-13 18:12:04'),(105,'admin','127.0.0.1','内网IP','Python-urllib 3.13','','0','登录成功','2026-10-13 18:12:54'),(106,'admin','127.0.0.1','内网IP','Curl 8.21.0','','0','登录成功','2026-10-13 18:16:00'),(107,'admin','127.0.0.1','内网IP','Curl 8.21.0','','0','登录成功','2026-10-13 18:16:11'),(108,'admin','127.0.0.1','内网IP','Python-urllib 3.13','','0','登录成功','2026-10-13 18:17:46'),(109,'admin','127.0.0.1','内网IP','Edge 155','Windows >=10','0','登录成功','2026-10-13 18:27:14');
/*!40000 ALTER TABLE `sys_logininfor` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_menu`
--

DROP TABLE IF EXISTS `sys_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `menu_id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `menu_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '菜单名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父菜单ID',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `path` varchar(200) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '路由地址',
  `component` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '组件路径',
  `query` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '路由参数',
  `route_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '路由名称',
  `is_frame` int DEFAULT '1' COMMENT '是否为外链（0是 1否）',
  `is_cache` int DEFAULT '0' COMMENT '是否缓存（0缓存 1不缓存）',
  `menu_type` char(1) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
  `visible` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '菜单状态（0显示 1隐藏）',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',
  `perms` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '#' COMMENT '菜单图标',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '备注',
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='菜单权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_menu`
--

LOCK TABLES `sys_menu` WRITE;
/*!40000 ALTER TABLE `sys_menu` DISABLE KEYS */;
INSERT INTO `sys_menu` VALUES (1,'系统管理',0,1,'system',NULL,'','',1,0,'M','0','0','','system','admin','2026-10-12 09:34:19','',NULL,'系统管理目录'),(2,'系统监控',0,2,'monitor',NULL,'','',1,0,'M','0','0','','monitor','admin','2026-10-12 09:34:19','',NULL,'系统监控目录'),(3,'系统工具',0,3,'tool',NULL,'','',1,0,'M','0','0','','tool','admin','2026-10-12 09:34:19','',NULL,'系统工具目录'),(100,'用户管理',1,1,'user','system/user/index','','',1,0,'C','0','0','system:user:list','user','admin','2026-10-12 09:34:19','',NULL,'用户管理菜单'),(101,'角色管理',1,2,'role','system/role/index','','',1,0,'C','0','0','system:role:list','peoples','admin','2026-10-12 09:34:19','',NULL,'角色管理菜单'),(102,'菜单管理',1,3,'menu','system/menu/index','','',1,0,'C','0','0','system:menu:list','tree-table','admin','2026-10-12 09:34:19','',NULL,'菜单管理菜单'),(103,'部门管理',1,4,'dept','system/dept/index','','',1,0,'C','0','0','system:dept:list','tree','admin','2026-10-12 09:34:19','',NULL,'部门管理菜单'),(104,'岗位管理',1,5,'post','system/post/index','','',1,0,'C','0','0','system:post:list','post','admin','2026-10-12 09:34:19','',NULL,'岗位管理菜单'),(105,'字典管理',1,6,'dict','system/dict/index','','',1,0,'C','0','0','system:dict:list','dict','admin','2026-10-12 09:34:19','',NULL,'字典管理菜单'),(106,'参数设置',1,7,'config','system/config/index','','',1,0,'C','0','0','system:config:list','edit','admin','2026-10-12 09:34:19','',NULL,'参数设置菜单'),(107,'通知公告',1,8,'notice','system/notice/index','','',1,0,'C','0','0','system:notice:list','message','admin','2026-10-12 09:34:19','',NULL,'通知公告菜单'),(108,'日志管理',1,9,'log','','','',1,0,'M','0','0','','log','admin','2026-10-12 09:34:19','',NULL,'日志管理菜单'),(109,'在线用户',2,1,'online','monitor/online/index','','',1,0,'C','0','0','monitor:online:list','online','admin','2026-10-12 09:34:19','',NULL,'在线用户菜单'),(110,'定时任务',2,2,'job','monitor/job/index','','',1,0,'C','0','0','monitor:job:list','job','admin','2026-10-12 09:34:19','',NULL,'定时任务菜单'),(111,'数据监控',2,3,'druid','monitor/druid/index','','',1,0,'C','0','0','monitor:druid:list','druid','admin','2026-10-12 09:34:19','',NULL,'数据监控菜单'),(112,'服务监控',2,4,'server','monitor/server/index','','',1,0,'C','0','0','monitor:server:list','server','admin','2026-10-12 09:34:19','',NULL,'服务监控菜单'),(113,'缓存监控',2,5,'cache','monitor/cache/index','','',1,0,'C','0','0','monitor:cache:list','redis','admin','2026-10-12 09:34:19','',NULL,'缓存监控菜单'),(114,'缓存列表',2,6,'cacheList','monitor/cache/list','','',1,0,'C','0','0','monitor:cache:list','redis-list','admin','2026-10-12 09:34:19','',NULL,'缓存列表菜单'),(115,'表单构建',3,1,'build','tool/build/index','','',1,0,'C','0','0','tool:build:list','build','admin','2026-10-12 09:34:19','',NULL,'表单构建菜单'),(116,'代码生成',3,2,'gen','tool/gen/index','','',1,0,'C','0','0','tool:gen:list','code','admin','2026-10-12 09:34:19','',NULL,'代码生成菜单'),(117,'系统接口',3,3,'swagger','tool/swagger/index','','',1,0,'C','0','0','tool:swagger:list','swagger','admin','2026-10-12 09:34:19','',NULL,'系统接口菜单'),(500,'操作日志',108,1,'operlog','monitor/operlog/index','','',1,0,'C','0','0','monitor:operlog:list','form','admin','2026-10-12 09:34:19','',NULL,'操作日志菜单'),(501,'登录日志',108,2,'logininfor','monitor/logininfor/index','','',1,0,'C','0','0','monitor:logininfor:list','logininfor','admin','2026-10-12 09:34:19','',NULL,'登录日志菜单'),(1000,'用户查询',100,1,'','','','',1,0,'F','0','0','system:user:query','#','admin','2026-10-12 09:34:19','',NULL,''),(1001,'用户新增',100,2,'','','','',1,0,'F','0','0','system:user:add','#','admin','2026-10-12 09:34:19','',NULL,''),(1002,'用户修改',100,3,'','','','',1,0,'F','0','0','system:user:edit','#','admin','2026-10-12 09:34:19','',NULL,''),(1003,'用户删除',100,4,'','','','',1,0,'F','0','0','system:user:remove','#','admin','2026-10-12 09:34:19','',NULL,''),(1004,'用户导出',100,5,'','','','',1,0,'F','0','0','system:user:export','#','admin','2026-10-12 09:34:19','',NULL,''),(1005,'用户导入',100,6,'','','','',1,0,'F','0','0','system:user:import','#','admin','2026-10-12 09:34:19','',NULL,''),(1006,'重置密码',100,7,'','','','',1,0,'F','0','0','system:user:resetPwd','#','admin','2026-10-12 09:34:19','',NULL,''),(1007,'角色查询',101,1,'','','','',1,0,'F','0','0','system:role:query','#','admin','2026-10-12 09:34:19','',NULL,''),(1008,'角色新增',101,2,'','','','',1,0,'F','0','0','system:role:add','#','admin','2026-10-12 09:34:19','',NULL,''),(1009,'角色修改',101,3,'','','','',1,0,'F','0','0','system:role:edit','#','admin','2026-10-12 09:34:19','',NULL,''),(1010,'角色删除',101,4,'','','','',1,0,'F','0','0','system:role:remove','#','admin','2026-10-12 09:34:19','',NULL,''),(1011,'角色导出',101,5,'','','','',1,0,'F','0','0','system:role:export','#','admin','2026-10-12 09:34:19','',NULL,''),(1012,'菜单查询',102,1,'','','','',1,0,'F','0','0','system:menu:query','#','admin','2026-10-12 09:34:19','',NULL,''),(1013,'菜单新增',102,2,'','','','',1,0,'F','0','0','system:menu:add','#','admin','2026-10-12 09:34:19','',NULL,''),(1014,'菜单修改',102,3,'','','','',1,0,'F','0','0','system:menu:edit','#','admin','2026-10-12 09:34:19','',NULL,''),(1015,'菜单删除',102,4,'','','','',1,0,'F','0','0','system:menu:remove','#','admin','2026-10-12 09:34:19','',NULL,''),(1016,'部门查询',103,1,'','','','',1,0,'F','0','0','system:dept:query','#','admin','2026-10-12 09:34:19','',NULL,''),(1017,'部门新增',103,2,'','','','',1,0,'F','0','0','system:dept:add','#','admin','2026-10-12 09:34:19','',NULL,''),(1018,'部门修改',103,3,'','','','',1,0,'F','0','0','system:dept:edit','#','admin','2026-10-12 09:34:19','',NULL,''),(1019,'部门删除',103,4,'','','','',1,0,'F','0','0','system:dept:remove','#','admin','2026-10-12 09:34:19','',NULL,''),(1020,'岗位查询',104,1,'','','','',1,0,'F','0','0','system:post:query','#','admin','2026-10-12 09:34:19','',NULL,''),(1021,'岗位新增',104,2,'','','','',1,0,'F','0','0','system:post:add','#','admin','2026-10-12 09:34:19','',NULL,''),(1022,'岗位修改',104,3,'','','','',1,0,'F','0','0','system:post:edit','#','admin','2026-10-12 09:34:19','',NULL,''),(1023,'岗位删除',104,4,'','','','',1,0,'F','0','0','system:post:remove','#','admin','2026-10-12 09:34:19','',NULL,''),(1024,'岗位导出',104,5,'','','','',1,0,'F','0','0','system:post:export','#','admin','2026-10-12 09:34:19','',NULL,''),(1025,'字典查询',105,1,'#','','','',1,0,'F','0','0','system:dict:query','#','admin','2026-10-12 09:34:19','',NULL,''),(1026,'字典新增',105,2,'#','','','',1,0,'F','0','0','system:dict:add','#','admin','2026-10-12 09:34:19','',NULL,''),(1027,'字典修改',105,3,'#','','','',1,0,'F','0','0','system:dict:edit','#','admin','2026-10-12 09:34:19','',NULL,''),(1028,'字典删除',105,4,'#','','','',1,0,'F','0','0','system:dict:remove','#','admin','2026-10-12 09:34:19','',NULL,''),(1029,'字典导出',105,5,'#','','','',1,0,'F','0','0','system:dict:export','#','admin','2026-10-12 09:34:19','',NULL,''),(1030,'参数查询',106,1,'#','','','',1,0,'F','0','0','system:config:query','#','admin','2026-10-12 09:34:19','',NULL,''),(1031,'参数新增',106,2,'#','','','',1,0,'F','0','0','system:config:add','#','admin','2026-10-12 09:34:19','',NULL,''),(1032,'参数修改',106,3,'#','','','',1,0,'F','0','0','system:config:edit','#','admin','2026-10-12 09:34:19','',NULL,''),(1033,'参数删除',106,4,'#','','','',1,0,'F','0','0','system:config:remove','#','admin','2026-10-12 09:34:19','',NULL,''),(1034,'参数导出',106,5,'#','','','',1,0,'F','0','0','system:config:export','#','admin','2026-10-12 09:34:19','',NULL,''),(1035,'公告查询',107,1,'#','','','',1,0,'F','0','0','system:notice:query','#','admin','2026-10-12 09:34:19','',NULL,''),(1036,'公告新增',107,2,'#','','','',1,0,'F','0','0','system:notice:add','#','admin','2026-10-12 09:34:19','',NULL,''),(1037,'公告修改',107,3,'#','','','',1,0,'F','0','0','system:notice:edit','#','admin','2026-10-12 09:34:19','',NULL,''),(1038,'公告删除',107,4,'#','','','',1,0,'F','0','0','system:notice:remove','#','admin','2026-10-12 09:34:19','',NULL,''),(1039,'操作查询',500,1,'#','','','',1,0,'F','0','0','monitor:operlog:query','#','admin','2026-10-12 09:34:19','',NULL,''),(1040,'操作删除',500,2,'#','','','',1,0,'F','0','0','monitor:operlog:remove','#','admin','2026-10-12 09:34:19','',NULL,''),(1041,'日志导出',500,3,'#','','','',1,0,'F','0','0','monitor:operlog:export','#','admin','2026-10-12 09:34:19','',NULL,''),(1042,'登录查询',501,1,'#','','','',1,0,'F','0','0','monitor:logininfor:query','#','admin','2026-10-12 09:34:19','',NULL,''),(1043,'登录删除',501,2,'#','','','',1,0,'F','0','0','monitor:logininfor:remove','#','admin','2026-10-12 09:34:19','',NULL,''),(1044,'日志导出',501,3,'#','','','',1,0,'F','0','0','monitor:logininfor:export','#','admin','2026-10-12 09:34:19','',NULL,''),(1045,'账户解锁',501,4,'#','','','',1,0,'F','0','0','monitor:logininfor:unlock','#','admin','2026-10-12 09:34:19','',NULL,''),(1046,'在线查询',109,1,'#','','','',1,0,'F','0','0','monitor:online:query','#','admin','2026-10-12 09:34:19','',NULL,''),(1047,'批量强退',109,2,'#','','','',1,0,'F','0','0','monitor:online:batchLogout','#','admin','2026-10-12 09:34:19','',NULL,''),(1048,'单条强退',109,3,'#','','','',1,0,'F','0','0','monitor:online:forceLogout','#','admin','2026-10-12 09:34:19','',NULL,''),(1049,'任务查询',110,1,'#','','','',1,0,'F','0','0','monitor:job:query','#','admin','2026-10-12 09:34:19','',NULL,''),(1050,'任务新增',110,2,'#','','','',1,0,'F','0','0','monitor:job:add','#','admin','2026-10-12 09:34:19','',NULL,''),(1051,'任务修改',110,3,'#','','','',1,0,'F','0','0','monitor:job:edit','#','admin','2026-10-12 09:34:19','',NULL,''),(1052,'任务删除',110,4,'#','','','',1,0,'F','0','0','monitor:job:remove','#','admin','2026-10-12 09:34:19','',NULL,''),(1053,'状态修改',110,5,'#','','','',1,0,'F','0','0','monitor:job:changeStatus','#','admin','2026-10-12 09:34:19','',NULL,''),(1054,'任务导出',110,6,'#','','','',1,0,'F','0','0','monitor:job:export','#','admin','2026-10-12 09:34:19','',NULL,''),(1055,'生成查询',116,1,'#','','','',1,0,'F','0','0','tool:gen:query','#','admin','2026-10-12 09:34:19','',NULL,''),(1056,'生成修改',116,2,'#','','','',1,0,'F','0','0','tool:gen:edit','#','admin','2026-10-12 09:34:19','',NULL,''),(1057,'生成删除',116,3,'#','','','',1,0,'F','0','0','tool:gen:remove','#','admin','2026-10-12 09:34:19','',NULL,''),(1058,'导入代码',116,4,'#','','','',1,0,'F','0','0','tool:gen:import','#','admin','2026-10-12 09:34:19','',NULL,''),(1059,'预览代码',116,5,'#','','','',1,0,'F','0','0','tool:gen:preview','#','admin','2026-10-12 09:34:19','',NULL,''),(1060,'生成代码',116,6,'#','','','',1,0,'F','0','0','tool:gen:code','#','admin','2026-10-12 09:34:19','',NULL,'');
INSERT INTO `sys_menu` VALUES (2000,'校园淘',0,5,'campus',NULL,'','',1,0,'M','0','0','','education','admin','2026-10-12 09:34:19','',NULL,'校园二手交易目录'),(2001,'商品管理',2000,1,'product','campus/product/index','','',1,0,'C','0','0','campus:product:list','money','admin','2026-10-12 09:34:19','',NULL,'商品管理菜单'),(2002,'分类管理',2000,2,'category','campus/category/index','','',1,0,'C','0','0','campus:category:list','tree','admin','2026-10-12 09:34:19','',NULL,'分类管理菜单'),(2003,'购物车',2000,3,'cart','campus/cart/index','','',1,0,'C','0','0','campus:cart:list','shopping','admin','2026-10-12 09:34:19','',NULL,'购物车菜单'),(2004,'我的订单',2000,4,'order','campus/order/index','','',1,0,'C','0','0','campus:order:list','list','admin','2026-10-12 09:34:19','',NULL,'我的订单菜单'),(2005,'支付账户',2000,5,'account','campus/account/index','','',1,0,'C','0','0','campus:account:balance','money','admin','2026-10-12 09:34:19','',NULL,'支付账户菜单'),(2006,'售后管理',2000,6,'aftersale','campus/aftersale/index','','',1,0,'C','0','0','campus:aftersale:list','warning','admin','2026-10-12 09:34:19','',NULL,'售后管理菜单'),(2100,'商品新增',2001,1,'','','','',1,0,'F','0','0','campus:product:add','#','admin','2026-10-12 09:34:19','',NULL,''),(2101,'商品修改',2001,2,'','','','',1,0,'F','0','0','campus:product:edit','#','admin','2026-10-12 09:34:19','',NULL,''),(2102,'商品删除',2001,3,'','','','',1,0,'F','0','0','campus:product:remove','#','admin','2026-10-12 09:34:19','',NULL,''),(2103,'分类新增',2002,1,'','','','',1,0,'F','0','0','campus:category:add','#','admin','2026-10-12 09:34:19','',NULL,''),(2104,'分类修改',2002,2,'','','','',1,0,'F','0','0','campus:category:edit','#','admin','2026-10-12 09:34:19','',NULL,''),(2105,'分类删除',2002,3,'','','','',1,0,'F','0','0','campus:category:remove','#','admin','2026-10-12 09:34:19','',NULL,''),(2106,'购物车新增',2003,1,'','','','',1,0,'F','0','0','campus:cart:add','#','admin','2026-10-12 09:34:19','',NULL,''),(2107,'购物车修改',2003,2,'','','','',1,0,'F','0','0','campus:cart:edit','#','admin','2026-10-12 09:34:19','',NULL,''),(2108,'购物车删除',2003,3,'','','','',1,0,'F','0','0','campus:cart:remove','#','admin','2026-10-12 09:34:19','',NULL,''),(2109,'订单创建',2004,1,'','','','',1,0,'F','0','0','campus:order:add','#','admin','2026-10-12 09:34:19','',NULL,''),(2110,'订单操作',2004,2,'','','','',1,0,'F','0','0','campus:order:edit','#','admin','2026-10-12 09:34:19','',NULL,''),(2111,'账户充值',2005,1,'','','','',1,0,'F','0','0','campus:account:edit','#','admin','2026-10-12 09:34:19','',NULL,''),(2112,'售后申请',2006,1,'','','','',1,0,'F','0','0','campus:aftersale:add','#','admin','2026-10-12 09:34:19','',NULL,''),(2113,'售后审核',2006,2,'','','','',1,0,'F','0','0','campus:aftersale:edit','#','admin','2026-10-12 09:34:19','',NULL,''),(2114,'商品评价',2004,3,'','','','',1,0,'F','0','0','campus:review:add','#','admin','2026-10-12 09:34:19','',NULL,''),(2007,'通知外呼日志',2000,7,'notifylog','campus/notifylog/index','','',1,0,'C','0','0','campus:notifylog:list','log','admin','2026-10-12 09:34:19','',NULL,'短信/邮件外呼留痕（仅管理员可见）');
-- v1.0.15 买家侧旅程与评价管理：2008 校园商城 / 2009 商品详情(隐藏) / 2010 评价管理
INSERT INTO `sys_menu` VALUES (2008,'校园商城',2000,8,'mall','campus/mall/index','','',1,0,'C','0','0','','shopping','admin','2026-10-16 00:00:00','',NULL,'买家浏览在售二手商品'),(2009,'商品详情',2000,9,'mall-detail','campus/mall/detail','','',1,0,'C','1','0','','','admin','2026-10-16 00:00:00','',NULL,'商品详情页（隐藏菜单，由商品卡片跳转进入）'),(2010,'评价管理',2000,10,'review','campus/review/index','','',1,0,'C','0','0','campus:review:list','star','admin','2026-10-16 00:00:00','',NULL,'卖家查看本人名下商品收到的评价');
/*!40000 ALTER TABLE `sys_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_notice`
--

DROP TABLE IF EXISTS `sys_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notice` (
  `notice_id` int NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `notice_title` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '公告标题',
  `notice_type` char(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '公告类型（1通知 2公告）',
  `notice_content` longblob COMMENT '公告内容',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '公告状态（0正常 1关闭）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`notice_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知公告表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_notice`
--

LOCK TABLES `sys_notice` WRITE;
/*!40000 ALTER TABLE `sys_notice` DISABLE KEYS */;
INSERT INTO `sys_notice` VALUES (1,'温馨提醒：2018-07-01 若依新版本发布啦','2',_binary '新版本内容','0','admin','2026-10-12 09:34:20','',NULL,'管理员'),(2,'维护通知：2018-07-01 若依系统凌晨维护','1',_binary '维护内容','0','admin','2026-10-12 09:34:20','',NULL,'管理员'),(3,'若依开源框架介绍','1',_binary '<p><span style=\"color: rgb(230, 0, 0);\">项目介绍</span></p><p><font color=\"#333333\">RuoYi开源项目是为企业用户定制的后台脚手架框架，为企业打造的一站式解决方案，降低企业开发成本，提升开发效率。主要包括用户管理、角色管理、部门管理、菜单管理、参数管理、字典管理、</font><span style=\"color: rgb(51, 51, 51);\">岗位管理</span><span style=\"color: rgb(51, 51, 51);\">、定时任务</span><span style=\"color: rgb(51, 51, 51);\">、</span><span style=\"color: rgb(51, 51, 51);\">服务监控、登录日志、操作日志、代码生成等功能。其中，还支持多数据源、数据权限、国际化、Redis缓存、Docker部署、滑动验证码、第三方认证登录、分布式事务、</span><font color=\"#333333\">分布式文件存储</font><span style=\"color: rgb(51, 51, 51);\">、分库分表处理等技术特点。</span></p><p><img src=\"https://foruda.gitee.com/images/1773931848342439032/a4d22313_1815095.png\" style=\"width: 64px;\"><br></p><p><span style=\"color: rgb(230, 0, 0);\">官网及演示</span></p><p><span style=\"color: rgb(51, 51, 51);\">若依官网地址：&nbsp;</span><a href=\"http://ruoyi.vip\" target=\"_blank\">http://ruoyi.vip</a><a href=\"http://ruoyi.vip\" target=\"_blank\"></a></p><p><span style=\"color: rgb(51, 51, 51);\">若依文档地址：&nbsp;</span><a href=\"http://doc.ruoyi.vip\" target=\"_blank\">http://doc.ruoyi.vip</a><br></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【不分离版】：&nbsp;</span><a href=\"http://demo.ruoyi.vip\" target=\"_blank\">http://demo.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【分离版本】：&nbsp;</span><a href=\"http://vue.ruoyi.vip\" target=\"_blank\">http://vue.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【微服务版】：&nbsp;</span><a href=\"http://cloud.ruoyi.vip\" target=\"_blank\">http://cloud.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【移动端版】：&nbsp;</span><a href=\"http://h5.ruoyi.vip\" target=\"_blank\">http://h5.ruoyi.vip</a></p><p><br style=\"color: rgb(48, 49, 51); font-family: &quot;Helvetica Neue&quot;, Helvetica, Arial, sans-serif; font-size: 12px;\"></p>','0','admin','2026-10-12 09:34:20','',NULL,'管理员');
/*!40000 ALTER TABLE `sys_notice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_notice_read`
--

DROP TABLE IF EXISTS `sys_notice_read`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notice_read` (
  `read_id` bigint NOT NULL AUTO_INCREMENT COMMENT '已读主键',
  `notice_id` int NOT NULL COMMENT '公告id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `read_time` datetime NOT NULL COMMENT '阅读时间',
  PRIMARY KEY (`read_id`),
  UNIQUE KEY `uk_user_notice` (`user_id`,`notice_id`) COMMENT '同一用户同一公告只记录一次'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公告已读记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_notice_read`
--

LOCK TABLES `sys_notice_read` WRITE;
/*!40000 ALTER TABLE `sys_notice_read` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_notice_read` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_oper_log`
--

DROP TABLE IF EXISTS `sys_oper_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_oper_log` (
  `oper_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `title` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '模块标题',
  `business_type` int DEFAULT '0' COMMENT '业务类型（0其它 1新增 2修改 3删除）',
  `method` varchar(200) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '方法名称',
  `request_method` varchar(10) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '请求方式',
  `operator_type` int DEFAULT '0' COMMENT '操作类别（0其它 1后台用户 2手机端用户）',
  `oper_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '操作人员',
  `dept_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '部门名称',
  `oper_url` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '请求URL',
  `oper_ip` varchar(128) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '主机地址',
  `oper_location` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '操作地点',
  `oper_param` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '请求参数',
  `json_result` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '返回参数',
  `status` int DEFAULT '0' COMMENT '操作状态（0正常 1异常）',
  `error_msg` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '错误消息',
  `oper_time` datetime DEFAULT NULL COMMENT '操作时间',
  `cost_time` bigint DEFAULT '0' COMMENT '消耗时间',
  PRIMARY KEY (`oper_id`),
  KEY `idx_sys_oper_log_bt` (`business_type`),
  KEY `idx_sys_oper_log_s` (`status`),
  KEY `idx_sys_oper_log_ot` (`oper_time`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_oper_log`
--

LOCK TABLES `sys_oper_log` WRITE;
/*!40000 ALTER TABLE `sys_oper_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_oper_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_post`
--

DROP TABLE IF EXISTS `sys_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_post` (
  `post_id` bigint NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  `post_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '岗位编码',
  `post_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '岗位名称',
  `post_sort` int NOT NULL COMMENT '显示顺序',
  `status` char(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`post_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='岗位信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_post`
--

LOCK TABLES `sys_post` WRITE;
/*!40000 ALTER TABLE `sys_post` DISABLE KEYS */;
INSERT INTO `sys_post` VALUES (1,'ceo','董事长',1,'0','admin','2026-10-12 09:34:19','',NULL,''),(2,'se','项目经理',2,'0','admin','2026-10-12 09:34:19','',NULL,''),(3,'hr','人力资源',3,'0','admin','2026-10-12 09:34:19','',NULL,''),(4,'user','普通员工',4,'0','admin','2026-10-12 09:34:19','',NULL,'');
/*!40000 ALTER TABLE `sys_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `role_id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色名称',
  `role_key` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色权限字符串',
  `role_sort` int NOT NULL COMMENT '显示顺序',
  `data_scope` char(1) COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  `menu_check_strictly` tinyint(1) DEFAULT '1' COMMENT '菜单树选择项是否关联显示',
  `dept_check_strictly` tinyint(1) DEFAULT '1' COMMENT '部门树选择项是否关联显示',
  `status` char(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色状态（0正常 1停用）',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role`
--

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` VALUES (1,'超级管理员','admin',1,'1',1,1,'0','0','admin','2026-10-12 09:34:19','',NULL,'超级管理员'),(2,'普通角色','common',2,'2',1,1,'0','0','admin','2026-10-12 09:34:19','',NULL,'普通角色');
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role_dept`
--

DROP TABLE IF EXISTS `sys_role_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_dept` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `dept_id` bigint NOT NULL COMMENT '部门ID',
  PRIMARY KEY (`role_id`,`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色和部门关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_dept`
--

LOCK TABLES `sys_role_dept` WRITE;
/*!40000 ALTER TABLE `sys_role_dept` DISABLE KEYS */;
INSERT INTO `sys_role_dept` VALUES (2,100),(2,101),(2,105);
/*!40000 ALTER TABLE `sys_role_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role_menu`
--

DROP TABLE IF EXISTS `sys_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`,`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色和菜单关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_menu`
--

LOCK TABLES `sys_role_menu` WRITE;
/*!40000 ALTER TABLE `sys_role_menu` DISABLE KEYS */;
INSERT INTO `sys_role_menu` VALUES (2,1),(2,2),(2,3),(2,100),(2,101),(2,102),(2,103),(2,104),(2,105),(2,106),(2,107),(2,108),(2,109),(2,110),(2,111),(2,112),(2,113),(2,114),(2,115),(2,116),(2,117),(2,500),(2,501),(2,1000),(2,1001),(2,1002),(2,1003),(2,1004),(2,1005),(2,1006),(2,1007),(2,1008),(2,1009),(2,1010),(2,1011),(2,1012),(2,1013),(2,1014),(2,1015),(2,1016),(2,1017),(2,1018),(2,1019),(2,1020),(2,1021),(2,1022),(2,1023),(2,1024),(2,1025),(2,1026),(2,1027),(2,1028),(2,1029),(2,1030),(2,1031),(2,1032),(2,1033),(2,1034),(2,1035),(2,1036),(2,1037),(2,1038),(2,1039),(2,1040),(2,1041),(2,1042),(2,1043),(2,1044),(2,1045),(2,1046),(2,1047),(2,1048),(2,1049),(2,1050),(2,1051),(2,1052),(2,1053),(2,1054),(2,1055),(2,1056),(2,1057),(2,1058),(2,1059),(2,1060);
INSERT INTO `sys_role_menu` VALUES (2,2000),(2,2001),(2,2003),(2,2004),(2,2005),(2,2006),(2,2100),(2,2101),(2,2102),(2,2106),(2,2107),(2,2108),(2,2109),(2,2110),(2,2111),(2,2112),(2,2114);
-- v1.0.15 新增菜单授予普通角色
INSERT INTO `sys_role_menu` VALUES (2,2008),(2,2009),(2,2010);
/*!40000 ALTER TABLE `sys_role_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID',
  `user_name` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户账号',
  `nick_name` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户昵称',
  `user_type` varchar(2) COLLATE utf8mb4_general_ci DEFAULT '00' COMMENT '用户类型（00系统用户）',
  `email` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '用户邮箱',
  `phonenumber` varchar(11) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '手机号码',
  `sex` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '用户性别（0男 1女 2未知）',
  `avatar` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '头像地址',
  `password` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '密码',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '账号状态（0正常 1停用）',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `login_ip` varchar(128) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '最后登录IP',
  `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
  `pwd_update_date` datetime DEFAULT NULL COMMENT '密码最后更新时间',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,103,'admin','若依','00','ry@163.com','15888888888','1','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','0','0','127.0.0.1','2026-10-13 18:27:15','2026-10-12 09:34:19','admin','2026-10-12 09:34:19','',NULL,'管理员'),(2,105,'ry','若依','00','ry@qq.com','15666666666','1','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','0','0','127.0.0.1','2026-10-12 09:34:19','2026-10-12 09:34:19','admin','2026-10-12 09:34:19','',NULL,'测试员'),(3,105,'liyutong','李雨桐','00','liyutong@stu.edu.cn','13910000003','1','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','0','0',NULL,NULL,NULL,'admin','2026-08-18 09:00:00','',NULL,'在校学生·2023级计算机科学与技术'),(4,105,'wanghao','王浩','00','wanghao@stu.edu.cn','13910000004','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','0','0',NULL,NULL,NULL,'admin','2026-08-18 09:00:00','',NULL,'在校学生·2022级电子信息工程'),(5,105,'zhaomin','赵敏','00','zhaomin@stu.edu.cn','13910000005','1','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','0','0',NULL,NULL,NULL,'admin','2026-08-18 09:00:00','',NULL,'在校学生·2024级英语'),(6,105,'chenxinyi','陈心怡','00','chenxinyi@stu.edu.cn','13910000006','1','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','0','0',NULL,NULL,NULL,'admin','2026-08-18 09:00:00','',NULL,'在校学生·2023级会计学'),(7,105,'zhoukai','周恺','00','zhoukai@stu.edu.cn','13910000007','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','0','0',NULL,NULL,NULL,'admin','2026-08-18 09:00:00','',NULL,'在校学生·2022级机械设计制造'),(8,105,'liuzhiyuan','刘志远','00','liuzhiyuan@stu.edu.cn','13910000008','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','0','0',NULL,NULL,NULL,'admin','2026-08-18 09:00:00','',NULL,'在校学生·2024级软件工程');
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_post`
--

DROP TABLE IF EXISTS `sys_user_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_post` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `post_id` bigint NOT NULL COMMENT '岗位ID',
  PRIMARY KEY (`user_id`,`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户与岗位关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_post`
--

LOCK TABLES `sys_user_post` WRITE;
/*!40000 ALTER TABLE `sys_user_post` DISABLE KEYS */;
INSERT INTO `sys_user_post` VALUES (1,1),(2,2);
/*!40000 ALTER TABLE `sys_user_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户和角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_role`
--

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` VALUES (1,1),(2,2),(3,2),(4,2),(5,2),(6,2),(7,2),(8,2);
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'campus_tao'
--

--
-- Dumping routines for database 'campus_tao'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-13 23:10:56
