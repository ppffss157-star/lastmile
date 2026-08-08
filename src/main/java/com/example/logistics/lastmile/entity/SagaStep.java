package com.example.logistics.lastmile.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Saga 补偿记录表 — 每一步执行结果都记在这里。
 * 失败时从后往前查，找到所有成功的步骤，依次调它们的补偿操作。
 */
@Entity
@Table(name = "saga_steps", indexes = {
    @Index(name = "idx_saga_steps_saga_id", columnList = "sagaId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SagaStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 一次 Saga 事务的唯一标识，同一批步骤共享 */
    @Column(nullable = false)
    private String sagaId;

    /** 关联的订单 ID */
    @Column(nullable = false)
    private Long orderId;

    /** 步骤名称：CREATE_ORDER / RESERVE_INVENTORY / PROCESS_PAYMENT */
    @Column(nullable = false)
    private String stepName;

    /** 步骤执行状态 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStepStatus status;

    /** 失败时的错误信息 */
    @Column(length = 500)
    private String errorMessage;

    /** 步骤上下文（JSON）：补偿时需要的原始参数 */
    @Column(columnDefinition = "TEXT")
    private String context;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
