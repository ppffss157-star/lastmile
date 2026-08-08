package com.example.logistics.lastmile.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 死信消息实体 —— 记录进入 DLQ 的消息，支持人工补偿。
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li><b>messageId</b> — 原始消息 ID（用于幂等去重判定）</li>
 *   <li><b>originalBody</b> — 原始消息体 JSON（用于重投）</li>
 *   <li><b>handled</b> — 是否已处理（人工标记）</li>
 *   <li><b>handledAt</b> — 处理时间</li>
 * </ul>
 */
@Entity
@Table(name = "dead_letter_message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeadLetterMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 原始消息唯一标识 */
    @Column(nullable = false, unique = true, length = 64)
    private String messageId;

    /** 关联的订单 ID */
    @Column(nullable = false)
    private Long orderId;

    /** 订单状态快照 */
    @Column(length = 32)
    private String status;

    /** 事件类型（CREATED / ACCEPTED / DELIVERED 等） */
    @Column(length = 32)
    private String event;

    /** 原始消息体 JSON，用于重投时反序列化 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalBody;

    /** 来源队列名（便于排查是哪条链路死的） */
    @Column(nullable = false, length = 128)
    private String queueName;

    /** 进入 DLQ 的时间 */
    @Column(nullable = false)
    private LocalDateTime arrivedAt;

    /** 是否已人工处理 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean handled = false;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 处理备注（处理人 + 处理方式） */
    @Column(length = 500)
    private String handleNote;
}
