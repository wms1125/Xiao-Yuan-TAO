#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
校园淘 · HTTP 层并发基准（标准库实现，零第三方依赖）

为什么自研而不是引 k6 / wrk / JMeter：
  交付包承诺"无网可构建、零额外依赖"，压测工具不能成为新的外部前置条件。
  本脚本只用 Python 标准库（http.client + threading），随包即可复现。

测什么：
  对买家侧最常走的 6 条只读接口，在 3 档并发（1 / 10 / 40）下各跑固定时长，
  记录每条请求的耗时，汇总 p50 / p95 / p99 / 均值 / 最大、总吞吐(QPS)、错误率，
  并给出单接口明细。

为什么要测到 40 并发：
  应用侧 Druid 连接池 maxActive=20（见 application-dataops 配置）。
  并发超过池上限时，超出部分的请求会在"等连接"上排队，这是最容易被忽略的容量瓶颈，
  必须在数字上看得见，而不是等上线后靠用户感知。

用法：
  python campus-tao/perf/bench_http.py
  python campus-tao/perf/bench_http.py --url http://localhost:8080 --user ry --concurrency 1,10,40 --duration 10
"""
import argparse
import http.client
import json
import math
import os
import statistics
import sys
import threading
import time
import urllib.parse
import urllib.request

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

# 被测接口：(名称, 路径, 是否代表"重查询")
# 路径全部取自归档的 OpenAPI 规格 docs/openapi/campus-tao-openapi.json 中 /campus/** 的 GET 项，
# 保证压测对象与真实契约一致，而不是随手拼出来的 URL。
ENDPOINTS = [
    ("商城列表", "/campus/product/list?pageNum=1&pageSize=20"),
    ("商城列表·分类筛选", "/campus/product/list?pageNum=1&pageSize=20&categoryId=3&status=1"),
    ("商品详情", "/campus/product/1"),
    ("我的订单", "/campus/order/list?pageNum=1&pageSize=20"),
    ("订单概览聚合", "/campus/order/stats"),
    ("购物车列表", "/campus/cart/list"),
]


def login(base, username, password):
    req = urllib.request.Request(
        base + "/login",
        data=json.dumps({"username": username, "password": password}).encode(),
        method="POST",
    )
    req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=15) as r:
        body = json.loads(r.read().decode())
    token = body.get("token")
    if not token:
        raise SystemExit("登录失败：%s" % body)
    return token


def percentile(sorted_vals, p):
    if not sorted_vals:
        return 0.0
    if len(sorted_vals) == 1:
        return sorted_vals[0]
    k = (len(sorted_vals) - 1) * p
    f, c = math.floor(k), math.ceil(k)
    if f == c:
        return sorted_vals[int(k)]
    return sorted_vals[f] * (c - k) + sorted_vals[c] * (k - f)


class Worker(threading.Thread):
    """每个线程持有一条 keep-alive 连接，避免把 TCP 握手开销算进服务端耗时。"""

    def __init__(self, host, port, token, deadline, sink, lock, idx_counter):
        super().__init__(daemon=True)
        self.host, self.port = host, port
        self.token = token
        self.deadline = deadline
        self.sink, self.lock, self.idx_counter = sink, lock, idx_counter
        self.conn = None
        self.requests = 0
        self.errors = 0

    def _conn(self):
        if self.conn is None:
            self.conn = http.client.HTTPConnection(self.host, self.port, timeout=15)
        return self.conn

    def run(self):
        local = []
        i = 0
        while time.time() < self.deadline:
            name, path = ENDPOINTS[i % len(ENDPOINTS)]
            i += 1
            t0 = time.perf_counter()
            status = 0
            try:
                c = self._conn()
                c.request("GET", path, headers={"Authorization": "Bearer " + self.token})
                resp = c.getresponse()
                resp.read()
                status = resp.status
            except Exception:
                # 连接可能已被服务端关闭，丢弃后重连一次
                try:
                    if self.conn:
                        self.conn.close()
                except Exception:
                    pass
                self.conn = None
                status = -1
            dt_ms = (time.perf_counter() - t0) * 1000.0
            ok = 200 <= status < 300
            if not ok:
                self.errors += 1
            local.append((dt_ms, ok, name))
            self.requests += 1
        with self.lock:
            self.sink.extend(local)
            self.idx_counter[0] += self.requests


def run_level(base, token, concurrency, duration):
    parsed = urllib.parse.urlparse(base)
    host = parsed.hostname
    port = parsed.port or 80
    sink, lock, counter = [], threading.Lock(), [0]
    deadline = time.time() + duration
    workers = [Worker(host, port, token, deadline, sink, lock, counter)
               for _ in range(concurrency)]
    t_start = time.time()
    for w in workers:
        w.start()
    for w in workers:
        w.join()
    elapsed = time.time() - t_start

    lat = sorted(x[0] for x in sink)
    ok_cnt = sum(1 for x in sink if x[1])
    total = len(sink)
    per_ep = {}
    for dt, ok, name in sink:
        per_ep.setdefault(name, []).append(dt)
    summary = {
        "concurrency": concurrency,
        "duration_s": round(elapsed, 2),
        "requests": total,
        "qps": round(total / elapsed, 1) if elapsed > 0 else 0,
        "errors": total - ok_cnt,
        "error_rate_pct": round((total - ok_cnt) / total * 100, 2) if total else 0,
        "p50_ms": round(percentile(lat, 0.50), 1),
        "p95_ms": round(percentile(lat, 0.95), 1),
        "p99_ms": round(percentile(lat, 0.99), 1),
        "mean_ms": round(statistics.fmean(lat), 1) if lat else 0,
        "max_ms": round(max(lat), 1) if lat else 0,
        "per_endpoint_p50": {k: round(percentile(sorted(v), 0.50), 1) for k, v in per_ep.items()},
        "per_endpoint_p95": {k: round(percentile(sorted(v), 0.95), 1) for k, v in per_ep.items()},
    }
    return summary


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default=os.environ.get("CAMPUS_BASE_URL", "http://localhost:8080"))
    ap.add_argument("--user", default="ry")
    ap.add_argument("--password", default="admin123")
    ap.add_argument("--concurrency", default="1,10,40")
    ap.add_argument("--duration", type=float, default=10.0)
    ap.add_argument("--out", default=os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                  "perf-http-result.json"))
    args = ap.parse_args()

    print("== 登录获取 token（账号 %s）==" % args.user)
    token = login(args.url, args.user, args.password)
    print("登录成功")

    # 预热：先用少量请求把 JIT / 连接池 / MyBatis 语句缓存跑到稳态，
    # 否则第一档低频并发的 p50 会被冷启动污染，看起来像"低并发反而更慢"。
    print("== 预热 2s ==")
    run_level(args.url, token, 4, 2.0)

    levels = [int(x) for x in args.concurrency.split(",") if x.strip()]
    results = []
    for level in levels:
        print("== 并发 %d，持续 %.1fs ==" % (level, args.duration))
        results.append(run_level(args.url, token, level, args.duration))

    print("\n| 并发 | 请求数 | QPS | p50(ms) | p95(ms) | p99(ms) | 均值(ms) | 最大(ms) | 错误率 |")
    print("|---|---|---|---|---|---|---|---|---|")
    for r in results:
        print("| %d | %d | %s | %s | %s | %s | %s | %s | %s%% |" % (
            r["concurrency"], r["requests"], r["qps"], r["p50_ms"], r["p95_ms"],
            r["p99_ms"], r["mean_ms"], r["max_ms"], r["error_rate_pct"]))

    print("\n== 单接口分位（并发 %d 档）==" % results[-1]["concurrency"])
    for name in [e[0] for e in ENDPOINTS]:
        p50 = results[-1]["per_endpoint_p50"].get(name)
        p95 = results[-1]["per_endpoint_p95"].get(name)
        if p50 is not None:
            print("  %-18s p50=%-8s p95=%s" % (name, p50, p95))

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump({"base_url": args.url, "endpoints": [e[1] for e in ENDPOINTS],
                   "duration_per_level_s": args.duration, "results": results},
                  f, ensure_ascii=False, indent=2)
    print("\n原始结果已写入 %s" % args.out)


if __name__ == "__main__":
    main()
