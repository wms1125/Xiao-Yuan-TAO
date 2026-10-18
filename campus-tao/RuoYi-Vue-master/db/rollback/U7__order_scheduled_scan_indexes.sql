-- U7：回滚 V7（campus_order 定时任务扫描索引）
--
-- 警告：回滚后两条定时任务扫描（超时未支付、物流轨迹同步）在订单量上来后
--       会退回「读几十万行 + filesort」的慢路径（百万行实测：Q7 读 30 万行、
--       Q8 读 24.8 万行，均带 filesort）。仅在确认不再需要这两条索引时执行。
--
-- 与 V7 同样的理由：本脚本也要可重入。MySQL 没有 DROP INDEX IF EXISTS，
-- 若索引已不存在（回滚被中断后重跑、或手工删过），直接 DROP 会报错并中断。
-- 故一律走 information_schema 判存 + 动态 SQL。

-- ---------- 1. (status, ship_time) ----------
SET @drop_status_ship = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'campus_order' AND index_name = 'idx_status_ship') > 0,
    'ALTER TABLE `campus_order` DROP INDEX `idx_status_ship`',
    'SELECT ''idx_status_ship 不存在，跳过'' AS skipped'));
PREPARE stmt FROM @drop_status_ship; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. (status, create_time) ----------
SET @drop_status_time = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'campus_order' AND index_name = 'idx_status_time') > 0,
    'ALTER TABLE `campus_order` DROP INDEX `idx_status_time`',
    'SELECT ''idx_status_time 不存在，跳过'' AS skipped'));
PREPARE stmt FROM @drop_status_time; EXECUTE stmt; DEALLOCATE PREPARE stmt;
