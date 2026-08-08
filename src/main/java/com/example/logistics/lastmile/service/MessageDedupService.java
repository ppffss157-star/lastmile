package com.example.logistics.lastmile.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.logistics.lastmile.entity.MessageDedup;
import com.example.logistics.lastmile.repository.MessageDedupRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <h1>消息幂等服务 —— 双重保障防止重复消费</h1>
 *
 * <h2>要解决的问题</h2>
 * RabbitMQ 有一个"至少一次投递"保证：消息可能被投递多次。
 * 场景：消费者处理到一半崩了 → 没发 ACK → RabbitMQ 重新投递。
 * 不做幂等的话，同一条通知推两次 WebSocket，或者更严重的——扣两次库存。
 *
 * <h2>解决方案：messageId 去重</h2>
 *
 * <pre>
 * 消费者收到消息:
 *   ┌─ 1. Redis SETNX messageId（TTL=24h）
 *   │    成功 → 第一次见，继续
 *   │    失败 → 重复消息，直接返回（99.9% 的情况在这挡住）
 *   ├─ 2. 插入 DB message_dedup 表
 *   │    成功 → 兜底记录
 *   │    失败（DuplicateKeyException）→ Redis 漏了但 DB 挡住了
 *   └─ 3. 执行业务逻辑
 * </pre>
 *
 * <h2>为什么 Redis + DB 两层？</h2>
 * Redis 快但不可靠（内存，重启丢失）；DB 慢但可靠（磁盘，主键唯一）。
 * 正常情况 Redis 就挡住了，DB 是兜底。
 *
 * <h2>比喻</h2>
 * Redis = 前台接待，看一眼就知道这人来过没有（快）<br>
 * DB = 签到簿，铁证如山（可靠）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageDedupService {

    private final StringRedisTemplate stringRedisTemplate;
    private final MessageDedupRepository messageDedupRepository;

    private static final String REDIS_KEY_PREFIX = "msg:dedup:";
    private static final Duration REDIS_TTL = Duration.ofHours(24);

    /**
     * 判断消息是否已处理过。首次遇到返回 true（放行），重复返回 false（拦截）。
     *
     * @param messageId 消息唯一标识
     * @param queueName 队列名（记 DB 用的，方便排查）
     * @return true=首次见可处理，false=重复消息跳过
     */
    public boolean isFirstDelivery(String messageId, String queueName) {
        // ===== 第一层：Redis SETNX（快，挡住 99.9% 的重复） =====
        String redisKey = REDIS_KEY_PREFIX + messageId;
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", REDIS_TTL);

        if (Boolean.FALSE.equals(success)) {
            log.warn("⚡ Redis 检测到重复消息: messageId={}", messageId);
            return false;
        }

        // ===== 第二层：DB 插入（慢但可靠，Redis 不可用时的兜底） =====
        try {
            messageDedupRepository.save(
                    new MessageDedup(messageId, queueName, LocalDateTime.now()));
        } catch (DuplicateKeyException e) {
            // Redis SETNX 成功但 DB 已存在 → Redis 之前挂了重启了
            log.warn("⚡ DB 检测到重复消息（Redis 漏网）: messageId={}", messageId);
            // 把 Redis key 也清理掉，防止不一致状态
            stringRedisTemplate.delete(redisKey);
            return false;
        }

        return true;
    }

    /**
     * 定时清理 7 天前的去重记录，防止表无限膨胀。
     * 每天凌晨 3 点执行。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void cleanExpiredRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        int deleted = messageDedupRepository.deleteOldRecords(cutoff);
        if (deleted > 0) {
            log.info("🗑 清理消息去重表，删除 {} 条 7 天前的记录", deleted);
        }
    }
}
