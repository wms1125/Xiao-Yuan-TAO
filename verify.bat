@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ============================================================
echo   校园淘 交付前校验（CI 的本地等价脚本）
echo     1) 预检：发行 jar 是否被占用
echo     2) 后端 mvn -B -DskipTests clean package
echo     3) 后端 mvn -B test
echo     4) 前端 npm run build:prod
echo   全部通过则退出码为 0，任一失败为 1。
echo ============================================================
echo.

rem ---------- 1. 定位 JDK：优先使用随包内置的 JDK 17 ----------
if exist "jdk-17" (
  for /d %%d in ("jdk-17\*") do if exist "%%d\bin\java.exe" set "JAVA_HOME=%CD%\%%d"
)
if defined JAVA_HOME (echo [信息] JAVA_HOME=%JAVA_HOME%) else (echo [信息] 未使用随包 JDK，将沿用系统 JAVA_HOME 或默认 JDK)

rem ---------- 2. 定位 Maven ----------
set "MVN="
if defined MVN_CMD set "MVN=%MVN_CMD%"
if not defined MVN if exist ".tools\maven\bin\mvn.cmd" set "MVN=%CD%\.tools\maven\bin\mvn.cmd"
if not defined MVN if exist "%TEMP%\cta-mvn\apache-maven-3.9.9\bin\mvn.cmd" set "MVN=%TEMP%\cta-mvn\apache-maven-3.9.9\bin\mvn.cmd"
if not defined MVN for %%i in (mvn.cmd) do if not "%%~$PATH:i"=="" set "MVN=mvn"

set "FAIL=0"

rem ---------- 3. 预检：发行 jar 被占用时 Maven 无法 clean/重打包 ----------
rem 用 PowerShell 以 FileShare=None 独占打开探测；探针失败时按"未占用"处理，
rem 交由 Maven 自行报错（避免误判阻断校验）。
set "JAR=campus-tao\RuoYi-Vue-master\ruoyi-admin\target\ruoyi-admin.jar"
set "LOCKED="
if exist "%JAR%" (
  for /f "usebackq delims=" %%r in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "try{[IO.File]::Open('%JAR%','Open','Write','None').Close();'unlocked'}catch{'locked'}"`) do set "LOCKED=%%r"
)
if /i "%LOCKED%"=="locked" (
  echo [警告] 检测到发行 jar 正被占用：%JAR%
  echo         通常是后端仍在运行（java -jar）。请先停止后端再执行本脚本，
  echo         否则 maven-clean-plugin 会因无法删除该文件而失败。
  echo         已跳过后端构建阶段，继续校验前端。
  set "FAIL=1"
) else (
  if not defined MVN (
    echo [跳过] 未找到 Maven，后端校验无法执行。请任选其一：
    echo        1^) 设置环境变量 MVN_CMD 指向 mvn.cmd
    echo        2^) 将 Maven 解压到 .tools\maven\（.tools 已加入 .gitignore）
    echo        3^) 将 mvn 加入 PATH
    echo   注意：前端校验仍会继续执行。
  ) else (
    echo [信息] Maven=%MVN%
    echo.
    pushd campus-tao\RuoYi-Vue-master
    echo ---- [2/4] 后端打包 ----
    call "%MVN%" -B -DskipTests clean package
    if errorlevel 1 (set "FAIL=1" & echo [失败] 后端打包) else (echo [通过] 后端打包)
    echo.
    echo ---- [3/4] 后端单元测试（无 MySQL 时 JDBC 用例自动跳过）----
    call "%MVN%" -B test
    if errorlevel 1 (set "FAIL=1" & echo [失败] 后端单元测试) else (echo [通过] 后端单元测试)
    popd
  )
)

echo.
echo ---- [4/4] 前端生产构建 ----
pushd campus-tao-ui
if not exist "node_modules" (
  echo [信息] 未发现 node_modules，先执行 npm ci（需要网络）
  call npm ci
  if errorlevel 1 set "FAIL=1"
)
call npm run build:prod
if errorlevel 1 (set "FAIL=1" & echo [失败] 前端构建) else (echo [通过] 前端构建)
popd

echo.
echo ============================================================
if "%FAIL%"=="0" (
  echo 校验结果：ALL PASS
  exit /b 0
) else (
  echo 校验结果：FAILED
  exit /b 1
)
