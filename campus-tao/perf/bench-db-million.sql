-- ============================================================================
-- 校园淘 · 百万行订单索引复测（P2-10，可复现）
--
-- 背景：docs/perf-report-v1.0.21.md 的基准规模是 5 万订单 / 10 万订单项。
--       千行级 EXPLAIN 的结论未必随量级保持——优化器可能在不同数据分布下换计划。
--       本脚本把订单表推到 100 万行、订单项 150 万行，对「应用真实发出的 SQL」
--       （CampusOrderMapper / CampusOrderItemMapper）逐一复测索引有效性。
--
-- 关键约束（与 bench-db.sql 相同）：
--   不触碰演示库 campus_tao，全部在独立的 campus_tao_perf 里进行；
--   表结构 CREATE TABLE ... LIKE 1:1 复制，索引口径与交付库一致。
--
-- 用法：
--   mysql -h127.0.0.1 -uroot -p123456 --table < campus-tao/perf/bench-db-million.sql \
--     | tee campus-tao/perf/million-db-explain-result.txt
--   （跑完自行 DROP DATABASE campus_tao_perf，或用脚本末尾注释掉的语句）
-- ============================================================================

DROP DATABASE IF EXISTS campus_tao_perf;
CREATE DATABASE campus_tao_perf DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 递归 CTE 默认深度仅 1000，造百万行必须放开（订单项 150 万行，取 1.6M 留余量）；
-- 仅本会话生效
SET SESSION cte_max_recursion_depth = 1600000;

CREATE TABLE campus_tao_perf.campus_order       LIKE campus_tao.campus_order;
CREATE TABLE campus_tao_perf.campus_order_item  LIKE campus_tao.campus_order_item;

SET @BASE = '2026-10-18 00:00:00';

-- ----------------------------------------------------------------------------
-- 造数：100 万订单（user_id 均匀落在 1..5000，status 按 n%7 分布），
--       150 万订单项（前 50 万单 2 项、后 50 万单 1 项）。
-- ----------------------------------------------------------------------------
INSERT INTO campus_tao_perf.campus_order
  (order_no, user_id, user_name, total_amount, status, receiver_name, receiver_phone,
   address, remark, logistics_no, create_time, update_time)
SELECT CONCAT('CTPERF', LPAD(n, 12, '0')),
       (n % 5000) + 1,
       CONCAT('买家', (n % 5000) + 1),
       10 + (n % 2000),
       n % 7,
       '基准收货人',
       '13900000000',
       '基准校区 1 号楼',
       '',
       CASE WHEN n % 7 = 2 THEN CONCAT('SF', LPAD(n, 14, '0')) ELSE '' END,
       DATE_SUB(@BASE, INTERVAL n MINUTE),
       DATE_SUB(@BASE, INTERVAL n MINUTE)
FROM (WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 1000000)
      SELECT n FROM seq) AS nums;

INSERT INTO campus_tao_perf.campus_order_item
  (order_id, order_no, product_id, product_title, image, price, quantity, subtotal)
SELECT (n % 1000000) + 1,
       CONCAT('CTPERF', LPAD((n % 1000000) + 1, 12, '0')),
       (n % 50000) + 1,
       CONCAT('基准商品 ', (n % 50000) + 1),
       '/campus-img/books.jpg',
       10 + (n % 900),
       1,
       10 + (n % 900)
FROM (WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 1500000)
      SELECT n FROM seq) AS nums;

ANALYZE TABLE campus_tao_perf.campus_order;
ANALYZE TABLE campus_tao_perf.campus_order_item;

SELECT '=== 规模核对（行数） ===' AS section;
SELECT 'campus_order' AS tbl, COUNT(*) AS rows_cnt FROM campus_tao_perf.campus_order
UNION ALL SELECT 'campus_order_item', COUNT(*) FROM campus_tao_perf.campus_order_item;

