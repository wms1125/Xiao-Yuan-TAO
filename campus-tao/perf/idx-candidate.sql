-- ============================================================================
-- 校园淘 · 索引候选方案的前后对照实测
--
-- 背景：bench-db.sql 在 5 万行规模上暴露了两处真实瓶颈——
--   · Q1 商城列表：命中 idx_status 但需回表 24,814 行后 filesort（分页 20 条却扫 2.5 万行）
--   · Q2 商品管理（卖家视角）：type=ALL，**全表扫描 49,628 行**，因为 campus_product 没有 seller_id 索引
--   且所有 ORDER BY create_time 的查询都出现 Using filesort（缺"过滤列 + create_time"复合索引）
--
-- 本脚本的作用：把候选索引先应用在**独立的 campus_tao_perf** 上，用同一组 SQL 再测一遍，
-- 用数字证明该 DDL 有效（而不是"看起来应该会变快"）。
-- 本脚本**不改动交付库 campus_tao**，末尾给出的 DDL 作为交付建议单独列出。
--
-- 用法：
--   mysql -h127.0.0.1 -uroot -p123456 --table -e "source <本文件绝对路径>"
-- ============================================================================

SELECT '========== 阶段一：加索引前的基线（同一组查询） ==========' AS phase;
SELECT '--- Q1 商城列表 ---' AS q;
EXPLAIN SELECT id FROM campus_tao_perf.campus_product WHERE status = 1 ORDER BY create_time DESC LIMIT 20;
SELECT '--- Q2 商品管理（sellerId 分支）---' AS q;
EXPLAIN SELECT id FROM campus_tao_perf.campus_product WHERE seller_id = 123 ORDER BY create_time DESC LIMIT 20;
SELECT '--- Q3 分类浏览 ---' AS q;
EXPLAIN SELECT id FROM campus_tao_perf.campus_product WHERE category_id = 3 AND status = 1 ORDER BY create_time DESC LIMIT 20;
SELECT '--- Q4 我的订单 ---' AS q;
EXPLAIN SELECT id FROM campus_tao_perf.campus_order WHERE user_id = 123 ORDER BY create_time DESC LIMIT 20;

-- ----------------------------------------------------------------------------
-- 阶段二：应用候选索引（仅在 campus_tao_perf 上验证）
-- 三条都遵循同一原则：把"过滤列"与"排序列(create_time)"编进同一个复合索引，
-- 让过滤 + 排序 + 分页在一次 B+ 树区间扫描内完成，消除 filesort 与回表放大。
-- ----------------------------------------------------------------------------
SELECT '========== 阶段二：应用候选索引 ==========' AS phase;

-- 覆盖商城列表的 status 过滤 + create_time 排序
CREATE INDEX idx_status_time ON campus_tao_perf.campus_product (status, create_time);
-- 覆盖商品管理的 seller_id 过滤 + create_time 排序（当前是全表扫描）
CREATE INDEX idx_seller_time ON campus_tao_perf.campus_product (seller_id, create_time);
-- 覆盖分类浏览的 category_id + status 双条件过滤 + create_time 排序
CREATE INDEX idx_category_status_time ON campus_tao_perf.campus_product (category_id, status, create_time);
-- 覆盖我的订单的 user_id 过滤 + create_time 排序
CREATE INDEX idx_user_time ON campus_tao_perf.campus_order (user_id, create_time);

ANALYZE TABLE campus_tao_perf.campus_product;
ANALYZE TABLE campus_tao_perf.campus_order;

SELECT '========== 阶段三：加索引后（与阶段一逐条对照） ==========' AS phase;
SELECT '--- Q1 商城列表 ---' AS q;
EXPLAIN SELECT id FROM campus_tao_perf.campus_product WHERE status = 1 ORDER BY create_time DESC LIMIT 20;
SELECT '--- Q2 商品管理（sellerId 分支）---' AS q;
EXPLAIN SELECT id FROM campus_tao_perf.campus_product WHERE seller_id = 123 ORDER BY create_time DESC LIMIT 20;
SELECT '--- Q3 分类浏览 ---' AS q;
EXPLAIN SELECT id FROM campus_tao_perf.campus_product WHERE category_id = 3 AND status = 1 ORDER BY create_time DESC LIMIT 20;
SELECT '--- Q4 我的订单 ---' AS q;
EXPLAIN SELECT id FROM campus_tao_perf.campus_order WHERE user_id = 123 ORDER BY create_time DESC LIMIT 20;

-- ----------------------------------------------------------------------------
-- 阶段四：耗时对照（与 bench-db.sql 中同样的 4 条查询，profiling 实测）
-- ----------------------------------------------------------------------------
SELECT '========== 阶段四：加索引后实测耗时（与基线对照） ==========' AS phase;
SET profiling = 1;
SELECT COUNT(*) FROM campus_tao_perf.campus_product WHERE status = 1 ORDER BY create_time DESC LIMIT 20;
SELECT COUNT(*) FROM campus_tao_perf.campus_product WHERE seller_id = 123 ORDER BY create_time DESC LIMIT 20;
SELECT COUNT(*) FROM campus_tao_perf.campus_product WHERE category_id = 3 AND status = 1 ORDER BY create_time DESC LIMIT 20;
SELECT COUNT(*) FROM campus_tao_perf.campus_order WHERE user_id = 123 ORDER BY create_time DESC LIMIT 20;
SHOW PROFILES;
SET profiling = 0;

-- ----------------------------------------------------------------------------
-- 交付建议：若要把它落到交付库 campus_tao，按下列 DDL 执行（等价于新增一个 Flyway 版本）
-- 本脚本刻意不自动执行这一段：索引变更属交付库的结构变更，应由版本化迁移管控，
-- 而不是由一次压测脚本顺手改掉交付库。
-- ----------------------------------------------------------------------------
SELECT '========== 交付建议 DDL（未在本脚本中执行） ==========' AS section;
SELECT 'ALTER TABLE campus_product ADD INDEX idx_status_time (status, create_time);' AS ddl_1
UNION ALL SELECT 'ALTER TABLE campus_product ADD INDEX idx_seller_time (seller_id, create_time);'
UNION ALL SELECT 'ALTER TABLE campus_product ADD INDEX idx_category_status_time (category_id, status, create_time);'
UNION ALL SELECT 'ALTER TABLE campus_order ADD INDEX idx_user_time (user_id, create_time);';
