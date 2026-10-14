@echo off
chcp 65001 >nul
setlocal
cd /d %~dp0

REM 定位内置 JDK（兼容嵌套目录）
set JDKDIR=%~dp0jdk-17
if exist "%JDKDIR%\jdk-17.0.20.1+1\bin\java.exe" set JDKDIR=%JDKDIR%\jdk-17.0.20.1+1
set JAVA_HOME=%JDKDIR%
set PATH=%JAVA_HOME%\bin;%PATH%

echo ============================================
echo   校园淘 - 第2步：启动后端 (8080)
echo   JDK: %JAVA_HOME%
echo   内置 Redis 将随应用自动拉起 (6379)
echo   请勿关闭本窗口！
echo ============================================

cd /d %~dp0campus-tao\RuoYi-Vue-master\ruoyi-admin
java -Dserver.port=8080 -Dfile.encoding=UTF-8 -jar target\ruoyi-admin.jar

pause
