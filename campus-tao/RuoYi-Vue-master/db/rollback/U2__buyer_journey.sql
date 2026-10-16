-- ============================================================
-- 校园淘 U2 · V2（买家侧旅程）的回滚脚本
--
-- 影响：删除 V2 建立的 4 张表、撤销 campus_order 的 address_snapshot 列、
--       清理 V2 写入的菜单与授权。**不可逆，请先备份。**
-- ============================================================

-- 1. 删除新增表（先子后主）
DROP TABLE IF EXISTS `campus_message`;
DROP TABLE IF EXISTS `campus_search_history`;
DROP TABLE IF EXISTS `campus_favorite`;
DROP TABLE IF EXISTS `campus_address`;

-- 2. 撤销订单表的地址快照列
ALTER TABLE `campus_order` DROP COLUMN `address_snapshot`;

-- 3. 清理菜单与授权（2011~2013 页面菜单 + 2115~2120 按钮权限）
DELETE FROM `sys_role_menu` WHERE `menu_id` BETWEEN 2011 AND 2013 OR `menu_id` BETWEEN 2115 AND 2120;
DELETE FROM `sys_menu`      WHERE `menu_id` BETWEEN 2011 AND 2013 OR `menu_id` BETWEEN 2115 AND 2120;
