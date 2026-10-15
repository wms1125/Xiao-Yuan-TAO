# 更新日志

本文件记录校园淘（campus-tao）交付包的版本变更。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

开发过程留痕见 `campus-tao/ai-log/001~008`，里程碑文档见 `campus-tao/M1~M5`。

## [未发布]
- 依赖漏洞（CVE）扫描（`npm audit` / OWASP Dependency-Check）尚未接入；当前 SBOM 仅覆盖许可合规。首次 `npm audit` 提示 16 项告警（5 moderate / 11 high），待专项治理。
- 运营侧能力仍有可扩展项：短信/邮件外呼通道未接入（站内通知已落地）、物流轨迹查询未对接第三方承运商（当前仅记录单号）。

## [1.0.5] - 2026-10-15

可观测性与运营侧能力建设：接入 Actuator 健康检查、关键操作审计留痕，并补齐订单超时自动取消、物流单号、站内通知三项运营能力。

### 新增
- **可观测性（Actuator）**：`ruoyi-admin` 引入 `spring-boot-starter-actuator`（4.1.0）。`/actuator/health`（含 `liveness`/`readiness` 分组）与 `/actuator/info`（应用名、描述、版本）匿名开放；`/actuator/env`、`/actuator/beans` 等敏感端点默认关闭（实测返回 401）。`show-details: never`，不对外暴露组件细节。
- **关键操作审计**：校园淘全部写接口补 `@Log` 注解——下单/支付/取消/发货/收货、售后申请与审核、充值、商品增删改与上下架；操作留痕自动写入框架 `sys_oper_log`，可按「系统管理 → 操作日志」检索（实测日志标题已出现「校园淘订单 / 校园淘商品 / 校园淘账户」）。
- **运营能力 · 订单超时自动取消**：新增 Quartz 任务 `campusOrderTask.autoCancelTimeoutOrder`（`sys_job` job_id=100，每分钟扫描一次，超时阈值 `campus.order.pay-timeout-minutes`，默认 30 分钟，可用环境变量 `CAMPUS_PAY_TIMEOUT_MINUTES` 覆盖）。取消动作使用原子 UPDATE（仅 `status=0` 才置为已取消），随后回补库存并向买家推送站内通知；已支付订单不受影响，重复执行幂等。
- **运营能力 · 物流单号**：`campus_order` 新增 `logistics_no`。发货接口支持录入（`POST /campus/order/ship/{orderNo}?logisticsNo=`），订单详情展示物流单号；前端发货交互由「二次确认弹窗」升级为「录入物流单号的提示弹窗」。
- **运营能力 · 站内通知**：新增 `campus_notice` 表与 `/campus/notice/**` 接口（`top` / `list` / `unreadCount` / `read/{id}` / `readAll`）。下单、发货、售后审核结果、订单自动取消四类事件均向买家推送消息；顶部铃铛由「仅系统公告」升级为「系统公告 + 校园淘消息合并展示」（消息类型以标签区分：订单/发货/售后/消息），未读数为两者之和。消息按登录态隔离：未登录访问返回 401，标记已读限定本人（实测他人消息不可代标记）。
- `sql/migration-v1.0.5.sql`：面向**已建库**的增量迁移脚本（新增物流单号列、建 `campus_notice` 表、注册定时任务）；交付包全量脚本 `sql/campus_tao_full.sql` 已同步为唯一建库源。

### 修复
- **系统监控 → 缓存监控页整页 500**：发行包内嵌的 Windows 版 `redis-server`，其 `INFO` 输出包含自身可执行文件的完整路径（形如 `C 盘 Users 目录下的 redis-server-*.exe`）。Lettuce 读取 `INFO` 后交由 `java.util.Properties.load` 解析，反斜杠被当作 Unicode 转义，抛出 `Malformed unicode encoding`，使 `/monitor/cache` 返回 500 且前端弹出「Cannot read Redis info」。`CacheController.getInfo()` 改为捕获异常后降级返回空统计，页面恢复可渲染。
- **`/actuator/health` 误报 DOWN**：Redis 健康指示器在同样的 `INFO` 解析问题下会把整体健康状态判为 DOWN。已显式关闭该指示器（`management.health.redis.enabled=false`，并在配置中注明原因）；Redis 连通性由业务链路（验证码、登录令牌、字典/参数缓存）实际验证，进程无 Redis 时应用启动即失败，不会静默降级。

