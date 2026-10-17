-- ============================================================
-- 校园淘 U4 · V4（商品/订单复合索引）的回滚脚本
--
-- 影响：删除 V4 新增的 4 个查询索引，表结构与数据不受影响。
--       索引缺失只影响查询计划（退回全表扫描 / filesort），不改变业务语义。
--
-- 幂等性：先查 information_schema.STATISTICS，索引不存在时执行 DO 0 空操作。
-- ============================================================

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_product'
                 AND INDEX_NAME = 'idx_status_time') > 0,
              'ALTER TABLE `campus_product` DROP INDEX `idx_status_time`',
              'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_product'
                 AND INDEX_NAME = 'idx_seller_time') > 0,
              'ALTER TABLE `campus_product` DROP INDEX `idx_seller_time`',
              'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_product'
                 AND INDEX_NAME = 'idx_category_status_time') > 0,
              'ALTER TABLE `campus_product` DROP INDEX `idx_category_status_time`',
              'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_order'
                 AND INDEX_NAME = 'idx_user_time') > 0,
              'ALTER TABLE `campus_order` DROP INDEX `idx_user_time`',
              'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
