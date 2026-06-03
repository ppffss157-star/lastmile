package com.example.logistics.lastmile.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.example.logistics.lastmile.dto.CreateOrderRequest;
import com.example.logistics.lastmile.entity.Courier;
import com.example.logistics.lastmile.entity.CourierStatus;
import com.example.logistics.lastmile.entity.Order;
import com.example.logistics.lastmile.entity.OrderStatus;
import com.example.logistics.lastmile.exception.CourierLockedException;
import com.example.logistics.lastmile.exception.CourierNotAvailableException;
import com.example.logistics.lastmile.exception.OrderNotFoundException;
import com.example.logistics.lastmile.repository.CourierRepository;
import com.example.logistics.lastmile.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CourierRepository courierRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private OrderService orderService;

    // ==================== 创建订单 ====================

    @Test
    void shouldCreateOrder() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerName("张三");
        request.setAddress("北京");
        request.setPhone("13800000000");

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });

        Order result = orderService.create(request);

        assertNotNull(result.getId());
        assertEquals("张三", result.getCustomerName());
        assertEquals(OrderStatus.CREATED, result.getStatus());
    }

    // ==================== 查询订单 ====================

    @Test
    void shouldFindByIdWhenExists() {
        Order order = new Order(1L, "李四", "上海", "13900000000", OrderStatus.CREATED, null, null);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Order result = orderService.findById(1L);

        assertEquals("李四", result.getCustomerName());
    }

    @Test
    void shouldThrowWhenOrderNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.findById(999L));
    }

    // ==================== 派单（分布式锁） ====================

    @Test
    void shouldAssignCourierWhenLockAcquired() {
        // 准备 Redis mock：opsForValue() → valueOperations
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // 抢锁成功
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(10))))
                .thenReturn(true);

        Order order = new Order(1L, "张三", "北京", "138", OrderStatus.CREATED, null, null);
        Courier courier = new Courier(5L, "配送员1", "136", CourierStatus.AVAILABLE, null);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(courierRepository.findById(5L)).thenReturn(Optional.of(courier));
        when(courierRepository.save(any(Courier.class))).thenReturn(courier);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        Order result = orderService.assignCourier(1L, 5L);

        assertEquals(OrderStatus.ASSIGNED, result.getStatus());
        assertEquals(5L, result.getCourier().getId());
        assertEquals(CourierStatus.BUSY, courier.getStatus());
    }

    @Test
    void shouldFailWhenLockNotAcquired() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // 抢锁失败：别的线程已经持有锁
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(10))))
                .thenReturn(false);

        assertThrows(CourierLockedException.class,
                () -> orderService.assignCourier(1L, 5L),
                "配送员正在被其他订单派单中，请稍后再试");

        // 抢锁失败后，不应该查数据库
        verify(orderRepository, never()).findById(anyLong());
        verify(courierRepository, never()).findById(anyLong());
    }

    @Test
    void shouldFailWhenCourierNotAvailable() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(10))))
                .thenReturn(true);

        Order order = new Order(1L, "张三", "北京", "138", OrderStatus.CREATED, null, null);
        Courier courier = new Courier(5L, "配送员1", "136", CourierStatus.BUSY, null);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(courierRepository.findById(5L)).thenReturn(Optional.of(courier));

        assertThrows(CourierNotAvailableException.class,
                () -> orderService.assignCourier(1L, 5L),
                "配送员当前不可接单");

        // 配送员 BUSY，不应该保存
        verify(courierRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    // ==================== 取消订单 ====================

    @Test
    void shouldCancelCreatedOrder() {
        Order order = new Order(1L, "张三", "北京", "138", OrderStatus.CREATED, null, null);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        Order result = orderService.cancelOrder(1L);

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
    }

    @Test
    void shouldCancelAssignedOrderAndFreeCourier() {
        Courier courier = new Courier(5L, "配送员1", "136", CourierStatus.BUSY, null);
        Order order = new Order(1L, "张三", "北京", "138", OrderStatus.ASSIGNED, courier, null);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(courierRepository.save(any(Courier.class))).thenReturn(courier);

        Order result = orderService.cancelOrder(1L);

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        assertEquals(CourierStatus.AVAILABLE, courier.getStatus());
    }
}