-- ----------------------------------------------------------------------------
-- EXPLAIN：逐条对应 CampusOrderMapper / CampusOrderItemMapper 真实发出的 SQL
-- 关注：type（ALL=全表扫）、key、rows、Extra（Using filesort / Using index）
-- ----------------------------------------------------------------------------
SELECT '=== Q4 我的订单（selectOrderList user_id 分支）：1M 行下必须仍走 idx_user_time 且无 filesort ===' AS section;
EXPLAIN SELECT id, order_no, status, total_amount, create_time
FROM campus_tao_perf.campus_order WHERE user_id = 123
ORDER BY create_time DESC LIMIT 20;

SELECT '=== Q4b 我的订单（user_id + status 双筛选 + 排序）：排序仍应由索引保证 ===' AS section;
EXPLAIN SELECT id, order_no, status, total_amount, create_time
FROM campus_tao_perf.campus_order WHERE user_id = 123 AND status = 2
ORDER BY create_time DESC LIMIT 20;

SELECT '=== Q4c 订单号模糊搜索（order_no LIKE %..%）：已知全表扫，量化代价 ===' AS section;
EXPLAIN SELECT id, order_no, status, total_amount, create_time
FROM campus_tao_perf.campus_order WHERE order_no LIKE CONCAT('%', 'CTPERF000001', '%')
ORDER BY create_time DESC LIMIT 20;

SELECT '=== Q5 订单概览聚合（countGroupByStatus user_id 分支）===' AS section;
EXPLAIN SELECT status, COUNT(1) AS cnt
FROM campus_tao_perf.campus_order WHERE user_id = 123 GROUP BY status;

SELECT '=== Q7 超时未支付扫描（selectTimeoutWaitPayOrders，定时任务）===' AS section;
EXPLAIN SELECT id, order_no, status, create_time
FROM campus_tao_perf.campus_order
WHERE status = 0 AND create_time < '2026-10-17 00:00:00'
ORDER BY create_time ASC;

SELECT '=== Q8 物流同步扫描（selectShippedOrders，定时任务）===' AS section;
EXPLAIN SELECT id, order_no, logistics_no, ship_time
FROM campus_tao_perf.campus_order
WHERE status = 2 AND logistics_no IS NOT NULL AND logistics_no != ''
ORDER BY ship_time ASC LIMIT 100;

SELECT '=== Q9 订单项回查（selectOrderItem by order_id，1.5M 行）===' AS section;
EXPLAIN SELECT id, order_id, order_no, product_id, product_title, price, quantity, subtotal
FROM campus_tao_perf.campus_order_item WHERE order_id = 500000;

-- ----------------------------------------------------------------------------
-- 实测耗时（profiling，秒）：与 EXPLAIN 互相印证
-- ----------------------------------------------------------------------------
SET profiling = 1;

SELECT id, order_no, status, total_amount, create_time
FROM campus_tao_perf.campus_order WHERE user_id = 123
ORDER BY create_time DESC LIMIT 20;

SELECT status, COUNT(1) FROM campus_tao_perf.campus_order WHERE user_id = 123 GROUP BY status;

SELECT id, order_no, status, total_amount, create_time
FROM campus_tao_perf.campus_order WHERE user_id = 123 AND status = 2
ORDER BY create_time DESC LIMIT 20;

SELECT id, order_no, status, total_amount, create_time
FROM campus_tao_perf.campus_order WHERE order_no LIKE CONCAT('%', 'CTPERF000001', '%')
ORDER BY create_time DESC LIMIT 20;

SELECT COUNT(*) FROM campus_tao_perf.campus_order
WHERE status = 0 AND create_time < '2026-10-17 00:00:00';

SELECT id, order_no, logistics_no, ship_time
FROM campus_tao_perf.campus_order
WHERE status = 2 AND logistics_no IS NOT NULL AND logistics_no != ''
ORDER BY ship_time ASC LIMIT 100;

SELECT COUNT(*) FROM campus_tao_perf.campus_order_item WHERE order_id = 500000;

SHOW PROFILES;

-- 复现完成后可清理（默认保留供人工复查，需要时手动执行）：
-- DROP DATABASE IF EXISTS campus_tao_perf;
