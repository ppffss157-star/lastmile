package com.example.logistics.lastmile.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计日志注解。
 * 贴在 Controller 方法上，AOP 自动记录"谁 + 什么时候 + 干了什么"到数据库。
 *
 * <p>用法：
 * <pre>{@code
 * @AuditLog("创建订单")
 * @PostMapping
 * public Result<Order> createOrder(@RequestBody @Valid CreateOrderRequest request) {
 *     return Result.success(orderService.create(request));
 * }
 * }</pre>
 *
 * <p>和 {@code @LogExecution} 的区别：
 * <ul>
 *   <li>{@code @LogExecution} → 打控制台日志，开发调试用</li>
 *   <li>{@code @AuditLog} → 存数据库，审计追溯用（谁在什么时候干了什么）</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 操作描述，如"创建订单""派单""取消订单" */
    String value();
}
