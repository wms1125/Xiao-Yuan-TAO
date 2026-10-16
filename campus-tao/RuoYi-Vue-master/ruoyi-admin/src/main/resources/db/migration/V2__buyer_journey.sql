-- ============================================================
-- 校园淘 V2 · 买家侧旅程补全（收货地址 / 收藏 / 搜索历史 / 买卖沟通）
--
-- 背景：企业级评估指出买家侧旅程存在断点——无收货地址管理、无收藏/关注、
--       无搜索历史与推荐、无买卖双方 IM 沟通。本版本补齐这四类能力。
--
-- 回滚脚本：db/rollback/U2__buyer_journey.sql
-- ============================================================

-- ---------- 1. 收货地址 ----------
CREATE TABLE IF NOT EXISTS `campus_address` (
  `address_id` bigint NOT NULL AUTO_INCREMENT COMMENT '地址ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `receiver` varchar(32) NOT NULL COMMENT '收货人',
  `phone` varchar(20) NOT NULL COMMENT '联系电话',
  `region` varchar(100) NOT NULL DEFAULT '' COMMENT '所在地区（校区/宿舍楼）',
  `detail` varchar(200) NOT NULL DEFAULT '' COMMENT '详细地址',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '是否默认 0否 1是',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`address_id`),
  KEY `idx_user` (`user_id`,`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='收货地址（买家自维护）';

-- ---------- 2. 商品收藏 ----------
CREATE TABLE IF NOT EXISTS `campus_favorite` (
  `favorite_id` bigint NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`favorite_id`),
  UNIQUE KEY `uk_user_product` (`user_id`,`product_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品收藏（唯一约束防重复收藏）';

-- ---------- 3. 搜索历史 ----------
CREATE TABLE IF NOT EXISTS `campus_search_history` (
  `history_id` bigint NOT NULL AUTO_INCREMENT COMMENT '历史ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `keyword` varchar(50) NOT NULL COMMENT '搜索关键词',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '搜索时间',
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_user_keyword` (`user_id`,`keyword`),
  KEY `idx_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='搜索历史（同词重复只更新时间）';

-- ---------- 4. 买卖双方沟通消息 ----------
CREATE TABLE IF NOT EXISTS `campus_message` (
  `message_id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `order_no` varchar(32) NOT NULL COMMENT '所属订单号（会话以订单为上下文）',
  `from_user_id` bigint NOT NULL COMMENT '发送方用户ID',
  `to_user_id` bigint NOT NULL COMMENT '接收方用户ID',
  `content` varchar(500) NOT NULL COMMENT '消息内容',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '是否已读 0未读 1已读',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`message_id`),
  KEY `idx_order_time` (`order_no`,`create_time`),
  KEY `idx_to_read` (`to_user_id`,`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单上下文内的买卖双方沟通消息';

-- ---------- 5. 订单表补充收货地址快照 ----------
-- 下单时把所选地址快照写入订单，避免地址后续被修改导致历史订单信息漂移。
ALTER TABLE `campus_order`
  ADD COLUMN `address_snapshot` varchar(400) NOT NULL DEFAULT '' COMMENT '收货地址快照（下单时写入）' AFTER `logistics_no`;

-- ---------- 6. 菜单与权限 ----------
-- 2011 收货地址 / 2012 我的收藏 / 2013 消息沟通（均为买家侧，所有登录用户可见）
INSERT IGNORE INTO `sys_menu`
  (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`)
VALUES
  (2011,'收货地址',2000,11,'address','campus/address/index','','',1,0,'C','0','0','campus:address:list','location','admin',NOW(),'',NULL,'买家维护收货地址'),
  (2012,'我的收藏',2000,12,'favorite','campus/favorite/index','','',1,0,'C','0','0','campus:favorite:list','star','admin',NOW(),'',NULL,'买家收藏的商品'),
  (2013,'消息沟通',2000,13,'message','campus/message/index','','',1,0,'C','0','0','campus:message:list','message','admin',NOW(),'',NULL,'订单上下文内与交易对方沟通');

INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES
  (2,2011),(2,2012),(2,2013);

-- 按钮级权限
INSERT IGNORE INTO `sys_menu`
  (`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`route_name`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`)
VALUES
  (2115,'地址新增',2011,1,'','','','',1,0,'F','0','0','campus:address:add','#','admin',NOW(),'',NULL,''),
  (2116,'地址修改',2011,2,'','','','',1,0,'F','0','0','campus:address:edit','#','admin',NOW(),'',NULL,''),
  (2117,'地址删除',2011,3,'','','','',1,0,'F','0','0','campus:address:remove','#','admin',NOW(),'',NULL,''),
  (2118,'收藏操作',2012,1,'','','','',1,0,'F','0','0','campus:favorite:add','#','admin',NOW(),'',NULL,''),
  (2119,'取消收藏',2012,2,'','','','',1,0,'F','0','0','campus:favorite:remove','#','admin',NOW(),'',NULL,''),
  (2120,'发送消息',2013,1,'','','','',1,0,'F','0','0','campus:message:add','#','admin',NOW(),'',NULL,'');

INSERT IGNORE INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES
  (2,2115),(2,2116),(2,2117),(2,2118),(2,2119),(2,2120);
