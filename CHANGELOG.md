# 更新日志

本文件记录校园淘（campus-tao）交付包的版本变更。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

开发过程留痕见 `campus-tao/ai-log/001~016`，里程碑文档见 `campus-tao/M1~M5`。

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
