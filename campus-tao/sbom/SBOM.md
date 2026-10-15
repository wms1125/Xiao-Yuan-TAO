# 软件物料清单（SBOM）——校园淘

本目录提供校园淘交付版本的依赖物料清单，用于回答"这个交付包里用了哪些第三方组件、各自的许可是什么"。

| 文件 | 内容 | 格式 |
|---|---|---|
| `backend-bom.json` | 后端全部第三方组件（含版本、分组、许可） | CycloneDX 1.6（JSON） |
| `frontend-dependencies.csv` | 前端 `node_modules` 中实际安装的全部包（含版本、许可、是否嵌套重复） | CSV（UTF-8 BOM，可用 Excel 直接打开） |
| `SBOM.md` | 本说明：口径、结论、风险提示与复核命令 | Markdown |

## 一、生成口径与工具

- **后端**：Maven 3.9.9 + JDK 17，CycloneDX Maven 插件（在 `campus-tao/RuoYi-Vue-master` 执行）。
  范围为**编译/运行期依赖**（不含 test 作用域，故 BOM 中无 JUnit 等测试组件）。
- **前端**：直接扫描 `campus-tao-ui/node_modules` 下每个包的 `package.json`，读取 `name`/`version`/`license`。
  之所以不依赖 lock 文件，是因为 `package-lock.json` 不携带许可字段；直接读已安装包的元数据更准确。含嵌套安装的重复版本，`nested_duplicate` 列标记。

## 二、后端（CycloneDX）

- 组件总数：**166**，其中 **160** 项带许可信息。
- **覆盖度校验**：与发行 fat jar `ruoyi-admin.jar` 内 `BOOT-INF/lib` 的 **150** 个依赖 jar 逐一对照，未发现 BOM 缺失。
- 许可分布：

| 许可 | 组件数 |
|---|---|
| Apache-2.0 | 132 |
| MIT | 11 |
| EPL-2.0 | 5 |
| BSD-3-Clause | 3 |
| LGPL-2.1-only / LGPL-2.1-or-later | 1 / 2 |
| CDDL-1.1 | 1 |
| (CDDL-1.0 OR GPL-2.0-with-classpath-exception) | 1 |
| MIT-0 | 1 |
| GPL-2.0 with Universal FOSS Exception | 1 |
| CC0-1.0 | 1 |
| Public Domain | 1 |
| **未声明** | 6 |

> v1.0.5 引入可观测性所需的 `spring-boot-starter-actuator` 及其传递依赖（`spring-boot-actuator`、`spring-boot-actuator-autoconfigure`、`micrometer-*` 等，均为 Apache-2.0），组件数 156 → 166。
> v1.0.6 因 CVE 治理整体升级 Spring Boot 4.1.0 → 4.1.1 及 Tomcat/Jackson 补丁版本，组件集合与许可结构基本不变（同上表，为上表口径重新生成的结果）。

- **需关注项（均为可商用，但按规范应披露）**：
  - `GPL-2.0 with Universal FOSS Exception` / `(CDDL-1.0 OR GPL-2.0-with-classpath-exception)`：MySQL Connector/J 等。类路径例外意味着以 jar 形式链接使用不触发传染，**可随闭源项目分发**。
  - `LGPL-2.1`（3 项）：动态链接（jar 引用）方式合规，修改其源码才涉及回馈义务。
  - `CDDL-1.1`（1 项）：文件级 copyleft，作为未修改的二进制依赖使用合规。
  - 无 AGPL、无 SSPL 等强传染许可——**不存在阻断分发的组件**。
- **未声明许可的 6 项为本项目自研模块**（`com.ruoyi:ruoyi-common/system/framework/quartz/generator/admin@3.9.2`）——上游 RuoYi 的 pom 未声明 `<licenses>`。建议由项目所有者决定许可后在根 `pom.xml` 补充（**未擅自代填**）。

## 三、前端

- 包总数：**398**（含嵌套重复版本）。
- 许可分布：

| 许可 | 包数 |
|---|---|
| MIT | 345 |
| ISC | 21 |
| BSD-2-Clause | 12 |
| BSD-3-Clause | 7 |
| Apache-2.0 | 6 |
| BlueOak-1.0.0 | 5 |
| CC0-1.0 | 1 |
| 0BSD | 1 |
| **UNKNOWN** | **0** |
| **copyleft（GPL/AGPL/LGPL/MPL 等）** | **0** |

- 结论：**全部为宽松许可，无未知许可、无 copyleft 组件**，前端依赖不构成许可风险。
- v1.0.6 重新生成：升级 `vue/unplugin-auto-import/vite/js-cookie/axios/echarts` 并加入 `overrides` 后，
  包总数由 461 降至 398（`unplugin-auto-import` 0.18 → 21 的依赖树大幅精简），许可结构不变。

## 四、依赖漏洞（CVE）扫描结果（v1.0.6）

扫描工具：`npm audit`（前端，基于 npm registry 通告）与 `osv-scanner` v2.7.0 `scan source -S backend-bom.cdx.json`（后端，基于 OSV/GHSA 数据库，需联网）。

### 4.1 后端

