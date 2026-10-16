-- ============================================================================
-- 校园淘 · 数据库层性能基准（可复现）
--
-- 目的：为「能不能扛量」提供可核验的证据，而不是凭感觉断言。三件事：
--   1) 造出有统计意义的规模（演示库只有几十行，EXPLAIN 在小表上必然选择全表扫描，
--      结论不可信；这里按 5 万商品 / 5 万订单 / 10 万订单项的规模造数）；
--   2) 对「应用真实发出的 SQL」逐一 EXPLAIN，看 type / key / rows / Extra(filesort)；
--   3) 用 MySQL 自带 profiling 记录每条查询的真实耗时。
--
-- 关键约束：本脚本**不触碰演示库 campus_tao**，全部在独立的 campus_tao_perf 里进行，
--         表结构用 CREATE TABLE ... LIKE 从演示库 1:1 复制（索引/约束完全一致），
--         故结论可原样迁移到 campus_tao。
--
-- 用法：
--   mysql -h127.0.0.1 -uroot -p123456 < campus-tao/perf/bench-db.sql
-- ============================================================================

DROP DATABASE IF EXISTS campus_tao_perf;
CREATE DATABASE campus_tao_perf DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 递归 CTE 默认深度仅 1000，造 5 万行需要放开；本会话内生效，不影响全局配置
SET SESSION cte_max_recursion_depth = 200000;

-- 1:1 复制结构与索引（含 PRIMARY / UNIQUE / KEY），保证索引口径与演示库一致
CREATE TABLE campus_tao_perf.campus_product     LIKE campus_tao.campus_product;
CREATE TABLE campus_tao_perf.campus_order       LIKE campus_tao.campus_order;
CREATE TABLE campus_tao_perf.campus_order_item  LIKE campus_tao.campus_order_item;

-- ----------------------------------------------------------------------------
-- 造数：用递归 CTE 生成序号，避免依赖任何外部脚本或第三方工具
-- ----------------------------------------------------------------------------
SET @BASE = '2026-10-17 00:00:00';

-- 商品 5 万条；status 按 5:1 分布（大部分上架），seller_id 均匀落在 1..500
INSERT INTO campus_tao_perf.campus_product
  (category_id, title, price, original_price, condition_level, description, images,
   stock, seller_id, seller_name, status, version, create_time, update_time)
SELECT (n % 10) + 1,
       CONCAT('基准商品 ', n),
       10 + (n % 900),
       20 + (n % 1800),
       (n % 5) + 1,
       '压测造数，无业务含义',
       '/campus-img/books.jpg',
       5 + (n % 50),
       (n % 500) + 1,
       CONCAT('卖家', (n % 500) + 1),
       CASE WHEN n % 6 = 0 THEN 0 ELSE 1 END,
       0,
       DATE_SUB(@BASE, INTERVAL n MINUTE),
       DATE_SUB(@BASE, INTERVAL n MINUTE)
FROM (WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 50000)
      SELECT n FROM seq) AS nums;

-- 订单 5 万条；user_id 均匀落在 1..500
INSERT INTO campus_tao_perf.campus_order
  (order_no, user_id, user_name, total_amount, status, receiver_name, receiver_phone,
   address, remark, logistics_no, create_time, update_time)
SELECT CONCAT('CTPERF', LPAD(n, 12, '0')),
       (n % 500) + 1,
       CONCAT('买家', (n % 500) + 1),
       10 + (n % 2000),
       n % 7,
       '基准收货人',
       '13900000000',
       '基准校区 1 号楼',
       '',
       '',
       DATE_SUB(@BASE, INTERVAL n MINUTE),
       DATE_SUB(@BASE, INTERVAL n MINUTE)
FROM (WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 50000)
      SELECT n FROM seq) AS nums;

-- 订单项 10 万条；order_id 落在 1..50000（每单约 2 项）
INSERT INTO campus_tao_perf.campus_order_item
  (order_id, order_no, product_id, product_title, image, price, quantity, subtotal)
SELECT (n % 50000) + 1,
       CONCAT('CTPERF', LPAD((n % 50000) + 1, 12, '0')),
       (n % 50000) + 1,
       CONCAT('基准商品 ', n),
       '/campus-img/books.jpg',
       10 + (n % 900),
       1,
       10 + (n % 900)
