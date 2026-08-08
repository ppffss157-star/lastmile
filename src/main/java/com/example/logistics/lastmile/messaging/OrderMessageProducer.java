package com.example.logistics.lastmile.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.example.logistics.lastmile.config.RabbitMQConfig;
import com.example.logistics.lastmile.dto.OrderNotification;

/**
 * 订单消息生产者 —— 把订单状态变更推送到 RabbitMQ。
 *
 * <h3>和 Spring Event 的对比</h3>
 * <table>
 *   <tr><td></td><td><b>Spring Event</b></td><td><b>RabbitMQ</b></td></tr>
 *   <tr><td>范围</td><td>同一个 JVM 内</td><td>跨服务、跨机器</td></tr>
 *   <tr><td>持久化</td><td>❌ 重启丢失</td><td>✅ 消息持久化到磁盘</td></tr>
 *   <tr><td>重试</td><td>❌ 消费失败就没了</td><td>✅ 可以 NACK + 重入队</td></tr>
 *   <tr><td>速度</td><td>快（内存）</td><td>稍慢（网络 + 磁盘）</td></tr>
 *   <tr><td>适用</td><td>日志、审计（掉一条无所谓）</td><td>订单通知、扣库存（一条都不能丢）</td></tr>
 * </table>
 *
 * <h3>比喻</h3>
 * Spring Event = 公司内部对讲机（同楼内喊一嗓子）<br>
 * RabbitMQ = 邮局（跨城市寄信，有记录可查，丢了能重发）
 */
@Component
public class OrderMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderMessageProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public OrderMessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发送订单通知消息到 RabbitMQ。
     * <p>
     * 每条消息自动生成唯一 messageId，消费者用这个 ID 做幂等去重。
     *
     * @param notification 订单通知 DTO
     */
    public void sendOrderNotification(OrderNotification notification) {
        // 没有 ID 就生成一个（兼容旧代码）
        OrderNotification withId = notification;
        if (notification.messageId() == null) {
            withId = new OrderNotification(
                    notification.orderId(),
                    notification.status(),
                    notification.event(),
                    notification.timestamp(),
                    java.util.UUID.randomUUID().toString());
        }
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_NOTIFICATION_KEY,
                withId);
        log.info("📬 发送 RabbitMQ 消息: messageId={}, orderId={}, status={}, event={}",
                withId.messageId(), withId.orderId(), withId.status(), withId.event());
    }
}
