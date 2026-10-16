# 更新日志

本文件记录校园淘（campus-tao）交付包的版本变更。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

开发过程留痕见 `campus-tao/ai-log/001~019`，里程碑文档见 `campus-tao/M1~M5`。

## [1.0.19] - 2026-10-17

《提升评分行动计划》P0-2：**收藏 / 站内信 / 搜索历史三张空表落地**。V2 迁移已建好这三张表与菜单 2011/2012、权限 2118~2120，但后端无实现、前端无页面——菜单点进去是空页（评估报告原话：「留一个点进去是空页的菜单，比没有菜单更伤可信度」）。本轮把数据底座变成可用功能。

### 新增
- **商品收藏（后端 5 文件）**：`domain/CampusFavorite`（含联表冗余的 `productTitle` / `productPrice` / `sellerName`）、`mapper/CampusFavoriteMapper` + XML、`service/ICampusFavoriteService` + `impl/CampusFavoriteServiceImpl`、`controller/CampusFavoriteController`（`list` / `count` / `status` / `toggle` / `remove`）。
- **站内信（后端 5 文件）**：`domain/CampusMessage`、`mapper/CampusMessageMapper` + XML、`service/ICampusMessageService` + `impl/CampusMessageServiceImpl`、`controller/CampusMessageController`（`list` / `unread` / `order/{orderNo}` / `send` / `read/{orderNo}`）。
- **搜索历史（后端 5 文件）**：`domain/CampusSearchHistory`、`mapper/CampusSearchHistoryMapper` + XML、`service/ICampusSearchHistoryService` + `impl/CampusSearchHistoryServiceImpl`、`controller/CampusSearchHistoryController`（`list` / `record` / `{historyId}` / `clear`）。
- **前端**：`api/campus/favorite.js` / `message.js` / `searchhistory.js` 三个接口封装；`views/campus/favorite/index.vue`（概览 + 清单 + 取消收藏 + 清空）；`views/campus/message/index.vue`（会话列表 + 消息流 + 未读角标 + 发送框）；商城页商品卡片与详情页收藏按钮；商城页搜索框最近搜索下拉。
- **订单页「联系对方」入口**：跳转站内信并直接进入该订单会话（`?orderNo=xxx`）。
- **V3 迁移** `db/migration/V3__search_history_perms.sql` + 回滚 `db/rollback/U3__search_history_perms.sql`：补 `campus:searchhistory:list` / `add` / `remove` 三个权限（挂在校园商城 2008 之下）并授予普通角色。收藏与消息的权限 V2 已建，无需重复。

### 变更
- `vitest.config.js` 覆盖率白名单纳入 `src/views/campus/favorite/index.vue` 与 `src/views/campus/message/index.vue`。
- 后端 `info.app.version` 更新为 `1.0.19`。

### 修复
- **站内信接收方由前端传入 → 改为服务端从订单推导并校验为当事方**：原设计若直接接受 `toUserId`，则把用户 ID 作为入参即可给任意用户发消息（典型的越权写）。现在：我是卖方 → 接收方固定为买方（忽略前端传值，防卖方冒充买方互发）；我是买方且订单只有一位卖家 → 接收方为该卖家；订单跨多位卖家（购物车不按卖家拆单，一个订单可能跨多个卖家）→ 买方必须显式指定，且**该值必须通过「是否为订单当事方」校验**。
- **`ElInput` 测试替身不保真**：渲染了 `input` 却从不在 `input` 事件时回传 `update:modelValue`，导致 `v-model` 双向绑定是断的——"输入内容后能发送消息"这条路径根本测不到（替身丢行为，测出来的绿是假的）。已在 `tests/unit/support/element-stubs.js` 补 `onInput`。
- **测试替身的 class 被 `attrs` 顶掉**：`h('div', { class: 'xxx-stub', ...attrs })` 中 Vue 会把模板上的 class 也放进 `attrs`，展开后模板 class 整体顶掉替身自己的类名，测试按 `.xxx-stub` 选择器找不到元素。改为**合并**而非覆盖（`mergeStubClass`），两类 class 同时保留。

### 验证
- 后端门禁 `mvn -B verify`：**BUILD SUCCESS，243 用例 / 0 失败 / 0 错误**（原 188，新增 55 例）。
- 后端覆盖率（JaCoCo 报告实测）：**行 69.77%** / **分支 59.53%**（原 65.49% / 53.76%，双升）。
- 前端门禁：`format:check` / `lint`（0 error，161 条既有 warn）/ `lint:strict`（0 problem）/ `test:coverage` / `build:prod` 全绿。
- 前端覆盖率（v8 实测）：**167 用例 / 10 文件 / 0 失败**；全量 stmt **67.91%** / branch **64.30%** / func **73.36%** / line **69.34%**（原 62.68 / 60.58 / 69.51 / 64.06，全项上升）。收藏页行覆盖 97.14%、站内信页 97.61%。
- 后端新增测试分布：FavoriteService 13 + MessageService 16 + SearchHistoryService 14 + Controller 三个共 25。

### 未纳入本轮
- **e2e 回归未跑**：验证环境中的 MySQL 与后端服务已停止（用户在验证过程中主动取消），端到端断言（收藏→列表→取消、买卖双方收发消息与已读递减、搜索记录与去重）待环境恢复后补跑。
- 站内信未做实时推送（无 WebSocket），目前是"打开页面/会话时拉取"的轮询式交互；表结构已预留 `idx_to_read(to_user_id, is_read)` 索引支撑后续升级。
- 消息未做分页（单订单会话量级可控，暂按全量返回）。
- `campus_favorite` 收藏上限未设（与地址簿的 20 条上限不同——收藏是"看过的好东西"而非"常用配置"，暂不限制，但已预留改造点）。

## [1.0.18] - 2026-10-16

《提升评分行动计划》P0-1：**抽取 `IPaymentGateway` 支付网关抽象层**。企业级评估报告（v1.0.17）给出的最高优先级缺口是"支付能力耦合在 `ICampusAccountService` 里，接微信支付必须改业务代码"。本轮把"支付方式"从订单/售后逻辑里彻底摘掉，业务代码只依赖接口、渠道靠配置切换。

### 新增
- **`payment` 包（10 个新文件，`ruoyi-system`）**：
  - `IPaymentGateway`：`channel()` / `enabled()` / `pay()` / `refund()` / `parseNotify()`——接微信支付时新增一个实现类即可，业务代码一行不改；
  - `PayCommand` / `RefundCommand`：扣款与退款指令（含 `orderNo` 与金额，金额必须为正数）；
  - `PayResult`：**三态** `SUCCESS` / `INSUFFICIENT_BALANCE` / `FAILED`。把"余额不足"与"系统失败"分开，是为了让业务层区别处理——余额不足时订单保持待付款并提示充值；渠道失败必须抛异常触发回滚，而不是把订单静默留在待付款（那会让用户以为没付成、实际钱已扣）；
  - `RefundResult`：成功 / 失败 + 退款单号；
  - `PayNotify`：`signValid` 是**第一道闸门**，验签失败即整体拒绝；
  - `PaymentChannelProperties`（`@ConfigurationProperties("campus.payment")`）：微信商户参数，`isConfigured()` 要求 appId / mchId / apiV3Key / serialNo / privateKeyPath 全部非空；
  - `PaymentGatewayResolver`：按 `campus.payment.channel` 选渠道，**失败显式抛异常、绝不静默回落**——渠道名写错或渠道未启用都抛异常，避免"以为在用微信、实际走的沙箱"这类资金错配；
  - `SandboxPaymentGateway`：原沙箱逻辑迁移为正式渠道实现；
  - `WechatPayGateway`：微信支付骨架（`NOT_IMPLEMENTED` 显式失败，不留"看起来能跑"的假实现）；
  - `impl/CampusPaymentServiceImpl`：业务门面，`handlePaidNotify` 实现**幂等 + 金额核对**。
- **`controller/CampusPaymentController`**：`GET /campus/payment/channel`（查当前生效渠道）+ `@Anonymous POST /campus/payment/notify/{channel}`（渠道回调）。回调端点必须匿名可访问（第三方服务器带不了我们的登录态），安全性完全押在验签上，故**两层都拒**：`parseNotify` 验签失败即拒绝，`CampusPaymentServiceImpl` 再拦一次。
- **配置块 `campus.payment`**（`application.yml`）：渠道与微信商户参数**全部走环境变量注入**，不写明文密钥。
- **`CampusPaymentGatewayTest`**（22 例）：渠道路由 / 沙箱与微信实现 / 回调幂等与金额核对 / `enabledChannels` 语义。

### 变更
- **`CampusOrderServiceImpl`**：支付路径改走 `paymentService.pay(...)`，取消路径改走 `paymentService.refund(...)`；**构造器注入**替代字段注入（依赖是真实对象而非 mock）。
- **`CampusAftersaleServiceImpl`**：审核通过的退款改走 `paymentService.refund(...)`，退款失败抛异常。
- 后端 `info.app.version` 更新为 `1.0.18`。

### 修复
- **取消已付款订单时"先回库存、后退款"的顺序缺陷（本行新引入，由负向用例抓出）**：退款是不可逆的外部动作，若先回库存再退款，退款失败时只能靠 `@Transactional` 回滚来撤销库存增量——一旦将来 `returnStock` 被标注 `REQUIRES_NEW` 或事务被移除，就会留下"库存已回补、钱却没退"的不一致。改为**先退款、后回库存**（与售后审核路径的既有顺序一致），退款失败时天然无副作用需要撤销。两个负向用例（订单取消 / 售后审核）分别用 `UnnecessaryStubbing` 与 `NeverWantedButInvoked` 把这条顺序钉死。
- **`availableChannels` 的语义会被前端误读**：原接口只暴露"已注册"渠道，微信未填商户参数时也会出现在列表里，前端若直接渲染会把"选了走不通"的渠道当成可用选项。新增 `enabledChannels()`（已注册**且** `enabled()`），接口同时返回 `registered` 与 `enabled` 两个字段，语义不再混淆。

### 验证
- 后端门禁 `mvn -B verify`：**BUILD SUCCESS，188 用例 / 0 失败 / 0 错误**（原 163 例，新增 25 例）。
- 覆盖率（取自 JaCoCo 报告实测，非自述）：**行 65.49%**（门禁 ≥55%）、**分支 53.76%**（门禁 ≥40%），较上轮 64.85% / 50.81% 双升。
- e2e 业务回归：**27 断言全 PASS / 0 FAIL**，`RESULT: ALL_PASS`（支付原子扣款、售后退款 + 回库存两条路径已走通新抽象层）。
- 运行时负向验证（对运行中的后端发真实请求）：未登录查渠道 → 401；回调未知渠道 `notexist` → "支付渠道不存在或未启用"；**沙箱渠道回调无验签 → "回调验签失败，已拒绝处理"**（沙箱没有签名体系，但绝不等于"不校验就放行"）；微信渠道未配置 → "支付渠道不存在或未启用"。
- YAML 重复键检查：`application.yml` / `application-druid.yml` 均无重复键。

### 未纳入本轮
- 微信支付 V3 的真实签名、验签、退款 API 对接（骨架已就位，商户参数到位后填实现即可）。
- `channelTradeNo` 尚未落库——沙箱渠道的交易号目前不持久化，真实渠道接入时需在 `campus_order` 增加对应列并纳入对账。
- 前端结算页尚未展示/选择支付渠道（后端接口已具备）。

## [1.0.17] - 2026-10-16

《提升评分行动计划》阶段三：**买家侧旅程断点补全（第一项·收货地址）**。1.0.16 的 V2 迁移已建好 `campus_address` 表、菜单 2011 与权限 2115~2117，但没有任何服务层与页面——菜单点进去是空壳。本轮把数据底座变成可用功能，并补齐前端单测与门禁。

### 新增
- **收货地址后端**（7 个新文件，`ruoyi-system`）：
  - `domain/CampusAddress`（含 `fullAddress()` 拼接辅助）、`mapper/CampusAddressMapper` + XML、`dto/CampusAddressSaveDTO`（手机号 `@Pattern("^1[3-9]\\d{9}$")`）、`service/ICampusAddressService` + `impl/CampusAddressServiceImpl`、`controller/CampusAddressController`；
  - **归属校验两层独立生效**：mapper 删除 SQL 自带 `user_id` 条件（挡住"绕过服务层直接写库"），服务层另有 `requireOwned()`（挡住"忘写条件"）；所有接口的 `userId` 一律取自 `SecurityUtils.getUserId()`，不接受前端传参；
  - 默认地址互斥用「先清后设」（`clearDefaultByUser` → 再置 1），首条地址强制默认，上限 `MAX_ADDRESSES_PER_USER = 20`。
