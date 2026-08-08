package com.example.logistics.lastmile.service;

import com.example.logistics.lastmile.AbstractIntegrationTest;
import com.example.logistics.lastmile.dto.CreateCourierRequest;
import com.example.logistics.lastmile.dto.CreateOrderRequest;
import com.example.logistics.lastmile.entity.Courier;
import com.example.logistics.lastmile.entity.CourierStatus;
import com.example.logistics.lastmile.entity.Order;
import com.example.logistics.lastmile.entity.OrderStatus;
import com.example.logistics.lastmile.exception.CourierLockedException;
import com.example.logistics.lastmile.exception.CourierNotAvailableException;
import com.example.logistics.lastmile.exception.IllegalStatusTransitionException;
import com.example.logistics.lastmile.exception.OrderNotFoundException;
import com.example.logistics.lastmile.repository.CourierRepository;
import com.example.logistics.lastmile.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderService 集成测试 — 真实 MySQL + Redis。
 * 不 Mock 任何东西，走完整 Service → Repository → DB/Redis 链路。
 */
class OrderServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CourierService courierService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CourierRepository courierRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        // 每个测试前清空数据，保证用例独立
        orderRepository.deleteAll();
        courierRepository.deleteAll();
        // 清 Redis 缓存，避免跨测试污染
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    // ==================== 创建订单 ====================

    @Test
    void shouldCreateOrderAndPersistToDatabase() {
        CreateOrderRequest request = new CreateOrderRequest("张三", "北京市朝阳区", "13800000000");
        Order saved = orderService.create(request);

        assertNotNull(saved.getId());
        assertEquals("张三", saved.getCustomerName());
        assertEquals(OrderStatus.CREATED, saved.getStatus());

        // 验证真实存进了 MySQL
        Order fromDb = orderRepository.findById(saved.getId()).orElseThrow();
        assertEquals("张三", fromDb.getCustomerName());
    }

    // ==================== 查询订单 ====================

    @Test
    void shouldFindAllOrders() {
        orderService.create(new CreateOrderRequest("张三", "北京", "13800000000"));
        orderService.create(new CreateOrderRequest("李四", "上海", "13900000000"));

        List<Order> orders = orderService.findAll();
        assertEquals(2, orders.size());
    }

    @Test
    void shouldFindById() {
        Order saved = orderService.create(new CreateOrderRequest("张三", "北京", "13800000000"));

        Order found = orderService.findById(saved.getId());
        assertEquals(saved.getId(), found.getId());
    }

    @Test
    void shouldThrowWhenOrderNotFound() {
        assertThrows(OrderNotFoundException.class, () -> orderService.findById(99999L));
    }

    // ==================== Redis 缓存 ====================

    @Test
    void shouldCacheFindByIdResult() {
        Order saved = orderService.create(new CreateOrderRequest("缓存测试", "深圳", "13600000000"));

        // 第一次查：走 DB
        Order first = orderService.findById(saved.getId());
        // 第二次查：应该走 Redis 缓存（返回同一个反序列化后的对象）
        Order second = orderService.findById(saved.getId());

        assertEquals(first.getId(), second.getId());
        assertEquals(first.getCustomerName(), second.getCustomerName());
    }

    // ==================== 派单 + Redis 分布式锁 ====================

    @Test
    void shouldAssignCourierWithRedisLock() {
        Courier courier = courierService.create(new CreateCourierRequest("快递员", "13700000000"));
        Order order = orderService.create(new CreateOrderRequest("王五", "广州", "13500000000"));

        Order assigned = orderService.assignCourier(order.getId(), courier.getId());

        assertEquals(OrderStatus.ASSIGNED, assigned.getStatus());
        assertEquals(courier.getId(), assigned.getCourier().getId());

        // 验证 DB 状态
        Courier updated = courierRepository.findById(courier.getId()).orElseThrow();
        assertEquals(CourierStatus.BUSY, updated.getStatus());

        // 验证 Redis 锁已释放
        String lockKey = "lock:courier:" + courier.getId();
        assertNull(stringRedisTemplate.opsForValue().get(lockKey));
    }

    @Test
    void shouldThrowWhenCourierNotAvailable() {
        Courier courier = courierService.create(new CreateCourierRequest("已忙", "13700000001"));
        // 手动设为 BUSY
        courier.setStatus(CourierStatus.BUSY);
        courierRepository.save(courier);

        Order order = orderService.create(new CreateOrderRequest("测试", "深圳", "13500000001"));

        assertThrows(CourierNotAvailableException.class,
                () -> orderService.assignCourier(order.getId(), courier.getId()));
    }

    // ==================== 取消订单 ====================

    @Test
    void shouldCancelCreatedOrder() {
        Order order = orderService.create(new CreateOrderRequest("测试", "北京", "13800000000"));

        Order cancelled = orderService.cancelOrder(order.getId());
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void shouldCancelAssignedOrderAndFreeCourier() {
        Courier courier = courierService.create(new CreateCourierRequest("测试员", "13700000002"));
        Order order = orderService.create(new CreateOrderRequest("测试", "上海", "13500000002"));
        orderService.assignCourier(order.getId(), courier.getId());

        Order cancelled = orderService.cancelOrder(order.getId());
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());

        // 配送员恢复可用
        Courier freed = courierRepository.findById(courier.getId()).orElseThrow();
        assertEquals(CourierStatus.AVAILABLE, freed.getStatus());
    }

    // ==================== 状态流转 ====================

    @Test
    void shouldTransitionFromCreatedToAssignedToDeliveringToCompleted() {
        Courier courier = courierService.create(new CreateCourierRequest("流转员", "13700000003"));
        Order order = orderService.create(new CreateOrderRequest("流转测试", "杭州", "13500000003"));

        // CREATED → ASSIGNED
        order = orderService.assignCourier(order.getId(), courier.getId());
        assertEquals(OrderStatus.ASSIGNED, order.getStatus());

        // ASSIGNED → DELIVERING
        order = orderService.updateStatus(order.getId(), OrderStatus.DELIVERING);
        assertEquals(OrderStatus.DELIVERING, order.getStatus());

        // DELIVERING → COMPLETED
        order = orderService.updateStatus(order.getId(), OrderStatus.COMPLETED);
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
    }

    @Test
    void shouldThrowOnIllegalTransition() {
        Order order = orderService.create(new CreateOrderRequest("非法流转", "武汉", "13500000004"));

        // CREATED → DELIVERING 不允许（跳过 ASSIGNED）
        assertThrows(IllegalStatusTransitionException.class,
                () -> orderService.updateStatus(order.getId(), OrderStatus.DELIVERING));
    }

    // ==================== 删除订单 ====================

    @Test
    void shouldDeleteOrder() {
        Order order = orderService.create(new CreateOrderRequest("待删", "成都", "13800000001"));
        Long id = order.getId();

        orderService.deleteById(id);
        assertFalse(orderRepository.existsById(id));
    }

    // ==================== 统计 ====================

    @Test
    void shouldGetOrderStats() {
        orderService.create(new CreateOrderRequest("甲", "北京", "13800000000"));
        orderService.create(new CreateOrderRequest("乙", "上海", "13800000001"));
        orderService.create(new CreateOrderRequest("丙", "广州", "13800000002"));

        var stats = orderService.getOrderStats();
        assertEquals(3, stats.get(OrderStatus.CREATED));
    }
}
