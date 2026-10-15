-- ============================================================
-- 校园淘 增量迁移脚本 v1.0.7
-- 面向「已建库」环境；全新环境请直接使用 campus_tao_full.sql（唯一建库源）。
--
-- 本次新增：
--   1) campus_logistics_trace  物流轨迹节点（承运商 Provider 查询结果落库）
--   2) campus_notify_log       短信/邮件外呼留痕
--   3) sys_job  101            「校园淘-物流轨迹自动同步」定时任务
--   4) sys_menu 2007           「通知外呼日志」菜单（仅管理员）
--
-- 执行方式（示例）：
--   mysql -uroot -p123456 campus_tao < migration-v1.0.7.sql
-- ============================================================

USE `campus_tao`;

-- ---------- 1. 物流轨迹节点 ----------
CREATE TABLE IF NOT EXISTS `campus_logistics_trace` (
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

-- ---------- 2. 通知外呼留痕 ----------
CREATE TABLE IF NOT EXISTS `campus_notify_log` (
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

-- ---------- 3. 物流轨迹自动同步定时任务 ----------
INSERT IGNORE INTO `sys_job` (`job_id`,`job_name`,`job_group`,`invoke_target`,`cron_expression`,`misfire_policy`,`concurrent`,`status`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`)
VALUES (101,'校园淘-物流轨迹自动同步','DEFAULT','campusLogisticsTask.syncShippedTrace','0 0/30 * * * ?','3','1','0','admin',NOW(),'',NULL,'每30分钟为已发货且有物流单号的订单同步物流轨迹；查询通道未配置时任务自动空转');

-- ---------- 4. 通知外呼日志菜单（仅管理员，故不写入 sys_role_menu）----------
INSERT IGNORE INTO `sys_menu` (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`)
VALUES (2007,'通知外呼日志',2000,7,'notifylog','campus/notifylog/index','','',1,0,'C','0','0','campus:notifylog:list','log','admin',NOW(),'',NULL,'短信/邮件外呼留痕（仅管理员可见）');
