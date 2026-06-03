package com.example.logistics.lastmile.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要自动记录日志的方法。
 * 配合 {@link LoggingAspect} 使用，在方法执行前后自动打印参数、返回值和执行耗时。
 *
 * <p>用法：在 Controller 或 Service 方法上添加 {@code @LogExecution} 即可，无需手写日志代码。
 *
 * <pre>{@code
 * @LogExecution
 * @PostMapping
 * public Result<Order> createOrder(@RequestBody @Valid CreateOrderRequest request) {
 *     return Result.success(orderService.create(request));
 * }
 * }</pre>
 *
 * <p>核心知识点：
 * <ul>
 *   <li>{@code @Target(ElementType.METHOD)} — 这个注解只能放在方法上</li>
 *   <li>{@code @Retention(RetentionPolicy.RUNTIME)} — 运行时保留，AOP 通过反射读取</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogExecution {
}
