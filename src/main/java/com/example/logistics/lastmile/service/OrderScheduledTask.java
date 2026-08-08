package com.example.logistics.lastmile.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.logistics.lastmile.entity.Order;
import com.example.logistics.lastmile.entity.OrderStatus;
import com.example.logistics.lastmile.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 订单定时任务 — 从 OrderService 拆出来，消除 @Lazy 自注入。
 *
 * <p>原来 autoCancelOldOrders 在 OrderService 里，为了调自己的 cancelOrder()
 * 需要用 @Lazy @Autowired 注入自己（字段注入 + 构造注入混用）。
 * 拆到独立类后干净注入 OrderService 即可，AOP 代理正常生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduledTask {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Scheduled(fixedRate = 60000)
    public void autoCancelOldOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        List<Order> oldOrders = orderRepository
                .findByStatusAndCreatedAtBefore(OrderStatus.CREATED, threshold);

        for (Order order : oldOrders) {
            orderService.cancelOrder(order.getId());
        }
    }
}
