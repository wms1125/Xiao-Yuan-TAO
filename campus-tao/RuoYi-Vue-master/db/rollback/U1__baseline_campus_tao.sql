-- ============================================================
-- 校园淘 U1 · V1 基线的回滚脚本
-- 说明：Flyway Community 版不提供 `flyway undo`，回滚通过本目录脚本人工/脚本化执行。
--       执行方式见 db/rollback/README.md 与根目录 rollback.bat。
-- 影响：删除 V1 建立的全部 42 张表。**不可逆，请先备份。**
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `campus_message`;
DROP TABLE IF EXISTS `campus_search_history`;
DROP TABLE IF EXISTS `campus_favorite`;
DROP TABLE IF EXISTS `campus_address`;
DROP TABLE IF EXISTS `campus_account`;
DROP TABLE IF EXISTS `campus_aftersale`;
DROP TABLE IF EXISTS `campus_cart`;
DROP TABLE IF EXISTS `campus_category`;
DROP TABLE IF EXISTS `campus_logistics_trace`;
DROP TABLE IF EXISTS `campus_notice`;
DROP TABLE IF EXISTS `campus_notify_log`;
DROP TABLE IF EXISTS `campus_order`;
DROP TABLE IF EXISTS `campus_order_item`;
DROP TABLE IF EXISTS `campus_product`;
DROP TABLE IF EXISTS `campus_review`;
DROP TABLE IF EXISTS `gen_table`;
DROP TABLE IF EXISTS `gen_table_column`;
DROP TABLE IF EXISTS `qrtz_blob_triggers`;
DROP TABLE IF EXISTS `qrtz_calendars`;
DROP TABLE IF EXISTS `qrtz_cron_triggers`;
DROP TABLE IF EXISTS `qrtz_fired_triggers`;
DROP TABLE IF EXISTS `qrtz_job_details`;
DROP TABLE IF EXISTS `qrtz_locks`;
DROP TABLE IF EXISTS `qrtz_paused_trigger_grps`;
DROP TABLE IF EXISTS `qrtz_scheduler_state`;
DROP TABLE IF EXISTS `qrtz_simple_triggers`;
DROP TABLE IF EXISTS `qrtz_simprop_triggers`;
DROP TABLE IF EXISTS `qrtz_triggers`;
DROP TABLE IF EXISTS `sys_config`;
DROP TABLE IF EXISTS `sys_dept`;
DROP TABLE IF EXISTS `sys_dict_data`;
DROP TABLE IF EXISTS `sys_dict_type`;
DROP TABLE IF EXISTS `sys_job`;
DROP TABLE IF EXISTS `sys_job_log`;
DROP TABLE IF EXISTS `sys_logininfor`;
DROP TABLE IF EXISTS `sys_menu`;
DROP TABLE IF EXISTS `sys_notice`;
DROP TABLE IF EXISTS `sys_notice_read`;
DROP TABLE IF EXISTS `sys_oper_log`;
DROP TABLE IF EXISTS `sys_post`;
DROP TABLE IF EXISTS `sys_role`;
DROP TABLE IF EXISTS `sys_role_dept`;
DROP TABLE IF EXISTS `sys_role_menu`;
DROP TABLE IF EXISTS `sys_user`;
DROP TABLE IF EXISTS `sys_user_post`;
DROP TABLE IF EXISTS `sys_user_role`;

SET FOREIGN_KEY_CHECKS = 1;

-- 说明：V1 写入的校园淘菜单（menu_id 2000~2199）随 `sys_menu` / `sys_role_menu`
-- 两表一并 DROP，无需再单独 DELETE（在 DROP 之后再 DELETE 会因表不存在而报错，
-- 导致回滚工具误判失败、跳过版本记录清理）。
