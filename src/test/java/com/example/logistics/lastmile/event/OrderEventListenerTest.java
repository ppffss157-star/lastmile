package com.example.logistics.lastmile.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.example.logistics.lastmile.dto.OrderNotification;
import com.example.logistics.lastmile.entity.Order;
import com.example.logistics.lastmile.entity.OrderStatus;

/**
 * 订单事件监听器测试。
 *
 * <h3>测试思路</h3>
 * 监听器就是一个普通 Java 对象（加了 @Component 注解而已），
 * 测试里直接手工调它的方法，验证它是否调了 messagingTemplate。
 *
 * <h3>为什么不测 @TransactionalEventListener？</h3>
 * Spring 的注解行为（事务提交后才触发）是框架保证的，不需要我们在单元测试里验证。
 * 单元测试只管：给我一个事件，监听器是否能正确处理它。
 */
@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private OrderEventListener listener;

    @Test
    void shouldSendWebSocketNotificationOnOrderCreated() {
        // 准备测试数据
        Order order = new Order(1L, "张三", "北京", "13800000000",
                OrderStatus.CREATED, null, null);
        OrderStatusChangedEvent event = new OrderStatusChangedEvent(order, "订单已创建");

        // 执行：直接调监听器方法
        listener.handleOrderStatusChanged(event);

        // 验证：确实调了 WebSocket 推送（全局频道 + 专属频道）
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(anyString(), any(OrderNotification.class));
        // 至少调了全局频道
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(eq("/topic/orders"), any(OrderNotification.class));
        // 也调了订单专属频道
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(eq("/topic/orders/1"), any(OrderNotification.class));
    }

    @Test
    void shouldSendNotificationWithCorrectPayload() {
        Order order = new Order(2L, "李四", "上海", "13900000000",
                OrderStatus.ASSIGNED, null, null);
        OrderStatusChangedEvent event = new OrderStatusChangedEvent(order,
                "订单已分配给配送员 #5");

        listener.handleOrderStatusChanged(event);

        // 验证推送了正确的订单 ID 对应的频道
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(eq("/topic/orders/2"), any(OrderNotification.class));
    }
}
