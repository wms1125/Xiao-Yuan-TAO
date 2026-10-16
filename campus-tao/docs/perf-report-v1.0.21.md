# 校园淘 · 性能基准报告（v1.0.20）

> **本报告的所有数字均由本仓库内的脚本实测产出，可通过命令复现**；不接受"看起来应该够快"这类推断。
> 复现入口：
> ```bash
> # 1) SQL 层：造 5 万行规模 + 逐条 EXPLAIN + profiling（独立库 campus_tao_perf，不碰演示库）
> mysql -h127.0.0.1 -uroot -p123456 --table -e "source <仓库绝对路径>/campus-tao/perf/bench-db.sql"
> # 2) 索引候选前后对照
> mysql -h127.0.0.1 -uroot -p123456 --table -e "source <仓库绝对路径>/campus-tao/perf/idx-candidate.sql"
> # 3) HTTP 层：并发延迟与吞吐（需后端 8080 在跑，账号 ry/admin123）
> python campus-tao/perf/bench_http.py --concurrency 1,10,40 --duration 10
> ```

## 一、为什么要单独造数（而不是直接测演示库）

演示库 `campus_tao` 只有几十行商品与订单。**在这个规模上做 EXPLAIN，MySQL 必然选择全表扫描，结论不可信**——它既不能证明索引有效，也不能暴露索引缺失。

因此 `bench-db.sql` 在**独立库 `campus_tao_perf`** 里用递归 CTE 造出有统计意义的规模，表结构用 `CREATE TABLE ... LIKE` 从演示库 1:1 复制（主键/唯一键/普通索引完全一致），结论可原样迁移：

| 表 | 行数 |
|---|---|
| `campus_product` | 50,000 |
| `campus_order` | 50,000 |
| `campus_order_item` | 100,000 |

**该脚本不触碰交付库 `campus_tao`**，避免压测污染演示数据。

## 二、SQL 层结论：两处真实瓶颈

被测的 6 条查询全部取自应用真实发出的 SQL（对应 `CampusProductMapper.xml` / `CampusOrderMapper.xml`），不是随手拼的语句。

### 2.1 优化前（基线）

| # | 查询 | type | 命中索引 | 预估扫描行数 | Extra | 实测耗时 |
|---|---|---|---|---|---|---|
| Q1 | 商城列表：`status=1 ORDER BY create_time DESC LIMIT 20` | ref | `idx_status` | 24,814 | **Using filesort** | 38.52 ms |
| Q2 | 商品管理：`seller_id=123 ORDER BY create_time DESC LIMIT 20` | **ALL** | **无** | **49,628** | Using where; **Using filesort** | 10.50 ms |
| Q3 | 分类浏览：`category_id=3 AND status=1 ORDER BY create_time DESC` | index_merge | `idx_category`∩`idx_status` | 2,500 | Using intersect; **Using filesort** | 5.87 ms |
| Q4 | 我的订单：`user_id=123 ORDER BY create_time DESC LIMIT 20` | ref | `idx_user` | 100 | **Using filesort** | 0.46 ms |
| Q5 | 订单概览：`COUNT(1) GROUP BY status WHERE user_id=123` | ref | `idx_user` | 100 | Using temporary | 0.38 ms |
| Q6 | 订单明细：`WHERE order_id=1000` | ref | `idx_order` | 2 | — | 0.18 ms |

**两条确定性缺陷：**

1. **Q2 是全表扫描。** `campus_product` 上没有任何以 `seller_id` 开头的索引，所以"商品管理"页（学生卖家用它维护自己的商品）每次都要扫过全表 49,628 行才筛出卖家的几条。**这是随数据量线性劣化的缺陷**——今天看起来还能忍，是因为库只有几万行。
2. **四处 `Using filesort`。** 所有 `ORDER BY create_time DESC` 的查询都没有可用的有序索引，MySQL 必须把过滤结果全部取出后在内存/磁盘里重排。Q1 尤其明显：**只要 20 条，却先回表 24,814 行再排序**。

根本原因是同一类：**索引只建在单个过滤列上，没有把"过滤列 + 排序列(create_time)"编进同一个复合索引。**

### 2.2 候选索引与优化后对照

四条候选索引都遵循同一原则——把过滤列与 `create_time` 组成复合索引，让「过滤 → 排序 → 分页」在一次 B+ 树区间扫描内完成：

| # | 查询 | 命中索引 | 预估扫描行数 | Extra | 实测耗时 | 提升 |
|---|---|---|---|---|---|---|
| Q1 | 商城列表 | `idx_status_time` | 24,774 | Backward index scan; **Using index** | 8.89 ms | **4.3×** |
| Q2 | 商品管理 | `idx_seller_time` | **100**（↓ 49,628→100） | Backward index scan; **Using index** | 0.219 ms | **48×** |
| Q3 | 分类浏览 | `idx_category_status_time` | 3,333 | Backward index scan; **Using index** | 1.035 ms | **5.7×** |
| Q4 | 我的订单 | `idx_user_time` | 100 | Backward index scan; **Using index** | 0.305 ms | 1.5× |

两处质变值得单独指出：

- **`Using index`（覆盖索引）**：卷扫描直接命中 B+ 树叶子节点，无需回表。
- **`Using filesort` 全部消失**：`Backward index scan` 表示 MySQL 直接反向扫描有序索引取得倒序结果，排序开销归零。
- **Q2 从全表扫描降为 100 行**，彻底消除随数据量线性劣化的缺陷。

