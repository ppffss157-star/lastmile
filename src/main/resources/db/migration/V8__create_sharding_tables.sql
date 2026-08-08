-- =============================================
-- V8: 分表 — 为 ShardingSphere 创建 4 张物理分片表
-- =============================================
-- 分片策略：id % 4 → orders_0 / orders_1 / orders_2 / orders_3
-- 主键：BIGINT NOT NULL（不用 AUTO_INCREMENT，Snowflake 生成全局唯一 ID）
-- =============================================

CREATE TABLE orders_0 (
    id            BIGINT       NOT NULL,
    customer_name VARCHAR(255),
    address       VARCHAR(255),
    phone         VARCHAR(255),
    status        VARCHAR(20),
    courier_id    BIGINT,
    created_at    DATETIME     NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE orders_1 LIKE orders_0;
CREATE TABLE orders_2 LIKE orders_0;
CREATE TABLE orders_3 LIKE orders_0;

-- 索引：和原 orders 表保持一致（V1 + V7）
CREATE INDEX idx_o0_courier_id ON orders_0 (courier_id);
CREATE INDEX idx_o0_status     ON orders_0 (status);
CREATE INDEX idx_o0_created_at ON orders_0 (created_at);
CREATE INDEX idx_o0_phone      ON orders_0 (phone);
CREATE INDEX idx_o0_cust_sts   ON orders_0 (customer_name, status);

CREATE INDEX idx_o1_courier_id ON orders_1 (courier_id);
CREATE INDEX idx_o1_status     ON orders_1 (status);
CREATE INDEX idx_o1_created_at ON orders_1 (created_at);
CREATE INDEX idx_o1_phone      ON orders_1 (phone);
CREATE INDEX idx_o1_cust_sts   ON orders_1 (customer_name, status);

CREATE INDEX idx_o2_courier_id ON orders_2 (courier_id);
CREATE INDEX idx_o2_status     ON orders_2 (status);
CREATE INDEX idx_o2_created_at ON orders_2 (created_at);
CREATE INDEX idx_o2_phone      ON orders_2 (phone);
CREATE INDEX idx_o2_cust_sts   ON orders_2 (customer_name, status);

CREATE INDEX idx_o3_courier_id ON orders_3 (courier_id);
CREATE INDEX idx_o3_status     ON orders_3 (status);
CREATE INDEX idx_o3_created_at ON orders_3 (created_at);
CREATE INDEX idx_o3_phone      ON orders_3 (phone);
CREATE INDEX idx_o3_cust_sts   ON orders_3 (customer_name, status);
