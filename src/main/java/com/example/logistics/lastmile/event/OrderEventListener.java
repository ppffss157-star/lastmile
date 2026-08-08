package com.example.logistics.lastmile.event;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.logistics.lastmile.dto.OrderNotification;
import com.example.logistics.lastmile.entity.Order;

import lombok.RequiredArgsConstructor;

/**
 * 订单事件监听器——专门负责 WebSocket 推送。
 *
 * <h3>核心注解：@TransactionalEventListener</h3>
 * 和 {@code @EventListener} 不同，这个注解<strong>等事务提交成功后才执行</strong>。
 * 好处：
 * <ul>
 *   <li>事务回滚了 → 监听器不执行 → 不会推送假消息给前端</li>
 *   <li>监听器执行时数据库里数据已经落盘了，保证一致性</li>
 * </ul>
 *
 * <h3>比喻</h3>
 * Service 是厨房（做菜），Event Publisher 是出菜铃（叮！），Listener 是服务员（端菜）。
 * 厨房只管做菜 + 按铃，不关心谁来端、怎么端。
 *
 * <h3>和 AOP 的区别</h3>
 * <table>
 *   <tr><td><b>AOP</b></td>  <td>拦截方法调用（横切），适合日志、权限这类"所有方法都要做"的通用逻辑</td></tr>
 *   <tr><td><b>事件</b></td><td>业务发生后通知（解耦），适合"A 发生后 B 要做某事"的场景，可随时增删监听器</td></tr>
 * </table>
 *
 * <h3>两个注解配合</h3>
 * <ul>
 *   <li>{@code @TransactionalEventListener} → <b>等事务提交</b>后才触发（回滚了就不触发）</li>
 *   <li>{@code @Async} → 触发后扔到<b>后台线程</b>执行，主线直接返回</li>
 * </ul>
 * 两条加一起：事务提交 → 事件发布 → 监听器收到 → 扔到 event- 线程池 → 主线不等它跑完。
 */
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 监听到订单状态变更事件后，推送 WebSocket 消息。
     *
     * <p>两个推送频道：
     * <ul>
     *   <li>{@code /topic/orders} — 全局频道，订单列表页订阅</li>
     *   <li>{@code /topic/orders/{id}} — 专属频道，订单详情页订阅</li>
     * </ul>
     *
     * @param event 订单状态变更事件
     */
    @Async
    @TransactionalEventListener
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        Order order = event.getOrder();

        OrderNotification payload = new OrderNotification(
                order.getId(),
                order.getStatus().name(),
                event.getDescription(),
                LocalDateTime.now().toString(),
                null);  // Spring Event 不走 MQ，无需 messageId

        log.info("📢 推送 WebSocket [线程: {}]: {}",
                Thread.currentThread().getName(), payload);

        messagingTemplate.convertAndSend("/topic/orders", payload);
        messagingTemplate.convertAndSend("/topic/orders/" + order.getId(), payload);
    }
}
