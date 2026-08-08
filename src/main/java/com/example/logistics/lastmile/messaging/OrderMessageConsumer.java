package com.example.logistics.lastmile.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.example.logistics.lastmile.config.RabbitMQConfig;
import com.example.logistics.lastmile.dto.OrderNotification;
import com.example.logistics.lastmile.service.MessageDedupService;

/**
 * 订单消息消费者 —— 从 RabbitMQ 接收订单通知，推送到 WebSocket。
 *
 * <h3>新增：消息幂等</h3>
 * 2026-07-25 加了 {@link MessageDedupService}：
 * <ol>
 *   <li>收到消息 → 先查 messageId 是否处理过</li>
 *   <li>处理过 → 直接 ACK（跳过，不重复推 WebSocket）</li>
 *   <li>没处理过 → 执行业务逻辑</li>
 * </ol>
 *
 * <h3>消息确认（ACK）</h3>
 * 默认自动确认：方法正常返回 → ACK。幂等检查通过后正常返回即 ACK。
 * 重复消息也直接返回（不抛异常），因为重复不是错误，只是需要跳过。
 *
 * <h3>和 Spring Event Listener 的区别</h3>
 * <ul>
 *   <li>{@code @EventListener / @TransactionalEventListener} → 监听 JVM 内部事件</li>
 *   <li>{@code @RabbitListener} → 监听外部消息队列</li>
 * </ul>
 */
@Component
public class OrderMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderMessageConsumer.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageDedupService messageDedupService;

    public OrderMessageConsumer(SimpMessagingTemplate messagingTemplate,
                                MessageDedupService messageDedupService) {
        this.messagingTemplate = messagingTemplate;
        this.messageDedupService = messageDedupService;
    }

    /**
     * 监听订单通知队列，收到消息后先做幂等检查，再推送到 WebSocket。
     */
    /**
     * 监听主队列。
     * <p>
     * 重试策略在 application-dev.yml 的 spring.rabbitmq.listener.simple.retry：
     * 3 次指数退避，重试用尽后 RejectAndDontRequeue → DLX → DLQ。
     * 幂等检查通过的重复消息直接 return，不抛异常，不触发重试。
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_NOTIFICATION_QUEUE)
    public void handleOrderNotification(OrderNotification notification) {
        log.info("📩 收到 RabbitMQ 消息 [线程: {}]: messageId={}, orderId={}, status={}, event={}",
                Thread.currentThread().getName(),
                notification.messageId(), notification.orderId(),
                notification.status(), notification.event());

        // ===== 幂等检查 =====
        if (notification.messageId() == null) {
            log.warn("⚠ 消息缺少 messageId，跳过幂等检查，直接处理");
        } else if (!messageDedupService.isFirstDelivery(
                notification.messageId(), RabbitMQConfig.ORDER_NOTIFICATION_QUEUE)) {
            log.info("⏭ 重复消息已跳过: messageId={}", notification.messageId());
            return;  // 重复消息，直接 ACK 不处理
        }

        // 推送到 WebSocket
        messagingTemplate.convertAndSend("/topic/orders", notification);
        messagingTemplate.convertAndSend("/topic/orders/" + notification.orderId(), notification);
    }
}
