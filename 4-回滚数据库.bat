@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
cd /d %~dp0

REM ============================================================
REM  校园淘 - 数据库回滚（Flyway Community 无 undo，用 U*.sql 补偿）
REM
REM  用法：4-回滚数据库.bat <目标版本>
REM     0 = 回滚到空库
REM     1 = 只保留 V1 基线（撤销 V2 买家侧旅程）
REM
REM  原理：执行 db\rollback\U<n>__*.sql 补偿脚本，并删除
REM        flyway_schema_history 中对应版本记录（否则再次 migrate
REM        会认为"已应用"而跳过，导致库结构与版本记录不一致）。
REM ============================================================

set MYSQLDIR=%~dp0mysql-8.0.40
set ROLLBACKDIR=%~dp0campus-tao\RuoYi-Vue-master\db\rollback
set DBHOST=127.0.0.1
set DBPORT=3306
set DBUSER=root
set DBPASS=123456
set DBNAME=campus_tao

if "%~1"=="" (
  echo 用法: 4-回滚数据库.bat ^<目标版本^>
  echo   0 = 回滚到空库
  echo   1 = 只保留 V1 基线
  echo.
  echo 警告：回滚不可逆，请先备份数据库。
  pause
  exit /b 2
)
set TARGET=%~1

set MYSQL="%MYSQLDIR%\bin\mysql" -h%DBHOST% -P%DBPORT% -u%DBUSER% -p%DBPASS%

echo ============================================
echo   校园淘 - 数据库回滚
echo ============================================
echo   目标库: %DBNAME%@%DBHOST%:%DBPORT%
echo.

for /f "usebackq delims=" %%v in (`%MYSQL% -N -B -e "SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL;" %DBNAME% 2^>nul`) do set CURRENT=%%v
if not defined CURRENT set CURRENT=0
echo   当前版本: %CURRENT%    目标版本: %TARGET%
echo.

if %CURRENT% LEQ %TARGET% (
  echo 无需回滚（当前版本不高于目标版本）。
  pause
  exit /b 0
)

set /a v=%CURRENT%
:loop
if %v% LEQ %TARGET% goto done

set SCRIPT=
for %%f in ("%ROLLBACKDIR%\U%v%__*.sql") do set SCRIPT=%%f
if "!SCRIPT!"=="" (
  echo [错误] 找不到版本 %v% 的回滚脚本 U%v%__*.sql，中止。
  pause
  exit /b 1
)

echo --------------------------------------------
echo 回滚 V%v%  ^<-  !SCRIPT!
%MYSQL% %DBNAME% < "!SCRIPT!"
if errorlevel 1 (
  echo [错误] 补偿脚本执行失败，请检查后重试。
  pause
  exit /b 1
)
%MYSQL% -e "DELETE FROM flyway_schema_history WHERE version='%v%';" %DBNAME%
echo   OK
set /a v=%v%-1
goto loop

:done
echo ============================================
for /f "usebackq delims=" %%v in (`%MYSQL% -N -B -e "SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL;" %DBNAME% 2^>nul`) do set NOW=%%v
echo   回滚完成，当前版本: %NOW%
echo   再次启动后端将自动把库升回最新版本。
echo ============================================
pause