- **下单接入地址簿**：`CampusOrderCreateDTO` 增 `addressId`，下单时地址簿条目**覆盖手填的收货人/电话/地址**，并冻结为 `address_snapshot`（后续改地址簿不追溯改写历史订单）。
- **收货地址前端**：`src/api/campus/address.js`（6 个接口封装）+ `src/views/campus/address/index.vue`——概览卡（总数 / 默认收货人 / 默认电话）+ 列表（默认标签、20 条上限禁用新增按钮）+ 新增/修改弹窗（含默认开关）。
- **前端测试**：`tests/unit/views.campus.address.test.js`（16 例，覆盖挂载请求 / 渲染 / 默认标签 / 概览 / 时间截断 / 上限禁用 / 弹窗开关与回填 / 新增与修改两条提交路径 / 设默认与删除的确认与取消）/ `api.campus.test.js` 增补地址接口 5 例。
- **测试替身库扩充**（`tests/unit/support/element-stubs.js`）：新增 `el-switch` 替身；`el-dialog` 如实渲染 `title`；`el-form` 补 `clearValidate`；`el-input` 渲染 `placeholder`。
- **`campus-tao/ai-log/020-买家侧收货地址模块与前端门禁补齐.md`**：五段式留痕。

### 变更
- **`vitest.config.js`** 覆盖率白名单纳入 `src/views/campus/address/index.vue`。
- 后端 `info.app.version` 更新为 `1.0.17`。

### 修复
- **`form` 裸解构导致地址簿输入框永远为空（最严重）**：`const { form } = data` 裸解构拿到的是旧对象引用，而 `reset()` / `handleUpdate()` 会整体替换 `form.value` —— 替换后模板里的 `v-model="form.receiver"` 仍指向旧对象，**输入框恒为空、`prop="receiver"` 的校验目标恒为 undefined**。特征是"接口层全绿、UI 层静默失效"，只测接口必然漏掉。改为若依正解 `const { form } = toRefs(data)`。
- **取消确认框产生未处理的 Promise 拒绝**：`await confirm().catch(() => { throw })` 把取消异常重新抛出 async 函数，而模板 `@click` 未 `await`，原 `try/catch` 只包住接口调用、包不住取消。改为确认与请求统一包进一个 `try`，取消与接口失败一并吞掉（取消不提示，失败由请求拦截器统一提示）。
- **测试替身库三处不保真**：`el-dialog` 不渲染 `title`（新增/修改弹窗靠标题区分）、`el-form` 未暴露 `clearValidate`（页面 `reset()` 经 `formRef` 调用，`nextTick` 里抛 `TypeError`）、`el-input` 把 `placeholder` 声明为 prop 却从不渲染（声明即消费，不进 `attrs`）。替身丢行为，测出来的绿是假的。
- **MyBatis 显式 NULL 不触发 MySQL `DEFAULT`**：`address_snapshot varchar(400) NOT NULL DEFAULT ''`，传显式 `NULL` 时 `DEFAULT` 不生效（只在整列被省略时套用），7 个订单用例报 `Column 'address_snapshot' cannot be null`。mapper 改 `COALESCE(#{addressSnapshot,jdbcType=VARCHAR}, '')`，并补 `jdbcType`（不声明类型时 MyBatis 对 `null` 推断不出 JDBC 类型）。
- **【CI 抓到的】容器内日志目录权限导致后端永远起不来**：`docker-compose.yml` 用宿主 bind mount `./logs/backend:/app/logs` 挂日志，而容器以非 root 用户 `campus` 运行。bind mount **不会**继承镜像里的 `chown`，CI runner 上该宿主目录由 Docker 以 root 创建 → logback 写 `logs/sys-*.log` 报 `Permission denied` → `Logging system failed to initialize` → 后端进程退出、health 永远 unhealthy（CI `docker` job 10 分钟超时）。改为**具名卷** `backend-logs:/app/logs`——具名卷首次创建时会把镜像目录的内容连同所有权拷入卷内，能正确继承 `campus`。已写下注释说明"要宿主侧直接翻日志可自行改回 bind mount，但必须先 chown"。
- **【CI 抓到的】e2e job 建库不含 V2 列，SQL 层集成测试 7 个 error**：e2e job 导入的 `campus_tao_full.sql` 是 Flyway V1 基线的等价物（42 表、无 `address_snapshot`），而 `CampusMapperSqlIntegrationTest` 需要该列，直接报 `Unknown column 'address_snapshot' in 'field list'`。**调整步骤顺序**修复——把「打包后端 + 启动后端」提到 SQL 层测试之前，让应用自身的 Flyway 完成存量库升级（baseline V1 → apply V2），再跑测试。这比"手工预先套增量脚本"更正确：后者会和后到的 Flyway 撞车（V2 的 `ALTER TABLE ADD COLUMN` 非幂等，列已存在时 Flyway 报 Duplicate column 而启动失败）。同时新增「断言 Flyway 版本记录与表数」步骤，把"迁移真的生效"变成可断言的证据而非默认放过。
- **`sql/migration-v1.0.17.sql` 移除硬编码 `USE campus_tao`**：带 `USE` 的脚本会无视命令行传入的库名、永远写到 `campus_tao` 上——在临时库上验证时会造成误写且难以发现。改为目标库由命令行参数指定；不指定时 mysql 明确报 `ERROR 1046 No database selected`，是安全的失败方式。

### 新增（增量迁移脚本）
- **`campus-tao/sql/migration-v1.0.17.sql`**：面向「已建库」环境的增量脚本，与 Flyway V2 **语义等价**（4 张新表 + `campus_order.address_snapshot` + 菜单 2011~2013 与按钮权限 2115~2120）。带幂等防护：`CREATE TABLE IF NOT EXISTS`、`INSERT IGNORE`，`ALTER ADD COLUMN` 用 `information_schema` 判存后动态 `PREPARE`（MySQL 8 无 `ADD COLUMN IF NOT EXISTS`）。**走 Flyway 的环境无需执行本脚本**——后端启动时自动应用 V2；本脚本面向手工维护、未启用 Flyway 的存量库。
- **CI e2e job 新增「断言 Flyway 版本记录与表数」步骤**：打印 `flyway_schema_history` 并核对 `baseline_ok` / `v2_ok` / `address_snapshot_ok` / `tables` 四项。


### 验证
- **后端门禁**：`mvn -B verify` **BUILD SUCCESS**（Spotless clean；首跑因 `CampusAddressController` import 顺序违规被 `spotless-check` 拦下，`spotless:apply` 后复跑通过）。surefire 聚合 **163 用例 / 0 失败 / 0 错误 / 0 跳过**；`jacoco.csv` 独立实算（`com/ruoyi/campus/**`，排除 `domain/**` 与 `mapper/**`）行 **64.85%**（642/990）/ 分支 **50.81%**（188/370），门禁 55 / 40 通过。
- **地址簿 → 下单快照冻结（DB 直查）**：下单时同时手填 `张三 / 13800000000 / 东校区1号楼101` 并传 `addressId` 指向 ry 的默认地址，入库结果以地址簿为准 —— `receiver_name=李四`、`receiver_phone=13900000000`、`address=西校区2号楼202`、`address_snapshot=李四 13900000000 西校区2号楼202`。
- **前端门禁全绿**：`format:check` 通过；`lint` 0 error / 161 warning（161 条均为若依脚手架既有债，新文件 0 条）；`lint:strict`（`--max-warnings 0`）0 problems；`build:prod` 成功。
- **前端测试与覆盖率**：vitest **111 用例 / 8 文件 / 0 失败**；覆盖率 语句 **62.68%** / 分支 **60.58%** / 函数 **69.51%** / 行 **64.06%**，门禁 45/45/55/40 全过（上一轮 59.58/57.46/66.58/60.54，棘轮只向上）。地址页自身 91.34 / 94.28 / 84.44 / 97.87；`address.js` 四项均 **100%**。
- **菜单与权限实跑确认**：`GET /system/menu/list` 命中 `(2011,'收货地址','address','campus/address/index','campus:address:list')` 与 2115/2116/2117 三个按钮权限，菜单 `component` 与前端页面实际路径对得上。
- **端到端业务回归**：`e2e_business.py` → **`RESULT: ALL_PASS`**（真实 MySQL + 真实 Service 层，含防超卖 / 越权 / 退款回滚 / 物流越权 / 外呼留痕隔离断言）。
- **负向验证**：
  - 取消路径缺陷修复前**稳定复现 2 个 Unhandled Rejection**（`handleSetDefault` / `handleDelete` 各一），修复后归零 —— 证明该断言非恒真；
  - `form` 裸解构缺陷修复前断言 `element.value === '王五'` 实际拿到 `''`；
  - `el-dialog` 不渲染 title 时「弹窗标题正确」断言**必然失败** —— 说明替身缺行为会被测试暴露而非悄悄放过。

### 本轮由 CI 独立复验抓到的缺陷（本地全绿、CI 红）

这两项本地跑都是绿的，只有 CI 暴露出来——正是"必须让 CI 独立复验"的理由。

**1. 容器内日志目录权限：后端永远起不来**

CI `docker` job 报 `后端容器未在预期时间内进入 healthy`，容器日志：

```
ERROR in ch.qos.logback.core.rolling.RollingFileAppender[sys-user] -
  openFile(logs/sys-user.log,true) call failed. java.io.FileNotFoundException: logs/sys-user.log (Permission denied)
Logging system failed to initialize using configuration from 'null'
```

根因：`docker-compose.yml` 用**宿主 bind mount** `./logs/backend:/app/logs` 挂日志目录，而容器以非 root 用户 `campus` 运行。**bind mount 不继承镜像里的 `chown`**，宿主目录在 CI runner 上由 Docker 以 root 创建，于是非 root 进程写不进去。logback 初始化失败直接让 Spring Boot 退出，health 永远 unhealthy。

修复：改为**具名卷** `backend-logs:/app/logs`。具名卷在首次创建时会把镜像目录的内容**连同所有权**拷入卷内，因此能正确继承 `campus`。

**2. e2e job 建库不含 V2 列：SQL 层集成测试 7 个 error**

```
### Error updating database. Cause: java.sql.SQLSyntaxErrorException:
    Unknown column 'address_snapshot' in 'field list'
```

本地能过是因为开发库早已被 Flyway 升到 V2；CI 的 e2e job 只导入了 `campus_tao_full.sql`（V1 基线等价物，42 表），而 `CampusMapperSqlIntegrationTest` 需要 `address_snapshot`。

修复是**调整步骤顺序**而非加脚本：把「打包后端 + 启动后端」提到 SQL 层测试之前，让应用自身的 Flyway 完成存量库升级（baseline V1 → apply V2），再跑测试。**没有**采用"手工预先套 `migration-v1.0.17.sql`"这个看似更直接的方案——因为 V2 的 `ALTER TABLE ADD COLUMN address_snapshot` 非幂等，手工套完之后 Flyway 再跑一遍会报 Duplicate column 而启动失败，等于把一个 bug 换成另一个。

**本地实测这条新顺序**（在 42 表、无版本记录的存量库上直接启动后端）：

```
Schema history table `campus_tao_fw`.`flyway_schema_history` does not exist yet
Creating Schema History table ... with baseline ...
Successfully baselined schema with version: 1
Migrating schema `campus_tao_fw` to version "2 - buyer journey"
Successfully applied 1 migration to schema `campus_tao_fw`, now at version v2 (execution time 00:00.165s)
```

表数 42 → **47**；随后 `mvn -Dtest=CampusMapperSqlIntegrationTest,CampusAccountAtomicSqlTest test` → **13 用例 / 0 失败 / 0 错误**（此前为 7 个 error）。

**3. 增量迁移脚本与 Flyway V2 的等价性交叉验证**

`sql/migration-v1.0.17.sql` 手工套到 42 表存量库后，与 Flyway 产出的库逐项 diff：

- 表数 46 vs 47，**唯一差异是 `flyway_schema_history`**（Flyway 自己的登记表，手工路径本就不该有）；
- `campus_order` 列（列名/类型/可空/默认值）**完全一致**；
- `campus_address` 表结构**完全一致**。

幂等性：连跑两遍，第二遍输出 `address_snapshot 已存在，跳过`，退出码 0。
负向验证：不指定库名时输出 `ERROR 1046 (3D000) at line 31: No database selected`——移除硬编码 `USE` 后失败方式是安全的，不会误写到别的库。


