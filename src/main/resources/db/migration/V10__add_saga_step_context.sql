-- V10__add_saga_step_context.sql
-- Saga 补偿需要记录原始请求参数，才能在补偿时用正确的值回滚

ALTER TABLE saga_steps
    ADD COLUMN context TEXT DEFAULT NULL COMMENT '步骤上下文（JSON）：补偿时需要的原始参数，如 productId/quantity/accountId/amount'
    AFTER error_message;
