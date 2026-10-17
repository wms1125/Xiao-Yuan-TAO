-- ============================================================
-- 校园淘 V4 · 商品/订单查询复合索引（v1.0.22）
--
-- 背景：perf/bench-db.sql 在 5 万行规模上暴露两处真实瓶颈——
--   · Q1 商城列表：命中 idx_status 但需回表 24,814 行后 filesort
--   · Q2 商品管理（卖家视角）：type=ALL，全表扫描 49,628 行
--     （campus_product 没有 seller_id 索引）
--   所有 ORDER BY create_time 的查询都出现 Using filesort。
--
-- 修复：把"过滤列 + create_time"编进同一个复合索引，让过滤、排序、
--       分页在一次 B+ 树区间扫描内完成。四条候选索引已在独立库
--       campus_tao_perf 上做过前后对照实测（perf/idx-candidate.sql）：
--       Q2 扫描行数 49,628 → 100，Q1 filesort 消除。
--
-- 回滚脚本：db/rollback/U4__product_order_indexes.sql
--
-- 幂等性：MySQL 8.0 不支持 CREATE INDEX IF NOT EXISTS，故先查
--         information_schema.STATISTICS，索引已存在时执行 DO 0 空操作。
-- 目标库由启动连接串决定，脚本内不出现 CREATE DATABASE / USE。
-- ============================================================

-- ---------- 1. campus_product：status 过滤 + 排序（商城列表） ----------
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_product'
                 AND INDEX_NAME = 'idx_status_time') = 0,
              'ALTER TABLE `campus_product` ADD INDEX `idx_status_time` (`status`,`create_time`)',
              'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. campus_product：seller_id 过滤 + 排序（商品管理） ----------
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_product'
                 AND INDEX_NAME = 'idx_seller_time') = 0,
              'ALTER TABLE `campus_product` ADD INDEX `idx_seller_time` (`seller_id`,`create_time`)',
              'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. campus_product：category_id + status 过滤 + 排序（分类浏览） ----------
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_product'
                 AND INDEX_NAME = 'idx_category_status_time') = 0,
              'ALTER TABLE `campus_product` ADD INDEX `idx_category_status_time` (`category_id`,`status`,`create_time`)',
              'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 4. campus_order：user_id 过滤 + 排序（我的订单） ----------
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_order'
                 AND INDEX_NAME = 'idx_user_time') = 0,
              'ALTER TABLE `campus_order` ADD INDEX `idx_user_time` (`user_id`,`create_time`)',
              'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
