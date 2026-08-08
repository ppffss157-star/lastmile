package com.example.logistics.lastmile.dto;

/**
 * WebSocket 订单状态推送的消息体。
 * 不直接用 Map 是为了避免 convertAndSend 方法重载歧义。
 *
 * @param orderId   订单 ID
 * @param status    当前状态
 * @param event     事件描述（如"订单已创建"）
 * @param timestamp 推送时间
 * @param messageId 消息唯一标识（UUID），消费者用来做幂等去重
 */
public record OrderNotification(
        Long orderId,
        String status,
        String event,
        String timestamp,
        String messageId) {
}
