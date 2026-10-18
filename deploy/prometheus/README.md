# 校园淘 · Prometheus 抓取与告警配置

## 一、应用侧已就绪的端点

| 端点 | 用途 | 访问控制 |
|---|---|---|
| `/actuator/health` | 存活/就绪探针 | 匿名；nginx 限内网 |
| `/actuator/info` | 版本信息（`info.app.version`） | 匿名 |
| `/actuator/prometheus` | 指标抓取 | 匿名；**nginx 限内网，公网 403** |

指标清单（自有业务指标）：

| 指标 | 类型 | 标签 | 含义 |
|---|---|---|---|
| `campus_payment_attempts_total` | counter | `channel` / `mode` / `result` | 支付尝试数与结果 |
| `campus_refund_attempts_total` | counter | `channel` / `mode` / `result` | 退款尝试数与结果 |
| `campus_payment_channel_available` | gauge | `channel` | 当前渠道是否可用（1/0） |

框架自带且已足够使用的：`http_server_requests_seconds_*`（QPS/延迟/状态码）、
`hikaricp_connections_*`（连接池）、`jvm_*`。

## 二、抓取配置

```yaml
# prometheus.yml
scrape_configs:
  - job_name: campus-tao
    metrics_path: /actuator/prometheus
    scrape_interval: 15s
    static_configs:
      # 若经 nginx 反代，须从内网地址访问（nginx 对 /actuator/prometheus 做了 allow/deny）
      - targets: ['10.0.0.12:8080']
```

规则文件：

```yaml
rule_files:
  - /etc/prometheus/rules/campus-tao-alerts.yml
```

## 三、验证抓取是否成功

```bash
# 1) 应用侧有输出（无需登录）
curl -s http://127.0.0.1:8080/actuator/prometheus | grep -E '^campus_'

# 2) Prometheus 侧目标为 UP
curl -s http://127.0.0.1:9090/api/v1/targets | grep -o '"health":"[a-z]*"'
```

## 四、日志侧：traceId 关联

`TraceIdFilter` 为每个请求生成（或沿用上游合法值）traceId，写入 MDC 并回写响应头
`X-Trace-Id`。日志 pattern 已含 `[%X{traceId:--}]`，因此：

```bash
# 按 traceId 取一次请求的全部日志
grep '3f2a9c1d4b5e4a7f8c9d0e1f2a3b4c5d' logs/*.log
```

前端报障时让用户提供响应头里的 `X-Trace-Id`，即可直接定位到那一次请求。

## 五、安全约束（重要）

`/actuator/prometheus` 在应用侧是**匿名**的（否则 Prometheus 抓不到），
因此安全边界必须由网络层提供：

- `deploy/nginx.conf` 已对该路径设 `allow 127.0.0.1 / 10.0.0.0/8; deny all;`
- 容器部署时不要把 8080 直接映射到公网；只暴露 nginx 的 80/443
- 指标会泄漏业务量与调用结构，属于内部数据