| 阶段 | 命中组件 | 命中条数 | 严重度 |
|---|---|---|---|
| 治理前（Spring Boot 4.1.0） | 10 | 30 | 4 项 CRITICAL、HIGH/MODERATE 若干 |
| 治理后（Spring Boot 4.1.1 + 补丁覆盖） | **1** | **1** | MODERATE |

治理动作：整体升级 `spring-boot.version` 4.1.0 → 4.1.1（同时修复 Spring Framework 7.0.8→7.0.9、Netty 4.2.15→4.2.17、Log4j 2.25.4→2.25.5），
并在根 `pom.xml` 的 `dependencyManagement` 中**显式**声明补丁版本（Tomcat 11.0.22→11.0.27、Jackson2 2.21.4→2.21.7、Jackson3(tools) 3.1.4→3.1.7）。

> 说明：直接覆盖 `tomcat.version` / `jackson-bom.version` 等属性**不生效**——import 型 BOM 的内部属性不随引入方 pom 的覆盖而改变，必须以"直接声明优先于 import"的方式显式声明（已实测确认）。

残余 1 项：`com.ruoyi:ruoyi-common@3.9.2` 命中 `GHSA-vp22-232w-h9x8`（CVE-2022-4348，"RuoYi-Cloud Cross-site Scripting"）。
**判定为同名坐标误报**：该通告针对 **RuoYi-Cloud**（另一产品）的 JSON Handler，影响范围 `last_affected: 4.6.2`；
本项目为 RuoYi-Vue，其 `com.ruoyi:ruoyi-common` 模块经核查**不含任何 Web 端点**（仅工具类、常量、注解与异常，唯一的 `BaseController` 为无映射注解的抽象基类），不存在通告所述攻击面。OSV 以 Maven 坐标 `com.ruoyi:ruoyi-common` 匹配，无法区分两个产品，故产生碰撞。
另：该通告在 OSV 中**无 `fixed` 事件**（上游未登记修复版本），无法通过升级消除。

### 4.2 前端

| 阶段 | 告警总数 | 严重度构成 |
|---|---|---|
| 治理前 | 16 | 11 high / 5 moderate |
| 治理后（`npm audit`） | **5** | 5 high |
| 治理后（`npm audit --omit=dev`，**生产依赖树**） | **0** | — |

治理动作：
- 运行时依赖升级：`axios` 1.13.2→1.20.0、`vue` 3.5.26→3.5.44、`js-cookie` 3.0.5→3.0.8、`echarts` 5.6.0→6.1.0。
- 构建期工具链升级：`vite` 6.4.1→6.4.4、`unplugin-auto-import` 0.18.6→21.1.0。
- 传递依赖收敛：`overrides` 固定 `postcss` 8.5.30、`micromatch` 4.0.8、`braces` 3.0.3、`fast-glob` 3.3.3。
- 显式声明 `fast-glob` 为 devDependency：`vite-plugin-svg-icons` 未在自身 `package.json` 声明该依赖（幽灵依赖），
  升级 `unplugin-auto-import` 后原先的提升安装消失，导致 `vite build` 报 `Cannot find package 'fast-glob'`，故显式补齐。

残余 5 项（`braces` / `micromatch` / `svg-baker` / `vite-plugin-svg-icons` / `fast-glob`）**已接受并记录为残余风险**，理由：
1. 全部位于 `devDependencies` 构建链路（SVG 图标生成），**不进入运行时产物**（生产构建输出为预编译静态文件）；
2. 输入为**可信源码**（`src/icons` 下的自家 SVG），非外部用户可控数据；
3. 根因是 `braces` 的 ReDoS 通告在 OSV/npm 中**至今无已修复版本**（`RANGE=*`，最新 3.0.3 仍命中），属上游无补丁而非本项目遗漏；
4. npm 给出的"修复方案"是把 `vite-plugin-svg-icons` 由 2.0.1 **降级到 0.1.0**（假修复），会破坏图标管线，**明确不采纳**。

> 可选替代（未实施）：以 `unplugin-icons` 替换 `vite-plugin-svg-icons`，可彻底移除该链路。

## 五、已知缺口（如实记录）

1. 前端清单未以 CycloneDX 格式输出。如需统一格式：
   `npx @cyclonedx/cyclonedx-npm --output-file frontend-bom.json`（需联网）。
2. 后端 BOM 覆盖编译/运行期依赖；如需含测试依赖，可加 `-DincludeTestScope=true` 重新生成。
3. `osv-scanner` 与 `npm audit` 的结果均依赖外部漏洞库时效性，建议在 CI 中定期重扫。

## 六、复核命令（可复现）

```bat
rem 后端 SBOM（需 Maven 与 JDK 17、需联网）
cd campus-tao\RuoYi-Vue-master
mvn -B org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom ^
    -DoutputFormat=json -DoutputName=backend-bom -DoutputDirectory=..\sbom

rem 后端漏洞扫描（需联网，SBOM 文件须按规范命名 *.cdx.json）
osv-scanner scan source -S backend-bom.cdx.json

rem 前端清单（离线，扫描 node_modules）
python tools\sbom-frontend.py

rem 前端漏洞扫描（生产依赖树）
cd campus-tao-ui
npm audit --omit=dev
```

前端扫描脚本见 `tools/sbom-frontend.py`，与本文件同批交付。
