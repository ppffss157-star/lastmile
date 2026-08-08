package com.example.logistics.lastmile.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.logistics.lastmile.service.ConcurrencyStressService;
import com.example.logistics.lastmile.service.ConcurrencyStressService.StressResult;

/**
 * 并发压测控制器 —— 三个端点对应三个场景。
 *
 * <h2>实战流程</h2>
 * <ol>
 *   <li>开两个终端：一个跑压测，一个反复调 /actuator/thread-pools 看变化</li>
 *   <li>先用小并发（count=20）观察 poolSize 不变、queue 积压</li>
 *   <li>再用大并发（count=200）观察 poolSize 涨到 8、CallerRunsPolicy 触发</li>
 *   <li>调 GC 场景时提前加好 JVM 参数，压完看 gc.log</li>
 * </ol>
 */
@RestController
@RequestMapping("/actuator/stress")
public class ConcurrencyStressController {

    private final ConcurrencyStressService stressService;

    public ConcurrencyStressController(ConcurrencyStressService stressService) {
        this.stressService = stressService;
    }

    /**
     * 场景 1：打满线程池。
     * 默认 50 个并发异步任务。
     */
    @GetMapping("/async")
    public Map<String, Object> stressAsync(@RequestParam(defaultValue = "50") int count) {
        StressResult r = stressService.stressAsyncThreadPool(count);
        return Map.of(
                "scene", "async-thread-pool",
                "requested", r.requested(),
                "completed", r.completed(),
                "elapsedMs", r.elapsedMs(),
                "throughputPerSec", r.completed() * 1000L / Math.max(r.elapsedMs(), 1),
                "hint", "调 /actuator/thread-pools 看线程池变化"
        );
    }

    /**
     * 场景 2：Redis 分布式锁竞态。
     * 默认 30 个线程抢同一把锁。
     */
    @GetMapping("/redis-lock")
    public Map<String, Object> stressRedisLock(@RequestParam(defaultValue = "30") int threads) {
        StressResult r = stressService.stressRedisLock(threads);
        return Map.of(
                "scene", "redis-distributed-lock",
                "threads", r.requested(),
                "completed", r.completed(),
                "elapsedMs", r.elapsedMs(),
                "details", r.details(),
                "hint", "只有 1 个线程拿到锁，其他全部被挡——这就是 SETNX 的效果"
        );
    }

    /**
     * 场景 3：触发 GC。
     * 默认 500w 个临时对象。
     *
     * <p>启动前加 JVM 参数：
     * <pre>
     * -Xms256m -Xmx256m
     * -Xlog:gc*:file=logs/gc.log::filecount=5,filesize=10m
     * </pre>
     */
    @GetMapping("/gc")
    public Map<String, Object> stressGC(@RequestParam(defaultValue = "5000000") int objects) {
        StressResult r = stressService.stressGC(objects);
        return Map.of(
                "scene", "gc-trigger",
                "objects", r.requested(),
                "created", r.completed(),
                "elapsedMs", r.elapsedMs(),
                "details", r.details(),
                "hint", "看 logs/gc.log 分析 GC 行为"
        );
    }
}
