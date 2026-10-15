# 更新日志

本文件记录校园淘（campus-tao）交付包的版本变更。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

开发过程留痕见 `campus-tao/ai-log/001~007`，里程碑文档见 `campus-tao/M1~M5`。

## [未发布]
- 第三方依赖清单（SBOM）与许可证说明。
- 前端单元测试（如 vitest）尚未纳入交付验证（生产构建校验已由 CI 与 `verify.bat` 覆盖）。
- 交付包内置 Maven 与离线依赖仓库，实现完全无网环境重建。
- 可观测性（健康检查、关键操作审计）与运营侧能力（订单超时自动取消、物流单号、站内通知等）。

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
