package com.example.courier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 配送员服务 — 独立部署的 Spring Boot 应用。
 *
 * 职责：配送员 CRUD、派单、状态管理。
 * 不再管理订单（那是 order-service 的事）。
 */
@SpringBootApplication
public class CourierServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourierServiceApplication.class, args);
    }
}
