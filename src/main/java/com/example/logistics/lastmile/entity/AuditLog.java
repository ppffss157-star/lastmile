package com.example.logistics.lastmile.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作人用户名（从 JWT 中提取） */
    @Column(nullable = false, length = 255)
    private String username;

    /** 操作描述，如"创建订单""派单" */
    @Column(nullable = false, length = 500)
    private String operation;

    /** 被调用的方法全名，如 OrderController.createOrder */
    @Column(nullable = false, length = 500)
    private String method;

    /** 请求 URI，如 /orders */
    @Column(name = "request_uri", length = 500)
    private String requestUri;

    /** 请求方 IP */
    @Column(length = 45)
    private String ip;

    /** 方法入参的 JSON 序列化 */
    @Column(columnDefinition = "TEXT")
    private String params;

    /** 执行结果：SUCCESS 或错误信息 */
    @Column(nullable = false, length = 500)
    private String result;

    /** 方法执行耗时，单位毫秒 */
    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ==================== getters & setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getRequestUri() { return requestUri; }
    public void setRequestUri(String requestUri) { this.requestUri = requestUri; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
