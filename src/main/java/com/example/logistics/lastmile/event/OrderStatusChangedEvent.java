package com.example.logistics.lastmile.event;

import com.example.logistics.lastmile.entity.Order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单状态变更事件。
 *
 * <h3>这是什么？</h3>
 * 一个普通 Java 对象，携带"发生了什么"的信息。Spring 事件不要求继承任何特定类
 * （Spring 4.2+ 之后任何对象都能当事件），只负责传数据。
 *
 * <h3>比喻</h3>
 * 广播站的大喇叭喊的一句话："订单 #123 已分配给配送员 #5"。
 * 喊完就不管了——谁听到了、谁做了什么，跟喊话的人无关。
 *
 * <h3>数据说明</h3>
 * <ul>
 *   <li>{@code order} — 变更后的订单对象（监听器能拿到完整订单信息）</li>
 *   <li>{@code description} — 人类可读的事件描述（如 "订单已创建"）</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public class OrderStatusChangedEvent {

    private final Order order;
    private final String description;

    /**
     * 方便日志/调试时打印。
     *
     * @return 如 "OrderStatusChangedEvent[orderId=123, desc=订单已创建]"
     */
    @Override
    public String toString() {
        return "OrderStatusChangedEvent[orderId=" + order.getId()
                + ", desc=" + description + "]";
    }
}
