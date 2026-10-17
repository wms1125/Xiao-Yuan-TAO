-- U6：回滚 V6（站内信分页索引 + 通知中心菜单与索引）
--
-- 警告：回滚这条会把站内信会话列表打回全索引扫描的慢路径
--       （idx_from_user 没了，UNION ALL 的 from 分支退化成全表扫），
--       通知中心页面也随之失去菜单入口。仅在确认不需要这两项时执行。
--
-- 与 V6 同样的理由：本脚本也要可重入。MySQL 没有 DROP INDEX IF EXISTS，
-- 若索引已不存在（回滚被中断后重跑、或手工删过），直接 DROP 会报错并中断。
-- 故一律走 information_schema 判存 + 动态 SQL。

-- ---------- 1. 通知中心菜单 ----------
DELETE FROM `sys_role_menu`
WHERE `menu_id` IN (SELECT `menu_id` FROM `sys_menu` WHERE `parent_id` = 2000 AND `path` = 'notice');
DELETE FROM `sys_menu` WHERE `parent_id` = 2000 AND `path` = 'notice' AND `component` = 'campus/notice/index';

-- ---------- 2. campus_notice：排序索引 ----------
SET @drop_notice_idx = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'campus_notice' AND index_name = 'idx_user_read_time') > 0,
    'ALTER TABLE `campus_notice` DROP INDEX `idx_user_read_time`',
    'SELECT ''idx_user_read_time 不存在，跳过'' AS skipped'));
PREPARE stmt FROM @drop_notice_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. campus_message：发送方索引 ----------
SET @drop_msg_idx = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'campus_message' AND index_name = 'idx_from_user') > 0,
    'ALTER TABLE `campus_message` DROP INDEX `idx_from_user`',
    'SELECT ''idx_from_user 不存在，跳过'' AS skipped'));
PREPARE stmt FROM @drop_msg_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;
