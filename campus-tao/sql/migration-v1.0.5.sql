-- 校园淘 v1.0.5 增量迁移：可观测性与运营侧能力
-- 1) 订单表新增物流单号
ALTER TABLE `campus_order`
  ADD COLUMN `logistics_no` varchar(64) DEFAULT '' COMMENT '物流单号' AFTER `ship_time`;

-- 2) 站内通知表
CREATE TABLE IF NOT EXISTS `campus_notice` (
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

-- 3) 超时未支付订单自动取消定时任务（每分钟扫描）
INSERT INTO `sys_job` VALUES (100,'校园淘-超时未支付订单自动取消','DEFAULT','campusOrderTask.autoCancelTimeoutOrder','0 0/1 * * * ?','3','1','0','admin','2026-10-15 09:00:00','',NULL,'每分钟扫描一次：取消超时未支付订单并回补库存，已支付订单不受影响');
