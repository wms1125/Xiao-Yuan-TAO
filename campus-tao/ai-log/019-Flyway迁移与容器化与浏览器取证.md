# ai-log 019 · 数据库版本化迁移（Flyway）· 容器化交付 · 浏览器实测取证

> 里程碑：M10（评分提升 · 阶段二） ｜ 主题：T2 可复现交付单点突破（Flyway + Docker）+ 运行态证据补齐 ｜ 记录方式：AI 辅助留痕
> 版本：v1.0.16（2026-10-16）｜ 对应交付项：用户指定的"Flyway 迁移与回滚 + Dockerfile/compose"、"修 M5 汇总表 + 补浏览器实测"、"push 让 CI 验证"

## 里程碑目标

上一轮评估后，**可复现交付**是四个维度里最低的一项（81/100），且缺口集中在两个"一眼可见"的事实：

1. **没有数据库版本化迁移**——建库靠一次性全量脚本 `campus_tao_full.sql`，schema 一变就得手工执行增量 SQL，无法回滚、无法在 CI 里验证；
2. **没有任何容器化交付物**——全仓搜不到 Dockerfile / compose / K8s 清单，`deploy/` 下只有一个 `nginx.conf`。

另外两个证据缺口：M5 报告顶部汇总表与实测值长期漂移；新增的三个买家页只有组件用例背书，**没有浏览器实测证据**——按本项目自己刚建立的《AI 辅助开发规范》，"自述不算证据"。

本轮把这四件事做完，并**push 让 CI 独立复验**。

## 关键决策与取舍

| 决策点 | 选择 | 备选方案 | 取舍理由 |
|---|---|---|---|
| 迁移工具 | Flyway | Liquibase / 自研 SQL runner | Flyway 的 `baseline-on-migrate` 恰好覆盖本项目最难的场景——**存量库无版本表**；Liquibase 的 changelog 抽象对"已有 mysqldump 全量脚本"的迁移成本更高 |
| 存量库怎么纳管 | `baseline-on-migrate: true` + `baseline-version: 1` | ① 手工先建 `flyway_schema_history`；② 要求所有人重建库 | ① 需要一份"只在升级时跑一次"的脚本，反而增加运维负担；② 与"老用户直接升级"的目标相悖。**同一套配置**同时覆盖空库与存量库，是本轮最关键的一个决策 |
| 回滚怎么做 | 补偿脚本 `U*.sql` + 清理版本记录 | 升级 Flyway Teams（有 `undo`） | Teams 是商业版；社区版无 `undo` 是事实约束。补偿脚本 + 显式清 `flyway_schema_history` 是社区版下的标准做法，**关键在"必须同时清记录"**，否则再次 migrate 会跳过 |
| V1 基线怎么生成 | 由 `campus_tao_full.sql` 等价转化（仅去掉 `CREATE DATABASE` / `USE`） | 按实体重新手写 42 张表的 DDL | 手写必然与线上实际结构产生偏差；等价转化能保证**基线 == 现网结构**，并且可以用"逐表行数一致"来机器核对 |
| CI 里怎么验迁移 | `mvn -N flyway:migrate`（不启动应用） | 启动应用让 Spring 触发 Flyway | 应用启动会写 `sys_job_log` / `sys_logininfor` 等运行期数据，**污染"逐表行数与全量脚本一致"的判定**。应用侧装配改由 e2e job 隐式覆盖（空库能起来即证明迁移生效） |
| 回滚脚本放哪 | `db/rollback/`（不打进 jar） | `resources/db/rollback/`（随 jar 分发） | 回滚只在人工排障时用，不需要随 jar 走；放进 `db/` 与工具脚本同处，运维更好找。**迁移脚本则必须进 `resources/`**（要随 jar 打包） |
| 后端镜像构建 | 多阶段（maven 构建 → JRE 运行） | 直接 `COPY` 本地 `target/*.jar` | 多阶段让镜像构建**自包含**（CI 里 `docker build` 即可复现），且运行镜像不含 Maven/JDK/源码 |
| 内嵌 Redis 怎么处理 | 新增 `EMBEDDED_REDIS_ENABLED` 开关 | 容器里任由它启动 | 容器内已有 compose 的 redis 服务，再起一个内嵌实例纯属浪费；开关让两种形态都干净 |
| 浏览器取证方式 | `agent-browser` 真实驱动 Chromium | 组件用例 + 静态截图 | 组件用例证明的是"逻辑正确"，不是"页面真的渲染出来了"。**本轮把两者都留下** |

## 踩坑与解决

本轮的"坑"几乎全部由**真跑**暴露——其中 3 个是"应用能启动但行为静默错误"级别，不跑根本发现不了：

