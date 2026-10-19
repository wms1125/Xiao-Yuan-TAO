#!/usr/bin/env bash
# ============================================================
# Flyway 迁移端到端验证：空库迁移 → 逐表行数核对 → 回滚 → 再迁移 → 再核对
#
# 用法：
#   DB_USERNAME=root DB_PASSWORD=123456 verify_flyway.sh
#
# 依赖：mysql 客户端、mvn（用 -N flyway:* 直接跑插件，不启动应用）
#
# 环境变量：
#   REF_DB   参照库名（默认 campus_tao_ref）——由全量脚本 campus_tao_full.sql 导入
#   TEST_DB  迁移库名（默认 campus_tao_flyway）
#
# 退出码：0 = 全部通过；非 0 = 有环节失败
# ============================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MIGRATION_DIR="$REPO_ROOT/ruoyi-admin/src/main/resources/db/migration"
FULL_SQL="${FULL_SQL:-$REPO_ROOT/../sql/campus_tao_full.sql}"

# 迁移数量与版本号**从迁移目录推导**，不写死。
# 曾经写死 "Successfully applied 5 migrations" 和 "1,2,3,4,5"，
# 每加一个 V* 这个门禁就假红一次，最后没人敢信它——推导才是可持续的。
MIG_COUNT=$(find "$MIGRATION_DIR" -maxdepth 1 -name 'V*.sql' | wc -l | tr -d ' ')
MIG_VERSIONS=$(find "$MIGRATION_DIR" -maxdepth 1 -name 'V*.sql' -printf '%f\n' \
  | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | paste -sd, -)
[ "$MIG_COUNT" -gt 0 ] || { echo "未在 $MIGRATION_DIR 找到任何迁移"; exit 1; }

REF_DB="${REF_DB:-campus_tao_ref}"
TEST_DB="${TEST_DB:-campus_tao_flyway}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
: "${DB_USERNAME:?请设置 DB_USERNAME}"
: "${DB_PASSWORD:?请设置 DB_PASSWORD}"

