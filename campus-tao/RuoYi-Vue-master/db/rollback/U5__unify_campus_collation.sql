-- ============================================================
-- 校园淘 U5 · V5（统一校园表排序规则）的回滚脚本
--
-- 影响：把 V5 转成 utf8mb4_0900_ai_ci 的 4 张校园表还原成 utf8mb4_general_ci。
--
-- ⚠️ 回滚前必须知道代价：还原成 general_ci 后，campus_message.order_no 与
--    campus_order.order_no 的排序规则再次不一致，站内信的会话列表查询会重新报
--      Illegal mix of collations ... for operation '='
--    也就是 V5 修掉的那个缺陷会完整复现（站内信页面打不开会话）。
--    只有在"必须退回 V4 的状态"时才对这 4 张表执行本脚本；
--    若只是想撤销某次别的变更，不要顺手回滚 V5。
--
-- 幂等性：CONVERT TO 在目标排序规则已生效时是空操作，重复执行不报错。
-- ============================================================

ALTER TABLE `campus_message` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `campus_favorite` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `campus_address` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE `campus_search_history` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
