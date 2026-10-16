#!/usr/bin/env bash
# ============================================================
# 校园淘 · 数据库回滚脚本（Flyway Community 无 undo，用 U*.sql 补偿）
#
# 用法：
#   ./rollback.sh <目标版本>            # 回滚到该版本（0 = 清空到空库）
#   ./rollback.sh <目标版本> --dry-run  # 只打印将要执行的动作
#
# 例：
#   ./rollback.sh 1      # 撤销 V2，保留 V1 基线
#   ./rollback.sh 0      # 撤销全部
#
# 连接信息来自环境变量（与后端一致）：
#   DB_HOST(默认127.0.0.1) DB_PORT(默认3306) DB_NAME(默认campus_tao)
#   DB_USERNAME(必填) DB_PASSWORD(必填)
#
# 原理：Flyway 社区版没有 undo 命令，因此回滚 = 执行 db/rollback/U<n>__*.sql
#       补偿脚本 + 删除 flyway_schema_history 中对应版本记录（否则再次
#       migrate 会认为"已应用"而跳过，导致库结构与版本记录不一致）。
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 迁移脚本实际位于 ruoyi-admin 的 resources 下（需随 jar 打包），此处仅用于定位版本清单
MIG_DIR="${MIG_DIR:-$(cd "$SCRIPT_DIR/../../ruoyi-admin/src/main/resources/db/migration" && pwd)}"

TARGET="${1:-}"
DRY_RUN="${2:-}"

if [ -z "$TARGET" ]; then
  echo "用法: $0 <目标版本> [--dry-run]" >&2
  echo "  目标版本 0 表示回滚到空库" >&2
  exit 2
fi

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-campus_tao}"
: "${DB_USERNAME:?请设置环境变量 DB_USERNAME}"
: "${DB_PASSWORD:?请设置环境变量 DB_PASSWORD}"

# 定位 mysql 客户端：优先环境变量 MYSQL_BIN，其次项目内置 MySQL，最后 PATH
if [ -z "${MYSQL_BIN:-}" ]; then
  for cand in \
    "$SCRIPT_DIR/../../../../mysql-8.0.40/bin/mysql" \
    "$SCRIPT_DIR/../../../../../mysql-8.0.40/bin/mysql" ; do
    if [ -x "$cand" ]; then MYSQL_BIN="$cand"; break; fi
  done
fi
if [ -z "${MYSQL_BIN:-}" ] || { [ "${MYSQL_BIN}" != "mysql" ] && [ ! -x "$MYSQL_BIN" ]; }; then
  if command -v mysql >/dev/null 2>&1; then
    MYSQL_BIN="mysql"
  else
    echo "[错误] 找不到 mysql 客户端。请把 mysql 加入 PATH，或设置 MYSQL_BIN=/path/to/mysql" >&2
    exit 1
  fi
fi
echo "== mysql 客户端: $MYSQL_BIN =="

MYSQL=("$MYSQL_BIN" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" -p"$DB_PASSWORD" --protocol=TCP)

# Windows 版 mysql 输出 CRLF，需剔除 \r
q() { "${MYSQL[@]}" -N -B -e "$1" "$DB_NAME" 2>/dev/null | tr -d '\r'; }
run() { "${MYSQL[@]}" -e "$1" "$DB_NAME" >/dev/null; }

echo "== 目标库: $DB_NAME@$DB_HOST:$DB_PORT =="

# 1. 读取当前已应用的最大版本
CURRENT="$(q "SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL;" || echo 0)"
echo "当前版本: $CURRENT"
echo "目标版本: $TARGET"

if [ "$CURRENT" -le "$TARGET" ]; then
  echo "无需回滚（当前版本不高于目标版本）。"
  exit 0
fi

# 2. 从 CURRENT 倒序回滚到 TARGET+1
for (( v=CURRENT; v>TARGET; v-- )); do
  SCRIPT="$(ls "$SCRIPT_DIR"/U${v}__*.sql 2>/dev/null | head -1 || true)"
  if [ -z "$SCRIPT" ]; then
    echo "[错误] 找不到版本 $v 的回滚脚本 U${v}__*.sql，中止。" >&2
    exit 1
  fi
  echo "--------------------------------------------"
  echo "回滚 V$v  <-  $(basename "$SCRIPT")"
  if [ "$DRY_RUN" = "--dry-run" ]; then
    echo "  (dry-run) 将执行 $(basename "$SCRIPT") 并删除 history 中 version=$v"
    continue
  fi
  # 补偿脚本可能包含幂等性告警（如 DROP TABLE IF EXISTS 不存在的表）而返回非零，
  # 因此这里显式捕获退出码并告警，但**绝不中断**——否则会跳过版本记录清理，
  # 造成"库结构已回滚、版本记录仍显示已应用"的不一致状态。
  err="$("${MYSQL[@]}" "$DB_NAME" < "$SCRIPT" 2>&1 >/dev/null)" && rc=0 || rc=$?
  if [ "$rc" -ne 0 ]; then
    echo "  [警告] $(basename "$SCRIPT") 返回非零，请确认以下输出是否影响回滚结果："
    printf '%s\n' "$err" | grep -v "Using a password" | head -5 | sed 's/^/      /'
  fi
  run "DELETE FROM flyway_schema_history WHERE version='$v';"
  echo "  ✓ V$v 已回滚（已清理版本记录）"
done

echo "============================================"
if [ "$DRY_RUN" = "--dry-run" ]; then
  echo "dry-run 结束，未做任何变更。"
else
  echo "回滚完成，当前版本: $(q "SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL;")"
fi
