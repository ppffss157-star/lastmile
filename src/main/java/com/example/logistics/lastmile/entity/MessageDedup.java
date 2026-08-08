package com.example.logistics.lastmile.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息去重表 —— 和 Redis SETNX 搭档做幂等消费。
 *
 * <h3>为什么需要 DB 去重？</h3>
 * Redis 是内存的，重启就没了。如果消息在处理前 Redis 刚好挂了重启，
 * SETNX 失效，消息就被重复消费了。DB 去重表靠 unique key 硬挡。
 *
 * <h3>Redis vs DB 各自的角色</h3>
 * Redis SETNX → 快（微秒级），99.9% 的情况在这挡住<br>
 * DB unique key → 慢但可靠，Redis 不可用时兜底
 */
@Entity
@Table(name = "message_dedup")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDedup {

    @Id
    @Column(name = "message_id", length = 64)
    private String messageId;

    @Column(name = "queue_name", length = 128, nullable = false)
    private String queueName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
