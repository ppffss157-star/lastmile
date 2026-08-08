package com.example.logistics.lastmile.service;

import java.time.LocalDateTime;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.logistics.lastmile.entity.AuditLog;
import com.example.logistics.lastmile.repository.AuditLogRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 审计日志异步写入服务。
 *
 * <p>为什么单独抽一个 Service？
 * 因为 Spring AOP 代理不能拦截本类内部调用：
 * {@code this.asyncMethod()} 直接调实例方法，不走代理 → {@code @Async} 失效。
 * 抽到独立 Bean 后，注入的 {@code AuditLogService} 是代理对象 → {@code @Async} 正常工作。
 *
 * <p>这就是设计模式复盘里提到的"代理陷阱"，实际项目中的典型场景。
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 异步写入审计日志。
     * 失败了只打日志不抛异常——审计不能拖垮业务。
     *
     * @param method    类名.方法名
     * @param operation 操作描述（来自 @AuditLog 注解）
     * @param requestUri 请求 URI
     * @param params     方法参数 JSON（已截断）
     * @param result     执行结果：SUCCESS 或错误信息
     * @param durationMs 执行耗时
     */
    @Async
    public void saveAsync(String method, String operation, String requestUri,
                          String params, String result, long durationMs) {
        try {
            AuditLog entity = new AuditLog();
            entity.setUsername(getCurrentUsername());
            entity.setOperation(operation);
            entity.setMethod(method);
            entity.setRequestUri(requestUri);
            entity.setIp(getClientIp());
            entity.setParams(params);
            entity.setResult(result);
            entity.setDurationMs(durationMs);
            entity.setCreatedAt(LocalDateTime.now());

            auditLogRepository.save(entity);
        } catch (Exception e) {
            log.error("审计日志写入失败: operation={}, method={}", operation, method, e);
        }
    }

    // ==================== 辅助方法 ====================

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "anonymous";
    }

    private String getClientIp() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) return "unknown";

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }
}
