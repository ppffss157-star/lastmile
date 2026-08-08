package com.example.logistics.lastmile.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 异步任务配置——开启 @Async 并配置线程池。
 *
 * <h3>@EnableAsync 做了什么？</h3>
 * Spring 在启动时扫描所有带 @Async 的方法，用 AOP 代理拦截这些方法的调用，
 * 把实际执行交给线程池。调用者不等方法跑完就直接返回。
 *
 * <h3>比喻</h3>
 * <ul>
 *   <li><b>不加 @Async</b> → 前台接待员帮你查资料，后面排队的全等着</li>
 *   <li><b>加了 @Async</b> → 接待员喊一句"小王处理一下"，然后接着接待下一位</li>
 * </ul>
 *
 * <h3>线程池参数（面试高频）</h3>
 * <table>
 *   <tr><td><b>corePoolSize (4)</b></td>  <td>常驻线程数。不忙时这 4 个线程闲着等活儿，不销毁</td></tr>
 *   <tr><td><b>maxPoolSize (8)</b></td>    <td>高峰期最多开到 8 个线程。超过 4 个的新线程闲了 60 秒就销毁</td></tr>
 *   <tr><td><b>queueCapacity (100)</b></td><td>等待队列。线程全忙时，新任务先排队，排满 100 个后再开新线程</td></tr>
 * </table>
 *
 * <h3>为什么不直接用默认的 SimpleAsyncTaskExecutor？</h3>
 * 默认那个来一个任务就 new 一个线程，高并发下能把系统资源吃光。
 * ThreadPoolTaskExecutor 复用线程，有上限保护。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 创建一个名为 "event-" 前缀的线程池，专用于事件监听。
     *
     * <p>这个 Bean 的名字是 "taskExecutor"，Spring 会优先用它作为 @Async 的默认执行器。
     * 如果有多种异步任务，可以建多个线程池，@Async("线程池名") 指定用哪个。
     *
     * @return 配置好的线程池
     */
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);                // 常驻 4 个线程
        executor.setMaxPoolSize(8);                 // 最多 8 个
        executor.setQueueCapacity(100);             // 等待队列 100 个
        executor.setKeepAliveSeconds(60);           // 超出 core 的线程闲 60 秒后回收
        executor.setThreadNamePrefix("event-");     // 线程名：event-1, event-2...
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        // ↑ 队列满了直接抛 RejectedExecutionException，显式告警而非静默降级

        executor.initialize();
        return executor;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