| # | 现象 | 根因 | 解决 |
|---|---|---|---|
| 1 | 应用启动直接失败：`found duplicate key spring` | 我新增 Flyway 配置时写了一个**独立的顶层 `spring:` 段**，与文件中原有的 `spring:` 重复。SnakeYAML 的重复键检测在 Spring Boot 中是开启的 | 把 `flyway:` 并入既有 `spring:` 段；另加 `tools/check_yml_dup_keys.py` 作为**提交前静态拦截**（这类错误只在运行期暴露） |
| 2 | 应用**正常启动**，但迁移完全没执行（随后 `Table 'sys_config' doesn't exist`） | **Spring Boot 4 把自动配置拆成了独立模块**：只引入 `flyway-core` 不会激活 `FlywayAutoConfiguration`，`spring.flyway.*` 全部静默失效 | 补上 `org.springframework.boot:spring-boot-flyway`。已在 `ruoyi-admin/pom.xml` 写下注释说明这个反直觉点 |
| 3 | 回滚到 0 后，`flyway_schema_history` 仍残留 `V1` 记录（库结构已回滚、版本记录说"已应用"） | `U1__*.sql` 在 `DROP TABLE sys_menu` **之后**又去 `DELETE FROM sys_menu` → mysql 退出码非零 → `rollback.sh` 的 `set -e` **在清理版本记录之前就中止了** | ① 删掉 U1 中已成死代码的 `DELETE`（表都 DROP 了）；② `rollback.sh` 改为**显式捕获退出码并告警但绝不中断**，确保版本记录一定被清理 |
| 4 | `rollback.sh` 报告"当前版本: 0"，而库里明明是 v2 | 脚本依赖 `mysql` 在 PATH 上，但本项目**自带 MySQL**（`mysql-8.0.40/bin`），PATH 里没有 → 查询失败被 `2>/dev/null` 吞掉，`\|\| echo 0` 把失败伪装成"版本 0" | 脚本自行定位内置 mysql（`MYSQL_BIN` 可覆盖，最后回落 PATH）；失败时明确报错而不是静默取 0 |
| 5 | `ERROR 1046 (3D000) No database selected` | `mysql < U2.sql` 没有指定库名 | 补上 `"$DB_NAME"` 参数 |
| 6 | 逐表行数核对：**42 张表全部"不一致"**，且全部被误判为"非预期新增" | Windows 版 mysql 客户端输出 **CRLF**，表名尾部带 `\r`，导致 `count(*)` 查询与表名比对全部失配 | `q()` 统一 `tr -d '\r'`；已在脚本注释里写明这个平台差异 |
| 7 | `rollback.sh` 找不到 `U2__*.sql` | 脚本按 `db/rollback/../migration` 定位迁移目录，实际迁移脚本在 `ruoyi-admin/src/main/resources/db/migration` | 修正 `MIG_DIR` 默认路径；同时把 `U*.sql` 从 `resources/db/rollback/` 统一挪到 `db/rollback/`，消除"工具与脚本分居两处"的隐患 |
| 8 | `ci.yml` 顶部注释称后端覆盖率门禁为"行 ≥35% / 分支 ≥18%" | 门禁早已上调到 **55% / 40%**（`ruoyi-system/pom.xml` 实测），注释未同步 | 按 pom 实际值更正注释——**注释与配置不一致本身就是缺陷** |

## 验证证据

### 正向验证

**Flyway 迁移与回滚（端到端，11/11 通过）** — `db/tools/verify_flyway.sh`：

| 环节 | 断言 | 结果 |
|---|---|---|
| 0 | 由全量脚本建参照库 | ✅ 42 张表 |
| 1 | 空库迁移 | ✅ `Successfully applied 2 migrations`；`flyway_schema_history` 记录 `1,2`；表数 **47**（42 基线 + 4 新增 + 1 版本表） |
| 2 | 逐表行数核对 | ✅ 与全量脚本**逐表一致**（差异项均命中预期清单：V2 菜单种子 `sys_menu` +9 / `sys_role_menu` +9、4 张新表） |
| 3 | 回滚到 0 | ✅ 业务表全部删除（仅剩空的版本表）；版本记录清空 |
| 4 | 回滚后重放 | ✅ `Successfully applied 2 migrations`；表数恢复 47 |
| 5 | 重放后再核对 | ✅ 逐表行数仍一致 |

**存量库原地升级（baseline 路径，最贴近生产的一条）** — 对**已有 42 张表、无版本表**的 `campus_tao` 直接启动新版后端：

```
Schema history table `campus_tao`.`flyway_schema_history` does not exist yet
Creating Schema History table `campus_tao`.`flyway_schema_history` with baseline ...
Successfully baselined schema with version: 1
Migrating schema `campus_tao` to version "2 - buyer journey"
Successfully applied 1 migration to schema `campus_tao`, now at version v2
Started RuoYiApplication in 10.387 seconds
```

