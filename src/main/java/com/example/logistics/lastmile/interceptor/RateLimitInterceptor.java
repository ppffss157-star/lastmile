package com.example.logistics.lastmile.interceptor;

import java.util.Collections;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.logistics.lastmile.annotation.RateLimit;
import com.example.logistics.lastmile.exception.RateLimitExceededException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * IP 级别限流拦截器。
 * <p>
 * 核心思路：每个请求 → Redis INCR 计数 → 第一次访问设 TTL 过期。
 * 用 Lua 脚本保证 INCR + EXPIRE 原子执行（两条命令要么都跑要么都不跑）。
 * </p>
 *
 * <h3>Lua 脚本为什么必要？</h3>
 * <pre>
 * // 不用 Lua 的竞态条件：
 * Long count = redis.opsForValue().increment(key);   // 线程 A: count=1
 * //                         ↑ 如果此时服务重启或 Redis 挂了...
 * redis.expire(key, 60);                              // 线程 A: 设 TTL
 * //   ↑ 如果这行没执行到，key 永不过期！用户永远被限流！
 *
 * // 用 Lua：两条命令打包发给 Redis，Redis 单线程执行，原子性有保证
 * </pre>
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redis;

    /**
     * Lua 脚本：原子 INCR + 首访设 TTL。
     * <ul>
     *   <li>KEYS[1] — Redis key（如 rate_limit:127.0.0.1:/orders:60）</li>
     *   <li>ARGV[1] — TTL 秒数</li>
     *   <li>返回值 — 当前窗口内请求计数</li>
     * </ul>
     */
    private static final String LUA_INCR_AND_EXPIRE = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """;

    private final DefaultRedisScript<Long> script;

    /** 没贴 @RateLimit 时的默认值 */
    static final int DEFAULT_MAX_REQUESTS = 60;
    static final int DEFAULT_WINDOW_SECONDS = 60;

    public RateLimitInterceptor(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptText(LUA_INCR_AND_EXPIRE);
        this.script.setResultType(Long.class);
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        // 静态资源不拦
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        // 1. 解析限流参数：方法级 > 类级 > 默认
        RateLimit anno = method.getMethodAnnotation(RateLimit.class);
        if (anno == null) {
            anno = method.getBeanType().getAnnotation(RateLimit.class);
        }
        int maxRequests = anno != null ? anno.maxRequests() : DEFAULT_MAX_REQUESTS;
        int windowSeconds = anno != null ? anno.windowSeconds() : DEFAULT_WINDOW_SECONDS;

        // 2. 客户端 IP
        String ip = getClientIp(request);

        // 3. Redis key: rate_limit:IP:路径:窗口秒数
        //    窗口写进 key 是为了不同窗口有独立计数器
        String key = "rate_limit:" + ip + ":" + request.getRequestURI() + ":" + windowSeconds;

        // 4. 原子计数（Lua 脚本保证 INCR + EXPIRE 两条命令不被打断）
        Long count = redis.execute(script, Collections.singletonList(key),
                String.valueOf(windowSeconds));

        if (count != null && count > maxRequests) {
            // 查剩余 TTL，告诉客户端等多久
            Long ttl = redis.getExpire(key);
            int retryAfter = ttl != null && ttl > 0 ? ttl.intValue() : windowSeconds;
            throw new RateLimitExceededException(retryAfter);
        }

        return true;
    }

    /**
     * 获取客户端真实 IP。
     * 先查代理头 X-Forwarded-For / X-Real-IP，没有就用 remoteAddr。
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            // X-Forwarded-For 格式: client, proxy1, proxy2 — 取第一个
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }
}