FROM (WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 100000)
      SELECT n FROM seq) AS nums;

ANALYZE TABLE campus_tao_perf.campus_product;
ANALYZE TABLE campus_tao_perf.campus_order;
ANALYZE TABLE campus_tao_perf.campus_order_item;

SELECT '=== 规模核对（行数） ===' AS section;
SELECT 'campus_product' AS tbl, COUNT(*) AS rows_cnt FROM campus_tao_perf.campus_product
UNION ALL SELECT 'campus_order', COUNT(*) FROM campus_tao_perf.campus_order
UNION ALL SELECT 'campus_order_item', COUNT(*) FROM campus_tao_perf.campus_order_item;

-- ----------------------------------------------------------------------------
-- EXPLAIN：逐条对应应用真实发出的 SQL（见对应 Mapper XML / Service）
-- 关注列：type（ALL=全表扫描）、key（命中的索引）、rows（预估扫描行数）、Extra（Using filesort）
-- ----------------------------------------------------------------------------
SELECT '=== Q1 商城列表：status 过滤 + create_time 排序 + 分页 ===' AS section;
EXPLAIN SELECT id, category_id, title, price, stock, seller_id, status, create_time
FROM campus_tao_perf.campus_product WHERE status = 1
ORDER BY create_time DESC LIMIT 20;

SELECT '=== Q2 商品管理：按卖家过滤 + create_time 排序（CampusProductMapper.selectCampusProductList 的 sellerId 分支）===' AS section;
EXPLAIN SELECT id, title, price, stock, seller_id, status, create_time
FROM campus_tao_perf.campus_product WHERE seller_id = 123
ORDER BY create_time DESC LIMIT 20;

SELECT '=== Q3 分类浏览：category_id + status 复合过滤 + 排序 ===' AS section;
EXPLAIN SELECT id, title, price, stock, seller_id, status, create_time
FROM campus_tao_perf.campus_product WHERE category_id = 3 AND status = 1
ORDER BY create_time DESC LIMIT 20;

SELECT '=== Q4 我的订单：按买家过滤 + create_time 排序（CampusOrderMapper.selectOrderList）===' AS section;
EXPLAIN SELECT id, order_no, status, total_amount, create_time
FROM campus_tao_perf.campus_order WHERE user_id = 123
ORDER BY create_time DESC LIMIT 20;

SELECT '=== Q5 订单概览聚合：CampusOrderMapper.countGroupByStatus ===' AS section;
EXPLAIN SELECT status, COUNT(1) AS cnt
FROM campus_tao_perf.campus_order WHERE user_id = 123 GROUP BY status;

SELECT '=== Q6 订单明细：按订单取明细（CampusOrderItemMapper）===' AS section;
EXPLAIN SELECT id, order_id, order_no, product_id, product_title, price, quantity, subtotal
FROM campus_tao_perf.campus_order_item WHERE order_id = 1000;

-- ----------------------------------------------------------------------------
-- 实测耗时：MySQL 自带 profiling，记录每条查询的真实 Duration（秒）
-- ----------------------------------------------------------------------------
SET profiling = 1;

SELECT COUNT(*) FROM campus_tao_perf.campus_product WHERE status = 1 ORDER BY create_time DESC LIMIT 20;
SELECT COUNT(*) FROM campus_tao_perf.campus_product WHERE seller_id = 123 ORDER BY create_time DESC LIMIT 20;
SELECT COUNT(*) FROM campus_tao_perf.campus_product WHERE category_id = 3 AND status = 1 ORDER BY create_time DESC LIMIT 20;
SELECT COUNT(*) FROM campus_tao_perf.campus_order WHERE user_id = 123 ORDER BY create_time DESC LIMIT 20;
SELECT status, COUNT(1) FROM campus_tao_perf.campus_order WHERE user_id = 123 GROUP BY status;
SELECT COUNT(*) FROM campus_tao_perf.campus_order_item WHERE order_id = 1000;

SELECT '=== 实测耗时（profiling，单位秒）===' AS section;
SHOW PROFILES;

SET profiling = 0;

SELECT '=== 索引清单（与演示库一致，用于核对查询是否命中索引）===' AS section;
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
FROM information_schema.statistics
WHERE table_schema = 'campus_tao_perf'
GROUP BY table_name, index_name
ORDER BY table_name, index_name;
