package com.example.logistics.lastmile.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.logistics.lastmile.config.RedisLuaScripts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.logistics.lastmile.dto.CreateOrderRequest;
import com.example.logistics.lastmile.dto.OrderNotification;
import com.example.logistics.lastmile.event.OrderStatusChangedEvent;
import com.example.logistics.lastmile.entity.Courier;
import com.example.logistics.lastmile.entity.CourierStatus;
import com.example.logistics.lastmile.entity.Order;
import com.example.logistics.lastmile.entity.OrderStatus;
import com.example.logistics.lastmile.exception.CourierLockedException;
import com.example.logistics.lastmile.exception.CourierNotAvailableException;
import com.example.logistics.lastmile.exception.CourierNotFoundException;
import com.example.logistics.lastmile.exception.IllegalStatusTransitionException;
import com.example.logistics.lastmile.exception.OrderNotFoundException;
import com.example.logistics.lastmile.messaging.OrderMessageProducer;
import com.example.logistics.lastmile.repository.CourierRepository;
import com.example.logistics.lastmile.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CourierRepository courierRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderMessageProducer messageProducer;
    private final CacheProtectionService cacheProtection;

    // OrderSearchService 在 dev 环境不可用（需要 ES），通过 @Autowired(required=false) 注入
    // 相关文件：OrderSearchService.java.bak, OrderSearchRepository.java.bak
    // private OrderSearchService orderSearchService;  // 已临时移除

    @Transactional(rollbackFor = Exception.class)
    public Order create(CreateOrderRequest request) {
        Order order = new Order();
        order.setCustomerName(request.getCustomerName());
        order.setAddress(request.getAddress());
        order.setPhone(request.getPhone());
        order.setStatus(OrderStatus.CREATED);
        order.setCreatedAt(java.time.LocalDateTime.now());

        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(saved, "订单已创建"));
        notify("订单已创建", saved);  // RabbitMQ 消息

        // 新订单 ID 加入布隆过滤器 + 预热缓存
        cacheProtection.onOrderCreated(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Order> findPage(int page, int size) {
        return orderRepository.findAll(PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Order findById(Long id) {
        // 走三层防护：布隆 → 缓存(带互斥锁) → DB
        Order order = cacheProtection.queryById(id);
        if (order == null) {
            throw new OrderNotFoundException();
        }
        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> findByCourierId(Long courierId) {
        return orderRepository.findByCourierId(courierId);
    }

    @Transactional(readOnly = true)
    public Page<Order> findByCourierId(Long courierId, Pageable pageable) {
        return orderRepository.findByCourierId(courierId, pageable);
    }

    @Transactional(rollbackFor = Exception.class)
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
            // 订单完成，释放配送员
            Courier courier = order.getCourier();
            if (courier != null) {
                courier.setStatus(CourierStatus.AVAILABLE);
                courierRepository.save(courier);
            }
        } else {
            throw new IllegalStatusTransitionException();
        }

        Order saved = orderRepository.save(order);
        cacheProtection.evict(id);  // 状态变了，缓存失效
        eventPublisher.publishEvent(new OrderStatusChangedEvent(saved,
                "订单状态变更: " + currentStatus + " → " + newStatus));
        notify("订单状态变更: " + currentStatus + " → " + newStatus, saved);
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public Order assignCourier(Long orderId, Long courierId) {
        // 1. 锁外：纯读操作不占锁，先查订单和配送员是否存在
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException());

        Courier courier = courierRepository.findById(courierId)
                .orElseThrow(() -> new CourierNotFoundException());

        // 2. 获取配送员锁——只保护"状态校验 + 修改"这段需要互斥的逻辑
        String lockKey = "lock:courier:" + courierId;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(10));

        if (Boolean.FALSE.equals(locked)) {
            throw new CourierLockedException();
        }

        try {
            // 3. 锁内：re-check 状态（锁外读到的是快照，进来时可能已变）
            if (courier.getStatus() != CourierStatus.AVAILABLE) {
                throw new CourierNotAvailableException();
            }

            order.setCourier(courier);
            order.setStatus(OrderStatus.ASSIGNED);
            courier.setStatus(CourierStatus.BUSY);

            courierRepository.save(courier);
            Order saved = orderRepository.save(order);
            cacheProtection.evict(orderId);
            eventPublisher.publishEvent(new OrderStatusChangedEvent(saved,
                    "订单已分配给配送员 #" + courierId));
            notify("订单已分配给配送员 #" + courierId, saved);
            return saved;

        } finally {
            stringRedisTemplate.execute(
                    RedisLuaScripts.RELEASE_LOCK,
                    List.of(lockKey),
                    lockValue);
        }
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
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
        cacheProtection.evict(id);  // 取消后缓存失效
        eventPublisher.publishEvent(new OrderStatusChangedEvent(saved, "订单已取消"));
        notify("订单已取消", saved);
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new OrderNotFoundException();
        }

        orderRepository.deleteById(id);
        cacheProtection.evict(id);  // 删除后清除缓存
        // ES 同步已临时移除（dev 环境无 ES），恢复时取消注释下面三行
        // if (orderSearchService != null) {
        //     orderSearchService.deleteById(id);
        // }
    }

    @Transactional(readOnly = true)
    public Map<OrderStatus, Long> getOrderStats() {
        return orderRepository.countByStatus().stream()
                .collect(Collectors.toMap(
                        row -> (OrderStatus) row[0],
                        row -> (Long) row[1]
                ));
    }

    @Transactional(readOnly = true)
    public List<String> findAllCustomerNames() {
        return orderRepository.findDistinctCustomerNames();
    }

    // ==================== 内部方法 ====================

    /**
     * 构建 OrderNotification 并发送到 RabbitMQ。
     * Spring Event（eventPublisher）负责 JVM 内部解耦，
     * RabbitMQ（messageProducer）负责跨服务通知。
     */
    private void notify(String description, Order order) {
        try {
            OrderNotification notification = new OrderNotification(
                    order.getId(),
                    order.getStatus().name(),
                    description,
                    LocalDateTime.now().toString(),
                    null);  // messageId 由 Producer 自动生成
            messageProducer.sendOrderNotification(notification);
        } catch (Exception e) {
            // RabbitMQ 不可用不能影响业务，只打日志
            log.warn("RabbitMQ 消息发送失败，不影响业务流程: {}", e.getMessage());
        }
    }
}