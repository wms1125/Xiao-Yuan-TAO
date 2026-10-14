@echo off
chcp 65001 >nul
setlocal
cd /d %~dp0campus-tao-ui

echo ============================================
echo   校园淘 - 第3步：启动前端 (8088)
echo   需本机已安装 Node.js（含 npm 命令）
echo   请勿关闭本窗口！
echo ============================================

REM 依赖未安装则自动安装（国内镜像加速，首次约 3~10 分钟，之后跳过）
if not exist "node_modules" (
  echo 首次运行，正在安装前端依赖（国内镜像，请耐心等待）...
  call npm install --registry=https://registry.npmmirror.com --no-audit --no-fund
)

call npm run dev

pause
