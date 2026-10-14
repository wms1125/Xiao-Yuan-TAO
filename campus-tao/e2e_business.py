#!/usr/bin/env python3
# 校园淘 完整业务链路 E2E 验证（双账号：admin 卖家 / ry 买家）
# 覆盖：双账号登录 → 充值 → 发布商品(伪造卖家身份被服务端强制覆盖) → 加购 → 下单 →
#       支付(原子扣款) → 发货 → 确认收货 → 评价 → 售后申请 → 审核(退款+回库存) →
#       余额/库存回滚断言 → 订单归属断言 → 防超卖 → 权限收敛断言(403)
import json, urllib.request, urllib.error, time, sys

try:  # Windows 控制台中文输出兜底
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

BASE = "http://localhost:8080"
TS = str(int(time.time()))[-6:]  # 6位时间戳保证标题唯一

def call(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {"raw": "non-json"}

def must(cond, msg, detail=""):
    global ok
    if cond:
        print("PASS", msg)
    else:
        print("FAIL", msg, detail)
        ok = False

def login(username):
    st, r = call("POST", "/login", body={"username": username, "password": "admin123"})
    token = r.get("token")
    assert token, f"登录失败 {username}: {r}"
    return token

ok = True

print("=== 1) 双账号登录（admin 卖家 / ry 买家）===")
ADMIN = login("admin")
RY = login("ry")
print("admin / ry 登录成功")

print("\n=== 2) ry 充值500（新账户行自动创建）===")
st, rc = call("POST", "/campus/account/recharge", token=RY, body={"amount": 500})
print(st, rc)
st, acc = call("GET", "/campus/account/balance", token=RY)
bal_before = float(acc.get("data", {}).get("balance", 0))
print("ry 下单前余额:", bal_before)

print("\n=== 3) admin 发布商品（伪造 sellerId=99，应被服务端强制覆盖）===")
st, p = call("POST", "/campus/product", token=ADMIN, body={
    "title": "测试二手显示器" + TS, "categoryId": 1, "price": 30, "originalPrice": 80,
    "conditionLevel": 2, "stock": 5, "status": 1, "description": "9成新", "images": "",
    "sellerId": 99, "sellerName": "同学A"})
print(st, p)
pid = None
st, plist = call("GET", "/campus/product/list?pageSize=5", token=ADMIN)
for row in plist.get("rows", []):
    if row.get("title") == "测试二手显示器" + TS:
        pid = row["id"]; break
print("商品ID:", pid)
assert pid, "未取到商品ID"

print("\n=== 4) 断言[修复③]：详情中卖家应为 admin(user_id=1)，而非伪造的99 ===")
st, pd = call("GET", f"/campus/product/{pid}", token=RY)
seller_id = pd.get("data", {}).get("sellerId")
seller_name = pd.get("data", {}).get("sellerName")
stock_before = pd.get("data", {}).get("stock")
print("落库 sellerId:", seller_id, " sellerName:", seller_name, " 库存:", stock_before)
must(seller_id == 1, "发布商品服务端强制卖家身份", f"实际 sellerId={seller_id}")

print("\n=== 5) ry 加购(数量2) ===")
st, c = call("POST", "/campus/cart/add", token=RY, body={"productId": pid, "quantity": 2})
print(st, c)
st, cl = call("GET", "/campus/cart/list", token=RY)
cart_id = None
for it in cl.get("data", []):
    if it.get("productId") == pid:
        cart_id = it["id"]; break
print("购物车项ID:", cart_id)
assert cart_id, "未取到购物车项ID"

print("\n=== 6) ry 下单（买家≠卖家，自购拦截不触发）===")
st, o = call("POST", "/campus/order/create", token=RY, body={
    "cartIds": [cart_id],
    "receiverName": "张三", "receiverPhone": "13800000000", "address": "1号宿舍楼", "remark": "尽快发货"})
print(st, o)
order_no = o.get("data")
print("订单号:", order_no)
assert order_no, "下单失败"

print("\n=== 7) ry 支付（原子扣款，应扣60）===")
st, pay = call("POST", f"/campus/order/pay/{order_no}", token=RY)
print(st, pay)
st, acc2 = call("GET", "/campus/account/balance", token=RY)
bal_after_pay = float(acc2.get("data", {}).get("balance", 0))
print("ry 支付后余额:", bal_after_pay)
must(abs((bal_before - 60) - bal_after_pay) < 0.001, "支付扣款金额正确(60)", f"{bal_before} -> {bal_after_pay}")

print("\n=== 8) 断言[修复②]：ry 订单列表仅含本人订单 ===")
st, ol = call("GET", "/campus/order/list", token=RY)
rows = ol.get("data", []) or []
mine_only = all(r.get("userId") == 2 for r in rows)
print("ry 可见订单数:", len(rows))
must(mine_only, "订单列表归属过滤（仅本人订单）", str([(r.get("orderNo"), r.get("userId")) for r in rows]))

print("\n=== 9) admin 发货 ===")
st, ship = call("POST", f"/campus/order/ship/{order_no}", token=ADMIN)
print(st, ship)

print("\n=== 10) ry 确认收货 ===")
st, fin = call("POST", f"/campus/order/finish/{order_no}", token=RY)
print(st, fin)

print("\n=== 11) ry 评价 ===")
st, det = call("GET", f"/campus/order/{order_no}", token=RY)
order_id = det.get("data", {}).get("id")
st, rev = call("POST", "/campus/review/add", token=RY, body={
    "orderId": order_id, "productId": pid, "rating": 5, "content": "东西很好，好评", "images": ""})
print(st, rev)

print("\n=== 12) ry 申请售后(退货退款) ===")
st, asv = call("POST", "/campus/aftersale/apply", token=RY, body={
    "orderNo": order_no, "type": 2, "reason": "不喜欢了"})
print(st, asv)
as_no = asv.get("data")
print("售后单号:", as_no)
assert as_no, "售后申请失败"

print("\n=== 13) admin 审核通过（退款+回库存），并伪造 handler ===")
st, aud = call("POST", "/campus/aftersale/audit", token=ADMIN, body={
    "aftersaleNo": as_no, "pass": True, "handler": "forged-hacker", "remark": "同意"})
print(st, aud)
must(aud.get("code") == 200, "审核成功", str(aud))

print("\n=== 13.1) 断言：审核人取自登录态，前端伪造值被忽略 ===")
st, asl = call("GET", "/campus/aftersale/list?pageSize=20", token=ADMIN)
rows = (asl.get("data") or asl.get("rows") or [])
hit = [r for r in rows if r.get("aftersaleNo") == as_no]
must(bool(hit), "查到售后单", str(as_no))
stored_handler = hit[0].get("handler")
print("库中审核人:", stored_handler)
must(stored_handler == "admin", "审核人=admin(伪造值被忽略)", "实际 " + str(stored_handler))

print("\n=== 14) 断言：余额回滚、库存回滚 ===")
st, acc3 = call("GET", "/campus/account/balance", token=RY)
bal_after_refund = float(acc3.get("data", {}).get("balance", 0))
print("ry 退款后余额:", bal_after_refund)
must(abs(bal_before - bal_after_refund) < 0.001, "余额回滚一致", f"{bal_before} vs {bal_after_refund}")
st, pd2 = call("GET", f"/campus/product/{pid}", token=RY)
stock_after = pd2.get("data", {}).get("stock")
print("库存后:", stock_after)
must(stock_after == 5, "库存回滚一致(5)", f"实际 {stock_after}")

print("\n=== 15) 防超卖：admin 发布库存1商品，ry 下单2件应失败 ===")
st, p2 = call("POST", "/campus/product", token=ADMIN, body={
    "title": "限量1件测试" + TS, "categoryId": 1, "price": 10, "originalPrice": 20,
    "conditionLevel": 1, "stock": 1, "status": 1, "description": "仅1件", "images": "",
    "sellerId": 99, "sellerName": "同学A"})
pid2 = None
st, pl2 = call("GET", "/campus/product/list?pageSize=10", token=ADMIN)
for row in pl2.get("rows", []):
    if row.get("title") == "限量1件测试" + TS:
        pid2 = row["id"]; break
assert pid2, "未取到限量商品ID"
st, c2 = call("POST", "/campus/cart/add", token=RY, body={"productId": pid2, "quantity": 2})
st, cl2 = call("GET", "/campus/cart/list", token=RY)
cid2 = None
for it in cl2.get("data", []):
    if it.get("productId") == pid2:
        cid2 = it["id"]; break
st, o2 = call("POST", "/campus/order/create", token=RY, body={"cartIds": [cid2], "receiverName": "x", "receiverPhone": "x", "address": "x"})
print("超卖下单结果:", o2.get("msg"))
must("库存不足" in str(o2.get("msg", "")), "防超卖生效（库存不足被拦截）", str(o2))

print("\n=== 16) C2C 卖方能力：学生角色 ry 可自主发布商品，卖家身份强制为本人 ===")
st, f1 = call("POST", "/campus/product", token=RY, body={
    "title": "学生发布测试" + TS, "categoryId": 1, "price": 8, "originalPrice": 15,
    "conditionLevel": 1, "stock": 2, "status": 1, "description": "ry 本人发布", "images": "",
    "sellerId": 99, "sellerName": "伪造卖家"})
print("ry 发布商品:", st, f1)
must(f1.get("code") == 200, "ry 具备 campus:product:add，发布成功", f"{st} {f1}")
st, pl3 = call("GET", "/campus/product/list?pageSize=20", token=RY)
mine = [r for r in pl3.get("rows", []) if r.get("title") == "学生发布测试" + TS]
must(bool(mine), "列表可见 ry 发布的商品", "未找到")
st, pd3 = call("GET", f"/campus/product/{mine[0]['id']}", token=RY)
must(pd3.get("data", {}).get("sellerId") == 2, "卖家身份强制为当前登录用户(2)",
     f"实际 {pd3.get('data', {}).get('sellerId')}/{pd3.get('data', {}).get('sellerName')}")

print("\n=== 17) 断言[权限收敛]：ry 调用管理员接口应 403 ===")
st, f2 = call("POST", "/campus/aftersale/audit", token=RY, body={
    "aftersaleNo": as_no, "pass": True, "remark": "自审"})
print("ry 售后审核:", st, f2)
must(st == 403 or f2.get("code") == 403, "ry 无 campus:aftersale:edit 被拒绝（防自审自批）", f"{st} {f2}")

print("\nRESULT:", "ALL_PASS" if ok else "HAS_FAIL")
