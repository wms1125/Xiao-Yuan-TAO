-- U9：回滚 V9（功能/数据整改 + 支付流水表）
-- 顺序：先删新增表与索引，再删新增列，最后还原定时任务状态。可重入：判存后再执行。

-- ---------- 1. 删支付流水表 ----------
DROP TABLE IF EXISTS `campus_payment_record`;

-- ---------- 2. campus_order 删索引 idx_seller_time ----------
SET @drop_idx = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'campus_order' AND index_name = 'idx_seller_time') > 0,
    'ALTER TABLE `campus_order` DROP INDEX `idx_seller_time`',
    'SELECT ''idx_seller_time 不存在，跳过'' AS skipped'));
PREPARE stmt FROM @drop_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. campus_order 删列 pay_confirm_by / pay_confirm_time ----------
SET @drop_pcb = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'campus_order' AND column_name = 'pay_confirm_by') > 0,
    'ALTER TABLE `campus_order` DROP COLUMN `pay_confirm_by`',
    'SELECT ''pay_confirm_by 不存在，跳过'' AS skipped'));
PREPARE stmt FROM @drop_pcb; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @drop_pct = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'campus_order' AND column_name = 'pay_confirm_time') > 0,
    'ALTER TABLE `campus_order` DROP COLUMN `pay_confirm_time`',
    'SELECT ''pay_confirm_time 不存在，跳过'' AS skipped'));
PREPARE stmt FROM @drop_pct; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 4. campus_aftersale 删列 order_status_before ----------
SET @drop_osb = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'campus_aftersale' AND column_name = 'order_status_before') > 0,
    'ALTER TABLE `campus_aftersale` DROP COLUMN `order_status_before`',
    'SELECT ''order_status_before 不存在，跳过'' AS skipped'));
PREPARE stmt FROM @drop_osb; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 5. 还原定时任务为 V9 之前的停用状态 ----------
-- V9 曾把 100/101 置为启用('1')；回滚即恢复为停用('0')。
UPDATE `sys_job` SET `status` = '0' WHERE `job_id` IN (100, 101) AND `status` = '1';