package com.example.logistics.lastmile.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.logistics.lastmile.config.RabbitMQConfig;
import com.example.logistics.lastmile.entity.DeadLetterMessage;
import com.example.logistics.lastmile.repository.DeadLetterMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 死信管理服务 —— 查询、重投、标记已处理。
 *
 * <h3>使用场景</h3>
 * <ol>
 *   <li>运维看到告警 → 打开死信列表页面</li>
 *   <li>排查根因（下游恢复了 / 消息格式有 bug）</li>
 *   <li>根因修复后 → 点击"重投"→ 消息回到主队列 → 正常消费</li>
 *   <li>标记"已处理"</li>
 * </ol>
 *
 * <h3>重投 ≠ ACK DLQ 消息</h3>
 * <p>
 * DLQ 里的消息和主队列是完全独立的。重投的意思是"重新发送一份到主队列"，
 * DLQ 里的原消息不会自动删 —— 需要手动标记 handled=true。
 * 这符合"人工补偿"语义：人确认修好了才算完。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterService {

    private final DeadLetterMessageRepository repository;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 未处理死信列表。
     */
    @Transactional(readOnly = true)
    public List<DeadLetterMessage> listUnhandled() {
        return repository.findByHandledFalseOrderByArrivedAtDesc();
    }

    /**
     * 按订单 ID 查死信。
     */
    @Transactional(readOnly = true)
    public List<DeadLetterMessage> listByOrderId(Long orderId) {
        return repository.findByOrderIdOrderByArrivedAtDesc(orderId);
    }

    /**
     * 重投单条死信到主队列。
     *
     * <h3>执行步骤</h3>
     * <ol>
     *   <li>查 DB 确认这条死信存在且未处理</li>
     *   <li>发消息到主交换机（原 routing key）</li>
     *   <li>标记 handled=true</li>
     * </ol>
     *
     * @param id     死信记录 ID
     * @param note   处理备注（谁重投的、为什么可以重投了）
     * @return 重投后的死信记录
     */
    @Transactional(rollbackFor = Exception.class)
    public DeadLetterMessage republish(Long id, String note) {
        DeadLetterMessage entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("死信记录不存在: id=" + id));

        if (Boolean.TRUE.equals(entity.getHandled())) {
            throw new IllegalStateException("该死信已处理过: id=" + id);
        }

        // ===== 重投到主队列 =====
        // 注意：这里直接发到主交换机，不经过 DLX。
        // messageId 保持不变，消费者幂等机制会处理可能的重复。
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_NOTIFICATION_KEY,
                entity.getOriginalBody());      // 发原始 JSON，消费者那边会自动反序列化

        // ===== 标记已处理 =====
        entity.setHandled(true);
        entity.setHandledAt(LocalDateTime.now());
        entity.setHandleNote(note);

        log.info("🔄 死信已重投: id={}, messageId={}, orderId={}, 备注: {}",
                id, entity.getMessageId(), entity.getOrderId(), note);

        return repository.save(entity);
    }

    /**
     * 批量重投：重投所有未处理的死信。
     *
     * @param note 处理备注
     * @return 每条记录的 {id, 结果}
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> republishAll(String note) {
        List<DeadLetterMessage> unhandled = repository.findByHandledFalseOrderByArrivedAtDesc();
        log.info("🔄 批量重投开始: 共 {} 条死信", unhandled.size());

        List<Map<String, Object>> results = unhandled.stream()
                .map(entity -> {
                    try {
                        rabbitTemplate.convertAndSend(
                                RabbitMQConfig.ORDER_EXCHANGE,
                                RabbitMQConfig.ORDER_NOTIFICATION_KEY,
                                entity.getOriginalBody());
                        entity.setHandled(true);
                        entity.setHandledAt(LocalDateTime.now());
                        entity.setHandleNote(note);
                        repository.save(entity);
                        log.info("  ✅ 已重投: id={}, messageId={}", entity.getId(), entity.getMessageId());
                        return Map.<String, Object>of(
                                "id", entity.getId(),
                                "messageId", entity.getMessageId(),
                                "result", "success");
                    } catch (Exception e) {
                        log.error("  ❌ 重投失败: id={}, messageId={}", entity.getId(), entity.getMessageId(), e);
                        return Map.<String, Object>of(
                                "id", entity.getId(),
                                "messageId", entity.getMessageId(),
                                "result", "failed: " + e.getMessage());
                    }
                })
                .toList();

        log.info("🔄 批量重投完成");
        return results;
    }

    /**
     * 标记已处理（不重投，只是记录"这条知道了，不用再管"）。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeadLetterMessage markHandled(Long id, String note) {
        DeadLetterMessage entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("死信记录不存在: id=" + id));

        entity.setHandled(true);
        entity.setHandledAt(LocalDateTime.now());
        entity.setHandleNote(note);

        log.info("🏷 死信已标记处理: id={}, messageId={}, 备注: {}", id, entity.getMessageId(), note);
        return repository.save(entity);
    }
}
