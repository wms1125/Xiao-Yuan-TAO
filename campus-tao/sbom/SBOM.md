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

- 组件总数：**156**，其中 **150** 项带许可信息。
- **覆盖度校验**：与发行 fat jar `ruoyi-admin.jar` 内 `BOOT-INF/lib` 的 **141** 个依赖 jar 逐一对照，未发现 BOM 缺失。
- 许可分布：

| 许可 | 组件数 |
|---|---|
| Apache-2.0 | 126 |
| MIT | 11 |
| EPL-2.0 | 5 |
| LGPL-2.1-only / LGPL-2.1-or-later | 3 / 2 |
| GPL-2.0 with Classpath Exception | 3 |
| BSD-3-Clause | 3 |
| CDDL-1.1 | 1 |
| MIT-0 | 1 |
| GPL-2.0 with Universal FOSS Exception | 1 |
| Public Domain | 1 |
| UNKNOWN | 1 |
| **未声明** | 6 |

- **需关注项（均为可商用，但按规范应披露）**：
  - `GPL-2.0 with Classpath Exception` / `GPL-2.0 with Universal FOSS Exception`：MySQL Connector/J 等。类路径例外意味着以 jar 形式链接使用不触发传染，**可随闭源项目分发**。
  - `LGPL-2.1`（5 项）：动态链接（jar 引用）方式合规，修改其源码才涉及回馈义务。
  - `CDDL-1.1`（1 项）：文件级 copyleft，作为未修改的二进制依赖使用合规。
  - 无 AGPL、无 SSPL 等强传染许可——**不存在阻断分发的组件**。
  - `UNKNOWN` 1 项建议上线前人工确认。
- **未声明许可的 6 项为本项目自研模块**（`com.ruoyi:ruoyi-common/system/framework/quartz/generator/admin@3.9.2`）——上游 RuoYi 的 pom 未声明 `<licenses>`。建议由项目所有者决定许可后在根 `pom.xml` 补充（**未擅自代填**）。

## 三、前端

- 包总数：**461**（含嵌套重复版本）。
- 许可分布：

| 许可 | 包数 |
|---|---|
| MIT | 407 |
| ISC | 20 |
| BSD-2-Clause | 12 |
| BSD-3-Clause | 9 |
| Apache-2.0 | 5 |
| BlueOak-1.0.0 | 5 |
| (MIT OR Apache-2.0) | 1 |
| CC0-1.0 | 1 |
| 0BSD | 1 |
| **UNKNOWN** | **0** |
| **copyleft（GPL/AGPL/LGPL/MPL 等）** | **0** |

- 结论：**全部为宽松许可，无未知许可、无 copyleft 组件**，前端依赖不构成许可风险。

## 四、已知缺口（如实记录）

1. 前端清单未以 CycloneDX 格式输出。如需统一格式：
   `npx @cyclonedx/cyclonedx-npm --output-file frontend-bom.json`（需联网）。
2. **未做漏洞（CVE）扫描**。建议接入 `npm audit`（前端）与 OWASP Dependency-Check 或 `mvn org.owasp:dependency-check-maven:check`（后端）；这属于"依赖安全"而非"许可合规"，本 SBOM 不覆盖。
3. 后端 BOM 覆盖编译/运行期依赖；如需含测试依赖，可加 `-DincludeTestScope=true` 重新生成。

## 五、复核命令（可复现）

```bat
rem 后端（需 Maven 与 JDK 17；交付机可用 verify.bat 同款定位方式）
cd campus-tao\RuoYi-Vue-master
mvn -B org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom ^
    -DoutputFormat=json -DoutputName=backend-bom -DoutputDirectory=..\sbom

rem 前端（离线，扫描 node_modules）
python tools\sbom-frontend.py
```

前端扫描脚本见 `tools/sbom-frontend.py`，与本文件同批交付。
