-- ============================================================
-- 校园淘 增量迁移脚本 v1.0.15
-- 面向「已建库」环境；全新环境请直接使用 campus_tao_full.sql（唯一建库源）。
--
-- 本次新增（买家侧旅程与评价管理）：
--   1) sys_menu 2008  「校园商城」  商品浏览页（买家视角，全部登录用户可见）
--   2) sys_menu 2009  「商品详情」  隐藏菜单，仅用于注册 /campus/mall-detail 路由
--   3) sys_menu 2010  「评价管理」  卖家查看本人名下商品收到的评价
--   4) sys_role_menu  把上述菜单授予普通角色（role_id=2）
--
-- 说明：2009 为 hidden 菜单（visible='1'）——详情页需从商品卡片跳转进入，
--       不应出现在侧边栏，但 RuoYi 的路由由 sys_menu 驱动，必须登记才能访问。
--       2008/2009 不设 perms：浏览商品与查看评价详情属所有登录用户的基础能力。
--
-- 执行方式（示例）：
--   mysql -uroot -p123456 campus_tao < migration-v1.0.15.sql
-- ============================================================

USE `campus_tao`;

-- ---------- 1~3. 新增菜单 ----------
INSERT IGNORE INTO `sys_menu`
  (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`)
VALUES
  (2008,'校园商城',2000,8,'mall','campus/mall/index','','',1,0,'C','0','0','','shopping','admin',NOW(),'',NULL,'买家浏览在售二手商品'),
  (2009,'商品详情',2000,9,'mall-detail','campus/mall/detail','','',1,0,'C','1','0','','','admin',NOW(),'',NULL,'商品详情页（隐藏菜单，由商品卡片跳转进入）'),
  (2010,'评价管理',2000,10,'review','campus/review/index','','',1,0,'C','0','0','campus:review:list','star','admin',NOW(),'',NULL,'卖家查看本人名下商品收到的评价');

-- ---------- 4. 授予普通角色（role_id=2）----------
INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES
  (2,2008),(2,2009),(2,2010);
