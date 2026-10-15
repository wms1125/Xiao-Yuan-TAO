# 更新日志

本文件记录校园淘（campus-tao）交付包的版本变更。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

开发过程留痕见 `campus-tao/ai-log/001~007`，里程碑文档见 `campus-tao/M1~M5`。

## [未发布]
- 第三方依赖清单（SBOM）与许可证说明。
- 前端构建校验与自动化测试（`npm run build` / 前端单测尚未纳入交付验证）。
- 交付包内置 Maven 与离线依赖仓库，实现完全无网环境重建。
- 可观测性（健康检查、关键操作审计）与运营侧能力（订单超时自动取消、物流单号、站内通知等）。

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
