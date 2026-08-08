package com.example.logistics.lastmile.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.logistics.lastmile.entity.DeadLetterMessage;

/**
 * 死信消息 Repository — 查询和更新死信记录。
 */
@Repository
public interface DeadLetterMessageRepository extends JpaRepository<DeadLetterMessage, Long> {

    /** 按 messageId 查（重投前判断是否已存在） */
    Optional<DeadLetterMessage> findByMessageId(String messageId);

    /** 查未处理的死信列表（人工补偿页面用） */
    List<DeadLetterMessage> findByHandledFalseOrderByArrivedAtDesc();

    /** 按订单 ID 查死信记录 */
    List<DeadLetterMessage> findByOrderIdOrderByArrivedAtDesc(Long orderId);
}
