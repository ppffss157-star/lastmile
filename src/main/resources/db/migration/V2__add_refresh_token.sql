-- =============================================
-- V2: Refresh Token 表 — 双 token 机制
-- Access Token (15min JWT) + Refresh Token (7天 JWT，存库可吊销)
-- =============================================

CREATE TABLE refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    token_id    VARCHAR(36)  NOT NULL,           -- UUID，存在 JWT claim 里，用来做数据库关联
    username    VARCHAR(255) NOT NULL,           -- 所属用户
    expiry_date DATETIME     NOT NULL,           -- 过期时间
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_token_id (token_id),
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
