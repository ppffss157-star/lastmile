-- V4: 消息去重表，配合 Redis SETNX 做双重幂等消费
CREATE TABLE IF NOT EXISTS message_dedup (
    message_id VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '消息唯一标识（UUID）',
    queue_name VARCHAR(128) NOT NULL COMMENT '队列名，方便排查',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次消费时间',
    INDEX idx_created_at (created_at) COMMENT '用于定时清理过期记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息去重表';
