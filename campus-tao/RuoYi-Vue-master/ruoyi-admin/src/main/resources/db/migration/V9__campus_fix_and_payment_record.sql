-- V9：功能/数据整改（P0-2 售后状态还原、P1-2 直付确认留痕、P1-3 支付流水、P1-4 卖家索引、P2 定时任务启用）
--
-- 一、本版本解决的问题（均为"影响功能/数据正确性"的项）
--
--   1) 售后驳回后订单状态自相矛盾：驳回分支把订单直接置为已完成(3)，既丢失了
--      原状态（可能是已发货(2)），又不写 finish_time，导致同一订单"已完成但无完成时间"。
--      修法：申请售后时用 order_status_before 记录原状态，驳回时还原。
--   2) 直付确认收款无操作留痕：卖家点"确认已收款"后只改状态、写 pay_time，
--      无法追溯"是谁、何时"确认的。修法：加 pay_confirm_time / pay_confirm_by。
--   3) 支付渠道交易号无落库位置：对账需要 channelTradeNo，之前注释里承认只能丢弃。
--      修法：新增 campus_payment_record 流水表，支付/退款成功时落库。
--   4) campus_order.seller_id 无索引：卖家维度列表查询走全表扫描。修法：建复合索引。
--   5) 校园淘两个定时任务（超时取消 / 物流同步）默认为停用，功能等于没接上。修法：启用。
--
-- 二、可重入（与 V6/V7/V8 相同：迁移中断重跑、DBA 预建都不能炸启动）

-- ---------- 1. campus_aftersale 加 order_status_before（P0-2） ----------
SET @add_osb = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'campus_aftersale' AND column_name = 'order_status_before') = 0,
    'ALTER TABLE `campus_aftersale` ADD COLUMN `order_status_before` tinyint DEFAULT NULL COMMENT ''申请售后前的订单状态，驳回时用于还原''',
    'SELECT ''order_status_before 已存在，跳过'' AS skipped'));
PREPARE stmt FROM @add_osb; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. campus_order 加 pay_confirm_time / pay_confirm_by（P1-2） ----------
SET @add_pct = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'campus_order' AND column_name = 'pay_confirm_time') = 0,
    'ALTER TABLE `campus_order` ADD COLUMN `pay_confirm_time` datetime DEFAULT NULL COMMENT ''卖家确认收款时间(直付)'' AFTER `pay_time`',
    'SELECT ''pay_confirm_time 已存在，跳过'' AS skipped'));
PREPARE stmt FROM @add_pct; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_pcb = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'campus_order' AND column_name = 'pay_confirm_by') = 0,
    'ALTER TABLE `campus_order` ADD COLUMN `pay_confirm_by` bigint DEFAULT NULL COMMENT ''确认收款操作人ID(卖家)'' AFTER `pay_confirm_time`',
    'SELECT ''pay_confirm_by 已存在，跳过'' AS skipped'));
PREPARE stmt FROM @add_pcb; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. campus_order.seller_id 建索引（P1-4） ----------
SET @add_idx = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'campus_order' AND index_name = 'idx_seller_time') = 0,
    'ALTER TABLE `campus_order` ADD INDEX `idx_seller_time` (`seller_id`, `create_time`)',
    'SELECT ''idx_seller_time 已存在，跳过'' AS skipped'));
PREPARE stmt FROM @add_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 4. 支付流水表（P1-3） ----------
CREATE TABLE IF NOT EXISTS `campus_payment_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `order_no` varchar(32) NOT NULL COMMENT '订单编号',
  `user_id` bigint NOT NULL COMMENT '付款/退款用户ID',
  `seller_id` bigint DEFAULT NULL COMMENT '卖家ID(直付收款方，可空)',
  `amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '金额',
  `trade_type` varchar(16) NOT NULL COMMENT '交易类型 PAY支付 REFUND退款',
  `channel` varchar(32) NOT NULL DEFAULT '' COMMENT '支付渠道 sandbox/wechat',
  `channel_trade_no` varchar(64) DEFAULT '' COMMENT '渠道交易号(对账核心字段)',
  `status` varchar(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '结果 SUCCESS/FAIL',
  `remark` varchar(255) DEFAULT '' COMMENT '备注(失败原因等)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_channel_trade_no` (`channel_trade_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付流水(校园淘)';

-- ---------- 5. 启用校园淘定时任务（P2） ----------
UPDATE `sys_job` SET `status` = '1' WHERE `job_id` IN (100, 101) AND `status` = '0';