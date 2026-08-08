package com.example.logistics.lastmile.config;

import java.net.InetAddress;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Nacos 服务注册组件（手写，不用 Spring Cloud Alibaba）。
 *
 * <p>学习目的：理解服务注册的三件事——
 * <ol>
 *   <li><b>注册</b>：应用启动后向 Nacos 报告"我在这儿，地址是 xxx:9090"</li>
 *   <li><b>心跳</b>：Nacos 2.x 用 gRPC 长连接，心跳由客户端 SDK 自动维护，
 *     不需要手动调 sendHeartbeat()。断开连接 Nacos 自动踢掉实例。</li>
 *   <li><b>注销</b>：应用关闭前告诉 Nacos "我下线了"（优雅下线）</li>
 * </ol>
 *
 * <p>Nacos 1.x vs 2.x 的区别：
 * <ul>
 *   <li>1.x：HTTP 协议，手动调 sendHeartbeat()，服务端 15s 收不到心跳标记不健康</li>
 *   <li>2.x：gRPC 长连接代替心跳，客户端连上就代表活着，断连自动踢。大幅减少心跳请求量</li>
 * </ul>
 */
@Slf4j
@Component
public class NacosServiceRegistry {

    @Value("${spring.application.name:lastmile}")
    private String serviceName;

    @Value("${server.port:9090}")
    private int serverPort;

    @Value("${nacos.server-addr:localhost:8848}")
    private String nacosServerAddr;

    @Value("${nacos.namespace:}")
    private String namespace;

    private NamingService namingService;

    /**
     * 等 Spring 容器完全就绪后再注册。
     * ApplicationReadyEvent 比 @PostConstruct 晚——此时内嵌服务器已启动，接口可访问。
     *
     * <p>注册失败会重试 3 次（2s / 4s / 8s 指数退避），全部失败才打 ERROR。
     * 首次启动时注册不上是严重问题——其他服务完全不知道这个实例存在。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        int maxRetries = 3;
        long delayMs = 2000;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                Properties props = new Properties();
                props.setProperty("serverAddr", nacosServerAddr);
                if (namespace != null && !namespace.isEmpty()) {
                    props.setProperty("namespace", namespace);
                }

                namingService = NacosFactory.createNamingService(props);

                Instance instance = new Instance();
                instance.setIp(InetAddress.getLocalHost().getHostAddress());
                instance.setPort(serverPort);
                instance.setServiceName(serviceName);
                instance.setHealthy(true);
                instance.setWeight(1.0);
                instance.setEphemeral(true);   // 临时实例，gRPC 断连自动踢

                namingService.registerInstance(serviceName, instance);
                log.info("✅ 已注册到 Nacos: service={}, ip={}, port={}, serverAddr={}",
                        serviceName, instance.getIp(), serverPort, nacosServerAddr);
                return;

            } catch (Exception e) {
                if (attempt < maxRetries) {
                    log.warn("Nacos 注册失败，{}ms 后重试 (第 {}/{} 次): {}",
                            delayMs, attempt + 1, maxRetries, e.getMessage());
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    delayMs *= 2;  // 2s → 4s → 8s
                } else {
                    // 重试用尽：首次启动时这是致命的——Gateway 和 order-service 都找不到它
                    log.error("❌ Nacos 注册最终失败（已重试 {} 次），服务对外不可见！" +
                            " 请检查 Nacos 是否已启动: serverAddr={}, service={}",
                            maxRetries, nacosServerAddr, serviceName, e);
                }
            }
        }
    }

    /**
     * 应用关闭时从 Nacos 注销。
     * @PreDestroy 在 Bean 销毁前调用，给 Nacos 发最后的"再见"。
     */
    @PreDestroy
    public void deregister() {
        if (namingService == null) return;
        try {
            namingService.deregisterInstance(serviceName,
                    InetAddress.getLocalHost().getHostAddress(),
                    serverPort);
            log.info("👋 已从 Nacos 注销: service={}, port={}", serviceName, serverPort);
        } catch (Exception e) {
            log.warn("Nacos 注销失败: {}", e.getMessage());
        }
    }

    /**
     * 暴露 NamingService，方便其他地方查询服务列表（如自建负载均衡）。
     */
    public NamingService getNamingService() {
        return namingService;
    }
}
