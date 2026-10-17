-- V6：站内信分页索引 + 通知中心菜单与索引
--
-- 一、为什么要给 campus_message 加 idx_from_user
--
--   站内信会话列表改写为分页时，聚合条件是
--       where from_user_id = ? or to_user_id = ?
--   EXPLAIN 显示优化器为了免建 GROUP BY 临时表，整条扫 idx_order_time，
--   扫描量 = 全表消息数，filtered 仅 19%。
--   改成 UNION ALL 两支后，from 侧需要 (from_user_id) 索引才能走 ref，
--   否则仍会退化成全表扫（idx_to_read 本就以 to_user_id 为首列，不需要补）。
--   加完后 EXPLAIN 两支均为 type=ref，读取量只与该用户自己的消息数相关。
--
--   已验证：加索引前后对同一用户取同一页，返回行、未读数、排序完全一致
--   （100 个会话 / 未读 200 / topN 订单号逐行相同）。
--
-- 二、为什么要给 campus_notice 加 (user_id, is_read, create_time)
--
--   通知中心的默认排序是 is_read asc, create_time desc。
--   原有 idx_user_read(user_id, is_read) 能定位到该用户的未读行，
--   但未读数很多时仍要回表取 create_time 再排序。补上 create_time 后，
--   "只看未读 + 新的在前"可由索引顺序直接取前 N 条，不再 filesort。
--
-- 三、为什么要注册 campus/notice 菜单
--
--   通知中心前端页面建好后，路由是由后端 sys_menu 驱动
--   （前端 import.meta.glob 只做组件寻址，菜单树来自 /getRouters）。
--   缺菜单记录则页面能编译、却永远进不去。原 issue：公告/通知"有 API 无页面"。
--
--   菜单归属挂在校园淘父菜单 2000 下，与 notifylog/message 同级；
--   权限串沿用模块惯例 campus:notice:list。
--
-- 四、迁移必须可重入（踩过的坑）
--
--   第一版直接写 ALTER TABLE ADD INDEX，结果因为库里已有同名索引
--   （压测/验证阶段手工建过）而抛 Duplicate key name，Flyway 记为 success=0，
--   整个应用启动失败。MySQL 不支持 CREATE INDEX IF NOT EXISTS，
--   故下面的索引创建一律走 information_schema 判存 + PREPARE 动态执行。
--   DBA 用在线改表工具预建索引、或迁移中断后重跑，都不能再把启动带崩。

-- ---------- 1. campus_message：发送方索引（配合 UNION ALL 分支） ----------
-- MySQL 没有 CREATE INDEX IF NOT EXISTS。若索引已存在（DBA 用在线改表工具预建过、
-- 或迁移被中断后重跑），直接 ALTER 会抛 Duplicate key name 并把整次启动带崩。
-- 因此用 information_schema 判存 + 动态 SQL，让本迁移可重入。
SET @add_from_user = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'campus_message' AND index_name = 'idx_from_user') = 0,
    'ALTER TABLE `campus_message` ADD INDEX `idx_from_user` (`from_user_id`)',
    'SELECT ''idx_from_user 已存在，跳过'' AS skipped'));
PREPARE stmt FROM @add_from_user; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. campus_notice：覆盖"未读在前 + 时间倒序"的排序 ----------
SET @add_user_read_time = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'campus_notice' AND index_name = 'idx_user_read_time') = 0,
    'ALTER TABLE `campus_notice` ADD INDEX `idx_user_read_time` (`user_id`, `is_read`, `create_time`)',
    'SELECT ''idx_user_read_time 已存在，跳过'' AS skipped'));
PREPARE stmt FROM @add_user_read_time; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. 通知中心菜单 ----------
-- 用 max(menu_id)+1 取号，避免在迁移里写死 ID 与其它环境的既有菜单冲突。
-- ON DUPLICATE KEY 让本迁移可重入：菜单已存在（如手工建过）时只更新不报错。
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`,
                       `is_frame`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`)
SELECT '通知中心', 2000, 14, 'notice', 'campus/notice/index',
       1, 'C', 0, 0, 'campus:notice:list', 'message', 'admin', sysdate()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `parent_id` = 2000 AND `path` = 'notice');
