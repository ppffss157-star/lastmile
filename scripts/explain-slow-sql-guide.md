# EXPLAIN 慢 SQL 实战指南

> 前置：已执行 `scripts/generate-orders-data.sql` 造了 100w 条订单。
> 进 MySQL 跟着敲：`mysql -u root -p123456 logistics_db`

---

## 一、EXPLAIN 输出核心字段速查表

| 字段 | 含义 | 重点关注值 |
|------|------|-----------|
| **id** | 执行顺序 | 数字越大越先执行，相同从上到下 |
| **select_type** | 查询类型 | SIMPLE（简单）、PRIMARY（外层）、SUBQUERY（子查询） |
| **type** | **访问方式，核心指标** | `ALL`（全表扫，❌）→ `index`（全索引扫）→ `range`（范围）→ `ref`（索引查找）→ `eq_ref`（唯一索引）→ `const`（主键/唯一键） |
| **possible_keys** | 可选的索引 | 显示有哪些索引可选 |
| **key** | 实际用的索引 | NULL = 没走索引 ⚠️ |
| **rows** | 预估扫描行数 | 越大越慢，100w 肯定要优化 |
| **Extra** | 额外信息 | `Using index`（覆盖索引👍）、`Using filesort`（文件排序👎）、`Using temporary`（临时表👎👎）、`Using where`（WHERE 过滤） |

**口诀**：type 从 ALL 往 const 走是优化方向，rows 越小越好，Extra 不能有 filesort 和 temporary。

---

## 二、场景实战

### 场景 1：按时间范围查订单（最常见的慢 SQL）

**业务**：后台管理 → 查近 7 天订单 → 慢。

```sql
-- ===== 优化前（没索引） =====
EXPLAIN
SELECT * FROM orders
WHERE created_at BETWEEN '2026-01-01' AND '2026-01-31'\G

-- 输出：
--           id: 1
--   select_type: SIMPLE
--         table: orders
--          type: ALL          ← 全表扫描！100w 行全过一遍
-- possible_keys: NULL
--           key: NULL         ← 没用索引
--          rows: 994562       ← 扫 100w 行
--         Extra: Using where  ← 用 WHERE 过滤
```

**问题**：`created_at` 上没有索引，MySQL 只能从头到尾扫一遍。

```sql
-- ===== 建索引 =====
CREATE INDEX idx_orders_created_at ON orders (created_at);

-- ===== 优化后 =====
EXPLAIN
SELECT * FROM orders
WHERE created_at BETWEEN '2026-01-01' AND '2026-01-31'\G

-- 输出：
--          type: range         ← 范围扫描！只扫索引区间
--           key: idx_orders_created_at
--          rows: 83125         ← 只扫符合条件的 8w 行
--         Extra: Using index condition
```

**效果**：type 从 `ALL` → `range`，rows 从 100w → 8w，耗时从 2 秒降到 0.1 秒 🚀

---

### 场景 2：手机号查订单（最左前缀原则）

**业务**：输手机号查历史订单。

```sql
-- ===== 优化前 =====
EXPLAIN
SELECT * FROM orders WHERE phone = '13812345678'\G

--          type: ALL
--           key: NULL
--          rows: 1000000
```

```sql
-- ===== 建索引 =====
CREATE INDEX idx_orders_phone ON orders (phone);

-- ===== 优化后 — 精确匹配 =====
EXPLAIN
SELECT * FROM orders WHERE phone = '13812345678'\G

--          type: ref          ← 非唯一索引查找
--           key: idx_orders_phone
--          rows: 1            ← 直接定位！
```

**进阶：LIKE 会不会走索引？**

```sql
-- ✅ 前缀匹配：走索引
EXPLAIN SELECT * FROM orders WHERE phone LIKE '138%'\G
-- key: idx_orders_phone, type: range

-- ❌ 中间匹配：不走索引！
EXPLAIN SELECT * FROM orders WHERE phone LIKE '%138%'\G
-- key: NULL, type: ALL ← 全表扫
```

