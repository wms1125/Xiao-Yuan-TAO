# main 分支保护（配置即代码）

## 为什么需要

评估报告 v1.0.22 的 C4「人工把关与责任边界」只有 70 分，依据是：

> 无 PR、无 merge commit、无分支保护、无评审记录；AI 产出直接进 main，
> 人工把关只存在于对话过程，不可审计。

「人工把关」在此之前**只发生在对话里**——对话是易失的，三个月后没人能证明
某一行代码是谁在什么依据下同意合入的。分支保护把这一步变成仓库侧的**可审计事实**。

## 当前配置

见 `branch-protection.json`，要点：

| 项 | 值 | 理由 |
|---|---|---|
| 必需 PR | 是（`required_pull_request_reviews` 存在即为要求 PR） | 禁止直推，改动必须有一个可评论、可追溯的载体 |
| 必需检查 | CI 全部 6 个 job，`strict: true` | `strict` 要求分支与 main 同步后再合并，避免"绿的是旧代码" |
| 管理员同样受限 | `enforce_admins: true` | 若管理员可绕过，规则在第一次赶工时就会被绕过，等于没有 |
| 强推 / 删分支 | 禁止 | 保护历史可追溯性 |
| 会话未解决则不可合并 | `required_conversation_resolution: true` | 评论里的异议不能被静默忽略 |

## 已知取舍（如实记录）

- **`required_approving_review_count = 0`，不是 1**。报告写的是「1 人评审」，但本仓库
  目前只有 1 名维护者，GitHub **不允许自我批准**——设成 1 会让任何 PR 都无法合并，
  规则随即会被关掉。所以当前强制的是「必须走 PR + CI 全绿」，评审责任由
  `pull_request_template.md` 的自查清单承载。
  **一旦有第二名协作者，把该值改为 1 即可，其余配置不用动。**
- **必需检查按 job 名称匹配**。CI 里改了 job 的 `name:` 而没同步本文件，
  PR 会永久卡在"Expected — Waiting for status to be reported"。改 job 名时务必同步这里。

## 应用方式

```bash
# 应用（幂等，可重复执行）
gh api -X PUT repos/wms1125/Xiao-Yuan-TAO/branches/main/protection \
  --input deploy/github/branch-protection.json

# 核对
gh api repos/wms1125/Xiao-Yuan-TAO/branches/main/protection \
  --jq '{pr: (.required_pull_request_reviews != null), admins: .enforce_admins.enabled, checks: [.required_status_checks.contexts[]]}'
```

## 负向验证（强制动作）

保护是否真的生效，不能只看 GET 返回 200——要**试着违反它**：

```bash
# 以管理员身份直接往 main 提交一个文件，期望被拒绝
gh api -X PUT repos/wms1125/Xiao-Yuan-TAO/contents/protection-probe.txt \
  -f message="probe: 直推 main 应被拒绝" \
  -f content="cHJvYmU=" -f branch=main
# 期望：HTTP 422 / "Protected branch update failed"
```

返回 422 且提示 `Protected branch update failed` 才算生效；
若返回 201，说明 `enforce_admins` 没起作用，配置是假的。
