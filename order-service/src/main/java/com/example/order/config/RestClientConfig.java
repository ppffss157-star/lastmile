package com.example.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

/**
 * RestClient — 微服务间 HTTP 通信工具。
 *
 * 两种模式，按 profile 切换：
 * - dev / docker-compose（默认）：@LoadBalanced + 服务名，通过 Nacos 发现 courier-service
 * - K8s：直连 URL（K8s 自带 Service DNS，不走 Nacos）
 *
 * Boot 3.x 的 RestClient.Builder 自动带 Observation（Tracing），
 * 不需要手动注入 ObservationRegistry。
 */
@Configuration
public class RestClientConfig {

    // ============================
    // dev / docker-compose：Nacos 服务发现
    // ============================

    @Bean
    @Profile("!k8s")
    @LoadBalanced
    RestClient.Builder loadBalancedBuilder() {
        return RestClient.builder();
    }

    @Bean
    @Profile("!k8s")
    RestClient restClient(@LoadBalanced RestClient.Builder builder) {
        return builder
                .baseUrl("http://courier-service")  // 服务名，由 LoadBalancer + Nacos 解析
                .build();
    }

    // ============================
    // K8s：直连 K8s Service DNS
    // ============================

    @Bean
    @Profile("k8s")
    RestClient directRestClient(
            @Value("${courier.service.url:http://courier-service:9092}") String url,
            RestClient.Builder builder) {
        return builder.baseUrl(url).build();
    }
}
