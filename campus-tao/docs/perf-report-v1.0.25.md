# 校园淘 · 百万行订单索引复测报告（P2-10 · v1.0.25）

> 上一次数据库层基准是 v1.0.21 的 5 万订单 / 10 万订单项（`docs/perf-report-v1.0.21.md`）。
> 本轮把订单表推到 **100 万行、订单项 150 万行**，对 `CampusOrderMapper` /
> `CampusOrderItemMapper` 真实发出的 SQL 逐一复测。结论：**主链路无回归；两条定时任务
> 扫描在量级下暴露 filesort 退化，已以 V7 迁移落地修复并复测确认。**
>
> 复现方式（全程在隔离库 `campus_tao_perf`，不触碰演示库）：
>
> ```bash
> mysql -h127.0.0.1 -uroot -p<密码> --default-character-set=utf8mb4 --table \
>   < campus-tao/perf/bench-db-million.sql \
>   | tee campus-tao/perf/million-db-explain-result.txt          # V7 前
> # 应用 V7 后重跑，另存 million-db-explain-result-after-v7.txt   # V7 后
> ```
> 原始输出（V7 前 / 后）分别归档于 `campus-tao/perf/million-db-explain-result.txt`
> 与 `million-db-explain-result-after-v7.txt`。全程约 1 分钟（造数 57s + 查询）。

## 1. 规模

| 表 | 行数 |
|---|---|
| campus_order | 1,000,000 |
| campus_order_item | 1,500,000 |

造数用递归 CTE（会话内放开 `cte_max_recursion_depth`），字段分布与真实业务对齐：
user_id 均匀 1..5000、status 按 n%7、已发货单带物流单号。

## 2. EXPLAIN 复测结果（V7 前 → V7 后）

| # | 查询（对应 Mapper） | V7 前 | V7 后 | 结论 |
|---|---|---|---|---|
| Q4 | 我的订单：`user_id=? order by create_time desc limit 20`（selectOrderList） | ref `idx_user_time` 200 行，Backward index scan | 同左 | ✅ 主路径在 1M 行下仍最优，零退化 |
| Q4b | 我的订单 + status 双筛选 | **index_merge intersect(idx_user,idx_status) + Using filesort** | ref `idx_user_time` 200 行，Backward index scan，无 filesort | ✅ 修复（V7 提供更多候选后优化器改选更优计划） |
| Q4c | 订单号模糊搜索 `order_no like '%..%'` | ALL 99 万行 + filesort，0.585s | 同左 | ⚠️ 前置通配符无法走索引，属产品决策（是否改前缀搜索），非索引缺陷，登记不处理 |
| Q5 | 订单概览聚合 `group by status`（user_id 分支） | ref 200 行 Using temporary，0.019s | 同左 | ✅ 用户维度聚合只读该用户的 200 行 |
| **Q7** | **超时未支付扫描**（selectTimeoutWaitPayOrders，定时任务） | ref `idx_status` **300,710 行 + Using filesort** | **range `idx_status_time`** 286,758 行，Using index condition，**零 filesort** | ✅ **本轮修复** |
| **Q8** | **物流同步扫描**（selectShippedOrders，定时任务） | ref `idx_status` **248,192 行 + Using filesort**，读完再排序取前 N | ref `idx_status_ship` 按索引序读取、**凑满 limit 即停**，实测 0.0039s | ✅ **本轮修复，读取量降两个数量级** |
| Q9 | 订单项回查（1.5M 行表） | ref `idx_order` 2 行，0.0001s | 同左 | ✅ |

实测耗时（profiling，1M 行）：买家列表 0.011s、概览聚合 0.041s、双筛选 0.001s、
物流同步 0.0039s；`COUNT(*)` 类（Q4c/超时总数）因语义必须扫完匹配行，仍为 0.5~1.2s。

## 3. 本轮落地：V7 迁移

`V7__order_scheduled_scan_indexes.sql`（幂等写法与 V6 相同：`information_schema` 判存 +
`PREPARE/EXECUTE/DEALLOCATE`）为 `campus_order` 增加：

- `idx_status_time (status, create_time)` —— 供超时未支付扫描（过滤列与排序列同索引，range 扫描零 filesort）
- `idx_status_ship (status, ship_time)` —— 供物流同步扫描（按索引序读、凑满 limit 即停）

配套：`U7` 幂等回滚、`sql/campus_tao_full.sql` 同步两条索引（新建库与升级库口径一致）、
`db/tools/verify_flyway.sh` 扩至 V7 断言。

**验证链**：`verify_flyway.sh` **19/19 通过**（空库迁移 V1~V7 → 逐表行数与全量脚本一致 →
回滚无残留 → 重放可重入 → 索引 6/6 恢复）；演示库经应用启动自动迁移，
`flyway_schema_history` 记录 `7 / order scheduled indexes / success`。

## 4. 如实说明

- Q7 的 rows 估计（28.6 万）仍不小——**该查询语义就是取全部超时订单**，行数下限由
  业务决定，索引只能消除排序与回表方式，不能减少必须访问的行。若未来订单量再涨，
  优化方向是给定时任务加分页批次（`limit`），属代码层改动，不在本轮。
- Q8 基准数据中 `ship_time` 为 NULL 的已发货单仍被读出（业务上已发货必有 ship_time，
  演示造数未填），EXPLAIN 的 filtered=81% 如实反映了这一点；真实数据下读取量更小。
- 本基准测的是**单表 SQL 行为**；HTTP 端到端吞吐见 `perf/bench_http.py` 与
  `perf-report-v1.0.21.md` 的对应章节，两者互补。
- 复测库 `campus_tao_perf`（约 0.5GB）在取证完成后已清理，需要时按头部命令 1 分钟可重建。