### 变更
- `ruoyi-quartz/pom.xml` 新增对 `ruoyi-system` 的依赖，用于承载校园淘业务定时任务（`ruoyi-system` 仅依赖 `ruoyi-common`，无循环依赖）。
- 定时任务按框架约定置于 `com.ruoyi.quartz.task` 包（命中 `Constants.JOB_WHITELIST_STR` 白名单），可直接在「系统监控 → 定时任务」界面维护、启停与手工触发。

### 验证
- **离线重建**：`verify.bat` 以 `-o` 强制离线执行 5 步校验 → 后端 `clean package` BUILD SUCCESS、后端单测 15/15、前端单测 **41/41**（新增 4 例站内通知接口用例）、前端 `build:prod` 通过，末行 `ALL PASS`。
- **端到端回归**：`e2e_business.py` 全链路 `RESULT: ALL_PASS`（双账号、资金与库存回滚、防超卖、越权拦截均保持通过），并新增断言：物流单号随发货落库并在详情可见、发货触发买家站内通知、本人可标记已读、未登录访问通知接口被拒(401)、关键操作已写入 `sys_oper_log`。
- **超时自动取消专项**（SQL 辅助脚手架，非交付物）：构造未支付订单 → 回拨创建时间 → **不调用任何手动触发接口，等待调度器自动执行**，51 秒内订单自动置为已取消(4)、库存由 3 回补至 4、买家收到「订单已自动取消」通知；重复执行不重复回补（幂等）；已支付订单保持 status=1 不受影响。
- **前端 UI 实测**（Playwright，Chromium）：admin 登录 → 订单列表 4 行正常渲染 → 对「已付款」订单点击发货并录入 `SF999888777` → 提示「已发货」、行状态变「已发货」、详情弹窗 `物流单号 = SF999888777`（与库中记录一致）；顶部铃铛未读徽标 4，下拉标题「消息通知」，条目含「消息/通知/公告」标签；`/monitor/cache` 正常渲染且无 Redis 报错；全程 `pageerror` 为空。
- **SBOM 同步**：后端 CycloneDX 由 156 → **166** 组件（新增 actuator/micrometer 等 10 项），与发行 jar 内 `BOOT-INF/lib` 的 **149** 个依赖 jar 逐一对照无缺失；无 AGPL/SSPL 强传染组件。前端依赖未变动。

## [1.0.4] - 2026-10-15

交付可复现性与前端测试：交付包内置 Maven 与离线依赖仓库，打通完全无网重建；前端接入 vitest 单元测试。

### 新增
- 交付包内置 Maven（`.tools/maven`，3.9.9）与离线依赖仓库（`.tools/m2-repo`，约 153MB / 2361 文件）；`verify.bat` 默认以 `-o` **强制离线**构建，证明无网可重建。如需联网补缺件，可先 `set CTA_ONLINE=1`。
- 前端单元测试：接入 vitest（`vitest.config.js` + `tests/unit/`，3 个测试文件 37 个用例：通用工具函数、校园淘订单/商品/购物车接口封装），新增脚本 `npm run test:unit`。
- CI 前端 job 增加「单元测试（vitest）」步骤；`verify.bat` 增加前端单测阶段（现为 5 步）。

### 变更
- `verify.bat` 后端构建改为使用内置 Maven 与内置仓库（不再依赖 `%TEMP%\cta-mvn` 与本机 `~/.m2`）。
- `.tools/` 已加入 `.gitignore`：Maven 与离线仓库随交付包物理内嵌，但不入版本库（避免约 160MB 二进制入库）。

### 验证
- **完全离线重建**：以 `-o` 强制离线执行 `verify.bat` → 后端 `clean package` BUILD SUCCESS、后端单测 15/15、前端单测 37/37、前端 `build:prod` 通过，末行 `ALL PASS`（退出码 0）。离线期间未访问任何远程仓库，Maven 与依赖全部来自包内 `.tools`。

## [1.0.3] - 2026-10-15

交付合规物料：生成第三方依赖清单（SBOM）与许可证说明。

