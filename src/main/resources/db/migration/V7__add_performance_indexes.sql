-- =============================================
-- V6: 性能优化索引 — EXPLAIN 慢 SQL 实战产物
-- =============================================

-- 场景 1: 按时间范围查订单（后台管理：今日订单、近7天报表）
-- 优化前：type=ALL, rows=1000000（全表扫描）
-- 优化后：type=range, rows≈该时间段的订单数
CREATE INDEX idx_orders_created_at ON orders (created_at);

-- 场景 2: 按手机号查订单（用户查自己的历史订单）
-- EXPLAIN 验证：WHERE phone = '13812345678' 走索引，type=ref, rows≈1
-- 注意：LIKE '138%' 也能走索引（最左前缀），但 LIKE '%138%' 不能
CREATE INDEX idx_orders_phone ON orders (phone);

-- 场景 3: 按客户名 + 状态组合查询（客服：查张三有哪些配送中的订单）
-- 联合索引：customer_name 放前面（区分度高），status 放后面
-- 优化前：可能走 idx_orders_status（rows 很大），或用不到索引
-- 优化后：type=ref, rows≈该客户的订单数
CREATE INDEX idx_orders_customer_status ON orders (customer_name, status);

-- 场景 4: 覆盖索引 — 按 courier_id 统计各状态订单数
-- 已有 idx_orders_courier_id，但 SELECT status, COUNT(*) 需要回表
-- 如果慢到需要优化，可建覆盖索引（本例先不建，EXPLAIN 时讲 Extra=Using index 啥意思）
-- CREATE INDEX idx_orders_courier_status ON orders (courier_id, status);
