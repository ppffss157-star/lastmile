package com.example.logistics.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Cloud Gateway 启动类。
 *
 * <p>和主应用的区别：
 * <ul>
 *   <li>没有 spring-boot-starter-webmvc —— 不能 @RestController</li>
 *   <li>用的是 WebFlux（Netty + Reactor 响应式）—— 非阻塞 I/O</li>
 *   <li>不是处理业务，而是转发请求 —— "路由器"不是"终端"</li>
 * </ul>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
