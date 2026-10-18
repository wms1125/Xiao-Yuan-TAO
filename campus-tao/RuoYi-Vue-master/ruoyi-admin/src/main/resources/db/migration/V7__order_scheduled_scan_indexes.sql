-- V7：campus_order 定时任务扫描索引（P2-10 百万行复测的直接产出）
--
-- 一、为什么要加这两个索引
--
--   P2-10 把订单表推到 100 万行复测（campus_tao/perf/bench-db-million.sql，
--   证据存于 campus-tao/perf/million-db-explain-result.txt），两条定时任务扫描
--   在量级下暴露真实退化（5 万行时无感，100 万行现形）：
--
--   Q7 selectTimeoutWaitPayOrders（超时未支付扫描，定时任务）：
--       where status = 0 and create_time < #{deadline} order by create_time asc
--       EXPLAIN：type=ref key=idx_status rows=300710 Extra=Using filesort
--       只有单列 idx_status，排序要额外 filesort 30 万行。
--
--   Q8 selectShippedOrders（物流轨迹同步扫描，定时任务）：
--       where status = 2 and logistics_no is not null ... order by ship_time asc limit #{limit}
--       EXPLAIN：type=ref key=idx_status rows=248192 Extra=Using filesort
--       每轮同步都要把 24.8 万条已发货行读完、排序，再取前 N 条。
--
--   加 (status, create_time) / (status, ship_time) 后：
--   过滤列与排序列编进同一索引——Q7 变为索引顺序的范围扫描、零 filesort；
--   Q8 按索引顺序读，凑满 limit 即停，读取量从 24.8 万行降到百行级。
--
--   对照组（复测中确认无需改动的）：
--   - 买家订单列表（user_id + order by create_time）：idx_user_time 已是最优
--     （ref 200 行 + Backward index scan）；
--   - 订单号模糊搜索 order_no like '%..%'：前置通配符无法走索引，属产品决策
--     而非索引缺陷，不在本轮处理。
--
-- 二、迁移必须可重入（与 V6 相同的原因）
--
--   MySQL 不支持 CREATE INDEX IF NOT EXISTS；DBA 预建、或迁移中断后重跑，
--   直接 ALTER 会抛 Duplicate key name 并把应用启动带崩。判存 + PREPARE 动态执行。

-- ---------- 1. (status, create_time)：超时未支付扫描 ----------
SET @add_status_time = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'campus_order' AND index_name = 'idx_status_time') = 0,
    'ALTER TABLE `campus_order` ADD INDEX `idx_status_time` (`status`, `create_time`)',
    'SELECT ''idx_status_time 已存在，跳过'' AS skipped'));
PREPARE stmt FROM @add_status_time; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. (status, ship_time)：物流同步扫描 ----------
SET @add_status_ship = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'campus_order' AND index_name = 'idx_status_ship') = 0,
    'ALTER TABLE `campus_order` ADD INDEX `idx_status_ship` (`status`, `ship_time`)',
    'SELECT ''idx_status_ship 已存在，跳过'' AS skipped'));
PREPARE stmt FROM @add_status_ship; EXECUTE stmt; DEALLOCATE PREPARE stmt;