### 未纳入本轮（如实记录）
- **`getDefaultAddress()` 已封装但页面未使用**：页面从列表首条推导默认地址（后端已按 `isDefault desc` 排序，与 `selectDefault` 口径一致）；独立接口留给后续购物车/下单页的"预填默认地址"场景，其本身已进单测覆盖（100%）。
- **Docker 镜像未在本机构建**：本机无 Docker，镜像与 compose 的**构建**验证只能由 CI `docker` job 承担——但也正因如此，本轮 CI 才独立抓出了「bind mount 日志目录权限导致后端起不来」这个本地不可能发现的缺陷（见上文验证节）。**该缺陷修复后 CI `docker` job 已转绿**：后端容器第 5 次探测进入 `healthy`、前端静态页可达、`/prod-api/captchaImage` 返回 200（nginx 反代后端生效）。1.0.16 轮的 `docker` job 曾因此失败。
- **收藏 / 搜索历史 / IM 沟通仍未实现**：V2 迁移已建 `campus_favorite` / `campus_search_history` / `campus_message` 三张表，但 Java 服务层与 Vue 页面尚未落地。收货地址只是买家侧四项断点里的第一项。
- **支付网关抽象层未动**：`CampusAccount` 仍是沙箱余额账户，未抽 `IPaymentGateway`。
- **旧增量脚本仍带硬编码 `USE campus_tao`**：`sql/migration-v1.0.5.sql` / `migration-v1.0.7.sql` / `migration-v1.0.15.sql` 三个已发布脚本沿用旧惯例，脚本内的 `USE` 会覆盖命令行传入的库名（在临时库上验证时可能误写）。本轮只在新脚本 `migration-v1.0.17.sql` 中修正；改动已发布过的脚本需单独评估影响面，故未在本轮处理。

---

## CI 最终结论（1.0.17 · run 38896154030）

修复上述两个 CI 缺陷后重跑，**6 个 job 全部通过——这是本项目第一次全部门禁转绿**（1.0.16 轮为 5/6，`docker` 失败；中间轮为 4/6，`docker` 与 `e2e` 失败）：

| job | 结果 |
|---|---|
| 后端构建 · 单元测试 · 覆盖率门禁 | success |
| 端到端业务回归（MySQL 8 · 双账号全链路） | success |
| 前端单测 · 覆盖率门禁 · 生产构建 | success |
| 数据库迁移与回滚（Flyway · 逐表行数核对） | success |
| 容器镜像构建与起栈自检（Dockerfile · docker compose） | success |
| 依赖漏洞扫描（前端 npm audit · 后端 osv-scanner） | success |

e2e job 关键输出（证明 Flyway 真的升级了存量库，而非"应用能启动就放过"）：

```
tables=47
baseline_ok=1
v2_ok=1
address_snapshot_ok=1
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
RESULT: ALL_PASS
```

docker job 关键输出（证明容器化交付物真的能跑）：

```
第 5 次探测：backend health = healthy
前端静态页可达
/prod-api/captchaImage → 200（nginx 反代后端生效）
```

## [1.0.16] - 2026-10-16

《提升评分行动计划》阶段二：**可复现交付单点突破**。上一轮评估中"可复现交付"是四个维度里最低的一项（81/100），缺口集中在两件一眼可见的事——**没有数据库版本化迁移**（建库靠一次性全量脚本，无法回滚、无法在 CI 验证）与**没有任何容器化交付物**（全仓无 Dockerfile/compose）。本轮把这两件做完，并补上上一轮明确欠下的浏览器实测证据，最后 push 交由 CI 独立复验。

### 新增
- **数据库版本化迁移（Flyway）**：
  - `ruoyi-admin/src/main/resources/db/migration/V1__baseline_campus_tao.sql`——由 `campus_tao_full.sql` **等价转化**的 42 表基线（唯一改动是移除 `CREATE DATABASE` / `USE`，目标库由连接串决定），保证"基线 == 现网结构"可被机器核对；
  - `.../V2__buyer_journey.sql`——买家侧旅程数据底座：`campus_address`（收货地址）、`campus_favorite`（收藏，`uk_user_product`）、`campus_search_history`（搜索历史，`uk_user_keyword`）、`campus_message`（买卖双方消息，带订单上下文与已读位）；`campus_order` 增 `address_snapshot` 列；菜单 2011「收货地址」/ 2012「我的收藏」/ 2013「消息沟通」及按钮权限 2115~2120 授予 `role_id=2`；
  - **`baseline-on-migrate: true` + `baseline-version: 1`**：同一套配置同时覆盖「空库顺序执行 V1→V2」与「存量库打基线后只补执行 V2 及之后」，老用户直接启动新版后端即自动升级，**无需任何手工 SQL**。
- **回滚能力**（Flyway 社区版无 `undo`，采用补偿脚本 + 清理版本记录）：
  - `db/rollback/U1__baseline_campus_tao.sql`、`U2__buyer_journey.sql`；
  - `db/rollback/rollback.sh` 通用回滚工具（`rollback.sh <目标版本> [--dry-run]`，自动定位项目内置 mysql，倒序执行 U 脚本并清理 `flyway_schema_history` 对应版本）；
  - `4-回滚数据库.bat` Windows 包装（`4-回滚数据库.bat 1` / `0`）；
  - `db/rollback/README.md`：迁移体系说明、日常用法、新增迁移的步骤与约束（已合并的脚本不可修改，Flyway 用 checksum 校验）。
- **迁移验证工具链**：
  - `db/tools/compare_migration.sh`——参照库与迁移库**逐表行数核对**，差异项须命中预期清单（V2 菜单种子 `+9`、4 张新表），运行期写入表（`sys_job_log` 等）显式豁免并标注理由；
  - `db/tools/verify_flyway.sh`——端到端 11 项断言：建参照库 → 空库迁移 → 行数核对 → 回滚到 0 → 重放 → 再核对。
- **容器化交付物**：
  - `campus-tao/RuoYi-Vue-master/Dockerfile`——后端多阶段镜像（maven 构建 → JRE 运行），非 root 用户、时区/编码、`HEALTHCHECK` 打 `/actuator/health`、上传目录与日志目录挂卷；
  - `campus-tao-ui/Dockerfile` + `nginx.docker.conf`——前端多阶段镜像（`npm ci` 严格按 lock 构建 → nginx 托管），容器内 nginx 反代 `/prod-api/` 与 `/profile/` 到 `backend:8080`；
  - `docker-compose.yml`——`mysql` + `redis` + `backend` + `frontend` 四服务，含健康检查与 `depends_on: service_healthy` 编排；数据库端口仅绑 `127.0.0.1`；数据库无需手工初始化（后端启动时 Flyway 自动迁移）；
  - `.env.example`（含口令/密钥必填项与生成建议）、两份 `.dockerignore`。
- **`campus-tao/RuoYi-Vue-master/tools/check_yml_dup_keys.py`**：Spring YAML 顶层重复键静态检查——本轮真实的启动失败（`found duplicate key spring`）只在运行期暴露，故加为提交前拦截。
- **浏览器实测证据** `campus-tao/docs/evidence-v1.0.15/`：6 张真实 Chromium 渲染截图（登录页 / 买家首页 / **校园商城** / **商品详情** / **评价管理** / 我的订单）+ 可复现的 `capture.sh`。
- **CI 新增两个 job**：
  - `flyway`——在 MySQL service 上跑 `verify_flyway.sh`（迁移 → 行数核对 → 回滚 → 重放 → 再核对），并用 `mvn -N flyway:migrate` **不启动应用**执行迁移，避免运行期数据污染行数判定；
  - `docker`——`docker compose build` → 起栈 → 轮询 backend 容器 health 至 `healthy` → 校验前端可达且 `/prod-api/` 反代生效 → 失败时 dump 容器日志。

### 变更
- **`spring-boot-flyway` 依赖（关键）**：Spring Boot 4 起自动配置被拆为独立模块，**只引 `flyway-core` 不会激活 `FlywayAutoConfiguration`**——应用照常启动、`/actuator/health` 也是 UP，但迁移静默不执行。已在 `ruoyi-admin/pom.xml` 补上该依赖并写下说明注释。
- **根 `pom.xml`** 引入 `flyway-maven-plugin`（挂 `flyway-mysql` + MySQL 驱动，**不绑定任何生命周期**），供运维/CI 以 `mvn -N flyway:migrate|info|validate` 在**不启动应用**的前提下执行与检查迁移。
- **配置外部化**：`application-druid.yml` 主库连接串改为 `${DB_URL:...}` 可覆盖；`application.yml` 的 Redis 地址/端口改为 `${REDIS_HOST:localhost}` / `${REDIS_PORT:6379}`。同时把连接串默认值的 `useSSL=true` 改为 `useSSL=false&allowPublicKeyRetrieval=true`（本地/容器场景下 `true` 会因无证书而连接失败）。
- **`EmbeddedRedisConfig` 新增 `EMBEDDED_REDIS_ENABLED` 开关**：容器内已有 compose 的 redis 服务，设为 `false` 即不再启动内嵌实例。
- **`.gitignore`** 忽略 `.env` / `.env.*`（保留 `.env.example`），避免容器部署的真实口令入库。
- **`ci.yml` 注释校正**：后端覆盖率门禁描述由「行 ≥35% / 分支 ≥18%」更正为 pom 实际值「**行 ≥55% / 分支 ≥40%**」；"仓库为本地库（无远程）"更正为实际远程仓库地址。注释与配置不一致本身就是缺陷。
- 后端 `info.app.version` 更新为 `1.0.16`。

### 修复
- **`U1__baseline_campus_tao.sql` 死代码导致回滚状态不一致**：脚本在 `DROP TABLE sys_menu` **之后**又 `DELETE FROM sys_menu` → mysql 退出码非零 → 回滚工具的 `set -e` 在清理版本记录**之前**中止，造成"库结构已回滚、`flyway_schema_history` 仍显示已应用"的不一致。现删除该已成死代码的 `DELETE`，并让回滚工具**显式捕获退出码、告警但不中断**，确保版本记录一定被清理。
- **`rollback.sh` 静默把失败伪装成"版本 0"**：脚本依赖 `mysql` 在 PATH 上，而本项目自带 MySQL，查询失败被 `2>/dev/null` 吞掉后由 `|| echo 0` 兜成"当前版本 0"。现改为自行定位内置 mysql（`MYSQL_BIN` 可覆盖、最后回落 PATH），并在找不到客户端时明确报错。
- **`rollback.sh` 未指定库名**：`mysql < U2.sql` 报 `ERROR 1046 No database selected`，现补上库名参数。
- **逐表行数核对在 Windows 上全量误判**：Windows 版 mysql 客户端输出 CRLF，表名尾部带 `\r`，导致 42 张表**全部**被判为"不一致/非预期新增"。现统一 `tr -d '\r'`。
- **回滚脚本迁移目录定位错误**：`U*.sql` 已统一收纳到 `db/rollback/`（与工具同处，不再随 jar 分发），`MIG_DIR` 指向 `ruoyi-admin/src/main/resources/db/migration`。

### 验证
- **Flyway 端到端（11/11 通过）**：参照库 42 表 → 空库迁移 `Successfully applied 2 migrations`、版本记录 `1,2`、表数 **47** → **逐表行数与全量脚本一致** → 回滚到 0（业务表全清、版本记录清空）→ 重放成功、表数恢复 47 → 重放后行数仍一致。
- **存量库原地升级（baseline 路径）**：对已有 42 表、无版本表的 `campus_tao` 直接启动新版后端，日志输出 `Creating Schema History table ... with baseline` → `Successfully baselined schema with version: 1` → `Migrating ... to version "2 - buyer journey"` → `Successfully applied 1 migration, now at version v2`，`Started RuoYiApplication in 10.387 seconds`，`/actuator/health` = `{"status":"UP"}`。
- **端到端业务回归**：`e2e_business.py` → **`RESULT: ALL_PASS`**（真实 MySQL + 真实 Service 层，含防超卖 / 越权 / 退款回滚 / 物流越权 / 外呼留痕隔离断言）。
- **后端门禁**：`mvn -B verify` **BUILD SUCCESS**（7 模块全绿，Spotless 25 文件 clean）。surefire 聚合 **136 用例 / 0 失败 / 0 错误 / 0 跳过**；`jacoco.csv` 独立实算 行 **62.40%**（566/907）/ 分支 **46.75%**（158/338），门禁 55 / 40 通过。
- **浏览器实测**：真实 Chromium 驱动，登录 `ry/admin123` 后逐页截图；商品详情页显示业务规则"不能购买自己发布的商品"（当前登录用户即卖家），证明为真实渲染而非静态占位。
- **负向验证**：
  - **"应用能启动" ≠ "迁移生效"**：只引 `flyway-core` 时应用正常启动、health 为 UP，但库是空的（由 `Table 'sys_config' doesn't exist` 暴露）——证明迁移证据必须落在 `flyway_schema_history` + 表数 + 行数核对上；
  - 重复顶层键 `spring:` 导致应用**启动即失败**，证明该静态检查项非形式主义；
  - 行数核对在 CRLF 缺陷未修时 42 张表**全部**报警，证明它不是"永远打印一致"的空壳；
  - 回滚工具修好前，`verify_flyway.sh` 的「版本记录已清空」断言**必然失败**，说明该断言非恒真。

