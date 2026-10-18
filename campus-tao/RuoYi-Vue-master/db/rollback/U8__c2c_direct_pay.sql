-- U8：回滚 V8（C2C 卖家收款码直付）
-- 顺序：先删依赖新列的数据结构，再删列、删表。可重入：判存后再执行。

-- ---------- 1. 删收款码表 ----------
DROP TABLE IF EXISTS `campus_user_pay_qr`;

-- ---------- 2. campus_order 删列 ----------
SET @drop_pay_proof = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'campus_order' AND column_name = 'pay_proof_url') > 0,
    'ALTER TABLE `campus_order` DROP COLUMN `pay_proof_url`',
    'SELECT ''pay_proof_url 不存在，跳过'' AS skipped'));
PREPARE stmt FROM @drop_pay_proof; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @drop_seller_id = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'campus_order' AND column_name = 'seller_id') > 0,
    'ALTER TABLE `campus_order` DROP COLUMN `seller_id`',
    'SELECT ''seller_id 不存在，跳过'' AS skipped'));
PREPARE stmt FROM @drop_seller_id; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @drop_pay_type = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'campus_order' AND column_name = 'pay_type') > 0,
    'ALTER TABLE `campus_order` DROP COLUMN `pay_type`',
    'SELECT ''pay_type 不存在，跳过'' AS skipped'));
PREPARE stmt FROM @drop_pay_type; EXECUTE stmt; DEALLOCATE PREPARE stmt;
