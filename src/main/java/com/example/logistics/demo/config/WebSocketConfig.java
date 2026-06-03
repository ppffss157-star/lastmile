package com.example.logistics.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP 配置。
 *
 * <h3>为什么需要 WebSocket？</h3>
 * HTTP 是"请求-响应"模式——客户端不问，服务器不说。但物流系统中，
 * 订单状态变了应该立即通知前端，不能让用户手动刷新页面。
 *
 * <h3>三种实时推送方案对比</h3>
 * <table>
 *   <tr><td><b>HTTP 轮询</b></td>      <td>前端定时发请求</td>         <td>简单但浪费资源，有延迟</td></tr>
 *   <tr><td><b>SSE</b></td>            <td>服务器单向推送事件流</td>   <td>简单但只能服务器→客户端</td></tr>
 *   <tr><td><b>WebSocket</b></td>      <td>全双工持久连接</td>         <td>双向、低延迟，最佳选择</td></tr>
 * </table>
 *
 * <h3>STOMP 是什么？</h3>
 * WebSocket 只是底层传输协议（像 TCP），STOMP 是之上的消息协议（像 HTTP）。
 * 它提供"目的地"（Destination）和"订阅"（Subscribe）的概念，让消息路由更清晰。
 *
 * <h3>架构图</h3>
 * <pre>
 * ┌──────────┐  STOMP frames   ┌────────────┐
 * │ 浏览器    │ ──────────────→ │  /ws 端点   │
 * │ (SockJS) │ ←────────────── │ (WebSocket)│
 * └──────────┘                 └─────┬──────┘
 *                                    │
 *                          SimpMessagingTemplate
 *                                    │
 *                              ┌─────▼──────┐
 *                              │ OrderService │
 *                              └────────────┘
 * </pre>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 注册 STOMP 端点——客户端 WebSocket 握手时的连接 URL。
     * 例如：客户端连接 ws://localhost:8080/ws
     *
     * <p>withSockJS()：降级方案。如果浏览器或网络不支持 WebSocket，
     * SockJS 自动回退到 XHR 轮询、JSONP 等方案，确保消息推送总能工作。
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // 开发环境允许跨域
                .withSockJS();
    }

    /**
     * 配置消息代理。
     *
     * <ul>
     *   <li>{@code /topic} — 广播模式：所有订阅者都收到消息（适合订单状态推送）</li>
     *   <li>{@code /queue} — 点对点模式：仅指定用户收到（适合私信通知）</li>
     *   <li>{@code /app}   — 应用前缀：客户端发消息给服务器时用（本项目暂未用到）</li>
     * </ul>
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 启用基于内存的简单消息代理
        registry.enableSimpleBroker("/topic", "/queue");
        // 客户端发送消息到服务器的目标前缀
        registry.setApplicationDestinationPrefixes("/app");
    }
}
