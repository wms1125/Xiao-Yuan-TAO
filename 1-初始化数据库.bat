@echo off
chcp 65001 >nul
setlocal
cd /d %~dp0

REM 0. 路径不能含中文（MySQL 8 在 Windows 不认中文路径，会在中文处截断导致启动失败）
powershell -NoProfile -Command "if(('%~dp0').ToCharArray()|Where-Object{[int]$_ -gt 127}){Write-Host '[错误] 当前文件夹路径包含中文，MySQL 无法启动！';Write-Host '请把整个 campus-tao-dist 文件夹移动到纯英文路径，例如 D:\campus-tao-dist，再重新运行本脚本。';exit 1}" 2>nul
if errorlevel 1 (echo. & pause & exit /b)

set MYSQLDIR=%~dp0mysql-8.0.40
set SQLFILE=%~dp0campus-tao\sql\campus_tao_full.sql

echo ============================================
echo   校园淘 - 第1步：初始化并导入数据库
echo ============================================

REM 1. 初始化 MySQL 数据目录（仅首次）
if not exist "%MYSQLDIR%\data" (
  echo [1/4] 正在初始化 MySQL 数据目录（首次较慢，请稍候）...
  "%MYSQLDIR%\bin\mysqld" --initialize-insecure --datadir="%MYSQLDIR%\data"
) else (
  echo [1/4] 数据目录已存在，跳过初始化。
)

REM 2. 后台启动 MySQL
echo [2/4] 正在启动 MySQL 服务...
start "campus-tao-mysql" "%MYSQLDIR%\bin\mysqld" --datadir="%MYSQLDIR%\data" --console

REM 3. 等待 MySQL 就绪
echo [3/4] 等待 MySQL 启动（约 12 秒）...
timeout /t 12 >nul

REM 4. 设置 root 密码为 123456（首次为空密码，重跑会跳过）
echo [4/4] 设置 root 密码并导入 campus_tao 数据库...
"%MYSQLDIR%\bin\mysql" -uroot -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '123456'; FLUSH PRIVILEGES;" 2>nul
if errorlevel 1 (
  echo （root 密码已设置，跳过）
)
"%MYSQLDIR%\bin\mysql" -uroot -p123456 < "%SQLFILE%"

echo.
echo ============================================
echo   数据库初始化完成！可关闭本窗口。
echo   接下来：双击「2-启动后端.bat」
echo ============================================
pause >nul
