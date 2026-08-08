package com.example.logistics.lastmile.aspect;

import java.util.Arrays;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.logistics.lastmile.annotation.AuditLog;
import com.example.logistics.lastmile.service.AuditLogService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 操作审计切面。
 * 拦截 {@code @AuditLog} 注解的方法，提取操作信息后委托
 * {@link AuditLogService#saveAsync} 异步写入数据库。
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>方法进入 → 记录开始时间</li>
 *   <li>执行目标方法（pjp.proceed()）</li>
 *   <li>成功 → result="SUCCESS"</li>
 *   <li>异常 → result=异常类名+消息 → 重新抛出</li>
 *   <li>finally 块调用 AuditLogService.saveAsync() 异步落库</li>
 * </ol>
 *
 * <h3>为什么抽 AuditLogService？</h3>
 * 本类内部 {@code this.asyncMethod()} 不走 Spring AOP 代理，
 * {@code @Async} 不生效。抽到独立 Bean 后注入的是代理对象，异步正常工作。
 * 这就是"代理陷阱"——之前学过的知识点在真实场景中的应用。
 *
 * <h3>比喻</h3>
 * Aspect = 店里的监控摄像头，拍到了就自动归档；
 * AuditLogService = 档案管理员（后台线程），把录像存入档案柜，不耽误前台接待
 */
@Aspect
@Component
public class AuditLogAspect {

    private final AuditLogService auditLogService;

    public AuditLogAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * 切点：匹配所有标注了 @AuditLog 的方法，同时绑定注解参数。
     */
    @Pointcut("@annotation(auditLog)")
    public void auditLogMethods(AuditLog auditLog) {
        // 仅承载 @Pointcut + 绑定注解参数
    }

    /**
     * 环绕通知：计时 → 执行 → 拼装信息 → 异步落库。
     *
     * @param pjp      被拦截方法的连接点
     * @param auditLog 绑定的 @AuditLog 注解实例
     * @return 原方法返回值，原样透传
     * @throws Throwable 原方法抛出的异常，原样重新抛出
     */
    @Around("auditLogMethods(auditLog)")
    public Object auditAround(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        String result = "SUCCESS";

        try {
            return pjp.proceed();
        } catch (Throwable e) {
            result = "FAILED: " + e.getClass().getSimpleName() + " - " + e.getMessage();
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - start;
            String method = pjp.getTarget().getClass().getSimpleName()
                    + "." + pjp.getSignature().getName();
            String params = truncate(Arrays.toString(pjp.getArgs()), 2000);
            String uri = getRequestUri();

            // 委托独立 Service 异步落库，不走 this 避免代理失效
            auditLogService.saveAsync(method, auditLog.value(), uri, params, result, duration);
        }
    }

    // ==================== 辅助方法 ====================

    private String getRequestUri() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "-";
        HttpServletRequest request = attrs.getRequest();
        return request != null ? request.getRequestURI() : "-";
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...(truncated)";
    }
}