### 新增
- `campus-tao/sbom/`：后端 `backend-bom.json`（CycloneDX 1.6，156 组件）、前端 `frontend-dependencies.csv`（461 包）、说明 `SBOM.md`。
- `campus-tao/tools/sbom-frontend.py`：离线扫描 `node_modules` 生成前端依赖清单的脚本，与 SBOM 同批交付、可复现。

### 说明
- 后端许可分布以宽松许可为主（Apache-2.0 126、MIT 11），含 `GPL-2.0 with Classpath Exception`（MySQL Connector/J 等）与 LGPL-2.1/CDDL-1.1 少量组件——均以未修改二进制形式链接使用，不阻断分发；无 AGPL/SSPL 强传染组件。
- 前端 461 包全部为宽松许可，未知许可 0、copyleft 组件 0。
- 后端 BOM 覆盖度已与发行 jar 内 `BOOT-INF/lib` 的 141 个依赖 jar 逐一对照，无缺失。
- 如实记录缺口：未做 CVE 扫描；前端未输出 CycloneDX 格式；上游 RuoYi 的 6 个自研模块 pom 未声明 `<licenses>`（未擅自代填）。

## [1.0.2] - 2026-10-15

交付工程化：加入 CI 流水线与本地校验脚本，并修复一键脚本的 Windows 行尾缺陷。

