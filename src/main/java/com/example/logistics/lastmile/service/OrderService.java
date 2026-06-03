package com.example.logistics.lastmile.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.script.DefaultRedisScript;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.logistics.lastmile.dto.CreateOrderRequest;
import com.example.logistics.lastmile.dto.OrderNotification;
import com.example.logistics.lastmile.entity.Courier;
import com.example.logistics.lastmile.entity.CourierStatus;
import com.example.logistics.lastmile.entity.Order;
import com.example.logistics.lastmile.entity.OrderStatus;
import com.example.logistics.lastmile.exception.CourierLockedException;
import com.example.logistics.lastmile.exception.CourierNotAvailableException;
import com.example.logistics.lastmile.exception.CourierNotFoundException;
import com.example.logistics.lastmile.exception.IllegalStatusTransitionException;
import com.example.logistics.lastmile.exception.OrderNotFoundException;
import com.example.logistics.lastmile.repository.CourierRepository;
import com.example.logistics.lastmile.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CourierRepository courierRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final SimpMessagingTemplate messagingTemplate; // WebSocket 消息推送

    public Order create(CreateOrderRequest request) {
        Order order = new Order();
        order.setCustomerName(request.getCustomerName());
        order.setAddress(request.getAddress());
        order.setPhone(request.getPhone());
        order.setStatus(OrderStatus.CREATED);
        order.setCreatedAt(java.time.LocalDateTime.now());

        Order saved = orderRepository.save(order);
        sendOrderStatusNotification(saved, "订单已创建");
        return saved;
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public Page<Order> findPage(int page, int size) {
        return orderRepository.findAll(PageRequest.of(page, size));
    }

    @Cacheable(value = "order", key = "#id")
    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException());
    }

    public List<Order> findByCourierId(Long courierId) {
        return orderRepository.findByCourierId(courierId);
    }

    @CacheEvict(value = "order", key = "#id")
    public Order updateStatus(Long id, OrderStatus newStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException());

        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == OrderStatus.CREATED && newStatus == OrderStatus.ASSIGNED) {
            order.setStatus(newStatus);
        } else if (currentStatus == OrderStatus.ASSIGNED && newStatus == OrderStatus.DELIVERING) {
            order.setStatus(newStatus);
        } else if (currentStatus == OrderStatus.DELIVERING && newStatus == OrderStatus.COMPLETED) {
            order.setStatus(newStatus);
        } else {
            throw new IllegalStatusTransitionException();
        }

        Order saved = orderRepository.save(order);
        sendOrderStatusNotification(saved,
                "订单状态变更: " + currentStatus + " → " + newStatus);
        return saved;
    }

    @CacheEvict(value = "order", key = "#orderId")
    public Order assignCourier(Long orderId, Long courierId) {
        // 1. 生成这把锁的唯一标识（UUID），防止误删别人的锁
        String lockKey = "lock:courier:" + courierId;
        String lockValue = UUID.randomUUID().toString();

        // 2. 尝试获取锁：setIfAbsent = SETNX，10 秒后自动过期（防止死锁）
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(10));

        if (Boolean.FALSE.equals(locked)) {
            throw new CourierLockedException();
        }

        try {
            // ===== 以下和原来一样：查订单、查配送员、校验、派单 =====
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException());

            Courier courier = courierRepository.findById(courierId)
                    .orElseThrow(() -> new CourierNotFoundException());

            if (courier.getStatus() != CourierStatus.AVAILABLE) {
                throw new CourierNotAvailableException();
            }

            order.setCourier(courier);
            order.setStatus(OrderStatus.ASSIGNED);

            courier.setStatus(CourierStatus.BUSY);

            courierRepository.save(courier);
            Order saved = orderRepository.save(order);
            sendOrderStatusNotification(saved,
                    "订单已分配给配送员 #" + courierId);
            return saved;

        } finally {
            // 3. 原子释放锁：Lua 脚本把 get+判断+delete 打包发给 Redis 一口气执行
            String script = "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                            "return redis.call('DEL', KEYS[1]) else return 0 end";
            stringRedisTemplate.execute(
                    new DefaultRedisScript<>(script, Long.class),
                    List.of(lockKey),
                    lockValue);
        }
    }

    @CacheEvict(value = "order", key = "#id")
    public Order cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException());

        OrderStatus status = order.getStatus();

        if (status != OrderStatus.CREATED && status != OrderStatus.ASSIGNED) {
            throw new IllegalStatusTransitionException("当前订单状态不允许取消");
        }

        Courier courier = order.getCourier();

        if (courier != null) {
            courier.setStatus(CourierStatus.AVAILABLE);
            courierRepository.save(courier);
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        sendOrderStatusNotification(saved, "订单已取消");
        return saved;
    }

    @CacheEvict(value = "order", key = "#id")
    public void deleteById(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new OrderNotFoundException();
        }

        orderRepository.deleteById(id);
    }

    /**
     * 发送订单状态变更的 WebSocket 推送消息。
     * <p>
     * SimpMessagingTemplate 是 Spring 的消息推送中枢，一行代码就能
     * 把任意 Java 对象（自动序列化为 JSON）发送到指定目的地。
     * 所有订阅了该目的地的客户端都会立即收到消息。
     *
     * @param order 变更后的订单对象
     * @param event 事件描述（如 "订单已创建"、"配送员已分配"）
     */
    private void sendOrderStatusNotification(Order order, String event) {
        OrderNotification payload = new OrderNotification(
                order.getId(),
                order.getStatus().name(),
                event,
                LocalDateTime.now().toString());

        // 推送到全局频道（所有订单列表页面都能收到）
        messagingTemplate.convertAndSend("/topic/orders", payload);
        // 同时推送到订单专属频道（仅查看该订单详情的页面能收到）
        messagingTemplate.convertAndSend("/topic/orders/" + order.getId(), payload);
    }

    @Scheduled(fixedRate = 60000)
    public void autoCancelStaleOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        List<Order> staleOrders = orderRepository
                .findByStatusAndCreatedAtBefore(OrderStatus.CREATED, threshold);

        for (Order order : staleOrders) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
        }
    }
}