→ **无需任何手工 SQL**，老库自动打基线并只补执行 V2；`/actuator/health` = `{"status":"UP"}`。

**端到端业务回归** — `CAMPUS_BASE_URL=http://localhost:8081 python3 campus-tao/e2e_business.py` → **`RESULT: ALL_PASS`**（含防超卖、越权、退款回滚、物流越权、外呼留痕隔离等断言，跑在真实 MySQL + 真实 Service 层上）。

**后端门禁** — `mvn -B verify` → **BUILD SUCCESS**，7 模块全绿，Spotless `keeping 25 files clean`。
- surefire 聚合（11 个 report 文件）：**136 用例 / 0 失败 / 0 错误 / 0 跳过**。
- `ruoyi-system/target/site/jacoco/jacoco.csv` **独立实算**：行 **566/907 = 62.40%**、分支 **158/338 = 46.75%**，覆盖 40 个类；门禁 行 ≥55 / 分支 ≥40 **通过**。

**浏览器实测取证（新增 6 张，全部真实渲染）** — `campus-tao/docs/evidence-v1.0.16/`：

| 文件 | 内容 |
|---|---|
| `01-login.png` | 登录页（账号密码为空，验证 v1.0.15 已清空预填凭证） |
| `02-buyer-index.png` | 买家首页（余额 ¥2000.00 / 在售 39 / 购物车 5） |
| `03-buyer-mall.png` | **校园商城**（新增页）：关键词/分类/成色筛选 + 真实商品卡片（库存、价格、卖家） |
| `04-buyer-mall-detail.png` | **商品详情**（新增页）：价格/原价/成色/库存/卖家/分类/发布时间/描述 + 数量选择器；页面显示业务规则 **"不能购买自己发布的商品"**（当前登录 `ry` 即卖家） |
| `05-buyer-review.png` | **评价管理**（新增页）：评价总数/平均评分/好评数统计卡 + 评价清单空态 |
| `06-buyer-order.png` | 我的订单：6 笔订单、待收货 1 / 已完成 2，状态标签与金额真实 |

**容器化交付物** — 后端/前端 Dockerfile（多阶段）、`docker-compose.yml`（mysql + redis + backend + frontend）、`nginx.docker.conf`、`.env.example`、两份 `.dockerignore`。
- `.github/workflows/ci.yml` 新增 `docker` job：`docker compose build` → 起栈 → **轮询 backend 容器 health 直到 healthy** → 校验前端静态页可达且 `/prod-api/` 反代回后端 → 失败时 dump 容器日志。
- `docker-compose.yml` / `ci.yml` 均经 YAML 解析校验（含重复键检测）：jobs = `backend, e2e, frontend, flyway, docker, security`；services = `mysql, redis, backend, frontend`。

### 负向验证（强制动作，见 `AI-辅助开发规范.md` 第六章）

- **"应用启动成功"不等于"迁移生效"**：只引 `flyway-core` 时，应用**正常启动**、`/actuator/health` 也是 UP，但库是空的（随后被 `sys_config doesn't exist` 暴露）。→ 证明"能启动"不能作为迁移生效的证据，必须**直查 `flyway_schema_history` + 表数 + 逐表行数**。
- **重复键检测确实生效**：`application.yml` 出现两个 `spring:` 时应用**启动即失败**（非静默忽略）。→ 证明该静态检查项不是形式主义；`tools/check_yml_dup_keys.py` 也据此写成提交前拦截。
- **回滚工具的"不中断"改动是被真实缺陷逼出来的**：修之前，U1 的非零退出码让版本记录残留（库结构与版本记录不一致）；修之后 `verify_flyway.sh` 的 [3/5] 才能断言 `版本记录已清空`。→ 该断言在修复前**必然失败**，说明它不是恒真断言。
- **逐表行数核对会真的报警**：CRLF 缺陷未修时，42 张表**全部**被判为"不一致/非预期新增"。→ 证明该核对不是"永远打印一致"的空壳。

### 数据来源可指

| 数字 | 来源 |
|---|---|
| 136 / 0 / 0 / 0 | `ruoyi-*/target/surefire-reports/*.txt` 聚合 |
| 行 62.40%（566/907）· 分支 46.75%（158/338） | `ruoyi-system/target/site/jacoco/jacoco.csv` 逐行累加 |
| 42 → 47 张表 | `information_schema.tables` 计数 |
| `Successfully applied 2 migrations` / `baselined ... version: 1` | 应用启动日志与 `mvn -N flyway:migrate` 输出 |
| `ALL_PASS` | `e2e_business.py` 退出输出 |
| 6 张截图 | `campus-tao/docs/evidence-v1.0.16/*.png`（真实 Chromium 渲染） |

