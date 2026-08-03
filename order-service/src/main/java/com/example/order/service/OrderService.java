package com.example.order.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.example.order.entity.Order;
import com.example.order.entity.OrderStatus;
import com.example.order.repository.OrderRepository;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 订单服务 — 核心业务逻辑。
 *
 * 和单体版的区别：
 * - assignCourier 不再是本地数据库操作，而是通过 HTTP 调 courier-service
 * - 没有 @Transactional 跨服务——分布式事务交给 Saga
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final RestClient restClient;

    @Transactional(rollbackFor = Exception.class)
    public Order create(String customerName, String address, String phone) {
        Order order = new Order();
        order.setCustomerName(customerName);
        order.setAddress(address);
        order.setPhone(phone);
        order.setStatus(OrderStatus.CREATED);
        order.setCreatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在: " + id));
    }

    /**
     * 派单 —— 通过 HTTP 调用 courier-service。
     *
     * 三层防护（注解从外到内）：
     * ① @Retry        — 网络抖了自动重试 3 次
     * ② @CircuitBreaker — 连续失败超 50% 熔断 10 秒，直接走 fallback
     * ③ @Bulkhead     — 最多 5 个并发，多了直接拒绝
     *
     * 注解可以堆叠，Resilience4j 按固定顺序执行：
     * Retry → CircuitBreaker → Bulkhead → 实际方法
     */
    @Retry(name = "courierService")
    @CircuitBreaker(name = "courierService", fallbackMethod = "assignCourierFallback")
    @Bulkhead(name = "courierService")
    @Transactional(rollbackFor = Exception.class)
    public Order assignCourier(Long orderId, Long courierId) {
        Order order = findById(orderId);

        // HTTP 调用 courier-service（base URL 在 RestClientConfig 里配好了）
        String response = restClient.post()
                .uri("/api/couriers/{id}/assign?orderId={orderId}",
                        courierId, orderId)
                .retrieve()
                .body(String.class);
        log.info("[order-service] 派单成功: {}", response);

        order.setCourierId(courierId);
        order.setStatus(OrderStatus.ASSIGNED);
        return orderRepository.save(order);
    }

    /**
     * 降级方法 —— 三种情况都会走到这里：
     * - Retry 重试 3 次全失败
     * - CircuitBreaker 熔断打开，直接短路
     * - Bulkhead 并发满了，拒绝新请求
     *
     * 方法签名必须和原方法一致，最后加一个 Throwable 参数接收异常。
     */
    public Order assignCourierFallback(Long orderId, Long courierId, Throwable e) {
        log.warn("[降级] 派单失败 orderId={}, courierId={}, 异常类型={}, 原因={}",
                orderId, courierId, e.getClass().getSimpleName(), e.getMessage());

        Order order = findById(orderId);
        order.setStatus(OrderStatus.CREATED);  // 回退到未分配
        return orderRepository.save(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public Order updateStatus(Long id, OrderStatus newStatus) {
        Order order = findById(id);
        order.setStatus(newStatus);
        return orderRepository.save(order);
    }
}