### 未纳入本轮（如实记录）
- **Docker 镜像未在本机实际构建**：本机**无 Docker**（`docker: command not found`），镜像与 compose 仅做 YAML/结构校验与静态审阅，真正的构建验证交由 CI 的 `docker` job。本轮结束时其状态是"已写好、未经构建验证"。
- **`1-初始化数据库.bat` 未在"全空机器上从零"重跑**：脚本导入的 `campus_tao_full.sql` 本轮未改动，且 V1 基线正是由它等价转化（已由"逐表行数一致"间接覆盖）；与 Flyway 的衔接只在**已有库**上实测过。
- **买家侧旅程功能实现仍未落地**：V2 迁移已建好 4 张表与菜单，但对应的 Java 服务层与 Vue 页面**尚未实现**——本轮铺的是数据底座，功能留待下一轮。
- **支付网关抽象层未动**：`CampusAccount` 仍是沙箱余额账户（内部转账），未抽 `IPaymentGateway`，未对接第三方支付。
- **eslint 161 条 warn 未清零**：均为若依脚手架文件的上游债务，维持"自有目录 0 warning"的口径。

## [1.0.15] - 2026-10-16

《提升评分行动计划》阶段一（T1 + T3 + T4）落地。本轮聚焦三件事：**补全买家侧业务闭环**（此前 7 个页面全是管理台风格，买家旅程断在"看不到商品"）、**建立 AI 辅助开发规范**、**清理项目自己已如实披露却一直未闭环的 7 个遗留项**。

### 新增
- **买家侧三页（业务闭环补全）**：
  - `src/views/campus/mall/index.vue` 校园商城——关键词 / 分类 / 排序筛选 + 商品卡片网格，服务端强制 `status=1`（仅展示在售），点击卡片跳详情；
  - `src/views/campus/mall/detail.vue` 商品详情——图片相册与缩略图切换、信息面板、评价列表与平均分；`canBuy` / `buyBlockReason` 在前端**提前拦截**自购 / 已下架 / 零库存三类不可购买情形（服务端仍有强制校验）；
  - `src/views/campus/review/index.vue` 评价管理——卖家视角统计卡（总数 / 均分 / 好评数）+ 列表 + 分页。
- **后端卖家视角评价接口** `GET /campus/review/seller`：`sellerId` **取自登录态**，不接受前端传参；`CampusReview` 增加非表字段 `productTitle`，由 mapper `join campus_product` 带出，供卖家列表展示。
- **DTO 校验收敛**：新增 `CampusCartQuantityDTO`、`CampusCartCheckDTO`、`CampusCategorySaveDTO`、`CampusProductStatusDTO`、`CampusProductUpdateDTO` 共 5 个 DTO，购物车数量/勾选、分类保存、商品更新/改状态等写接口全部改为 `@Valid` + 声明式约束，替代原先的裸字段接收与散落判空。
- **`campus-tao/AI-辅助开发规范.md`**：十章规范，确立"**'用了 AI'不是加分项，'能被验证的 AI 产出'才是**"的立场；五原则（人在环内 / 产出即证据 / **自述不算证据** / 单一事实来源 / 留痕可追溯）；AI 可做 / 须人工确认 / 红线三段式边界；`ai-log` 五段式留痕模板；门禁表；**负向验证为强制动作**；提交前 Checklist。
- **`campus-tao/M3-业务闭环实现与前端交付.md`**：补齐缺失的 M3 里程碑文档（此前仅有 M1/M2/M4/M5），十章覆盖闭环全景、订单状态机、10 个业务域接口清单、10 个前端页面、动态菜单权限矩阵、建库脚本与 E2E 证据。至此 M1~M5 齐备。
- **`sql/migration-v1.0.15.sql`**：面向已建库环境的增量脚本，新增菜单 2008「校园商城」、2009「商品详情」（`visible='1'` 隐藏菜单，仅为注册 `/campus/mall-detail` 路由）、2010「评价管理」，并授予 `role_id=2`；`campus_tao_full.sql` 同步追加。
- **前端 `lint:strict` 脚本**：`eslint src/views/campus src/api/campus src/utils/ruoyi.js tests --max-warnings 0`。
- **组件用例**：`tests/unit/views.campus.mall.test.js`（13 例）、`tests/unit/views.campus.review.test.js`（5 例）。

### 变更
- **ESLint 分两档门禁**：全仓仍守 **0 error**（若依脚手架遗留 161 条 warn 属上游债务，不阻断）；**校园淘自有目录额外守 0 warning**（`lint:strict`）。CI `frontend` job 与根 `verify.bat` 均同步新增该步骤，防止新增代码的告警混进既有噪声被长期忽略。
- **前端覆盖率白名单**纳入 `mall/index.vue`、`mall/detail.vue`、`review/index.vue` 三页。
- **订单页**：新增「评价」按钮（`status===3` 且 `v-hasPermi=['campus:review:add']`）与评价弹窗（逐商品星级 + 内容），提交用 `Promise.allSettled`；操作列宽 260→320。
- **CI `frontend` job 注释校正**：覆盖率棘轮基线描述由「行 ≥25%」更正为「行 ≥40 / 分支 ≥45 / 语句 ≥45 / 函数 ≥55」，与 `vitest.config.js` 实际阈值一致。
- 后端 `info.app.version` 更新为 `1.0.15`。

### 修复
- **订单号同秒并发冲突**：原 `genOrderNo` 仅含秒级时间戳（`CT + yyyyMMddHHmmss + userId`），同一用户同秒多次下单会撞 `uk_order_no` 且无重试。现追加进程内 `AtomicInteger` 序列（4 位、`%10000` 回绕），并以唯一索引兜底；多实例部署的已知边界已写入方法注释。
- **`getStats()` 缺 `.catch()`**：订单页概览请求失败会产生未处理 Promise rejection 且残留旧统计，与列表侧行为不一致。现补 `.catch()` 并清空 `statusStats`。
- **删除死代码** `updateCampusProductWithVersion`（mapper 接口 + 两处 XML 块）：该乐观锁 SQL 无任何调用方。

### 验证
- **后端**：`mvn -B verify` **BUILD SUCCESS**（7 模块全绿，Spotless 25 文件 clean）。surefire 聚合 **123 用例 / 0 失败 / 0 错误 / 0 跳过**；`jacoco.csv` 独立实算 行 **62.40%**（566/907）/ 分支 **46.75%**（158/338），门禁 55 / 40 通过。
- **前端**：`vitest run --coverage` **90/90 全绿**（7 个测试文件）。实测 语句 **59.89%** / 分支 **57.46%** / 函数 **67.52%** / 行 **60.88%**，门禁 45 / 45 / 55 / 40 通过。
- **前端静态检查**：`format:check` 通过；`lint` **0 error / 161 warn**（全部位于若依脚手架文件）；`lint:strict` **退出码 0**（校园淘自有目录 0 warning）。`build:prod` 退出码 0。
- **负向验证（订单号并发用例真在断言）**：临时还原旧 `genOrderNo` 实现 → 并发用例失败，报 `expected: <128> but was: <1>`（16 线程 × 8 单 = 128 次下单仅产出 **1** 个唯一订单号）；恢复修复后全绿。
- **未做浏览器实测（如实记录）**：本轮**未取得**新增三页的浏览器实测证据——本地 8080 后端与 8088 前端均未启动，MySQL 3306 亦不可连。三页的行为由组件用例（18 例）与前端四道门禁覆盖，但"页面实测确认"**不成立**，需在服务启动后补验。

### 未纳入本轮（如实记录）
- **T2 Flyway 数据库版本化迁移与回滚**：属交付行为变更且需先启动本地 MySQL，待确认后再做。
- **T5 容器化 / T6 `@SpringBootTest` / T7 SpotBugs + CodeQL / T8 覆盖率上调 / T9 k6 压测 / T10 ai-log 可审计化**：阶段二、三任务，尚未开始。
- **eslint 161 条 warn 未清零**：均为若依脚手架文件（`tool/build/RightPanel.vue`、`tool/gen/*`、`Crontab/*` 等）的上游债务，本轮通过"自有目录 0 warning"把质量口径钉在本项目代码上，而非改动上游脚手架。

## [1.0.14] - 2026-10-16

前端测试纵深：v1.0.13 已如实记录「**前端组件测试仍为 0**」——分页与概览联动的页面行为只靠人工浏览器实测，没有沉淀成自动化用例。本轮把它补上，并把前端覆盖率门禁从"只盖纯逻辑层（接口封装 + 工具函数）"扩到"覆盖页面行为"。

### 新增
- **devDependency `jsdom` + `@vue/test-utils`**：共引入 50 个包，`npm audit`（全量与 `--omit=dev`）仍为 **0 项**，`resolved` 全部指向 `registry.npmjs.org`（与 v1.0.13 归一化后的口径一致，未回落到本机代理/镜像源）。
- **`tests/unit/support/element-stubs.js`**：Element Plus 轻量替身。不把整个组件库挂进 jsdom——`el-table`/`el-scrollbar` 依赖 jsdom 未实现的 `ResizeObserver`，真实挂载需要额外 polyfill，且会把用例变成"验证 Element Plus 自己"。替身按"结构保真、能力最小"实现：需要渲染处渲染默认插槽（保证模板绑定被求值）、`el-table`/`el-table-column` 之间用 provide/inject 按行调用列的 scoped slot（否则 `scope.row` 为 undefined，模板表达式直接抛错）、`el-form` 暴露 `validate`/`resetFields` 使表单提交与重置路径可被驱动。
- **组件用例**：`tests/unit/views.campus.order.test.js`（10 例）、`tests/unit/views.campus.aftersale.test.js`（8 例），合计 **18 例**。聚焦本轮新增行为：分页翻页后 `listOrder` 收到新 `pageNum` 且列表条数取自分页信封 `rows`/`total`；状态筛选后 `pageNum` 归 1 且**概览与列表一起刷新**；概览卡片数字与退款金额合计**来自 `/stats` 聚合而非当前页**（构造"当前页全是已完成"的数据，若按当前页推导则待付款/待收货会显示 0，直接揭穿分页后统计卡片"撒谎"）。另覆盖详情、支付、取消、发货、收货、售后申请与审核等交互路径，以及金额/状态文案格式化与缺失关联订单的文案兜底。

### 变更
- **`vitest.config.js` 挂 `@vitejs/plugin-vue`**：否则 `.vue` 单文件组件无法在测试中被编译。刻意不引入 `vite.config.js` 里的压缩、图标、自动导入等构建期插件——测试只需要能跑业务逻辑。
- **按文件切换运行环境**：保留默认 `environment: 'node'`（纯逻辑层用例无需 DOM），需要 DOM 的组件用例在文件首行用 `// @vitest-environment jsdom` 就近声明。**未使用 `environmentMatchGlobs`**：该配置项已在 Vitest 3 弃用、Vitest 4 移除，按文件声明是它替代方案里最直观的一种。
- **覆盖率白名单纳入两个列表页**：`src/views/campus/order/index.vue`、`src/views/campus/aftersale/index.vue` 进入 `coverage.include`，使"页面行为"真正被门禁覆盖，而不是只统计它们调用的接口封装。
- **覆盖率门禁整体上调（棘轮）**：语句 24→**45**、分支 23→**45**、函数 32→**55**、行 25→**40**（按计划要求行覆盖率抬到 40%，其余三项同步上调，且均比实测值留出若干百分点余量——门禁的职责是拦住覆盖率塌方，不是拦掉无关改动的正常抖动）。前端用例由 **48 → 66**。
- 后端 `info.app.version` 更新为 `1.0.14`。

### 验证
- **前端单测**：`66/66` 全绿（原 48 例全部保留，新增 18 例组件用例）。实测覆盖率 语句 **50.17%** / 分支 **50.35%** / 函数 **60.93%** / 行 **51.27%**（上一版为 25.04 / 23.61 / 35.57 / 26.2），四项门禁全部通过。
- **负向验证 A（门禁真会拦）**：以 `--coverage.thresholds.lines=90` 覆盖阈值运行 → 退出码 **1**，日志 `ERROR: Coverage for lines (51.27%) does not meet global threshold (90%)`。证明阈值不是摆设。
- **负向验证 B（用例真在断言新行为）**：① 删掉 `order/index.vue` 中 `handleStatusFilter` 的 `queryParams.pageNum = 1` → 「切换状态筛选」用例失败（`expected { pageNum: 3, … } to match object { pageNum: 1, … }`）；② 把 `aftersale/index.vue` 的 `handleQuery` 由 `refresh()` 改为只 `getList()` → 「按订单号搜索」用例失败。两处均改回即恢复 66/66 全绿，且 `git diff` 确认源码无残留改动。
- **前端其余门禁**：`format:check` 通过；eslint **0 error / 161 warn**（与基线逐项一致，新增文件未引入任何告警）；`build:prod` 通过。
- **观察项（未改动，如实记录）**：两个页面的 `getStats()` 只有 `.then()` 没有 `.catch()`，概览请求失败会产生未处理的 Promise rejection（列表侧 `getList()` 有 catch，故行为不一致）。本轮只测试不修改，用例亦刻意不构造概览失败分支；是否收口由后续决定。

## [1.0.13] - 2026-10-16