BUNDLED_MYSQL="$SCRIPT_DIR/../../../../mysql-8.0.40/bin"
[ -d "$BUNDLED_MYSQL" ] && export PATH="$BUNDLED_MYSQL:$PATH"
MYSQL=("${MYSQL_BIN:-mysql}" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" -p"$DB_PASSWORD" --protocol=TCP)

JDBC="jdbc:mysql://$DB_HOST:$DB_PORT/$TEST_DB?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=GMT%2B8"

sql()  { "${MYSQL[@]}" -N -B -e "$1" 2>/dev/null | tr -d '\r'; }
tsql() { "${MYSQL[@]}" -N -B -e "$1" "$TEST_DB" 2>/dev/null | tr -d '\r'; }
# -N = 非递归：插件声明在根 pom，避免解析子模块（ruoyi-common 等未安装到本地仓库）
flyway() {
  ( cd "$REPO_ROOT" && mvn -B -N "flyway:$1" \
      "-Dflyway.url=$JDBC" "-Dflyway.user=$DB_USERNAME" "-Dflyway.password=$DB_PASSWORD" 2>&1 )
}

PASS=0; FAIL=0
ok()   { echo "  [PASS] $1"; PASS=$((PASS+1)); }
bad()  { echo "  [FAIL] $1"; FAIL=$((FAIL+1)); }

echo "============================================================"
echo " Flyway 迁移端到端验证"
echo "   参照库: $REF_DB    迁移库: $TEST_DB"
echo "============================================================"

# ---------- 0. 准备参照库（全量脚本导入） ----------
echo
echo "[0/5] 准备参照库 $REF_DB"
if [ ! -f "$FULL_SQL" ]; then
  echo "  [SKIP] 未找到全量脚本 $FULL_SQL，跳过行数核对环节"
  HAVE_REF=0
else
  sql "DROP DATABASE IF EXISTS $REF_DB; CREATE DATABASE $REF_DB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
  grep -v "^CREATE DATABASE\|^USE \|^-- Current Database" "$FULL_SQL" | "${MYSQL[@]}" "$REF_DB" 2>/dev/null
  n=$(sql "select count(*) from information_schema.tables where table_schema='$REF_DB';")
  if [ "$n" = "43" ]; then ok "参照库建库完成（43 张表）"; else bad "参照库表数异常：$n（期望 43）"; fi
  HAVE_REF=1
fi

# ---------- 1. 空库迁移 ----------
echo
echo "[1/5] 空库执行迁移（共 $MIG_COUNT 个：$MIG_VERSIONS）"
sql "DROP DATABASE IF EXISTS $TEST_DB; CREATE DATABASE $TEST_DB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
out=$(flyway migrate)
if echo "$out" | grep -q "Successfully applied $MIG_COUNT migrations"; then
  ok "迁移成功：Successfully applied $MIG_COUNT migrations"
else
  bad "迁移未按预期完成"; echo "$out" | tail -8 | sed 's/^/      /'
fi
v=$(tsql "select group_concat(version order by installed_rank) from flyway_schema_history where success=1;")
LAST_VER=${MIG_VERSIONS##*,}
[ "$v" = "$MIG_VERSIONS" ] && ok "flyway_schema_history 记录 V1~V$LAST_VER（共 $MIG_COUNT 个）" || bad "版本记录异常：$v（期望 $MIG_VERSIONS）"
t=$(sql "select count(*) from information_schema.tables where table_schema='$TEST_DB';")
[ "$t" = "49" ] && ok "迁移后表数 49（43 基线 + 5 新增 + 1 版本表）" || bad "迁移后表数异常：$t（期望 49）"
i=$(sql "select count(*) from information_schema.statistics where table_schema='$TEST_DB' and seq_in_index=1 and ((table_name='campus_product' and index_name in ('idx_status_time','idx_seller_time','idx_category_status_time')) or (table_name='campus_order' and index_name='idx_user_time'));")
[ "$i" = "4" ] && ok "V4 复合索引已生效（4/4）" || bad "V4 索引缺失：$i（期望 4）"
c=$(sql "select count(*) from information_schema.tables where table_schema='$TEST_DB' and table_name like 'campus\\_%' and table_collation <> 'utf8mb4_0900_ai_ci';")
[ "$c" = "0" ] && ok "V5 排序规则已统一（0 张非 0900 校园表）" || bad "仍有 $c 张校园表排序规则不一致"
m=$(sql "select count(*) from information_schema.statistics where table_schema='$TEST_DB' and table_name='campus_message' and index_name='idx_from_user' and seq_in_index=1;")
[ "$m" = "1" ] && ok "V6 idx_from_user 已生效（UNION ALL 分支的 from 侧索引）" || bad "V6 idx_from_user 缺失：$m"
n=$(sql "select count(*) from information_schema.statistics where table_schema='$TEST_DB' and table_name='campus_notice' and index_name='idx_user_read_time' and seq_in_index=1;")
[ "$n" = "1" ] && ok "V6 idx_user_read_time 已生效（通知中心排序索引）" || bad "V6 idx_user_read_time 缺失：$n"
mn=$(tsql "select count(*) from sys_menu where parent_id=2000 and path='notice' and component='campus/notice/index';")
[ "$mn" = "1" ] && ok "V6 通知中心菜单已注册" || bad "V6 菜单缺失：$mn"
o=$(sql "select count(*) from information_schema.statistics where table_schema='$TEST_DB' and table_name='campus_order' and seq_in_index=1 and index_name in ('idx_status_time','idx_status_ship');")
[ "$o" = "2" ] && ok "V7 定时任务扫描索引已生效（2/2）" || bad "V7 索引缺失：$o（期望 2）"
q=$(tsql "select count(*) from information_schema.columns where table_schema=DATABASE() and table_name='campus_order' and column_name in ('pay_type','seller_id','pay_proof_url');")
[ "$q" = "3" ] && ok "V8 直付列已生效（pay_type/seller_id/pay_proof_url 3/3）" || bad "V8 直付列缺失：$q（期望 3）"
p=$(tsql "select count(*) from information_schema.tables where table_schema=DATABASE() and table_name='campus_user_pay_qr';")
[ "$p" = "1" ] && ok "V8 收款码表已建立" || bad "V8 收款码表缺失"
v9t=$(tsql "select count(*) from information_schema.tables where table_schema=DATABASE() and table_name='campus_payment_record';")
[ "$v9t" = "1" ] && ok "V9 支付流水表已建立" || bad "V9 支付流水表缺失"
v9c=$(tsql "select count(*) from information_schema.columns where table_schema=DATABASE() and table_name='campus_order' and column_name in ('pay_confirm_time','pay_confirm_by');")
[ "$v9c" = "2" ] && ok "V9 直付确认留痕列已生效（pay_confirm_time/pay_confirm_by 2/2）" || bad "V9 直付确认留痕列缺失：$v9c（期望 2）"
v9a=$(tsql "select count(*) from information_schema.columns where table_schema=DATABASE() and table_name='campus_aftersale' and column_name='order_status_before';")
[ "$v9a" = "1" ] && ok "V9 售后原状态列 order_status_before 已生效" || bad "V9 order_status_before 缺失：$v9a"
v9i=$(tsql "select count(*) from information_schema.statistics where table_schema=DATABASE() and table_name='campus_order' and index_name='idx_seller_time' and seq_in_index=1;")
[ "$v9i" = "1" ] && ok "V9 卖家维度索引 campus_order.idx_seller_time 已生效" || bad "V9 idx_seller_time 缺失：$v9i"
v9j=$(tsql "select count(*) from sys_job where job_id in (100,101) and status='1';")
[ "$v9j" = "2" ] && ok "V9 校园淘定时任务已启用（100/101 status=1）" || bad "V9 定时任务未启用：$v9j（期望 2）"

# ---------- 2. 逐表行数核对 ----------
echo
echo "[2/5] 逐表行数核对（参照库 vs 迁移库）"
if [ "$HAVE_REF" = "1" ]; then
  if bash "$SCRIPT_DIR/compare_migration.sh" "$REF_DB" "$TEST_DB" \
      campus_address campus_favorite campus_search_history campus_message campus_payment_record > /tmp/cmp1.txt 2>&1; then
    ok "空库迁移后逐表行数与全量脚本一致"
  else
    bad "逐表行数存在非预期差异"; tail -20 /tmp/cmp1.txt | sed 's/^/      /'
  fi
else
  echo "  [SKIP] 无参照库"
fi

# ---------- 3. 回滚 ----------
echo
echo "[3/5] 回滚到空库（V$MIG_VERSIONS → V1）"
( cd "$REPO_ROOT/db/rollback" && DB_NAME="$TEST_DB" DB_HOST="$DB_HOST" DB_PORT="$DB_PORT" \
  DB_USERNAME="$DB_USERNAME" DB_PASSWORD="$DB_PASSWORD" bash rollback.sh 0 ) > /tmp/rb.txt 2>&1
rc=$?
[ "$rc" = "0" ] && ok "回滚脚本执行完成" || { bad "回滚脚本返回 $rc"; tail -10 /tmp/rb.txt | sed 's/^/      /'; }
left=$(sql "select group_concat(table_name) from information_schema.tables where table_schema='$TEST_DB';")
[ "$left" = "flyway_schema_history" ] && ok "业务表已全部删除（仅保留空的版本表）" || bad "回滚后残留表：$left"
h=$(tsql "select count(*) from flyway_schema_history;")
[ "$h" = "0" ] && ok "版本记录已清空" || bad "版本记录残留 $h 行"
i=$(sql "select count(*) from information_schema.statistics where table_schema='$TEST_DB' and seq_in_index=1 and ((table_name='campus_product' and index_name in ('idx_status_time','idx_seller_time','idx_category_status_time')) or (table_name='campus_order' and index_name in ('idx_user_time','idx_status_time','idx_status_ship','idx_seller_time')) or (table_name='campus_message' and index_name='idx_from_user') or (table_name='campus_notice' and index_name='idx_user_read_time'));")
[ "$i" = "0" ] && ok "回滚后 V4/V6/V7/V9 索引无残留" || bad "回滚后残留索引：$i"

# ---------- 4. 再迁移 ----------
echo
echo "[4/5] 回滚后再次迁移（验证可重放）"
out=$(flyway migrate)
if echo "$out" | grep -q "Successfully applied $MIG_COUNT migrations"; then
  ok "再次迁移成功"
else
  bad "再次迁移未按预期完成"; echo "$out" | tail -8 | sed 's/^/      /'
fi
t=$(sql "select count(*) from information_schema.tables where table_schema='$TEST_DB';")
[ "$t" = "49" ] && ok "表数恢复为 49" || bad "表数异常：$t（期望 49）"
i=$(sql "select count(*) from information_schema.statistics where table_schema='$TEST_DB' and seq_in_index=1 and ((table_name='campus_product' and index_name in ('idx_status_time','idx_seller_time','idx_category_status_time')) or (table_name='campus_order' and index_name in ('idx_user_time','idx_status_time','idx_status_ship','idx_seller_time')));")
[ "$i" = "7" ] && ok "重放后 V4+V7+V9 索引恢复（7/7）" || bad "重放后索引缺失：$i（期望 7）"

# ---------- 5. 再核对 ----------
echo
echo "[5/5] 再次逐表行数核对"
if [ "$HAVE_REF" = "1" ]; then
  if bash "$SCRIPT_DIR/compare_migration.sh" "$REF_DB" "$TEST_DB" \
      campus_address campus_favorite campus_search_history campus_message campus_payment_record > /tmp/cmp2.txt 2>&1; then
    ok "重放后逐表行数仍一致"
  else
    bad "重放后行数存在差异"; tail -20 /tmp/cmp2.txt | sed 's/^/      /'
  fi
else
  echo "  [SKIP] 无参照库"
fi

# ---------- 汇总 ----------
echo
echo "============================================================"
echo " 通过 $PASS 项，失败 $FAIL 项"
[ "$FAIL" = "0" ] && echo " 结论：Flyway 迁移与回滚验证全部通过 ✓" || echo " 结论：存在失败项 ✗"
echo "============================================================"
exit "$([ "$FAIL" = "0" ] && echo 0 || echo 1)"
