#!/usr/bin/env bash
# ============================================================
# 迁移一致性核对：比较"参照库"（全量脚本导入）与"迁移库"（Flyway 建库）的逐表行数。
#
# 用法：
#   compare_migration.sh <参照库> <迁移库> [V2 新增表 ...]
#
# 例：
#   compare_migration.sh campus_tao_ref campus_tao_flyway \
#       campus_address campus_favorite campus_search_history campus_message
#
# 可调环境变量：
#   EXPECT_DELTA   "表名=期望增量 ..."  迁移脚本预期写入的种子数据（默认见下）
#   VOLATILE       "表名 ..."           运行期写入的表，不参与一致性判定（默认见下）
#   STRICT=1       忽略上述豁免，要求逐表完全相等（用于 CI 纯迁移核对）
#
# 退出码：0 = 一致；1 = 存在非预期差异
# ============================================================
set -uo pipefail

REF="${1:?用法: compare_migration.sh <参照库> <迁移库> [V2 新增表...]}"
NEW="${2:?缺少迁移库名}"
shift 2
ALLOW_NEW=("$@")

# V2 写入的菜单种子：3 个页面菜单(2011~2013) + 6 个按钮权限(2115~2120) = 9
EXPECT_DELTA="${EXPECT_DELTA:-sys_menu=9 sys_role_menu=9}"
# 运行期由应用写入的表（应用启动/调度会自然产生数据，与迁移无关）
VOLATILE="${VOLATILE:-sys_job_log sys_logininfor sys_oper_log sys_notice_read qrtz_fired_triggers qrtz_scheduler_state qrtz_simple_triggers qrtz_triggers}"
[ "${STRICT:-0}" = "1" ] && { EXPECT_DELTA=""; VOLATILE=""; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 本地演示环境自带 MySQL；CI 里走服务容器提供的 mysql 客户端
BUNDLED_MYSQL="$SCRIPT_DIR/../../../../mysql-8.0.40/bin"
[ -d "$BUNDLED_MYSQL" ] && export PATH="$BUNDLED_MYSQL:$PATH"
if [ -n "${MYSQL_BIN:-}" ]; then :; elif [ -x "$BUNDLED_MYSQL/mysql" ]; then MYSQL_BIN="$BUNDLED_MYSQL/mysql"; else MYSQL_BIN="mysql"; fi

DBHOST="${DB_HOST:-127.0.0.1}"
DBPORT="${DB_PORT:-3306}"
DBUSER="${DB_USERNAME:-root}"
DBPASS="${DB_PASSWORD:-123456}"
MYSQL=("$MYSQL_BIN" -h"$DBHOST" -P"$DBPORT" -u"$DBUSER" -p"$DBPASS" -N -B --protocol=TCP)

# 注意：Windows 版 mysql 客户端输出 CRLF，必须剔除 \r，否则表名比对会全部失配
q() { "${MYSQL[@]}" -e "$1" 2>/dev/null | tr -d '\r'; }
tables() { q "select table_name from information_schema.tables where table_schema='$1' and table_name<>'flyway_schema_history' order by table_name;"; }
rows() { q "select count(*) from \`$1\`.\`$2\`;"; }
in_list() { local n="$1"; shift; for x in $*; do [ "$x" = "$n" ] && return 0; done; return 1; }
delta_of() { local n="$1"; for kv in $EXPECT_DELTA; do [ "${kv%%=*}" = "$n" ] && { echo "${kv#*=}"; return; }; done; echo ""; }

mapfile -t REF_T < <(tables "$REF")
mapfile -t NEW_T < <(tables "$NEW")

printf "%-32s %10s %10s  %s\n" "表名" "参照库" "迁移库" "说明"
printf -- "----------------------------------------------------------------------------\n"

FAIL=0
for t in "${REF_T[@]}"; do
  r=$(rows "$REF" "$t"); n=$(rows "$NEW" "$t")
  if [ -z "$n" ]; then
    printf "%-32s %10s %10s  %s\n" "$t" "$r" "-" "<<< 迁移库缺表"; FAIL=1; continue
  fi
  note=""
  if [ "$n" != "$r" ]; then
    exp="$(delta_of "$t")"
    if in_list "$t" $VOLATILE; then
      note="运行期写入，豁免（+$((n - r))）"
    elif [ -n "$exp" ] && [ $((n - r)) = "$exp" ]; then
      note="迁移种子，符合预期（+$exp）"
    else
      note="<<< 非预期差异"; FAIL=1
    fi
  fi
  printf "%-32s %10s %10s  %s\n" "$t" "$r" "$n" "$note"
done

for t in "${NEW_T[@]}"; do
  in_list "$t" "${REF_T[@]}" && continue
  n=$(rows "$NEW" "$t")
  if in_list "$t" "${ALLOW_NEW[@]:-}"; then
    printf "%-32s %10s %10s  %s\n" "$t" "-" "$n" "迁移新增表，预期"
  else
    printf "%-32s %10s %10s  %s\n" "$t" "-" "$n" "<<< 非预期新增表"; FAIL=1
  fi
done

printf -- "----------------------------------------------------------------------------\n"
echo "参照库表数: ${#REF_T[@]}    迁移库表数: ${#NEW_T[@]}"
if [ "$FAIL" = 0 ]; then
  echo "结论：逐表行数一致 ✓（差异项均已标注豁免理由）"
else
  echo "结论：存在非预期差异 ✗"
fi
exit "$FAIL"
