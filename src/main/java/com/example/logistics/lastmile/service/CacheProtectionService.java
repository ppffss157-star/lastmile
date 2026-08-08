package com.example.logistics.lastmile.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.data.redis.core.StringRedisTemplate;
import com.example.logistics.lastmile.config.RedisLuaScripts;
import org.springframework.stereotype.Service;

import com.example.logistics.lastmile.entity.Order;
import com.example.logistics.lastmile.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.BloomFilter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <h1>缓存防护服务 — 穿透 / 击穿 / 雪崩三道防线</h1>
 *
 * <pre>
 * 请求进来：
 *   ┌─ 第一层：布隆过滤器（防穿透）
 *   │   说"不存在" → 直接返回 null，不查缓存不查 DB
 *   │   说"可能存在" → 进第二层
 *   ├─ 第二层：Redis 缓存查询
 *   │   命中 → 返回
 *   │   未命中（含空值标记） → 进第三层
 *   └─ 第三层：互斥锁 + DB 回源（防击穿）
 *        抢到锁 → 查 DB → 随机 TTL 写入缓存（防雪崩）
 *        没抢到 → 休眠 50ms 后重试一次
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheProtectionService {

    private final BloomFilter<Long> bloomFilter;
    private final StringRedisTemplate stringRedisTemplate;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "cache:order:";
    private static final String LOCK_PREFIX = "lock:cache:order:";
    private static final String NULL_MARKER = "__NULL__";

    // TTL 范围（分钟）：防雪崩——每次写入在范围内随机取
    private static final int TTL_MIN = 5;
    private static final int TTL_MAX = 10;
    private static final int NULL_TTL = 1;       // 不存在的 key 只缓存 1 分钟
    private static final int LOCK_TTL_SEC = 5;   // 锁最多持有 5 秒

    // ==================== 公开 API ====================

    /**
     * 查询单个订单，走完整三层防护。
     *
     * @return 订单对象，不存在返回 null
     */
    public Order queryById(Long id) {
        // ===== 第一层：布隆过滤器（防穿透） =====
        if (!bloomFilter.mightContain(id)) {
            log.debug("布隆判定不存在 id={}，直接返回 null", id);
            return null;
        }

        // ===== 第二层：查 Redis 缓存 =====
        String cacheKey = CACHE_PREFIX + id;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (NULL_MARKER.equals(cached)) {
                log.debug("缓存命中空值标记 id={}", id);
                return null;
            }
            log.debug("缓存命中 id={}", id);
            return deserialize(cached);
        }

        // ===== 第三层：互斥锁 + 回源 DB（防击穿） =====
        return queryDbWithMutex(id, cacheKey);
    }

    /**
     * 新订单创建后调用：把 ID 加入布隆过滤器 + 写入缓存。
     */
    public void onOrderCreated(Order order) {
        Long id = order.getId();
        bloomFilter.put(id);
        String cacheKey = CACHE_PREFIX + id;
        int ttl = randomTtl();
        stringRedisTemplate.opsForValue().set(cacheKey, serialize(order), Duration.ofMinutes(ttl));
        log.debug("订单创建后刷新：id={} 加入布隆 + 缓存 (TTL={}min)", id, ttl);
    }

    /**
     * 缓存失效：更新或删除订单时调用。
     */
    public void evict(Long id) {
        // Guava BloomFilter 不支持删除，这里只清缓存
        stringRedisTemplate.delete(CACHE_PREFIX + id);
        log.debug("缓存已清除 id={}", id);
    }

    /**
     * 判断 ID 是否可能存在（用于 Controller 前置校验等场景）。
     */
    public boolean mightExist(Long id) {
        return bloomFilter.mightContain(id);
    }

    // ==================== 内部逻辑 ====================

    /**
     * <h3>互斥锁回源：防止缓存击穿</h3>
     * <p>
     * 场景：热点 key 缓存刚好过期，同一瞬间 100 个请求涌入。
     * <br>
     * 策略：Redis SETNX 抢锁，只有一个请求能抢到并去查 DB 重建缓存，
     * 其余请求 sleep(50ms) 后重新查缓存。
     */
    private Order queryDbWithMutex(Long id, String cacheKey) {
        String lockKey = LOCK_PREFIX + id;
        String lockValue = UUID.randomUUID().toString();

        // 尝试抢锁
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(LOCK_TTL_SEC));

        if (Boolean.TRUE.equals(locked)) {
            try {
                // Double-check：抢锁期间别的线程可能已经重建了
                String cached = stringRedisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    return NULL_MARKER.equals(cached) ? null : deserialize(cached);
                }

                // 查 DB
                Order order = orderRepository.findById(id).orElse(null);
                int ttl = randomTtl();

                if (order != null) {
                    stringRedisTemplate.opsForValue()
                            .set(cacheKey, serialize(order), Duration.ofMinutes(ttl));
                    log.info("缓存重建 id={} TTL={}min", id, ttl);
                } else {
                    // 空值缓存：防止不存在的 key 反复穿透（短 TTL）
                    stringRedisTemplate.opsForValue()
                            .set(cacheKey, NULL_MARKER, Duration.ofMinutes(NULL_TTL));
                    log.debug("空值缓存 id={} TTL={}min", id, NULL_TTL);
                }

                return order;

            } finally {
                // 原子释放锁（只删自己的）
                stringRedisTemplate.execute(
                        RedisLuaScripts.RELEASE_LOCK,
                        List.of(lockKey), lockValue);
            }

        } else {
            // 没抢到锁 → 别的线程在重建，短间隔轮询等待
            int maxRetries = 3;
            for (int i = 0; i < maxRetries; i++) {
                try {
                    Thread.sleep(10L + i * 10L);  // 10ms → 20ms → 30ms，逐次递增
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                // 每次醒来查一次缓存，不等白不等
                String cached = stringRedisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    return NULL_MARKER.equals(cached) ? null : deserialize(cached);
                }
            }
            // 重试完还是没有 → 降级查 DB（极少数情况，比如抢锁的线程崩了）
            log.warn("缓存重建超时 id={}，降级查 DB", id);
            return orderRepository.findById(id).orElse(null);
        }
    }

    /**
     * 随机 TTL：在 [TTL_MIN, TTL_MAX] 范围内随机取。
     * <br>
     * 防雪崩的核心——如果所有 key 都是 10 分钟 TTL，到了第 10 分钟会集体过期。
     * 加点随机尾巴，错开过期时间。
     */
    private int randomTtl() {
        return ThreadLocalRandom.current().nextInt(TTL_MIN, TTL_MAX + 1);
    }

    // ==================== 序列化工具 ====================

    private String serialize(Order order) {
        try {
            return objectMapper.writeValueAsString(order);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化订单失败 id=" + order.getId(), e);
        }
    }

    private Order deserialize(String json) {
        try {
            return objectMapper.readValue(json, Order.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化订单失败", e);
        }
    }
}