企业级评估 P0 项集中治理：按上一轮评估报告（综合 77/100）列出的四组 P0 差距逐项修复——**业务边界**（列表无界增长 / N+1 / 空指针）、**测试纵深**（核心交易逻辑无 service 层单测）、**交付安全**（配置内置弱口令默认值、登录页预填演示凭证）、**持续集成**（e2e 未入流水线）。此外补上**真实 SQL 层集成测试**：此前测试纵深只到 Mockito 打桩，本轮让真实 mapper XML 与真实 MySQL 一起进入回归。

### 新增
- **订单/售后列表页概览接口**：`GET /campus/order/stats`、`GET /campus/aftersale/stats`，按状态分组聚合（售后单另带退款金额合计），与列表复用同一筛选条件（`Filter_Where` 片段），避免两处 where 漂移。新增 `CampusStatusStat` 聚合 VO。
- **service 层单测**：`CampusOrderServiceTest`（25 例）、`CampusAftersaleServiceTest`（10 例），覆盖下单扣库存/防超卖拦截、支付扣款与幂等、取消退款与回库存、超时取消的原子性分支、售后审核退款与回滚、**列表批量查询的 N+1 回归**、**关联订单缺失时的 fail-fast 回归**。
- **SQL 层集成测试**：`CampusMapperSqlIntegrationTest`（9 例）。不启动 Spring 上下文，用 MyBatis 直接加载**仓库里真实的 mapper XML**连本地 MySQL 执行真 SQL，断言：`countGroupByStatus` 的聚合结果与"列表查询后内存分组"**逐项一致**（含 `userId` / `status` / `orderNo` 三种筛选与无筛选共 4 种口径，证明 `Filter_Where` 片段复用没有漂移）、`selectByOrderNos` 一次取回与逐单查询**逐字段一致**且不串单（消除 N+1 的回归防线）、售后概览的状态分组计数与**退款金额合计**与列表口径一致。用例沿用既有 `Assumptions.assumeTrue(库可达)` 约定，**无 MySQL 时自动跳过**，不影响无库环境的构建。
- **CI `e2e` job**：`services: mysql:8.0 + redis:7`，导入全量建库脚本 → **执行 SQL 层集成测试（真实 MySQL）** → 打包后端 → 启动 jar 并轮询 `/actuator/health` → 执行 `e2e_business.py`，失败时自动输出后端日志尾部。SQL 层用例在无库的 `backend` job 只会显示 skipped，故必须放到有 service 容器的 `e2e` job 里真实执行。
- **前端接口单测**：补 `orderStats` / 售后模块（`listAftersale` / `aftersaleStats` / `applyAftersale` / `auditAftersale`）的 url/method/params 断言。

### 修复
- **订单与售后列表无分页**：两处 `list` 接口补 `startPage()` 并改为 `getDataTable(list)` 分页信封（`{code,msg,rows,total}`）；前端订单页、售后页补 `<pagination>` 并改读 `res.rows` / `res.total`。
- **订单列表 N+1**：原实现先查订单再逐条 `selectByOrderNo` 取明细；改为 `selectByOrderNos` 一次批量取回整页订单项后按订单号分组装配，查询次数由 `1+N` 降为 `2`。
- **售后审核空指针与半成品状态风险**：`CampusAftersaleServiceImpl.audit` 取关联订单后未判空即 `setStatus`，订单缺失时会先退款/回库存再抛 NPE。现改为在退款动作**之前**显式抛 `ServiceException("关联订单不存在，无法审核")` 并整体回滚。
- **配置内置弱口令默认值**：`application-druid.yml` 的 `DB_USERNAME`/`DB_PASSWORD`/`DRUID_LOGIN_USERNAME`/`DRUID_LOGIN_PASSWORD` 与 `application.yml` 的 `JWT_SECRET` 原为 `${VAR:默认值}` 形式，生产一旦忘记覆盖即等于无防护。现全部移除默认值，**缺失即启动失败（fail-fast）**；本地/演示值改由 `2-启动后端.bat` 显式注入（生产部署走系统环境变量，见 `启动说明.md` 11.4）。
- **登录页预填演示凭证**：`login.vue` 原预填 `admin`/`admin123`，任何拿到发行包的人可直接登录。现改为空表单。
- `CampusAccountAtomicSqlTest` 的连接凭据改为读 `DB_USERNAME`/`DB_PASSWORD` 环境变量（保留本地演示回退值），便于在 CI 指向服务容器。
- **lockfile 不可移植导致 CI 前端 job 失败**：`package-lock.json` 中 **61 条 `resolved` 指向本机 npm 代理 `http://localhost:4777`**，另有 87 条指向 `registry.npmmirror.com`——混源且本机代理在任何其他机器（含 CI）都不存在。因这些多为 `optional` 的平台二进制依赖，npm 拉取失败时会**静默跳过**而不报错，于是 CI 上 `npm ci` 表面上"成功"，直到 vitest 启动才抛 `Cannot find module @rollup/rollup-linux-x64-gnu` 而崩溃（本机装的是 win32 版本，完全掩盖了该问题）。现将全部 229 条 `resolved` 归一化为 `https://registry.npmjs.org/`，**仅改 URL，版本与 `integrity` 字段一字未动**；并实测：222 个唯一 tarball URL 全部可达（HTTP 200），抽样 5 个包（含关键的 `@rollup/rollup-linux-x64-gnu`）下载后重算 SHA-512，与 lock 中 `integrity` **逐字节一致**。
- **logback 日志路径硬编码开发机绝对路径**：`logback.xml` 的 `log.path` 原为 `/home/ruoyi/logs`（上游若依自带）。Windows 下该路径会被映射到当前盘符根目录且恰好可写，问题被完全掩盖；到 Linux（CI runner、容器）该目录无写权限、logback 无法创建父目录，`LogbackLoggingSystem` 直接抛 `IllegalStateException` **中止应用启动**，`e2e` job 因而健康检查超时失败。现改为相对路径 `logs`（相对进程启动目录，`.gitignore` 已含 `logs/`），不新增任何环境变量。

### 变更
- **JaCoCo 覆盖率门禁上调**：行 **35% → 55%**、分支 **18% → 40%**（实测 行 **58.43%** / 分支 **43.79%**，下方留出余量）。补齐 service 层单测与 SQL 层集成测试后，后端用例由 **75 → 119**。
- **依赖漏洞门禁增加"可豁免但必须留据"机制**：新增 `campus-tao/sbom/osv-scanner.toml`——注意它必须与 `backend-bom.json` **同目录**，因为 osv-scanner 只读取"被扫描文件所在目录"下的配置且不向子目录传播，放到仓库根目录不会生效（首次修复即踩此坑）。登记经核查判定不适用于本项目的告警，每条必须写明理由与复核期限（`ignoreUntil`），**未登记的告警照常阻断构建**，避免"门禁一响就全局关闭"的偷懒做法。当前仅登记 1 条 `GHSA-vp22-232w-h9x8`（CVE-2022-4348，RuoYi-Cloud JSON Handler XSS）：本项目是 RuoYi-Vue 分支，`com.ruoyi:ruoyi-common:3.9.2` 是**本仓库自建的一手模块**，其坐标与上游若依同名构件重合，而 OSV 只按「组:构件:版本」匹配，无法区分"上游发布的制品"与"本地源码 fork"——即便修补代码，只要版本号不变该匹配就不会消失。登记理由中**如实写明核查范围仅止于"上游差异 + 坐标归属"，未逐行审计 Cloud 版注入点是否存在于本仓库**，并设 2027-06-30 复核期限。
- 前端覆盖率门禁维持棘轮基线不变（本轮仅新增接口封装用例，未改统计口径），实测 行 **26.2%** / 语句 **25.04%** / 分支 **23.61%** / 函数 **35.57%**。
- 后端 `info.app.version` 更新为 `1.0.13`；`2-启动后端.bat` 增补演示环境变量注入段与"生产务必覆盖"提示。

### 验证
- **后端**：离线 `mvn -B verify` **BUILD SUCCESS**；单测 **119/119** 全绿（0 失败 / 0 错误 / **0 跳过**，即 SQL 层用例真实执行而非跳过）；jacoco 门禁通过（行 **58.43%** ≥ 55%、分支 **43.79%** ≥ 40%）；`spotless:check` 通过（新增/修改文件 0 needs changes）。
- **SQL 层集成测试**：本地 MySQL（8.0.40 / `campus_tao`）在跑时用例**真跑非跳过**，9/9 全绿。**负向验证**：临时删掉 `CampusOrderMapper.xml` 中 `countGroupByStatus` 的 `group by status` 后，3 条相关用例立即失败（MySQL `only_full_group_by` 直接拒绝该 SQL），改回后恢复全绿——证明该测试确实在拦截 SQL 语义错误，而非摆设。
- **端到端**：注入环境变量重启后端（验证 fail-fast 配置下可正常启动，`/actuator/health` = 200）后执行 `e2e_business.py` → **RESULT: ALL_PASS**（双账号全链路、防超卖、越权 403、余额与库存回滚一致）。
- **分页与概览实测**（API 层）：`/campus/order/list?pageNum=1&pageSize=5` → `rows=5 / total=25` 且第 2 页与第 1 页首单不同；`/campus/order/stats` 返回 6 条状态分组；`/campus/aftersale/list?pageSize=2` → `rows=2 / total=5`；`/campus/aftersale/stats` 金额合计 **3169.00**（950 + 1650 + 569）。
- **前端实测**（浏览器，直连 8088）：订单页概览显示「订单总数=25 / 待付款=0 / 待收货=4 / 已完成=11」（与接口聚合逐项一致，证明概览已脱离"当前页"口径）、分页控件「共 25 条」且表格 10 行；售后页概览「售后单总数=5 / 待审核=1 / 涉及退款金额=¥3169.00」；登录页账号密码为空（预填已移除）。
- **前端门禁**：`format:check` 通过、eslint **0 error / 161 warn**（与基线一致）、单测 **48/48**、v8 覆盖率门禁通过、`build:prod` 通过。
- **CI**：`ci.yml` 结构校验通过（4 个 job：backend / e2e / frontend / security），YAML 可正常解析。
- **CI 首次真实执行（把"纸面门禁"变成"实证门禁"）**：本轮将本地库推送至 GitHub（`wms1125/Xiao-Yuan-TAO`）并触发真实运行，结果恰好印证了"配置齐全但从未执行"的风险——4 个 job 中**只有 `backend` 全绿**，其余 3 个各暴露一个此前完全看不见的真实缺陷，逐项修复后复跑全绿。首次运行结论：
  - `backend`：**成功**（可编译 + 119 单测 + jacoco + spotless 全通过）。
  - `e2e`：**失败** → 后端启动中止，`/actuator/health` 120s 超时；根因 `logback.xml` 硬编码绝对路径（见下方修复）。
  - `frontend`：**失败** → vitest 启动报 `Cannot find module @rollup/rollup-linux-x64-gnu`；根因 lockfile 不可移植（见下方修复）。
  - `security`：**失败** → osv-scanner 报 1 条告警；经核查属坐标碰撞，改为带理由登记（见下方变更）。
  - 另：`e2e` job 中新增的 SQL 层集成测试步骤在应用启动失败**之前**已执行，官方 `mysql:8.0` / `redis:7` service 镜像可用性亦在此首次被实证。

## [1.0.12] - 2026-10-16

后端代码规范基线：v1.0.11 已如实记录「后端 spotless/checkstyle 本轮未接入」。本次按与前端相同的**先基线格式化、后上门禁**顺序补齐后端这一半，并避开了一个坑——直接用 google-java-format 会改写全部 346 个 Java 文件、约 3.3 万行，并把项目既有的 **4 空格缩进压成 2 空格**，与 `.editorconfig` 及上游 RuoYi 风格冲突，diff 噪声远超收益。故只启用三项"零争议"的卫生类步骤，把门禁从"风格口味"收窄到"客观事实"。

### 新增
- **spotless-maven-plugin 2.44.3**（根 `pom.xml`，属性 `<spotless.version>`）：仅启用 `removeUnusedImports` / `trimTrailingWhitespace` / `endWithNewline` 三项卫生类步骤。
- **门禁绑定 verify 阶段**：`spotless:check` 绑定到 `verify`（与仓库既有 `jacoco:check` 的绑定方式一致），任何一次 `mvn verify`（含 CI 的 `mvn -B verify`）都会校验代码规范，不达标即构建失败。格式化（`apply`）刻意**不绑定生命周期**，由人工执行 `mvn spotless:apply` 触发，避免"边构建边改源码"掩盖问题。
- **离线仓库同步**：`.tools/m2-repo` 补入 spotless 全部构件（含 `spotless-lib`、`spotless-lib-extra`、`durian-*`、`google-java-format` 等）并清理 `_remote.repositories` / `*.lastUpdated` 标记文件，离线 `-o` 下 `mvn verify` 可完整执行（已实测）。

