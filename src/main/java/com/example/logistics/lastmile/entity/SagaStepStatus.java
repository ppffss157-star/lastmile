package com.example.logistics.lastmile.entity;

/**
 * Saga 步骤状态：
 * PENDING     — 步骤已记录，等待执行
 * SUCCESS     — 步骤执行成功
 * FAILED      — 步骤执行失败，触发补偿
 * COMPENSATED — 补偿已完成
 */
public enum SagaStepStatus {
    PENDING,
    SUCCESS,
    FAILED,
    COMPENSATED
}
