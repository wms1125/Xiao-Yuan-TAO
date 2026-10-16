#!/usr/bin/env bash
# 买家侧新增页面浏览器实测取证脚本（v1.0.15）
set -u
OUT="D:/campus-tao-dist/campus-tao-dist/campus-tao/docs/evidence-v1.0.16"
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

echo "== 5. 校园商城（新增）=="
agent-browser open "http://localhost:8088/campus/mall"
sleep 10
agent-browser get url
agent-browser screenshot "$OUT/03-buyer-mall.png"

echo "== 6. 商品详情（新增）=="
agent-browser open "http://localhost:8088/campus/mall-detail?id=1"
sleep 10
agent-browser get url
agent-browser screenshot "$OUT/04-buyer-mall-detail.png"

echo "== 7. 评价管理（新增）=="
agent-browser open "http://localhost:8088/campus/review"
sleep 10
agent-browser get url
agent-browser screenshot "$OUT/05-buyer-review.png"

echo "== 8. 我的订单 =="
agent-browser open "http://localhost:8088/campus/order"
sleep 10
agent-browser get url
agent-browser screenshot "$OUT/06-buyer-order.png"

echo "== DONE =="
ls -la "$OUT"