### 变更
- **后端 Java 基线格式化（249 个 Java 文件 / 997 增 1004 删）**：242 个文件为纯行尾空白改动，7 个文件删除未使用 import（`java.math.BigDecimal`、`java.util.List`、`org.springframework.web.bind.annotation.PathVariable`、`org.springframework.http.HttpStatus`、`org.apache.ibatis.annotations.Param`，全部位于自有 `campus` 模块）。该改动独立成一个提交，**纯机械**、可单独评审。
- **为何不用 google-java-format**：实测它改写 **346 个文件 / 29008 增 32945 删**，且把 `[*.java] indent_size=4` 的既有约定压成 2 空格；本方案实测只改 **250 个文件 / 1023 增 1004 删**，其中 pom 仅 26 行纯新增（注释与配置），其余为 Java 机械改动。
- 行尾沿用 spotless 默认 `PLATFORM_NATIVE`（Windows 检出为 CRLF，与仓库现状一致），与前端 Prettier 的 `endOfLine: auto` 同理——只改真实格式问题，不引入行尾噪音（抽查 60 个改动文件仍为 CRLF）。
- `.editorconfig` 注释更正：原文「本轮未接入 spotless/checkstyle，故这里只做缩进与编码声明，不构成后端门禁」已过期，改为说明后端门禁由 `mvn verify` 内的 `spotless:check` 承担。
- `verify.bat` 第 3 步更名为「后端单元测试 + 覆盖率 + 代码规范门禁」（步数仍为 6 步，不新增步骤）；CI 后端 job 的单条 `mvn -B verify` 说明同步更新，无需增删步骤即可获得该门禁。
- 后端 `info.app.version` 更新为 `1.0.12`。

### 验证
- **基线改动的语义等价校验**：对全部 249 个改动文件，把"全部空白字符与 import 行"剔除后与改动前版本比对 SHA-256，**249/249 全部一致**——证明改动不含任何标识符、逻辑、文案或接口契约变化。
- **门禁有效性（负向验证）**：在 `CampusCartController.java` 注入一个未使用 import 与一行行尾空白后执行 `mvn verify`，构建按预期失败（退出码 1），日志打印该文件的违规 diff 并提示 `Run 'mvn spotless:apply' to fix these violations.`；回滚探针后 `spotless:check` 恢复通过（345 个 Java 文件全部 clean）。**只做正向验证的门禁等于没有验证。**
- **交付前离线校验**：`verify.bat`（默认 `-o` 离线、内置 Maven 与离线仓库）**6 步 ALL PASS**、退出码 **0**；日志可见 `spotless:2.44.3:check (spotless-check)` 确实在 `mvn verify` 内执行并报告各模块 345 个 Java 文件 clean；后端单测 **75/75** 全绿、jacoco 门禁通过（行 **36.05%** ≥ 35%、分支 **19.09%** ≥ 18%，与 v1.0.11 完全一致，因本轮无业务代码变更）；前端侧未受影响：`format:check` 通过、eslint **0 error / 161 warn**、单测 **44/44**、v8 门禁通过（行 **25.33%** ≥ 25%）、`build:prod` 通过。
- **依赖面复核**：本轮未改动任何前后端运行期依赖——后端 SBOM（CycloneDX 1.6）仍为 **166 组件**（spotless 属构建期插件，不进入运行期依赖树，已核对 BOM 内无 diffplug 条目）；前端包总数仍 **329**、生产依赖树仍 **167**，`npm audit` 全量与 `--omit=dev` 均为 **0 项**。
- **后端服务**：重启后 `/actuator/health` = **UP**、`/actuator/info` 版本 = **1.0.12**。

## [1.0.11] - 2026-10-15

前端代码规范基线：项目此前**没有任何格式化或 lint 配置**，同一批源码里缩进、折行、空行风格并存；v1.0.10 已如实记录「直接接入 lint 会让上游数百处既有风格问题一次性变成 CI 红灯，属要么全量整改、要么形同虚设的两难」。本次按**先基线格式化、后上 ESLint**的顺序把这条路走通：先让代码整齐到"开规则不会满屏红"，再让规则真正成为门禁。

### 新增
- **Prettier 3.9.10 基线**：`campus-tao-ui/.prettierrc.json`（无分号、单引号、2 空格缩进、`printWidth 120`、`trailingComma none`、`endOfLine auto`）与 `.prettierignore`（排除依赖/产物/随包静态资源/锁文件与自动生成文件）。
- **ESLint 10.12.0 扁平配置**：`campus-tao-ui/eslint.config.js`——`@eslint/js` recommended + `eslint-plugin-vue` 的 `flat/essential` + `eslint-config-prettier`（关闭全部与 Prettier 冲突的排版类规则）。**不使用 `eslint-plugin-prettier`**：不把格式化塞进 lint 规则，避免"linter 说左、formatter 说右"的死循环。
- **自动导入标识符联通**：`vite/plugins/auto-import.js` 增 `eslintrc` 产出 `.eslintrc-auto-import.json`（`ref`/`computed`/`useDict` 等登记为全局变量），该文件随仓库提交，`eslint.config.js` 以 `createRequire` 读取。缺此步骤时 `no-undef` 会对每个使用自动导入的页面误报，规则开得再多也只是噪音。
- **四个 npm 脚本**：`lint` / `lint:fix` / `format` / `format:check`，供本地与 CI 复用。
- **CI 前端 job 增两步静态检查**：`format:check` 与 `lint` 置于单测之前，任一步失败即中止；`verify.bat` 由 5 步扩为 **6 步**（新增第 4 步静态检查）。

### 变更
- **前端源码全量基线重排**：按上述规则一次性格式化 `src/**`、`vite/**`、`tests/**` 与根配置文件。**该提交为纯机械重排，未改动任何标识符、逻辑与对外契约**，并作为独立提交单独可评审。
- `printWidth` 取 **120** 系实测最小扰动：80 / 100 / 120 分别需重排 157 / 152 / 151 个文件、改动 21373 / 17901 / 16324 行。
- 存量 lint 问题清零：`no-undef`、`no-unused-vars`、`vue/valid-define-emits`、`vue/require-valid-default-prop` 等 121 个 error 全部按"保持行为不变"修复（含 3 个真实缺陷，见下），**未使用任何 `eslint-disable` 豁免**。
- 脚手架既有债显式降级为 warn（限 `views/tool/**`、`components/RightToolbar/**`、`components/Crontab/**`、`system/user/profile/userInfo.vue`）：`vue/no-mutating-props` 与 `vue/no-side-effects-in-computed-properties` 命中的是上游脚手架写法，改成 emit/defineModel 属重构且有回归风险，故**降级但不静默**——每次 lint 都会打印明细，作为后续专项重构的待办清单。
- `vue/multi-word-component-names` 关闭：上游在 `main.js` 注册了单名全局组件（`Pagination`/`Editor`），全站模板按 `<pagination>`/`<editor>` 引用，改名仅满足命名约定而无正确性收益。
- 后端 `info.app.version` 更新为 `1.0.11`。

### 修复
- **`src/utils/ruoyi.js:124` 真实 ReferenceError**：`sprintf` 内部使用 `args[i++]`，但函数从未声明 `args`（既无局部变量也未绑定 `arguments`），任何调用该格式化函数的分支都会在运行时抛 `ReferenceError`。已补 `const args = arguments`，行为不变。
- **`src/views/monitor/operlog/detail.vue:177/185` 缺失导入**：解码失败分支调用 `ElMessage(...)` 但文件未导入，运行到该分支即报错。已补 `import { ElMessage } from 'element-plus'`。
- **`src/components/DictTag/index.vue` 过滤器语法失效**：`{{ unmatchArray | handleArray }}` 是 Vue 2 过滤器写法，Vue 3 已移除该语法（会被解析为位运算，标签根本渲染不出来）。改为 `{{ handleArray(unmatchArray) }}`；同时把 `unmatchArray` 由「ref + 在 computed 内 push」改为纯 computed 返回结果、`unmatch` 由其长度派生，副作用一并消除。该组件是几乎所有业务列表页的字典标签渲染入口，已单独做等价性验证。
- `src/utils/validate.js` 3 处正则内多余转义（`[\[\]\\]` 等）已清理，字符类语义不变。
- `src/utils/generator/html.js` 首行失效的 `/* eslint-disable max-len */` 指令已删除（Chromium 记录无对应问题）。

### 验证
- **静态检查**：`npx eslint .` → **`✖ 161 problems (0 errors, 161 warnings)`**，warning 全部落在上述豁免路径的那两条规则内（`vue/no-mutating-props` 131 条 + `vue/no-side-effects-in-computed-properties` 30 条）；`npm run format:check` → `All matched files use Prettier code style!`。
- **门禁有效性（负向验证）**：故意写入一个未使用变量后 `npm run lint` 报错并返回非 0 退出码；故意写入一处不合风格的间距后 `npm run format:check` 报该文件不符并返回非 0。验证后均已删除探针文件。**只做正向验证的门禁等于没有验证。**
- **行为不变性**：基线重排后复跑前端单测 **44/44 通过**、`vite build` 通过；随后完成 121 个 error 的修复，再次复跑单测 **44/44 通过**、生产构建通过（`✓ built in ...`）。
- **交付前离线校验**：`verify.bat`（默认 `-o` 离线、内置 Maven 与离线仓库）**6 步 ALL PASS**，退出码 **0**；后端单测 **75/75** 全绿且 jacoco 覆盖率门禁（行 ≥35% / 分支 ≥18%）通过，前端单测 **44/44** 全绿且 v8 覆盖率门禁（行 ≥25%）通过。
- **依赖面复核**：新增的 lint/format 工具链**未引入任何漏洞**——`npm audit` 全量与 `npm audit --omit=dev` 均 **0 项**；前端包总数 **252 → 329**（+77，全部为 devDependency），**生产依赖树仍为 167 个包**，UNKNOWN 许可 0、copyleft 0；`frontend-dependencies.csv` 已重新生成（+77 行、移除 0 行，纯新增）。
- **后端服务**：重启后 `/actuator/health` = **UP**、`/actuator/info` 版本 = **1.0.11**。

> 未纳入本轮（如实记录）：**后端的 spotless/checkstyle**（P1 原文包含此项）——后端本轮仅做了前端基线，后端风格统一与静态检查尚未落地；**`vue3-recommended`/`strongly-recommended` 的更严规则集**（当前为 `essential`，收紧路径是把上面 161 条 warn 逐类重构后逐级上调）；**CI 真正跑起来**（仓库仍为本地库、无远程，工作流从未被执行，需用户提供远程仓库）；**Flyway/Liquibase 数据库版本化迁移**（待确认）。

## [1.0.10] - 2026-10-15

工程流水线加固：此前的测试只有"跑得通/全绿"这一个结论，**覆盖到什么程度无从度量**，流水线也没有任何会拦人的门禁；同时前端生产接口前缀为 `/prod-api` 却**没有任何反向代理配置**，真按生产方式部署会直接 404。本次让流水线"会拦人"、让测试有数字、让部署有据可依。

### 新增
- **后端覆盖率门禁（jacoco 0.8.12）**：根 `pom.xml` 增 `<jacoco.version>`，`ruoyi-system/pom.xml` 加 `jacoco-maven-plugin`——`prepare-agent` 插桩、`report` 绑定 `test`、`check` 绑定 `verify`。统计口径限定 `com/ruoyi/campus/**`，**排除 `domain/**`（纯 POJO）与 `mapper/**`（MyBatis 接口代理）**，阈值 **行 ≥35% / 分支 ≥18%**。
- **前端覆盖率门禁（`@vitest/coverage-v8@4.1.11`）**：`vitest.config.js` 增 v8 provider 与阈值，`package.json` 增 `test:coverage` 脚本。口径为 `src/api/campus/**` + `src/utils/**`（排除代码生成器运行期工具），阈值 **行 ≥25% / 语句 ≥24% / 分支 ≥23% / 函数 ≥32%**。
- **Controller 层与异常兜底测试 52 个新用例**（4 个测试类）：把 Controller 层与 DTO 层的覆盖率从 **0 拉到 100%**，重点覆盖"身份与归属一律取自登录态、前端伪造的 `sellerId`/`handler` 被丢弃、越权与订单不存在同样被拒、物流通道未配置时降级提示"等判定分支。后端单测总数 **23 → 75**。
- **生产部署配置 `deploy/nginx.conf`**：`/prod-api/` 反代到后端（`proxy_pass` 末尾 `/` 剥离前缀）、`/profile/` 上传目录、dist 静态托管 + SPA 兜底、`gzip_static` 复用构建期 `.gz`、HTTPS 与安全响应头、`/actuator/health` 与接口文档限内网。配套步骤写入 `campus-tao/启动说明.md` 第十一章（构建制品、目录约定、密钥环境变量、上线前改回验证码、上线冒烟命令）。
- **CI 依赖漏洞扫描 job**：前端 `npm audit --omit=dev`（官方 registry，要求 0 告警）+ 后端 `osv-scanner` 扫 `sbom/backend-bom.json`。

