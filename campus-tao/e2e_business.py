#!/usr/bin/env python3
# 校园淘 完整业务链路 E2E 验证（双账号：admin 卖家 / ry 买家）
# 覆盖：双账号登录 → 充值 → 发布商品(伪造卖家身份被服务端强制覆盖) → 加购 → 下单 →
#       支付(原子扣款) → 发货 → 确认收货 → 评价 → 售后申请 → 审核(退款+回库存) →
#       余额/库存回滚断言 → 订单归属断言 → 防超卖 → 权限收敛断言(403)
import json, os, urllib.request, urllib.error, time, sys

try:  # Windows 控制台中文输出兜底
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

BASE = os.environ.get("CAMPUS_BASE_URL", "http://localhost:8080")  # 容器/换端口时可用环境变量覆盖
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

print("\n=== 9) admin 发货（录入物流单号）===")
st, ship = call("POST", f"/campus/order/ship/{order_no}?logisticsNo=SF1234567890", token=ADMIN)
print(st, ship)

print("\n=== 9.1) 断言：物流单号已随发货落库并在订单详情可见 ===")
st, sd = call("GET", f"/campus/order/{order_no}", token=RY)
logistics = sd.get("data", {}).get("logisticsNo")
print("详情中物流单号:", logistics)
must(logistics == "SF1234567890", "发货物流单号记录正确", f"实际 {logistics}")

print("\n=== 9.2) 断言：发货触发站内通知（买家 ry 收到发货消息）===")
st, nt = call("GET", "/campus/notice/top", token=RY)
items = (nt.get("data") or {}).get("items", [])
unread = (nt.get("data") or {}).get("unreadCount", 0)
print("ry 未读消息数:", unread, " 类型:", [i.get("type") for i in items])
must(any(i.get("bizNo") == order_no and i.get("type") == "ship" for i in items),
     "发货通知已推送给买家且关联订单号", str(items))
must(unread > 0, "未读数量 > 0", f"实际 {unread}")
if items:
    st, rd = call("POST", f"/campus/notice/read/{items[0]['noticeId']}", token=RY)
    print("标记已读:", st, rd)
    must(rd.get("code") == 200, "本人可标记自己的消息已读", str(rd))

print("\n=== 9.3) 断言[越权防护]：未登录不得访问站内通知（消息按登录态隔离）===")
st, anon = call("GET", "/campus/notice/top")
print("匿名访问站内通知:", st, anon)
must(st == 401 or anon.get("code") == 401, "站内通知要求登录，未登录被拒", f"{st} {anon}")

print("\n=== 9.4) 断言：校园淘关键写操作已入操作日志（@Log 审计）===")
st, lg = call("GET", "/monitor/operlog/list?pageSize=50", token=ADMIN)
if st != 200:
    st, lg = call("GET", "/system/log/operlog/list?pageSize=50", token=ADMIN)
titles = [r.get("title") for r in (lg.get("rows") or [])]
print("近期操作日志标题:", sorted(set(titles)))
must(any(t and "校园淘" in t for t in titles), "关键操作已写入 sys_oper_log（审计留痕）", str(sorted(set(titles))))

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

print("\n=== 18) 断言[运营能力 v1.0.7]：物流轨迹查询与通道降级 ===")
st, lstat = call("GET", "/campus/logistics/status", token=RY)
provider_on = lstat.get("data")
print("物流轨迹通道已启用:", provider_on)
must(st == 200, "物流通道状态接口可用", f"{st} {lstat}")
st, tr = call("GET", f"/campus/logistics/trace/{order_no}", token=RY)
tdata = tr.get("data") or {}
print("轨迹接口:", st, "providerEnabled=", tdata.get("providerEnabled"),
      "轨迹节点数=", len(tdata.get("traces") or []))
must(st == 200 and tdata.get("logisticsNo") == "SF1234567890",
     "本人可查询订单物流轨迹且返回物流单号", str(tr)[:200])

print("\n=== 18.1) 断言[越权防护]：未登录不得访问物流轨迹 ===")
st, anon2 = call("GET", f"/campus/logistics/trace/{order_no}")
print("匿名访问物流轨迹:", st, anon2)
must(st == 401 or anon2.get("code") == 401, "物流轨迹要求登录，未登录被拒", f"{st} {anon2}")

print("\n=== 18.2) 断言：不存在/非本人订单的轨迹被拒绝 ===")
st, other = call("GET", "/campus/logistics/trace/CT209901010000001", token=RY)
print("越权查询他人订单轨迹:", st, other)
must(other.get("code") != 200, "非本人订单轨迹被拒绝", f"{st} {other}")

print("\n=== 18.3) 断言[运营能力 v1.0.7]：外呼留痕按登录态隔离（仅管理员可查）===")
st, nl403 = call("GET", "/campus/notifylog/list", token=RY)
print("ry 访问外呼留痕:", st, nl403)
must(st == 403 or nl403.get("code") == 403, "ry 无 campus:notifylog:list 被拒绝", f"{st} {nl403}")
st, nl = call("GET", "/campus/notifylog/list?limit=50", token=ADMIN)
rows = nl.get("data") or []
print("admin 可见外呼留痕条数:", len(rows),
      "渠道:", sorted(set(r.get("channel") for r in rows)),
      "结果:", sorted(set(r.get("status") for r in rows)))
must(st == 200 and len(rows) > 0, "外呼留痕已留痕可查（短信/邮件通道各一条链路）", f"{st} {str(nl)[:200]}")
must(any(r.get("bizNo") == order_no for r in rows), "外呼留痕关联到本次订单业务号", str([r.get("bizNo") for r in rows][:10]))
must(set(r.get("channel") for r in rows) >= {"sms", "email"}, "短信与邮件通道均有留痕", str(sorted(set(r.get("channel") for r in rows))))

print("\n=== 19) 断言[可观测性 v1.0.23]：traceId 与业务指标 ===")
# /actuator/prometheus 返回的是文本格式，call() 会按 JSON 解析而失败，故此处单独取原始文本
try:
    with urllib.request.urlopen(BASE + "/actuator/prometheus", timeout=15) as r:
        prom_status, prom_text = r.status, r.read().decode()
except urllib.error.HTTPError as e:
    prom_status, prom_text = e.code, e.read().decode(errors="ignore")
print("指标端点:", prom_status, "长度:", len(prom_text))
must(prom_status == 200, "指标端点匿名可抓取（Prometheus 抓取前提）", str(prom_status))
must("campus_payment_channel_available" in prom_text, "业务指标已注册：渠道可用性 gauge", prom_text[:120])
must("campus_payment_attempts_total" in prom_text, "业务指标已注册：支付尝试计数", "")

# traceId 由 TraceIdFilter 回写；健康检查同样经过该过滤器
with urllib.request.urlopen(urllib.request.Request(BASE + "/actuator/health"), timeout=15) as r:
    trace_id = r.headers.get("X-Trace-Id")
print("traceId:", trace_id)
must(bool(trace_id) and len(trace_id) == 32, "响应头回写 traceId（32 位）", str(trace_id))

print("\nRESULT:", "ALL_PASS" if ok else "HAS_FAIL")
