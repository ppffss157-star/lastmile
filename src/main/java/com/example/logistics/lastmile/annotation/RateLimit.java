package com.example.logistics.lastmile.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * IP 级别接口限流。
 * <p>
 * 贴在方法上：只限该方法；贴在类上：限该 Controller 所有方法。
 * 方法级优先级高于类级。
 * </p>
 *
 * <pre>{@code
 * // 每分钟最多 5 次（登录防爆破）
 * @RateLimit(maxRequests = 5, windowSeconds = 60)
 * @PostMapping("/login")
 * public Result<?> login(...) { ... }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 时间窗口内最大请求数，默认 30 */
    int maxRequests() default 30;

    /** 时间窗口，单位秒，默认 60 */
    int windowSeconds() default 60;
}