## 未纳入本轮（如实记录）

- **Docker 镜像未在本机实际构建**：本机**没有 Docker**（`docker: command not found`），镜像与 compose 只做了 YAML/结构校验与静态审阅。**真正的构建验证交给 CI 的 `docker` job**（GitHub runner 自带 Docker）。这一点不回避：本轮结束时它们的状态是"已写好、未经构建验证"。
- **`1-初始化数据库.bat` 未重跑**：该脚本导入的 `campus_tao_full.sql` 本轮未改动，且 V1 基线正是由它等价转化而来（已由"逐表行数一致"间接覆盖）。但它与 Flyway 的衔接（导入后首次启动自动打基线）只在**已有库**上实测过，未在"全空机器上从零跑 bat"实测。
- **买家侧旅程仍有缺口**：买卖双方 IM 沟通、收货地址管理、收藏/关注、搜索历史与推荐、订单物流轨迹**买家可视化页**——V2 迁移已建好 4 张表与菜单（`campus_address` / `campus_favorite` / `campus_search_history` / `campus_message`），但**对应的 Java 服务层与 Vue 页面尚未实现**。本轮把"数据底座"铺好了，功能实现留待下一轮。
- **支付网关抽象层未动**：`CampusAccount` 仍是沙箱余额账户（内部转账），未抽 `IPaymentGateway` 接口，也未对接第三方。

## 涉及文件

**新增**
- `campus-tao/RuoYi-Vue-master/ruoyi-admin/src/main/resources/db/migration/V1__baseline_campus_tao.sql`（42 表基线，1415 行）
- `.../db/migration/V2__buyer_journey.sql`（4 张新表 + `campus_order.address_snapshot` + 菜单 2011~2013 / 2115~2120）
- `campus-tao/RuoYi-Vue-master/db/rollback/U1__baseline_campus_tao.sql`、`U2__buyer_journey.sql`、`rollback.sh`、`README.md`
- `campus-tao/RuoYi-Vue-master/db/tools/compare_migration.sh`、`verify_flyway.sh`
- `campus-tao/RuoYi-Vue-master/tools/check_yml_dup_keys.py`
- `4-回滚数据库.bat`、`docker-compose.yml`、`.env.example`
- `campus-tao/RuoYi-Vue-master/Dockerfile`、`.dockerignore`；`campus-tao-ui/Dockerfile`、`.dockerignore`、`nginx.docker.conf`
- `campus-tao/docs/evidence-v1.0.16/`（6 张截图 + `capture.sh`）

**修改**
- `ruoyi-admin/pom.xml`（+ `spring-boot-flyway` / `flyway-core` / `flyway-mysql`）
- 根 `pom.xml`（+ `flyway-maven-plugin`、`flyway.version`、`mysql.version`）
- `application.yml`（`spring.flyway` 段、`REDIS_HOST`/`REDIS_PORT` 可外部化）
- `application-druid.yml`（连接串 `DB_URL` 可覆盖，并修正 `useSSL=true` → `false`+`allowPublicKeyRetrieval`）
- `EmbeddedRedisConfig.java`（+ `EMBEDDED_REDIS_ENABLED` 开关）
- `.github/workflows/ci.yml`（+ `flyway` / `docker` job；更正覆盖率门禁注释与远程仓库说明）
- `.gitignore`（忽略 `.env`，保留 `.env.example`）

## 人工复核结论

| 复核项 | 结论 |
|---|---|
| 迁移能否覆盖**空库**与**存量库**两种起点 | ✅ 两种都实测通过；存量库那条用真实 42 表库跑出 `baselined ... version: 1` + 只补执行 V2 |
| 回滚是否真的可逆 | ✅ 回滚后业务表清空、版本记录清空；重放后行数仍与全量脚本一致 |
| "应用能启动"是否被当作迁移证据 | ✅ 没有——负向验证明确证明两者不等价，证据落在 `flyway_schema_history` + 表数 + 行数核对上 |
| 浏览器实测是否为真实渲染 | ✅ 6 张截图均来自真实 Chromium；`04` 还带出"不能购买自己发布的商品"这条业务规则，非静态占位 |
| 容器化是否已构建验证 | ❌ **未验证**——本机无 Docker，已在"未纳入本轮"如实记录，交由 CI `docker` job 验证 |
| 买家侧功能缺口是否被夸大或掩盖 | ✅ 未掩盖——V2 只建了表与菜单，服务层/页面未实现，已在"未纳入本轮"逐项列明 |
| 发现的问题是否都已修复 | ✅ 8 个坑全部修复，其中 3 个属"启动成功但行为静默错误"，均已写入本表并配套静态拦截或断言 |