### 变更
- **`verify.bat` 对齐门禁**：第 3 步 `mvn -B test` → `mvn -B verify`（覆盖率不达标即失败），第 4 步 `npm run test:unit` → `npm run test:coverage`；步骤标签与说明同步更新（脚本已归一化为 CRLF）。
- **`.github/workflows/ci.yml` 补齐**：后端由"`clean package -DskipTests` + `test`"两步合并为单条 `mvn -B verify`；前端切 `test:coverage`；两侧均上传覆盖率报告产物（`if: always()`）；新增「依赖漏洞扫描」job（共 3 个 job）。
- **离线仓库同步**：`org/jacoco` 补入 `.tools/m2-repo`，并清理全仓 `_remote.repositories` / `*.lastUpdated` 标记文件共 6 个，保证 `-o` 离线模式下 `mvn verify` 可完整执行。
- `.gitignore` 增 `coverage/`、`jacoco.exec`。
- 后端 `info.app.version` 更新为 `1.0.10`。

### 修复
- **生产部署路径缺失**：此前交付包只有前端 `/prod-api` 前缀的代码事实，没有任何反代配置或部署说明，按生产方式直接部署会 404。已补齐配置文件与部署步骤（含"上线前必须把验证码改回 `true`"的安全提醒）。

### 验证
- **覆盖率（后端）**：原口径（含 POJO/mapper）仅行 **8.57%**、分支 **4.53%**——指标被无逻辑价值的字节码稀释而失真；改为限定业务逻辑类后实测 **行 36.05% / 分支 19.09%**，其中 Controller 层与 DTO 层均 **100%**。
- **覆盖率（前端）**：实测 **语句 24.25% / 分支 23.61% / 函数 32.65% / 行 25.24%**，44/44 全绿。
- **门禁有效性（负向验证）**：阈值临时抬至 99% 时，后端报 `Rule violated for bundle ruoyi-system: lines covered ratio is 0.36, but expected minimum is 0.99` 并 **BUILD FAILURE**，前端同样失败；验证后恢复真实阈值。**只做正向验证的门禁等于没有验证。**
- **后端单元测试**：`Tests run: 75, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS；新增用例复用 `spring-boot-starter-test` 自带 Mockito，**未新增任何第三方依赖**（后端 SBOM 组件数不变）。
- **交付前离线校验**：`verify.bat`（默认 `-o` 离线、内置 Maven 与离线仓库）5 步 **ALL PASS**，退出码 **0**。
- **依赖面复核**：`npm audit` 全量与 `--omit=dev` 均 **0 项**；前端包总数 **243 → 252**（新增 9 个、移除 0 个，全部为覆盖率链路 devDependency），生产依赖树仍 **167** 个包，UNKNOWN 许可 0、copyleft 0；`frontend-dependencies.csv` 已用 `tools/sbom-frontend.py` 重新生成并做差集比对。
- **后端服务**：重启后 `/actuator/health` = **UP**、`/actuator/info` 版本 = **1.0.10**。

> 阈值说明（如实记录）：阈值为**棘轮基线**——以实测值向下取整设定，**只允许上调、不允许下调**，并非"企业级应有水平"。未直接设 60% 的原因是当前缺口集中在 `service/**` 与 `notify/**`（依赖 Spring 容器与数据库，属集成测试范畴，需 Testcontainers/H2 方案），强行设高只会逼出"为凑覆盖率而写无断言测试"的劣化。
>
> 未纳入本轮（如实记录）：**lint 阶段**（项目此前无任何 lint 配置，直接接入会让 RuoYi 上游数百处既有风格问题一次性变成 CI 红灯，属"要么全量整改、要么形同虚设"的两难，已列为 P1，需先做基线格式化）；**CI 真正跑起来**（仓库当前为本地库、无远程，工作流从未被执行，需用户提供远程仓库后确认）；**Flyway/Liquibase**（会改变现有初始化策略，对已交付基线库有回归风险，待确认）。

## [1.0.9] - 2026-10-15

前端构建链路告警清零：v1.0.6 曾把前端 16 项告警压到 5 项 high，剩余 5 项全部来自 `vite-plugin-svg-icons` 的 SVG 图标 sprite 链路（`→ svg-baker → micromatch → braces`），根因 `braces` 上游无补丁版本，当时判定为"可接受的残余风险"。本次不再接受，改为**移除该依赖链**，前端 `npm audit` 全量归零。

### 变更
- **SVG 图标插件自研替换**：`campus-tao-ui/vite/plugins/svg-icon.js` 重写为**零第三方依赖**的 sprite 插件（仅用 Node `fs`/`path`），
  继续提供 `virtual:svg-icons-register`，仍把 `src/assets/icons/svg` 下全部 SVG 拼为 `<symbol id="icon-{name}">` 注入 body 的隐藏容器，
  `SvgIcon` 组件与全部页面用的 `#icon-{iconClass}` 契约**零改动**（103 处用法、22 个文件不受影响）；另附 `configureServer` 监听 SVG 增删改触发整页刷新，保留原插件的开发体验。
- **依赖移除**：`package.json` 删除 `vite-plugin-svg-icons` 2.0.1 与其幽灵依赖 `fast-glob` 3.3.3，
  并删除随之失效的 `overrides`（`micromatch` 4.0.8、`braces` 3.0.3、`fast-glob` 3.3.3），仅保留 `quill` / `postcss` 两条。
- 后端 `info.app.version` 更新为 `1.0.9`。

### 修复
- **构建期 high 告警清零**：`npm audit` 由 5 项 high（`braces` / `micromatch` / `svg-baker` / `vite-plugin-svg-icons` / `fast-glob`）降为 **0** 项；`npm audit --omit=dev` 生产依赖树保持 **0** 项。实测 `npm ls braces micromatch fast-glob svg-baker vite-plugin-svg-icons` 为空——整条链路从依赖树中消失。
- 之前 npm 给出的"修复"是把 `vite-plugin-svg-icons` 降级到 0.1.0（假修复，会破坏图标管线），本次以替换实现的方式规避，未做任何降级。

### 验证
- **漏洞扫描**（`npm audit`，官方 registry）：全量 **0** 项（0 告警）；`--omit=dev` **0** 项。作为对照，处置前同一命令为 5 项 high。
- **图标渲染实测**（Playwright，Chromium，headless）：登录页 sprite 容器 `__svg__icons__dom__` 存在且含 **90** 个 `<symbol>`；`#icon-user` 实际渲染 **15×15 px**、`use.getBBox()` 为 **15×15**（确证 symbol 内容被正确引用而非空引用）；登录 `admin/admin123` 后共 **39** 个 `svg-icon` 实例，其中侧边栏可见图标 `#icon-dashboard` / `#icon-system` 渲染 **14×14 px** 且 `getBBox` 非零；**console error 0 条、失败请求 0 条**；截图核对侧边栏与顶栏图标均正常显示。
- **交付前校验**：`verify.bat` 离线 5 步 **ALL PASS**（后端 `clean package` BUILD SUCCESS、后端单测 **23/23**、前端单测 **44/44**、前端 `build:prod` 通过），退出码 0；重启后端后 `/actuator/health` = **UP**、`/actuator/info` 版本 = **1.0.9**。
- **SBOM 同步**：前端包总数 **398 → 243**（-155，即移除的整条链路及其传递依赖），许可分布仍为全宽松、**0** 未知许可、**0** copyleft；`frontend-dependencies.csv` 已重新生成。
- **产物体积如实记录**：自研插件未内置 `svgo` 压缩，90 个图标的 sprite 实测 **106.9 KB**（gzip **39.2 KB**），
  较原链路（svgo 压缩后 **86.5 KB** / gzip 32.5 KB）增约 **20 KB**、gzip 后增约 **7 KB**。取舍是"以可接受的体积增量换取依赖面收敛与告警清零"。

## [1.0.8] - 2026-10-15

演示数据重做：原交付库里的演示数据随机感过强（`测试二手显示器886373`、`限量1件测试`、评价一律`东西很好，好评`、收货人一律`张三`、购物车数量一律 2），与真实校园二手交易场景差距明显，直接影响验收观感。本次以脚本化方式重建整套演示数据，并随包交付品类实拍图作为商品封面。

### 新增
- `campus-tao/tools/gen-seed-data.py`：**演示数据生成器**。按真实校园场景集中定义分类、学生账号、商品、订单、评价、售后、购物车、站内通知、物流轨迹与状态链，继而直接改写 `sql/campus_tao_full.sql` 对应数据块（可重复执行、结果确定），并在结束时打印一致性自检结果。
- `campus-tao-ui/public/campus-img/`：**11 张品类实拍图**（教材 / 考研 / 数码 / 电器 / 运动 / 代步 / 乐器 / 生活 / 美妆 / 票券），作为商品封面随前端交付。`public/` 下的文件由 Vite 以站点根路径直达，故 `campus_product.images` 直接存 `/campus-img/<品类>.jpg`，无需拼接 base API。

### 变更
- **商品 49 件 / 10 个分类**：标题、描述、成色、价格均取真实二手行情与真实话术（如`《操作系统概念》第9版（恐龙书）`、`Kindle Paperwhite 4 8G 含原装皮套`、`宿舍小电锅 1.2L（可煮面/粥）`），描述带真实瑕疵说明（如`书角有轻微卷边，内页无笔记`）；图片统一改用本地品类实拍图。**上架时间在 2026-08-18 ~ 2026-09-05 间确定性打散**（须早于最早订单 2026-09-08），避免列表按时间倒序时扎堆在某个分类。**售罄商品自动置下架**（19 件），不再出现「上架却买不到」。
- **订单 24 笔 / 七种状态齐全**：待付款、已付款、已发货、已完成、已取消、退款中、已退款，含完整的「下单 → 支付 → 发货 → 收货」时间链与买家备注（如`书有几页笔记，介意勿拍`）；收货人按买家真实档案落库（姓名 / 手机 / 校内宿舍地址，如`东区6号宿舍楼302室（可放菜鸟驿站）`），不再一律`张三`。订单总额由商品单价 × 数量推导，与订单项小计之和严格一致。
- **待付款订单的创建时间取「导入时刻」**：表达式为 `DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR)`，订单号同步由 `CONCAT('CT', DATE_FORMAT(...), '1')` 生成。之所以不用 `NOW()`：`campus_tao_full.sql` 由 mysqldump 导出、脚本头部会执行 `SET TIME_ZONE='+00:00'`，脚本内的 `NOW()` 拿到的是 UTC 时刻。如此处理可使该订单**导入后立即落在 30 分钟支付超时窗口内**，超时自动取消定时任务不会在服务启动时把它当成陈旧数据静默改写（此前静态写入的待付款订单会被定时任务直接取消，数据集不可复现）。**30 分钟后它会按业务规则自然转为「已取消」并回补库存**——这正是超时取消能力的真实体现；若需长期停留在「待付款」态，当场下一单即可。
- **学生卖家账号 6 名**：`liyutong` / `wanghao` / `zhaomin` / `chenxinyi` / `zhoukai` / `liuzhiyuan`（统一密码 `admin123`，角色 `2` 普通角色），作为 C2C 卖家承载商品货源，使「学生发布 → 学生购买」的校园二手形态真实可演。
- **其余数据**：评价 20 条（文案呼应具体商品，如`书基本全新，笔记做得很整齐`）、售后 4 单（覆盖待处理 / 已通过 / 已驳回）、物流轨迹 16 条、站内通知 14 条（含`订单已自动取消`与`订单待支付提醒`）、外呼留痕 8 条（交付默认未注入网关，`结果=2 未启用已降级`）、购物车 12 条、沙箱账户 8 个。
- 后端 `info.app.version` 更新为 `1.0.8`。

### 修复
- **购物车不合理数据**：原数据集存在「用户加入自己的商品」「加入售罄（下架）商品」「数量超过剩余库存」三类问题，现已调整，并在生成器中加入自检，落库前拦截越界数据。
- **「在线 AI 生成图」方案不可用**（方案降级的依据，留痕备查）：实测平台文生图接口 `https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image` 对任意 `prompt` / 任意 `image_size` 恒返回同一张 `176626` 字节图片（MD5 恒为 `19A0B822EDB11957055E4588C2159058`），内容为「The image is generating… Please refresh page to preview.」灰色占位图 —— 若沿用该方案，49 件商品将全部显示同一张占位图，观感劣于无图。故改为**随包交付本地品类实拍图**。

### 验证
- **生成器自检**：分类 10 / 商品 49 / 订单 24 / 订单项 33 / 评价 20 / 售后 4 / 轨迹 16 / 外呼留痕 8 / 站内通知 14，订单「买家 = 卖家」数 0、订单总额与订单项小计之和偏差 0、商品上架时间早于最早订单、售罄商品上架数 0 —— 全部通过。
- **库内复核**（重新导入 `campus_tao` 后 SQL 抽查）：13 张表计数与生成器一致；`total_amount` 与 `Σsubtotal` 不一致 0 笔；订单表「买家 = 卖家」0 笔；购物车自有商品 / 已下架商品 / 超库存 各 0 条；`stock=0 且 status=1` 0 件；49 件商品 `images` 全部为 `/campus-img/` 前缀；待付款订单 `create_time` 距当前 0 分钟（仍在支付窗口内）。
- **接口抽查**：商品列表分页 49 条、分类筛选与关键词搜索正常、图片字段为本地路径；订单接口 24 笔且七种状态齐全；学生账号 `zhoukai` 站内通知 3 条未读（含`订单待支付提醒`）；售后列表 4 单；学生账号发布商品 200 且卖家被服务端强制改写为本人。
- **前端实测**（浏览器）：`/campus/product` 第 1 页 10 行缩略图**全部渲染为品类实拍图**、无`无图`占位；`/campus/order` 24 行、七种状态标签齐全；静态资源 `/campus-img/books.jpg` 返回 `200 image/jpeg`（232 KB）。
- **端到端回归**：`e2e_business.py` 全链路 `RESULT: ALL_PASS`（种子数据与 e2e 双账号流程无冲突：列表按发布时间倒序、购物车按显式 `cartIds` 结算、C2C 用例以时间戳标题区分）；回归后重新导入全量脚本以还原交付基线与自增 ID。
- **交付前校验**：`verify.bat` 离线 5 步 `ALL PASS`（后端 `clean package` BUILD SUCCESS、后端单测 **23/23**、前端单测 **44/44**、前端 `build:prod` 通过），退出码 0。

## [1.0.7] - 2026-10-15

运营通道对接：接入短信/邮件外呼通道与第三方物流轨迹查询，补齐 v1.0.5 遗留的两项运营侧缺口。通道以接口抽象、换网关只改环境变量；**未配置时自动降级且全程留痕**，交付默认态即安全降级态，任何情况下不阻断下单、发货等主流程。

### 新增
- **外呼通道抽象**：`CampusNotifyChannel` 接口（`code`/`name`/`enabled`/`targetOf`/`targetAvailable`/`send`）与 `CampusNotifyDispatcher`（异步分发 + 留痕）。短信通道取 `sys_user.phonenumber`、邮件通道取 `sys_user.email`；网关地址由 `CAMPUS_SMS_ENDPOINT` / `CAMPUS_MAIL_ENDPOINT` 注入，未注入即视为「未启用」并降级为仅站内通知。
- **外呼留痕**：新增 `campus_notify_log` 表，逐通道记录 `channel`/`target`/`status`（0失败 / 1成功 / 2未启用已降级）/`response`/`error_msg`/`cost_ms`。运营侧新增「校园淘 → 通知外呼日志」菜单（`campus:notifylog:list`，仅管理员），支持按渠道、结果、业务号筛选并统计成功/失败/降级数。
- **外呼和接入点**：`CampusNoticeServiceImpl.push()` 在站内通知落库后追加一次外呼分发，故**下单、发货、售后审核结果、超时自动取消**四类既有事件自动获得短信/邮件通知能力，业务代码零改动。
- **物流轨迹对接**：`ILogisticsProvider` 抽象 + `HttpLogisticsProvider`（通用 HTTP 表单网关，网关地址由 `CAMPUS_LOGISTICS_ENDPOINT` 注入，承运商可用 `CAMPUS_LOGISTICS_CARRIER` 指定，默认 `SF`）。新增 `campus_logistics_trace` 表（唯一键 `(order_no, logistics_no, status_label, context)` 使重复同步幂等），订单详情新增物流轨迹时间线。
- **物流轨迹接口**：`GET /campus/logistics/status`（通道是否启用）、`GET /campus/logistics/trace/{orderNo}`（轨迹查询）、`POST /campus/logistics/sync/{orderNo}`（手工同步，需 `campus:order:edit`）。查询接口**强制登录并校验订单归属**（本人或管理员），与订单详情同口径。
- **物流轨迹定时同步**：Quartz 任务 `campusLogisticsTask.syncShippedTrace`（`sys_job` job_id=101，每 30 分钟一次），为已发货且有物流单号的订单拉取轨迹；通道未配置时任务自动空转，单笔失败不影响其余订单。
- 共用 HTTP 能力 `campus/support/HttpFormClient`（POST 表单、连接 3s / 读取 5s 超时、`success` 与 `code/ret/status` 双约定判成功、响应截断），**基于 JDK `HttpURLConnection`，不新增任何第三方依赖**（SBOM 组件数保持 166）。
- `sql/migration-v1.0.7.sql`：面向**已建库**的增量迁移（建 2 张表、注册 job 101、新增菜单 2007）；交付包全量脚本 `sql/campus_tao_full.sql` 已同步为唯一建库源。

### 变更
- `campus-tao/RuoYi-Vue-master/ruoyi-system/pom.xml`：`fastjson2` 由 `ruoyi-common` 的**传递依赖**改为**显式声明**（v1.0.6 已出现过同类幽灵依赖消失导致构建中断的问题），组件坐标与版本不变。
- 后端 `info.app.version` 更新为 `1.0.7`。

### 验证
- **离线重建与单测**：`verify.bat` 离线 5 步 `ALL PASS`（后端 `clean package` BUILD SUCCESS、后端单测 **23/23**、前端单测 **44/44**、前端 `build:prod` 通过），退出码 0。
- **外呼真实发出（本地 mock 网关闭环，非交付物）**：以环境变量注入本地 mock 网关后跑 e2e，网关侧 `requests.log` 实测收到 **6 次真实 HTTP 调用**（下单 / 发货 / 售后审核 × 短信 / 邮件），留痕 `status=1`、耗时 1~15ms；报文经核对，短信为 `【校园淘】订单已发货 订单 CT… 已发货，物流单号：SF1234567890…`，邮件 `to=ry@qq.com`、`subject=订单已发货`。
- **降级态验证（交付默认态）**：不注入任何网关环境变量时，`/campus/logistics/status` 返回 `false`；e2e 断言每条业务消息均落 `status=2`（未启用降级）留痕，短信与邮件通道各一条链路，主流程全部正常通过，`RESULT: ALL_PASS`。
- **物流轨迹专项**：手工同步 #1 新增 **2** 条节点（已揽收 09:10:00 / 运输中 13:20:00），#2 新增 **0** 条（唯一键幂等）；直接触发 `sys_job` 101 返回「操作成功」且节点数保持 2，无重复写入。
- **越权与权限专项**（e2e 新增断言）：未登录访问物流轨迹 → **401**；ry 查询非本人订单轨迹 → 被拒（「无权查看该订单物流轨迹」）；ry 访问 `/campus/notifylog/list` → **403**（未授予 `campus:notifylog:list`），admin 可见留痕。
- **前端 UI 实测**（Playwright，Chromium）：admin 登录 → 订单详情弹窗渲染「物流轨迹」区块与两条节点（含时间戳）；「通知外呼日志」页渲染 6 行（首行 `短信 ｜ 15666666666 ｜ 售后审核通过 ｜ 成功 ｜ 4 ms`）；全程 `pageerror` 与 console error 均为空。
- **交付基线复核**：清空 8 张业务表（含新增的 `campus_notify_log`、`campus_logistics_trace`）并重置自增，仅保留 `campus_category` 1 条主数据；重启后 `/actuator/health` = **UP**、物流通道 = **false**、外呼留痕 0 条。
- **产物体检**：发行 `ruoyi-admin.jar` 内 `BOOT-INF/lib` 实测 **149** 个 jar，与 SBOM 的 166 个组件逐一对照，唯一未列入 BOM 的是打包插件注入的 `spring-boot-jarmode-tools-4.1.1.jar`（非业务依赖），**无依赖缺失**。

## [1.0.6] - 2026-10-15

依赖漏洞（CVE）专项治理：后端整体升级并清零严重项、前端生产依赖树归零，同时补齐 SBOM 的漏洞扫描口径。

### 新增
- `campus-tao/sbom/SBOM.md` 新增「依赖漏洞（CVE）扫描结果」章节：前后端治理前后对比、残余风险判定依据、可复现的复核命令（`osv-scanner` / `npm audit`）。

### 变更
- **后端依赖整体升级**：`spring-boot.version` 4.1.0 → **4.1.1**，连带 Spring Framework 7.0.8→7.0.9、Netty 4.2.15→4.2.17、Log4j 2.25.4→2.25.5。
- **后端补丁版本显式声明**（置于根 `pom.xml` 的 `dependencyManagement` 中、**必须先于 Spring Boot BOM**）：Tomcat 11.0.22→**11.0.27**、Jackson 2 2.21.4→**2.21.7**、Jackson 3（`tools.jackson`）3.1.4→**3.1.7**。
- **前端运行时依赖升级**：`axios` 1.13.2→**1.20.0**、`vue` 3.5.26→**3.5.44**、`js-cookie` 3.0.5→**3.0.8**、`echarts` 5.6.0→**6.1.0**。
- **前端构建工具链升级**：`vite` 6.4.1→**6.4.4**、`unplugin-auto-import` 0.18.6→**21.1.0**；并新增 `overrides` 收敛传递依赖（`postcss` 8.5.30、`micromatch` 4.0.8、`braces` 3.0.3、`fast-glob` 3.3.3）。
- `fast-glob` 由隐式提升改为**显式声明**为 devDependency（`vite-plugin-svg-icons` 自身 `package.json` 未声明该依赖，属幽灵依赖）。
- `verify.bat` 前置校验步骤已在 v1.0.5 修正为在 `campus-tao-ui` 目录执行前端单测，本次治理后 5 步全绿。

### 修复
- **构建中断（幽灵依赖消失）**：`unplugin-auto-import` 升级后其依赖树精简，原本靠它提升安装的 `fast-glob` 不再存在，`vite build` 报 `Cannot find package 'fast-glob'`（由 `vite-plugin-svg-icons` 运行时 import 触发）。已显式补齐依赖后恢复正常构建。

### 验证
- **后端漏洞扫描**（`osv-scanner` v2.7.0，基于 CycloneDX BOM）：命中组件由 **10** 个 / **30** 条（含 4 项 CRITICAL）降至 **1** 个 / **1** 条（MODERATE）。残余项为 `com.ruoyi:ruoyi-common@3.9.2` 命中 `GHSA-vp22-232w-h9x8`（CVE-2022-4348，RuoYi-**Cloud** 的 JSON Handler XSS）——本项目为 RuoYi-Vue，经核查其 `ruoyi-common` 模块**不含任何 Web 端点**，且该通告无 `fixed` 版本，判定为**同名坐标碰撞误报**。
- **前端漏洞扫描**（`npm audit`）：告警由 **16** 项（11 high / 5 moderate）降至 **5** 项（全 high，均为构建期 devDependencies）；**`npm audit --omit=dev` 生产依赖树为 0 项**。
- **浏览器冒烟**（Playwright，Chromium，11 项检查全通过）：`echarts` 5→6 在 `/monitor/cache` 正常渲染图表 canvas；`unplugin-auto-import` 0.18→21 未静默失效（用户管理/商品/订单页表格与下拉正常，无白屏）；`axios` 1.20 拦截器与 `js-cookie` 3.0.8 令牌写入正常（登录成功进入主框架、Cookie 内含令牌）；顶部「消息通知」下拉正常；全程 `pageerror` 为空。
- **校验与回归**：`verify.bat` 离线 5 步 `ALL PASS`（后端 `clean package` BUILD SUCCESS、后端单测 **15/15**、前端单测 **41/41**、前端 `build:prod` 通过）；`e2e_business.py` 全链路 `RESULT: ALL_PASS`。
- **产物体检**：发行 `ruoyi-admin.jar` 的 `BOOT-INF/lib` 实测为 `tomcat-embed-core-11.0.27`、`spring-webmvc-7.0.9`、`jackson-databind-2.21.7`（2.x）与 `3.1.7`（3.x）、`netty-handler-4.2.17.Final`、`log4j-api-2.25.5`，依赖 jar 总数 149 → **150**。
- **SBOM 同步**：后端 CycloneDX **166** 组件（160 项带许可，无 AGPL/SSPL）；前端 **398** 包（原 461，因 `unplugin-auto-import` 升级后依赖树精简），**0** 未知许可、**0** copyleft。
- **离线仓库维护**：联网补入新版本依赖后，清理 `.tools/m2-repo` 中的 `_remote.repositories` / `*.lastUpdated`（271 / 5 个），保证 `-o` 离线构建仍可复现。

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
- **`verify.bat` 前端单测在错误目录执行**：第 4 步 `npm run test:unit` 原本在交付根目录运行，而根目录没有 `package.json`，导致该步必然失败（`ENOENT: Could not read package.json`）。已把前端单测与构建统一并入 `campus-tao-ui` 目录执行，交付前校验 5 步方能真实全绿。
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
