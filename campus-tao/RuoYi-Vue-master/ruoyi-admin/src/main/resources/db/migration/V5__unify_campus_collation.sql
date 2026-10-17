-- V5：统一校园模块表的排序规则
--
-- 背景（真实故障，不是理论风险）：
--   V2/V3 建的 4 张表显式写了 COLLATE=utf8mb4_general_ci，
--   而 V1 那批表沿用 MySQL 8 默认的 utf8mb4_0900_ai_ci。
--   两套排序规则的 varchar 列一旦参与 = 比较，MySQL 直接报：
--     Illegal mix of collations (utf8mb4_0900_ai_ci,IMPLICIT)
--                        and (utf8mb4_general_ci,IMPLICIT) for operation '='
--
--   实际被打挂的是站内信：CampusMessageMapper.selectConversations 里
--     left join campus_order o on o.order_no = t.order_no
--   campus_message.order_no 是 general_ci，campus_order.order_no 是 0900_ai_ci，
--   于是「站内信」页面的会话列表 100% 查询失败，功能整体不可用。
--
--   为什么单测没发现：单测里的 Mapper 是 mock 的，SQL 根本没下到 MySQL；
--   而接口级脚本没覆盖这个列表接口。是 Playwright 走真实浏览器点进页面才暴露的。
--
-- 处理方式：
--   把这 4 张校园表统一转成 utf8mb4_0900_ai_ci，与 V1 那批表对齐。
--   不反向对齐到 general_ci，因为那要改 11 张已有业务表，收益更低、风险更大。
--   项目依赖 MySQL 8.0（内置 8.0.40，docker-compose 也是 mysql:8.0），
--   0900_ai_ci 在该版本上必然可用。

ALTER TABLE `campus_message` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE `campus_favorite` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE `campus_address` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE `campus_search_history` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
