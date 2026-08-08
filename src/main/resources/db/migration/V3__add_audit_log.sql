-- =============================================
-- V3: 操作审计日志表 — 记录"谁 + 什么时候 + 干了什么"
-- AOP 切面自动写入，@Async 异步落库不阻塞业务
-- =============================================

CREATE TABLE audit_logs (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    username     VARCHAR(255) NOT NULL,              -- 操作人
    operation    VARCHAR(500) NOT NULL,              -- 操作描述（如"创建订单"）
    method       VARCHAR(500) NOT NULL,              -- 类名.方法名
    request_uri  VARCHAR(500),                       -- HTTP 请求路径
    ip           VARCHAR(45),                        -- 请求方 IP（IPv6 最长 45 字符）
    params       TEXT,                               -- 方法参数 JSON
    result       VARCHAR(500) NOT NULL,              -- SUCCESS 或错误信息
    duration_ms  BIGINT,                             -- 执行耗时（毫秒）
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_username (username),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
