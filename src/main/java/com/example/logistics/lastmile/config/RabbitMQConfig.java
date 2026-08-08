package com.example.logistics.lastmile.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置 —— 交换机、队列、绑定关系、死信队列、重试策略。
 *
 * <h3>死信队列（DLX / DLQ）</h3>
 * <p>
 * 当消息消费失败且重试用尽时，消息会被路由到死信交换机（DLX），
 * 再由 DLX 投递到死信队列（DLQ）保存，而非直接丢弃。
 * </p>
 *
 * <h3>消息变成死信的三种情况</h3>
 * <ol>
 *   <li>消费者 reject（basic.reject/basic.nack），且 requeue=false</li>
 *   <li>消息 TTL 过期</li>
 *   <li>队列达到最大长度</li>
 * </ol>
 * 本项目用第 1 种：Spring Retry 重试 3 次 → 仍失败 → reject → DLQ。
 *
 * <h3>完整流程图</h3>
 * <pre>
 * Producer → order.exchange → order.notification.queue → Consumer
 *                                                              ↓ 抛异常
 *                                                         Spring Retry
 *                                                    （yml 配置：3 次）
 *                                                              ↓ 全失败
 *                                                    RejectAndDontRequeue
 *                                                              ↓
 *                                                    order.dlx.exchange
 *                                                              ↓
 *                                                       order.dlq.queue
 *                                                              ↓
 *                                                    DeadLetterConsumer
 *                                                    （入库 + 告警）
 *                                                              ↓
 *                                                    人工排查后重投
 * </pre>
 *
 * <h3>重试配置在哪</h3>
 * <p>application-dev.yml 里 {@code spring.rabbitmq.listener.simple.retry}。
 * 不用编程式 RetryInterceptorBuilder，因为 yml 配的 RejectAndDontRequeueRecoverer
 * 正好就是 reject → DLX → DLQ，功能完全够用，还少一个依赖。</p>
 *
 * <h3>比喻</h3>
 * 主队列 = 快递柜，DLX = 退货分拣中心，DLQ = 问题件仓库。
 * 快递投递 3 次失败 → 退回分拣中心 → 入问题件仓库 → 人工处理。
 */
@Configuration
public class RabbitMQConfig {

    // ==================== 主交换机 & 主队列（原有） ====================

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_NOTIFICATION_QUEUE = "order.notification.queue";
    public static final String ORDER_NOTIFICATION_KEY = "order.notification";

    // ==================== 死信交换机 & 死信队列（新增） ====================

    public static final String ORDER_DLX_EXCHANGE = "order.dlx.exchange";
    public static final String ORDER_DLQ_QUEUE = "order.dlq.queue";
    public static final String ORDER_DLQ_KEY = "order.dlq";

    // ==================== 交换机 ====================

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    @Bean
    public TopicExchange orderDlxExchange() {
        return new TopicExchange(ORDER_DLX_EXCHANGE);
    }

    // ==================== 队列 ====================

    /**
     * 主队列 —— 绑定死信交换机。
     * <p>
     * 当消息变成死信时，RabbitMQ 自动把消息发到绑定的 DLX，
     * routing key 用 x-dead-letter-routing-key 指定的值。
     * </p>
     */
    @Bean
    public Queue orderNotificationQueue() {
        return QueueBuilder
                .durable(ORDER_NOTIFICATION_QUEUE)
                .deadLetterExchange(ORDER_DLX_EXCHANGE)   // 死信发到这个交换机
                .deadLetterRoutingKey(ORDER_DLQ_KEY)      // 死信用这个 routing key
                .build();
    }

    /**
     * 死信队列 —— 存重试用尽的消息，等人工处理。
     * 这个名字在 RabbitMQ 管理后台一眼就能认出是问题件。
     */
    @Bean
    public Queue orderDlqQueue() {
        return QueueBuilder
                .durable(ORDER_DLQ_QUEUE)
                .build();
    }

    // ==================== 绑定 ====================

    @Bean
    public Binding orderNotificationBinding() {
        return BindingBuilder
                .bind(orderNotificationQueue())
                .to(orderExchange())
                .with(ORDER_NOTIFICATION_KEY);
    }

    @Bean
    public Binding orderDlqBinding() {
        return BindingBuilder
                .bind(orderDlqQueue())
                .to(orderDlxExchange())
                .with(ORDER_DLQ_KEY);
    }

    // ==================== 消息转换器 ====================

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

}
