-- ============================
-- V5: 死信消息表 —— 记录进入 DLQ 的消息，支持人工补偿
-- ============================
CREATE TABLE IF NOT EXISTS dead_letter_message (
    id            BIGINT          AUTO_INCREMENT PRIMARY KEY,
    message_id    VARCHAR(64)     NOT NULL UNIQUE COMMENT '原始消息唯一标识',
    order_id      BIGINT          NOT NULL COMMENT '关联订单ID',
    status        VARCHAR(32)     NULL COMMENT '订单状态快照',
    event         VARCHAR(32)     NULL COMMENT '事件类型',
    original_body TEXT            NOT NULL COMMENT '原始消息体JSON（用于重投）',
    queue_name    VARCHAR(128)    NOT NULL COMMENT '来源队列名',
    arrived_at    DATETIME        NOT NULL COMMENT '进入DLQ时间',
    handled       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否已处理 0=未处理 1=已处理',
    handled_at    DATETIME        NULL COMMENT '处理时间',
    handle_note   VARCHAR(500)    NULL COMMENT '处理备注',
    INDEX idx_handled     (handled),
    INDEX idx_order_id    (order_id),
    INDEX idx_arrived_at  (arrived_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='死信消息表';
