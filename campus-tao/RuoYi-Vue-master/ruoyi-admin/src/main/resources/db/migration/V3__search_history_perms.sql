-- ============================================================
-- 校园淘 V3 · 搜索历史权限（v1.0.18）
--
-- 背景：V2 建好了 campus_favorite / campus_search_history / campus_message 三张表
--       与菜单 2011~2013、权限 2115~2120。v1.0.17 落地了收货地址；
--       v1.0.18 补齐收藏、站内信、搜索历史三项功能。
--
--       收藏与消息的权限（2118/2119/2120）V2 已建；**搜索历史**因无独立菜单
--       （能力挂在商城页搜索框上），V2 未建对应权限，故本迁移补齐：
--       campus:searchhistory:list / add / remove，挂在校园商城（2008）之下。
--
-- 回滚脚本：db/rollback/U3__search_history_perms.sql
--
-- 幂等性：INSERT IGNORE 保证重复执行不报错。
-- 目标库由启动连接串决定，脚本内不出现 CREATE DATABASE / USE。
-- ============================================================

-- ---------- 1. 搜索历史权限（F 型按钮权限，不渲染在侧边栏） ----------
INSERT IGNORE INTO `sys_menu`
  (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`remark`)
VALUES
  (2121,'搜索历史查询',2008,1,'','','',1,0,'F','0','0','campus:searchhistory:list','#','admin',NOW(),'查看最近搜索（商城页搜索框下拉）'),
  (2122,'记录搜索',2008,2,'','','',1,0,'F','0','0','campus:searchhistory:add','#','admin',NOW(),'记录一次搜索关键词'),
  (2123,'删除搜索历史',2008,3,'','','',1,0,'F','0','0','campus:searchhistory:remove','#','admin',NOW(),'删除单条或清空搜索历史');

-- ---------- 2. 授予普通角色（role_id=2，与既有 campus 权限一致） ----------
INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES (2,2121),(2,2122),(2,2123);
