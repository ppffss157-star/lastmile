package com.example.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 订单服务 — 独立部署的 Spring Boot 应用。
 *
 * 职责：订单 CRUD、Saga 分布式事务编排。
 * 不再管理配送员（那是 courier-service 的事）。
 *
 * @EnableAspectJAutoProxy — 开启 AOP 代理，Resilience4j 注解（@Retry/@CircuitBreaker/@Bulkhead）依赖它
 */
@SpringBootApplication
@EnableAspectJAutoProxy
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
