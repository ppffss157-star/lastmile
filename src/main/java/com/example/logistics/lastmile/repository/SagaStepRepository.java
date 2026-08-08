package com.example.logistics.lastmile.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.logistics.lastmile.entity.SagaStep;
import com.example.logistics.lastmile.entity.SagaStepStatus;

public interface SagaStepRepository extends JpaRepository<SagaStep, Long> {

    /** 查一次 Saga 事务的所有步骤，按创建时间排序（正序） */
    List<SagaStep> findBySagaIdOrderByCreatedAtAsc(String sagaId);

    /** 查一次 Saga 事务中所有成功的步骤（补偿时用） */
    List<SagaStep> findBySagaIdAndStatusOrderByCreatedAtAsc(String sagaId, SagaStepStatus status);

    /** 统计指定 sagaId 下失败步骤数 */
    long countBySagaIdAndStatus(String sagaId, SagaStepStatus status);
}
