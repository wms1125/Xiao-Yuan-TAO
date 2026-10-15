@echo off
chcp 65001 >nul
setlocal
cd /d %~dp0

REM 定位内置 JDK（兼容嵌套目录）
set JDKDIR=%~dp0jdk-17
if exist "%JDKDIR%\jdk-17.0.20.1+1\bin\java.exe" set JDKDIR=%JDKDIR%\jdk-17.0.20.1+1
set JAVA_HOME=%JDKDIR%
set PATH=%JAVA_HOME%\bin;%PATH%

REM 应用配置不再内置默认凭据（缺失即启动失败）。本脚本为「本地/演示沙箱」显式注入演示值；
REM 生产部署请通过系统环境变量或密钥管理注入，切勿沿用下列演示值（尤其 JWT_SECRET 必须更换）。
if not defined DB_USERNAME set "DB_USERNAME=root"
if not defined DB_PASSWORD set "DB_PASSWORD=123456"
if not defined JWT_SECRET set "JWT_SECRET=campus-tao-demo-only-jwt-secret-please-change"
if not defined DRUID_LOGIN_USERNAME set "DRUID_LOGIN_USERNAME=ruoyi"
if not defined DRUID_LOGIN_PASSWORD set "DRUID_LOGIN_PASSWORD=123456"

echo ============================================
echo   校园淘 - 第2步：启动后端 (8080)
echo   JDK: %JAVA_HOME%
echo   内置 Redis 将随应用自动拉起 (6379)
echo   请勿关闭本窗口！
echo ============================================

cd /d %~dp0campus-tao\RuoYi-Vue-master\ruoyi-admin
java -Dserver.port=8080 -Dfile.encoding=UTF-8 -jar target\ruoyi-admin.jar

pause