> **口径说明**：profiling 的耗时用 `SELECT COUNT(*)` 变体测量（`LIMIT` 无法缩短 `COUNT(*)`，属测量手段所限），故耗时数字属**指示性**；**权威证据是 EXPLAIN 的 `type` / `key` / `rows` / `Extra` 四列**——它们直接反映访问路径，不受测量方法影响。

### 2.3 交付建议 DDL

以下 DDL 已在 `campus_tao_perf` 上验证有效，但**刻意未自动作用于交付库**——索引属于交付库的结构变更，应由版本化迁移管控，而不是由一次压测脚本顺手改掉：

```sql
ALTER TABLE campus_product ADD INDEX idx_status_time          (status, create_time);
ALTER TABLE campus_product ADD INDEX idx_seller_time          (seller_id, create_time);
ALTER TABLE campus_product ADD INDEX idx_category_status_time (category_id, status, create_time);
ALTER TABLE campus_order   ADD INDEX idx_user_time            (user_id, create_time);
```

落地方式（建议）：新增 `V4__product_order_indexes.sql` 迁移 + 对应 `U4` 回滚，并同步 `campus_tao_full.sql` 的建表语句，使存量库与全量脚本两条路径的索引口径一致。

## 三、HTTP 层结论：端到端延迟与吞吐

被测的 6 条只读接口路径全部取自归档规格 `docs/openapi/campus-tao-openapi.json` 中 `/campus/**` 的 GET 项，保证压测对象与真实契约一致。压测前先以 4 并发预热 2s，避免 JIT / 连接池 / MyBatis 语句缓存的冷启动污染第一档数据。

| 并发 | 请求数 | QPS | p50 | p95 | p99 | 均值 | 最大 | 错误率 |
|---|---|---|---|---|---|---|---|---|
| 1 | 2,493 | 249.3 | 3.3 ms | 8.9 ms | 13.6 ms | 4.0 ms | 82.7 ms | **0.0%** |
| 10 | 16,433 | 1,642.4 | 5.4 ms | 10.7 ms | 12.3 ms | 6.1 ms | 29.0 ms | **0.0%** |
| 40 | 39,645 | **3,961.4** | 9.3 ms | 17.8 ms | 21.8 ms | 10.1 ms | 73.9 ms | **0.0%** |

单接口分位（40 并发档）：

| 接口 | p50 | p95 |
|---|---|---|
| 商城列表 | 11.0 ms | 15.1 ms |
| 商城列表·分类筛选 | 10.9 ms | 14.9 ms |
| 商品详情 | 6.8 ms | 10.4 ms |
| 我的订单 | 16.2 ms | 21.3 ms |
| 订单概览聚合 | 6.7 ms | 10.0 ms |
| 购物车列表 | 6.8 ms | 9.7 ms |

**读到的三件事：**

1. **延迟随并发增长是次线性的**：并发从 1 提到 40（40 倍），p99 仅从 13.6 ms 升到 21.8 ms，吞吐从 249 提到 3,961 QPS，且**全程零错误**。说明读路径在这个量级下没有出现排队放大。
2. **"我的订单"是最重的一条**（p50 16.2 ms / p95 21.3 ms），与它需要组装订单项 + 状态聚合相符，也和 2.1 中该查询存在 `filesort` 相互印证。
3. **连接池不是当前瓶颈**：Druid `maxActive=20`，而本次最高压到 40 并发——超出池上限却仍零错误、延迟平稳，原因是读查询耗时在毫秒级、连接回收极快。

## 四、连接池与慢 SQL 配置现状

数据源配置见 `campus-tao/RuoYi-Vue-master/ruoyi-admin/src/main/resources/application-druid.yml`：

| 项 | 值 | 说明 |
|---|---|---|
| `initialSize` / `minIdle` / `maxActive` | 5 / 10 / 20 | 上限 20 |
| `maxWait` | 60000 ms | 池满时请求最长阻塞 60 s 才失败 |
| `connectTimeout` / `socketTimeout` | 30000 / 60000 ms | |
| `validationQuery` | `SELECT 1 FROM DUAL` | `testWhileIdle=true`、`testOnBorrow=false` |
| 空闲回收 | 检查间隔 60 s；最小空闲 300 s；最大存活 900 s | |
| 慢 SQL | `log-slow-sql=true`、`slow-sql-millis=1000`、`merge-sql=true` | 超过 1 s 记入日志 |

**一处值得注意的配置后果**：`maxWait=60000` 意味着连接池耗尽的极端情况下，请求会**阻塞最长 60 秒**才拿到失败响应。读路径目前打不满池，所以无影响；但一旦出现慢查询长期占用连接（例如 Q2 那种全表扫描在数据量涨到百万行后），影响会被这 60 s 放大成"前端整体卡死"。**这也是第 2.3 节索引 DDL 值得优先落地的原因之一。**

## 五、未纳入本轮（如实记录）

- **写路径未压测**：下单/支付属资金路径，其正确性由并发资金一致性与防超卖专项用例覆盖（见 M5），但**未做写路径的吞吐/延迟压测**。
- **未做长稳压测**（>1 h，用于暴露连接泄漏与内存增长），本轮为固定时长短压。
- **HTTP 层数字基于演示库规模**（几十行）——它反映的是框架、鉴权、序列化与连接池的开销，**不代表大数据量下的延迟**；SQL 层的容量结论来自独立的 5 万行 `campus_tao_perf`。
- **未测前端页面加载性能**（首屏、资源体积）。
- 索引候选 DDL 已在沙箱验证，**尚未落地到交付库**（须走版本化迁移，见 2.3）。
