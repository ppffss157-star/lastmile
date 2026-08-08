-- V9__create_saga_steps.sql
-- Saga 补偿记录表：记录每一次 Saga 事务的每一步执行结果

CREATE TABLE IF NOT EXISTS saga_steps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    saga_id VARCHAR(64) NOT NULL COMMENT 'Saga 事务唯一标识',
    order_id BIGINT NOT NULL COMMENT '关联订单 ID',
    step_name VARCHAR(64) NOT NULL COMMENT '步骤名称',
    status VARCHAR(20) NOT NULL COMMENT '步骤状态：PENDING/SUCCESS/FAILED/COMPENSATED',
    error_message VARCHAR(500) DEFAULT NULL COMMENT '失败/补偿失败时的错误信息',
    created_at DATETIME NOT NULL COMMENT '步骤创建时间',
    INDEX idx_saga_steps_saga_id (saga_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Saga 分布式事务补偿记录表';
