#!/usr/bin/env bash
# 校园淘 · 买家侧页面浏览器实测取证脚本（v1.0.21）
#
# 沿革：本脚本是 docs/evidence-v1.0.16/capture.sh 的等价扩展。
#   证据停更的原因见 M5「本节未纳入」：v1.0.16 ~ v1.0.20 新增/落地的页面
#   （收货地址、我的收藏、消息沟通、购物车结算联动、商品管理）一直没有截图级证据。
#   本轮把场景从 6 个补齐到 11 个，覆盖 v1.0.16~v1.0.21 的全部新增页面。
#
# 为什么补齐：项目自定《AI-辅助开发规范》的原则是「产出即证据」。
#   行为有 197 例前端用例 + 五道门禁 + e2e 接口级断言覆盖，但仍缺"页面在当前代码上
#   真的能渲染出来"这一层最直观、也最无法用断言替代的证据。
#
# 取证前置（三个服务必须在跑）：
#   MySQL 8 (3306)  →  后端 8080（/actuator/health 返回 UP）  →  前端 8088
#   浏览器驱动用 agent-browser CLI（非 playwright/puppeteer，故不引入新依赖）
#
# 执行环境说明（如实记录）：
#   本机为 Windows，且**没有 Git Bash**（只有 WSL bash，无法调用 Windows 侧的 agent-browser npm CLI）。
#   因此本脚本在本次取证中是**逐条等价执行**的——命令、顺序、等待时长与文件名完全一致，
#   只是由 PowerShell 逐条下发而非由 bash 整份运行。脚本本身在 Linux/macOS/Git Bash 下可直接运行。
#
# 用法：
#   bash capture.sh          # Linux / macOS / Git Bash
#   或在 Windows 上按 README.md「逐条等价执行」清单逐条下发同名命令。
set -u
OUT="D:/campus-tao-dist/campus-tao-dist/campus-tao/docs/evidence-v1.0.21"
cd "$OUT" || exit 1

echo "== 1. 打开登录页 =="
agent-browser open "http://localhost:8088/login"
sleep 3
agent-browser screenshot "$OUT/01-login.png"

echo "== 2. 填账号密码 =="
agent-browser find placeholder "校园账号" fill "ry"
agent-browser find placeholder "登录密码" fill "admin123"
sleep 1

echo "== 3. 点登录 =="
agent-browser find text "登 录" click
sleep 14
agent-browser get url

echo "== 4. 买家首页 =="
agent-browser screenshot "$OUT/02-buyer-index.png"

echo "== 5. 校园商城（v1.0.15 新增）=="
agent-browser open "http://localhost:8088/campus/mall"
sleep 10
agent-browser get url
agent-browser screenshot "$OUT/03-buyer-mall.png"

echo "== 6. 商品详情（v1.0.15 新增）=="
agent-browser open "http://localhost:8088/campus/mall-detail?id=1"
sleep 10
agent-browser get url
agent-browser screenshot "$OUT/04-buyer-mall-detail.png"

echo "== 7. 评价管理（v1.0.15 新增）=="
agent-browser open "http://localhost:8088/campus/review"
sleep 10
agent-browser get url
agent-browser screenshot "$OUT/05-buyer-review.png"

echo "== 8. 我的订单 =="
agent-browser open "http://localhost:8088/campus/order"
sleep 10
agent-browser get url
agent-browser screenshot "$OUT/06-buyer-order.png"

echo "== 9. 收货地址（v1.0.17 新增）=="
agent-browser open "http://localhost:8088/campus/address"
sleep 10
agent-browser get url
agent-browser screenshot "$OUT/07-buyer-address.png"

echo "== 10. 我的收藏（v1.0.19 落地）=="
agent-browser open "http://localhost:8088/campus/favorite"
sleep 10
agent-browser get url
agent-browser screenshot "$OUT/08-buyer-favorite.png"

echo "== 11. 消息沟通（v1.0.19 落地）=="
agent-browser open "http://localhost:8088/campus/message"
sleep 10
agent-browser get url
agent-browser screenshot "$OUT/09-buyer-message.png"

echo "== 12. 购物车（v1.0.20 组件测试补强：勾选联动结算）=="
agent-browser open "http://localhost:8088/campus/cart"
sleep 10
agent-browser get url
agent-browser screenshot "$OUT/10-buyer-cart.png"

echo "== 13. 商品管理（v1.0.20 组件测试补强：发布/修改/上下架/加购）=="
agent-browser open "http://localhost:8088/campus/product"
sleep 10
agent-browser get url
agent-browser screenshot "$OUT/11-buyer-product.png"

echo "== DONE =="
ls -la "$OUT"
