-- =============================================
-- 数据迁移：orders → orders_0/1/2/3
-- =============================================
-- 前置条件：
--   1. V8 迁移已执行（orders_0~3 表已存在）
--   2. 业务已停写（或接受短暂不一致）
--
-- 迁移策略：
--   原 orders 表的数据按 id % 4 散列到 4 张分片表。
--   id 是原自增主键，迁移后保持不变（分片键保证各片 ID 不冲突）。
--
-- 执行方式：
--   在 MySQL 客户端直接 source 本文件，或逐段复制执行。
--   100w 数据约 10~20 秒完成（4 条 INSERT...SELECT 并行写）。
-- =============================================

-- Step 1: 迁移数据（按分片键散列）
INSERT INTO orders_0 SELECT * FROM orders WHERE id % 4 = 0;
INSERT INTO orders_1 SELECT * FROM orders WHERE id % 4 = 1;
INSERT INTO orders_2 SELECT * FROM orders WHERE id % 4 = 2;
INSERT INTO orders_3 SELECT * FROM orders WHERE id % 4 = 3;

-- Step 2: 验证行数
SELECT 'orders (original)' AS tbl, COUNT(*) AS cnt FROM orders
UNION ALL
SELECT 'orders_0', COUNT(*) FROM orders_0
UNION ALL
SELECT 'orders_1', COUNT(*) FROM orders_1
UNION ALL
SELECT 'orders_2', COUNT(*) FROM orders_2
UNION ALL
SELECT 'orders_3', COUNT(*) FROM orders_3
UNION ALL
SELECT 'SUM(orders_0..3)', COUNT(*) FROM (
    SELECT * FROM orders_0 UNION ALL
    SELECT * FROM orders_1 UNION ALL
    SELECT * FROM orders_2 UNION ALL
    SELECT * FROM orders_3
) total;

-- Step 3: 抽查分片分布（应该接近 1:1:1:1）
SELECT id % 4 AS shard, COUNT(*) AS cnt
FROM orders
GROUP BY id % 4
ORDER BY shard;

-- Step 4: 确认无误后，可把原表改名备份
-- RENAME TABLE orders TO orders_backup;
-- 改名后 ShardingSphere 的"逻辑表 orders → 物理表 orders_0..3"路由正式生效
