-- =============================================
-- V1: 初始化物流配送系统表结构
-- 快递员 courier + 订单 orders
-- =============================================

CREATE TABLE courier (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    name     VARCHAR(255),
    phone    VARCHAR(255),
    status   VARCHAR(20),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_courier_status ON courier (status);

CREATE TABLE orders (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    customer_name VARCHAR(255),
    address       VARCHAR(255),
    phone         VARCHAR(255),
    status        VARCHAR(20),
    courier_id    BIGINT,
    created_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_orders_courier FOREIGN KEY (courier_id) REFERENCES courier (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_orders_courier_id ON orders (courier_id);
CREATE INDEX idx_orders_status      ON orders (status);
