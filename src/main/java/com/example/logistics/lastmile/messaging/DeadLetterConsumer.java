package com.example.logistics.lastmile.messaging;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.example.logistics.lastmile.config.RabbitMQConfig;
import com.example.logistics.lastmile.dto.OrderNotification;
import com.example.logistics.lastmile.entity.DeadLetterMessage;
import com.example.logistics.lastmile.repository.DeadLetterMessageRepository;

/**
 * 死信消费者 —— 监听 DLQ，记录重试用尽的消息，等待人工处理。
 *
 * <h3>死信消息包含的信息</h3>
 * <ul>
 *   <li>x-death header — RabbitMQ 自动附的死因诊断信息：
 *       失败队列名、失败原因、重试次数、失败时间戳等</li>
 *   <li>原始消息体 — OrderNotification JSON</li>
 * </ul>
 *
 * <h3>这个消费者不重试</h3>
 * <p>
 * DLQ 里的消息已经是重试用尽才来的，再重试没意义。
 * 直接入库记录，等人工排查后从管理后台重投。
 * </p>
 */
@Component
public class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);

    private final DeadLetterMessageRepository repository;

    public DeadLetterConsumer(DeadLetterMessageRepository repository) {
        this.repository = repository;
    }

    /**
     * 监听死信队列 —— 入库登记，不做业务处理。
     * <p>
     * 收到死信说明消息在主队列重试 3 次全失败了，可能是：
     * <ul>
     *   <li>下游服务挂了（WebSocket 推送失败）</li>
     *   <li>消息体格式有问题（JSON 反序列化失败）</li>
     *   <li>数据库连接池耗尽（幂等检查写 DB 失败）</li>
     * </ul>
     * 不管哪种，先记下来，人来看。
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_DLQ_QUEUE)
    public void handleDeadLetter(OrderNotification notification) {
        log.error("""
                        💀 死信消息到达 DLQ!
                           messageId = {}
                           orderId   = {}
                           status    = {}
                           event     = {}""",
                notification.messageId(), notification.orderId(),
                notification.status(), notification.event());

        // ===== 入库：方便后台查询和人工处理 =====
        DeadLetterMessage entity = DeadLetterMessage.builder()
                .messageId(notification.messageId())
                .orderId(notification.orderId())
                .status(notification.status())
                .event(notification.event())
                .originalBody(toJson(notification))
                .queueName(RabbitMQConfig.ORDER_DLQ_QUEUE)
                .arrivedAt(LocalDateTime.now())
                .handled(false)
                .build();

        repository.save(entity);
        log.info("📝 死信已入库: id={}, messageId={}", entity.getId(), notification.messageId());
    }

    /**
     * 简单 JSON 序列化 —— 不引入额外依赖，用 toString 近似。
     * 生产环境建议用 ObjectMapper 或 Jackson。
     */
    private String toJson(OrderNotification n) {
        return String.format(
                "{\"messageId\":\"%s\",\"orderId\":%d,\"status\":\"%s\",\"event\":\"%s\"}",
                n.messageId(), n.orderId(), n.status(), n.event());
    }
}
