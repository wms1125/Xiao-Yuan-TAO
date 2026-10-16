# 数据库版本化迁移与回滚（Flyway）

本目录是校园淘的**数据库版本化迁移**体系，采用 [Flyway](https://documentation.red-gate.com/flyway) 管理
schema 演进，取代"手工跑一次性 SQL"的做法。

## 目录结构

```
ruoyi-admin/src/main/resources/db/
└── migration/                        # 前向迁移（随 jar 打包，应用启动时自动执行）
    ├── V1__baseline_campus_tao.sql   # 基线：42 张表全量结构 + 种子数据
    └── V2__buyer_journey.sql         # 买家侧旅程：收货地址/收藏/搜索历史/消息 + 订单地址快照

db/                                   # 运维工具（不打进 jar）
├── rollback/                         # 回滚补偿脚本（Flyway 社区版无 undo，手工执行）
│   ├── U1__baseline_campus_tao.sql   # 撤销 V1（DROP 全部 42 张表，危险）
│   ├── U2__buyer_journey.sql         # 撤销 V2
│   ├── rollback.sh                   # 通用回滚工具（bash / CI）
│   └── README.md                     # 本文件
└── tools/
    ├── compare_migration.sh          # 逐表行数核对（参照库 vs 迁移库）
    └── verify_flyway.sh              # 端到端验证：迁移 → 核对 → 回滚 → 重放 → 再核对
```

> 回滚脚本刻意**不放进 `resources/`**：它们只在人工排障时使用，不需要随 jar 分发；
> 放进 `db/` 与工具脚本同处，运维时更容易找到。

## 工作原理

`ruoyi-admin/src/main/resources/application.yml` 中的 `spring.flyway` 配置：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true     # 关键：兼容"已存在的旧库"
    baseline-version: 1           # 旧库直接标记为 V1 已应用
    baseline-description: baseline_campus_tao
    table: flyway_schema_history
    validate-on-migrate: true
    out-of-order: false
```

`baseline-on-migrate: true` + `baseline-version: 1` 让**同一套配置**同时覆盖两种场景：

| 场景 | Flyway 行为 |
|---|---|
| **空库**（全新部署） | 顺序执行 `V1` → `V2`，建全表并写入种子数据 |
| **已有数据的旧库**（升级） | 检测到非空且无 `flyway_schema_history`，按 `baseline-version: 1` 打基线，**只执行 V2 及之后**的迁移 |

因此老用户直接启动新版后端即可自动升级，无需手工跑 SQL。

## 日常使用

### 升级（自动）
启动后端即自动迁移，无需额外操作：
```bash
2-启动后端.bat
```
启动日志会打印 `Successfully applied N migration(s)`。

### 查看当前版本
```sql
SELECT version, description, success, installed_on
FROM flyway_schema_history ORDER BY installed_rank;
```

### 回滚
Flyway **社区版不提供 `undo` 命令**（该能力属商业版）。本项目的回滚方式是
"补偿脚本 `U<n>__*.sql` + 清理版本记录"，两条路径：

**Windows：**
```bat
4-回滚数据库.bat 1      :: 撤销 V2，保留 V1 基线
4-回滚数据库.bat 0      :: 回滚到空库
```

**bash / CI：**
```bash
export DB_USERNAME=root DB_PASSWORD=123456
db/rollback/rollback.sh 1            # 回滚到版本 1
db/rollback/rollback.sh 0 --dry-run  # 预演，不落库
```

> **为什么必须同时删 `flyway_schema_history` 记录？**
> 如果只执行 `U2` 删表、却不删版本记录，`flyway_schema_history` 仍显示 V2 已应用，
> 下次 migrate 会判定"无待执行迁移"而跳过，导致**库结构（缺 4 张表）与版本记录（声称 V2 已应用）不一致**。
> 回滚工具已内置这一步。

> ⚠️ **回滚不可逆，请先备份。** `U1` 会 DROP 全部 42 张表（含业务数据）。

## 新增一次迁移

1. 在 `db/migration/` 新建 `V<n>__<描述>.sql`（`n` 递增，双下划线分隔，如 `V3__logistics_page.sql`）；
2. 在 `db/rollback/` 新建对应 `U<n>__<描述>.sql`，写成可逆的补偿语句；
3. 本地验证：
   - 空库跑一遍 → 逐表行数与基线一致；
   - `migrate → rollback → migrate` 往返无报错；
4. 提交，CI 的 `flyway` job 会自动做同样的往返验证。

**约束：** 迁移脚本一旦合并进 `main` 就**不可修改**（Flyway 用 checksum 校验，
改动已应用脚本会导致启动失败 `Migration checksum mismatch`）。需要变更请新增 `V<n+1>`。

## CI 验证

`.github/workflows/ci.yml` 的 `flyway` job 在 MySQL 服务容器上跑 `db/tools/verify_flyway.sh`：

1. 由全量脚本 `campus-tao/sql/campus_tao_full.sql` 建**参照库**（42 张表）；
2. 空库执行迁移 → 断言 `flyway_schema_history` 记录 `V1,V2`、表数 47；
3. **逐表行数核对**参照库与迁移库（差异项必须命中预期清单：V2 菜单种子 +9、4 张新表）；
4. 回滚到 0 → 断言业务表全部删除、版本记录清空；
5. 再次迁移（验证可重放）→ 再核对一次行数。

本地等价执行：

```bash
cd campus-tao/RuoYi-Vue-master
DB_USERNAME=root DB_PASSWORD=123456 bash db/tools/verify_flyway.sh
```

迁移用 `mvn -N flyway:migrate` 直接跑 Maven 插件，**不启动应用**——应用启动会写入
`sys_job_log` 等运行期数据，会干扰"逐表行数与全量脚本一致"的判定。
应用侧的 Flyway 装配由 `e2e` job 隐式覆盖（该 job 用空库启动应用，能起来即说明迁移生效）。

### 单独执行迁移（不启动应用）

```bash
cd campus-tao/RuoYi-Vue-master
mvn -N flyway:migrate \
  -Dflyway.url='jdbc:mysql://localhost:3306/campus_tao?useSSL=false&allowPublicKeyRetrieval=true' \
  -Dflyway.user=root -Dflyway.password=123456

mvn -N flyway:info      # 查看版本状态
mvn -N flyway:validate  # 校验脚本 checksum
```

> `-N`（非递归）是必需的：插件声明在根 pom，不加会去解析未安装到本地仓库的子模块。
