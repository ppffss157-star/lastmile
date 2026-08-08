package com.example.logistics.lastmile.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * 并发压测工具 —— 用于实战演练线程池调参和 GC 分析。
 *
 * <h2>三个压测场景</h2>
 * <ol>
 *   <li><b>打满线程池</b>：N 个并发任务扔进 @Async 线程池，观察 poolSize 膨胀和队列积压</li>
 *   <li><b>Redis SETNX 抢锁</b>：N 个线程同时抢同一把锁，模拟配送员派单竞态</li>
 *   <li><b>创建大量临时对象</b>：触发 Young GC / Full GC，配合 GC 日志分析</li>
 * </ol>
 *
 * <h2>怎么用</h2>
 * <pre>
 * # 场景 1：50 个并发异步任务
 * curl "http://localhost:9090/actuator/stress/async?count=50"
 *
 * # 场景 2：30 个线程抢锁
 * curl "http://localhost:9090/actuator/stress/redis-lock?threads=30"
 *
 * # 场景 3：触发 GC（造 500w 个临时对象）
 * curl "http://localhost:9090/actuator/stress/gc?objects=5000000"
 *
 * # 看线程池状态
 * curl "http://localhost:9090/actuator/thread-pools"
 * </pre>
 */
@Service
public class ConcurrencyStressService {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyStressService.class);

    private final ThreadPoolTaskExecutor taskExecutor;
    private final StringRedisTemplate stringRedisTemplate;

    public ConcurrencyStressService(
            @Qualifier("taskExecutor") ThreadPoolTaskExecutor taskExecutor,
            StringRedisTemplate stringRedisTemplate) {
        this.taskExecutor = taskExecutor;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ==================== 场景 1：打满线程池 ====================

    /**
     * 提交 count 个异步任务，每个耗时 200~800ms。
     * 观察点：
     * <ul>
     *   <li>count ≤ coreSize(4)：poolSize 保持 4，queue 部分积压，无新线程创建</li>
     *   <li>coreSize < count ≤ coreSize+queue(104)：4 个线程全忙，100 个排队，poolSize 仍是 4</li>
     *   <li>count > 104：开始创建临时线程，poolSize 从 4 涨到 max(8)</li>
     *   <li>count > 108：触发 CallerRunsPolicy，主线程自己跑任务</li>
     * </ul>
     */
    public StressResult stressAsyncThreadPool(int count) {
        long start = System.currentTimeMillis();
        AtomicInteger completed = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(count);
        List<String> rejectedTasks = new ArrayList<>();

        log.info("🔥 场景1：提交 {} 个异步任务，观察 /actuator/thread-pools", count);

        for (int i = 0; i < count; i++) {
            final int taskId = i;
            try {
                taskExecutor.submit(() -> {
                    try {
                        // 模拟业务耗时（200~800ms 随机）
                        Thread.sleep(200 + (long) (Math.random() * 600));
                        completed.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            } catch (Exception e) {
                // CallerRunsPolicy 触发时，submit 可能被拒绝
                rejectedTasks.add("task-" + taskId + ": " + e.getMessage());
                latch.countDown();
            }
        }

        // 等所有任务完成（最多 30 秒）
        try {
            boolean finished = latch.await(30, TimeUnit.SECONDS);
            if (!finished) {
                log.warn("⚠️ 超时：{}/{} 个任务未完成", latch.getCount(), count);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("✅ 场景1完成：{} 个任务，耗时 {}ms，{} 个被拒绝",
                completed.get(), elapsed, rejectedTasks.size());

        return new StressResult(count, completed.get(), elapsed, rejectedTasks);
    }

    // ==================== 场景 2：Redis SETNX 抢锁 ====================

    /**
     * threads 个线程同时抢同一把分布式锁，只有 1 个能成功。
     * 模拟高并发派单时配送员被锁的场景。
     *
     * <p>观察点：
     * <ul>
     *   <li>只有 1 个线程返回 "got lock"</li>
     *   <li>其他 threads-1 个线程返回 "locked by another"</li>
     *   <li>用 Lua 脚本释放，不会误删别人的锁</li>
     * </ul>
     */
    public StressResult stressRedisLock(int threads) {
        long start = System.currentTimeMillis();
        String lockKey = "stress:lock:courier:999";
        AtomicInteger gotLock = new AtomicInteger(0);
        AtomicInteger blocked = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threads);

        log.info("🔒 场景2：{} 个线程同时抢锁 {}", threads, lockKey);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    String lockValue = UUID.randomUUID().toString();
                    Boolean locked = stringRedisTemplate.opsForValue()
                            .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(10));

                    if (Boolean.TRUE.equals(locked)) {
                        gotLock.incrementAndGet();
                        // 模拟持锁业务（50ms）
                        Thread.sleep(50);
                        // Lua 原子释放
                        stringRedisTemplate.execute(
                                new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                                        "if redis.call('get',KEYS[1])==ARGV[1] then " +
                                        "return redis.call('del',KEYS[1]) else return 0 end",
                                        Long.class),
                                java.util.List.of(lockKey), lockValue);
                    } else {
                        blocked.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("锁竞争异常: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            }, "lock-competitor-" + i).start();
        }

        try {
            latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("✅ 场景2完成：{} 个抢锁，{} 个拿到锁，{} 个被挡，耗时 {}ms",
                threads, gotLock.get(), blocked.get(), elapsed);

        return new StressResult(threads, gotLock.get() + blocked.get(), elapsed,
                List.of("gotLock=" + gotLock.get(), "blocked=" + blocked.get()));
    }

    // ==================== 场景 3：触发 GC ====================

    /**
     * 创建 objects 个临时字符串对象，用完后立即丢弃。
     * 配合 JVM GC 日志观察 Young GC / Full GC 行为。
     *
     * <p>JVM 参数（加到启动命令）：
     * <pre>
     * -Xms256m -Xmx256m                          # 限制堆大小，让 GC 更容易触发
     * -Xlog:gc*:file=logs/gc.log::filecount=5,filesize=10m  # GC 日志输出到文件
     * </pre>
     *
     * <p>分析命令：
     * <pre>
     * # 看 GC 次数和耗时
     * grep "GC(" logs/gc.log | head -20
     *
     * # 统计 Young GC vs Full GC
     * grep -c "Pause Young" logs/gc.log
     * grep -c "Pause Full" logs/gc.log
     * </pre>
     */
    public StressResult stressGC(int objects) {
        long start = System.currentTimeMillis();

        log.info("🗑️  场景3：创建 {} 个临时对象，观察 GC 日志...", objects);

        // 分批创建，每批 100w，防止 OOM
        int batchSize = 1_000_000;
        int created = 0;

        for (int batch = 0; batch < objects / batchSize; batch++) {
            List<String> garbage = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                // 每个 String 对象约 40~80 字节（含 char[]），100w 个 ≈ 60MB
                garbage.add("order-" + i + "-" + UUID.randomUUID().toString().substring(0, 8));
            }
            created += batchSize;

            if ((batch + 1) % 5 == 0) {
                log.info("  已创建 {} 个对象，堆内存: {}MB / {}MB",
                        created,
                        Runtime.getRuntime().totalMemory() / 1024 / 1024,
                        Runtime.getRuntime().maxMemory() / 1024 / 1024);
            }
            // 循环结束，garbage 引用消失，这批对象变成垃圾
        }

        // 手动触发一次 GC，对比前后的内存
        long beforeGC = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.gc();
        long afterGC = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long elapsed = System.currentTimeMillis() - start;
        log.info("✅ 场景3完成：创建 {} 个对象，耗时 {}ms，GC 释放 {}MB",
                created, elapsed, (beforeGC - afterGC) / 1024 / 1024);

        return new StressResult(objects, created, elapsed,
                List.of("gcFreedMB=" + (beforeGC - afterGC) / 1024 / 1024));
    }

    // ==================== 结果 DTO ====================

    public record StressResult(
            int requested,
            int completed,
            long elapsedMs,
            List<String> details) {
    }
}
