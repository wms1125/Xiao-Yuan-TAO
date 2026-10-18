-- V8：C2C 卖家收款码直付（P0-1 真实支付通道的过渡方案）
--
-- 一、业务模式（为什么不是平台收款码）
--
--   正规通道需要微信支付特约商户号（外部依赖，暂缺）。个人收款码由平台代收
--   再结算给卖家，既违反微信个人收款码使用规范，又构成无牌照「二清」。
--   因此本版本采用 C2C 直付：每个卖家上传本人收款码，买家扫码直接付给卖家，
--   平台全程不经手资金，只做状态撮合：
--
--     待付款(0) --买家扫码付款+点[我已付款]--> 待卖家确认收款(7)
--     待卖家确认收款(7) --卖家确认已收到--> 已付款(1, 写 pay_time, pay_type=1)
--     待付款(0)/待卖家确认(7) --超时或买家取消--> 已取消(4, 回库存)
--
--   状态 7 与状态 0 一样占用库存且可能被定时任务超时取消，故超时扫描
--   （selectTimeoutWaitPayOrders / cancelWaitPayOrderIfStillWaitPay）同步纳入，
--   与 V7 的 idx_status_time 索引继续兼容。
--
--   已知边界（如实登记，不隐藏）：
--   - 平台无资金担保能力，退款依赖买卖双方线下协商，用户需知情；
--   - 直付订单须全部订单项同属一个卖家（发起时校验），跨卖家订单仍走沙箱余额；
--   - 卖家须先上传收款码，未上传时买家发起直付会被明确拒绝。
--
-- 二、可重入（与 V6/V7 相同的原因：迁移中断重跑、DBA 预建都不能炸启动）
--
-- ---------- 1. campus_order 加列 ----------
SET @add_pay_type = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'campus_order' AND column_name = 'pay_type') = 0,
    'ALTER TABLE `campus_order` ADD COLUMN `pay_type` tinyint NOT NULL DEFAULT 0 COMMENT ''支付方式 0沙箱余额 1卖家收款码直付'' AFTER `status`',
    'SELECT ''pay_type 已存在，跳过'' AS skipped'));
PREPARE stmt FROM @add_pay_type; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_seller_id = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'campus_order' AND column_name = 'seller_id') = 0,
    'ALTER TABLE `campus_order` ADD COLUMN `seller_id` bigint DEFAULT NULL COMMENT ''卖家ID(直付发起时写入，单卖家校验结果)''',
    'SELECT ''seller_id 已存在，跳过'' AS skipped'));
PREPARE stmt FROM @add_seller_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_pay_proof = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'campus_order' AND column_name = 'pay_proof_url') = 0,
    'ALTER TABLE `campus_order` ADD COLUMN `pay_proof_url` varchar(500) NOT NULL DEFAULT '''' COMMENT ''直付付款截图(买家可选上传)''',
    'SELECT ''pay_proof_url 已存在，跳过'' AS skipped'));
PREPARE stmt FROM @add_pay_proof; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. 卖家收款码表 ----------
CREATE TABLE IF NOT EXISTS `campus_user_pay_qr` (
  `user_id` bigint NOT NULL COMMENT '卖家用户ID',
  `qr_url` varchar(500) NOT NULL COMMENT '收款码图片URL(/profile/..)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户收款码(C2C直付)';
