package com.example.logistics.lastmile.controller;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 线程池监控端点 —— 面试时能回答"线程池参数怎么定的"的底气来源。
 *
 * <h2>关键指标</h2>
 * <table>
 *   <tr><td>activeCount</td>  <td>正在干活的线程数。接近 poolSize = 忙</td></tr>
 *   <tr><td>poolSize</td>     <td>当前线程数（含空闲）。超过 coreSize = 队列满了正在扩容</td></tr>
 *   <tr><td>queueSize</td>    <td>等待队列积压。持续增长 = 处理能力跟不上</td></tr>
 *   <tr><td>completedTasks</td><td>已完成任务数。用于算吞吐量（任务/秒）</td></tr>
 *   <tr><td>coreSize/maxSize</td><td>配置值，对比 poolSize 看是否在扩容</td></tr>
 * </table>
 *
 * <h2>面试怎么说</h2>
 * <pre>
 * 面试官："你线程池参数怎么定的？"
 * 你：   "coreSize 设 CPU 核数，maxSize 设核数×2。
 *         然后压测看几个指标：queueSize 持续增长说明处理能力不够，调大 core；
 *         poolSize 经常打满 max 说明峰值超预期，要扩容或加机器。
 *         不是拍脑袋定的，是压测数据调的。"
 * </pre>
 */
@RestController
@RequestMapping("/actuator")
public class ThreadPoolMonitorController {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolMonitorController.class);

    private final ThreadPoolTaskExecutor taskExecutor;

    public ThreadPoolMonitorController(
            @Qualifier("taskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    @GetMapping("/thread-pools")
    public Map<String, Object> threadPoolStats() {
        ThreadPoolExecutor pool = taskExecutor.getThreadPoolExecutor();

        Map<String, Object> stats = Map.ofEntries(
                // 配置
                Map.entry("corePoolSize", pool.getCorePoolSize()),
                Map.entry("maximumPoolSize", pool.getMaximumPoolSize()),
                Map.entry("keepAliveSeconds", taskExecutor.getKeepAliveSeconds()),

                // 实时状态
                Map.entry("poolSize", pool.getPoolSize()),              // 当前线程数
                Map.entry("activeCount", pool.getActiveCount()),         // 正在干活
                Map.entry("largestPoolSize", pool.getLargestPoolSize()), // 历史峰值的线程数

                // 队列
                Map.entry("queueSize", pool.getQueue().size()),
                Map.entry("queueRemainingCapacity", pool.getQueue().remainingCapacity()),
                Map.entry("queueType", pool.getQueue().getClass().getSimpleName()),

                // 吞吐
                Map.entry("completedTaskCount", pool.getCompletedTaskCount()),
                Map.entry("taskCount", pool.getTaskCount()),             // 总共接了多少任务

                // 拒绝策略
                Map.entry("rejectedPolicy", pool.getRejectedExecutionHandler().getClass().getSimpleName())
        );

        log.info("📊 线程池状态: poolSize={}, active={}, queue={}/{}, completed={}",
                stats.get("poolSize"), stats.get("activeCount"),
                stats.get("queueSize"),
                pool.getCorePoolSize() + pool.getQueue().size() + pool.getMaximumPoolSize(),
                stats.get("completedTaskCount"));

        return stats;
    }
}