**原因**：B+Tree 索引按前缀排序，`'138%'` 定位到 138 开头的子树就行；`'%138%'` 不匹配前缀，只能全扫。

---

### 场景 3：客户名 + 状态组合查询

**业务**：客服说"查张三有哪些配送中的订单"。

```sql
-- ===== 优化前 =====
EXPLAIN
SELECT * FROM orders
WHERE customer_name = '张三' AND status = 'DELIVERING'\G

--          type: ALL（或者走 idx_orders_status 但 rows 很大）
-- 两种坏情况：
--   a) 两个索引都可用，优化器选一个但过滤后剩很多行
--   b) 两个单列索引都不够精准
```

```sql
-- ===== 建联合索引 =====
-- 区分度高的放前面：customer_name > status（名字种类多，状态只有 5 种）
CREATE INDEX idx_orders_customer_status ON orders (customer_name, status);

-- ===== 优化后 =====
EXPLAIN
SELECT * FROM orders
WHERE customer_name = '张三' AND status = 'DELIVERING'\G

--          type: ref
--           key: idx_orders_customer_status
--          rows: 5            ← 张三只有 5 个配送中的订单
```

**知识点：最左匹配原则**

联合索引 `(customer_name, status)` 相当于给 `(customer_name)` 和 `(customer_name, status)` 建了索引。

```
✅ WHERE customer_name = '张三'                    — 走索引
✅ WHERE customer_name = '张三' AND status = 'X'   — 走索引
❌ WHERE status = 'X'                              — 不走！跳过了最左列
```

---

### 场景 4：GROUP BY 聚合统计

**业务**：统计每个快递员的订单数 → 报表页面。

```sql
-- ===== 查询 =====
EXPLAIN
SELECT courier_id, COUNT(*) AS cnt
FROM orders
GROUP BY courier_id
ORDER BY cnt DESC\G

--          type: index        ← 走 idx_orders_courier_id（全索引扫）
--           key: idx_orders_courier_id
--          rows: 1000000      ← 还是要扫 100w 行
--         Extra: Using index; Using temporary; Using filesort
--                                         ↑               ↑
--                                     临时表排序      文件排序（慢！）
```

**分析**：走了索引但还是慢，因为要扫描全索引 + 内存里建临时表排序。

**优化思路**：
1. 如果数据是实时的，这个查询跑报表是合理的（100w 全扫，0.5~1 秒凑合）
2. 如果还要更快的实时统计 → 建汇总表/用 Redis 计数器
3. 如果只是看 EXPLAIN 学习 → `Extra: Using temporary; Using filesort` 是关注重点

---

## 三、核心结论（面试用）

| 问题 | 答案 |
|------|------|
| **type 从差到好怎么排？** | ALL → index → range → ref → eq_ref → const |
| **最左前缀原则是什么？** | 联合索引 (a,b,c) 只支持 (a)、(a,b)、(a,b,c)，跳过 a 就不走索引 |
| **LIKE 啥时候走索引？** | `'abc%'` 走，`'%abc'` 和 `'%abc%'` 不走 |
| **filesort 为什么不好？** | 在磁盘/内存里额外排序，数据量大时特别慢 |
| **rows 是准确值吗？** | 不是，是优化器的统计估算，但偏离太多说明统计信息过期 |
| **怎么判断索引有没有用上？** | EXPLAIN 看 key 列，NULL = 没走；再对 type 看走了什么级别 |
| **覆盖索引是什么？** | 查询的列全在索引里，Extra 显示 `Using index`，不用回表查数据行 |

---

## 四、日常排查套路

```
发现慢 SQL
  → EXPLAIN 看 type（是不是 ALL）、key（有没有 NULL）、rows（大不大）
  → 找到全表扫描的表 → 看 WHERE/JOIN 的列有没有索引
  → 加索引 → 再 EXPLAIN 验证 type 改善 & rows 下降
  → 搞不定？OPTIMIZE TABLE 更新统计信息 → 看执行计划是否变化
```