### 新增
- `.github/workflows/ci.yml`：后端 job（JDK 17 + Maven 缓存 → `mvn -B -DskipTests clean package` → `mvn -B test` → 上传 jar 产物）与前端 job（Node 22 + npm 缓存 → `npm ci` → `npm run build:prod`）。仓库当前为本地库，推送后自动生效。端到端回归（`e2e_business.py`）**未纳入 CI**（需可达 MySQL 8 与已初始化的库），仍由本地执行。
- `verify.bat`：CI 的本地等价脚本，交付前一键校验。含发行 jar 占用预检（后端运行期间 `maven-clean-plugin` 无法删除 jar，会给出明确提示而非 Maven 报错）、Maven 自动定位（`MVN_CMD` → `.tools\maven\` → `%TEMP%\cta-mvn` → `PATH`）、随包 JDK 自动作为 `JAVA_HOME`，末行输出 `ALL PASS`/`FAILED` 并以退出码 0/1 返回。
- `.gitattributes`：`*.bat text eol=crlf`（防止行尾缺陷复发）。

### 修复
- **四个 `.bat` 行尾为 LF（交付级缺陷）**：`1-初始化数据库.bat`、`2-启动后端.bat`、`3-启动前端.bat` 与新增的 `verify.bat` 均为 LF 行尾，Windows cmd 在解析 `call` 与括号块时读偏，脚本被执行时被逐字拆碎（报 `'t' is not recognized` 一类错误）。已全部转为 CRLF，并加 `.gitattributes` 固化。

### 验证
- `verify.bat` 端到端实跑：退出码 **0**，`后端打包 ✓ / 后端单元测试 15/15 ✓ / 前端构建 ✓ / 校验结果：ALL PASS`。
- `2-启动后端.bat` 实跑验证：后端 8080 正常起来并返回 200，确认 CRLF 修复后一键脚本可用。
- 过程发现：`verify.bat` 首次运行即失败，原因并非构建问题，而是后端进程占用 `target\ruoyi-admin.jar` 使 `clean` 无法删除文件——据此新增了占用预检。

## [1.0.1] - 2026-10-15

修复前端页面大面积白屏，并清理上游演示菜单。

### 修复
- **系统管理/系统监控页面内容区白屏**：`vite/plugins/auto-import.js` 的自动导入只配置了 `vue`/`vue-router`/`pinia`，遗漏 `@/utils/dict` 的 `useDict` 与 `@/utils/ruoyi` 的 `selectDictLabel`。视图在 `<script setup>` 中裸调用（脚本块内的裸标识符只能由构建期自动导入提供，不能像模板那样解析 `globalProperties`），运行时抛 `ReferenceError: useDict is not defined` 使 setup 中断，页面壳与侧边栏尚在但内容区空白。影响所有取字典的页面（系统管理全部、日志管理、定时任务、在线用户等）；首页与不取字典的页面（服务监控、校园淘各页）不受影响。已补全自动导入配置。

### 变更
- 删除「若依官网」外链菜单（`sys_menu` menu_id=4，指向 http://ruoyi.vip，属上游演示菜单）：运行库已删除，并同步从交付 SQL `sql/campus_tao_full.sql` 的 `sys_menu` 与 `sys_role_menu` 插入语句中移除对应元组，保证重建后不再出现。

### 验证
- 浏览器全菜单走查（重新登录 + 硬刷新）：系统管理 10 项与系统监控 6 项全部正常渲染、无 console error，侧边栏已无「若依官网」。
- 缓存列表页控制台有 `Cannot read Redis info` 警告（演示环境未启用 Redis 监控），页面渲染正常，非本次改动引入。

## [1.0.0] - 2026-10-15

首个交付版本：完成校园二手交易业务闭环，通过企业级能力加固与端到端回归验证，并打通可复现构建链路。

### 新增
- 业务闭环：商品发布与浏览、购物车、下单、模拟支付、发货、收货、评价、售后申请与退款。
- 企业级能力：订单归属校验与横向越权（IDOR）防护、发布商品卖家身份服务端强制、账户余额原子扣减与退款入账、售后退款回补库存、防超卖校验、统一异常处理、DTO 参数校验（Bean Validation）。
- 权限体系：校园淘菜单与按钮级权限（目录 2000 + 菜单 2001~2006 + 按钮 2100~2114），前端路由由后端动态菜单驱动。
- 自动化测试：`mvn test` 单元测试 15 例（DTO 校验 11 例 + 账户原子扣款 4 例）；`e2e_business.py` 双账号端到端回归脚本（覆盖越权、防超卖、身份伪造等专项）。
- 交付配套：`1-初始化数据库.bat`、`2-启动后端.bat`、`3-启动前端.bat` 三步启动脚本，内置 JDK 17 与 MySQL 8.0.40，附 `同学运行指南.md`。
- 过程与版本治理：Git 仓库（`main` 分支）、`.gitignore`、ai-log 开发日志、M1~M5 里程碑文档、本 CHANGELOG。

### 变更
- 账户扣款/退款改为单条原子 SQL（`WHERE balance >= ?`），消除并发下的丢失更新。
- 售后审核人由服务端从登录态写入（`SecurityUtils.getUsername()`），`CampusAftersaleAuditDTO` 移除 `handler` 字段，前端不再传该值。
- 商品编辑改为字段白名单回填，不再接收 `sellerId`/`sellerName`，杜绝批量赋值篡改归属。
- 为买家角色（学生）补授 `campus:product:add/edit/remove`（2100~2102），支持学生自主发布并维护本人商品；后端仍强制「卖家本人或管理员」方可修改/删除。
- 敏感配置外部化：`${JWT_SECRET:...}`、`${DB_USERNAME:...}`、`${DB_PASSWORD:...}`、`${DRUID_LOGIN_USERNAME:...}`、`${DRUID_LOGIN_PASSWORD:...}`；上传路径改为相对路径 `./uploadPath`。

### 修复
- `ruoyi-system/pom.xml` 缺少编译期 `jakarta.validation` 依赖，`mvn compile` 报 164 处「程序包 jakarta.validation 不存在」；已补入 `spring-boot-starter-validation`。
- 订单列表/详情接口遗留的匿名访问（`@Anonymous`）与缺失的归属校验。
- 购物车改数量/勾选/删除未校验记录归属；商品修改/上下架/删除未校验归属。
- 上传路径硬编码原开发机绝对路径导致交付环境上传失效。

### 构建与交付
- 发行 jar `ruoyi-admin.jar` 由 `mvn -DskipTests clean package`（Maven 3.9.9 + JDK 17）纯净重建，外层 jar 无手工注入 class，改动类位于内层 `BOOT-INF/lib/ruoyi-system-3.9.2.jar`。
- 清理交付包内 fat jar 解包残留（`BOOT-INF`/`META-INF`/`org`，约 99MB）与测试日志。
- 依赖已缓存于本机 `~/.m2`，无网环境可加 `-o` 离线构建。
- 验收：`mvn -DskipTests clean package` BUILD SUCCESS；`mvn test` 15/15 全绿；以重建 jar 启动后 E2E `RESULT: ALL_PASS`。

> 说明：仓库中的 `ruoyi-*` 模块 `pom.xml` 版本号 `3.9.2` 沿用上游 RuoYi-Vue 框架版本，本 CHANGELOG 的版本号标识的是校园淘**交付版本**。
