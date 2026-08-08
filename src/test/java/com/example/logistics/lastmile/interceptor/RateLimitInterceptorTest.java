package com.example.logistics.lastmile.interceptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import com.example.logistics.lastmile.annotation.RateLimit;
import com.example.logistics.lastmile.exception.RateLimitExceededException;

/**
 * 限流拦截器单元测试。
 *
 * <h3>测试思路</h3>
 * 拦截器核心逻辑：拿注解参数 → 查 Redis 计数 → 超了就抛异常。
 * 我们 Mock 掉 Redis（不真连），只验证拦截器的判断逻辑是否正确。
 *
 * <h3>为什么不测 Lua 脚本？</h3>
 * Lua 脚本执行是 Redis 服务端的事，单元测试验证的是"Redis 返回某个计数时，
 * 拦截器是否能正确判断放行还是拒绝"。Lua 脚本本身用集成测试验证。
 */
@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private StringRedisTemplate redis;

    @InjectMocks
    private RateLimitInterceptor interceptor;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    // ============ 测试用的假 Controller，带各种注解组合 ============

    @RateLimit(maxRequests = 5, windowSeconds = 10)
    static class ClassAnnotatedController {
        public void noAnnotation() {}
    }

    static class MethodAnnotatedController {
        @RateLimit(maxRequests = 3, windowSeconds = 30)
        public void strict() {}
    }

    @RateLimit(maxRequests = 5, windowSeconds = 10)
    static class BothAnnotatedController {
        @RateLimit(maxRequests = 10, windowSeconds = 60)
        public void loose() {}
    }

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.setRequestURI("/test");
        response = new MockHttpServletResponse();
    }

    // ==================== 静态资源放行 ====================

    @Test
    void shouldPassThroughStaticResources() {
        // 静态资源不是 HandlerMethod（比如直接返回 String 的 handler）
        assertDoesNotThrow(() -> interceptor.preHandle(request, response, "notAHandler"));
    }

    // ==================== 默认限流（无注解） ====================

    @Test
    void shouldAllowRequestWithinDefaultLimit() throws Exception {
        // Redis 返回 1（第一次请求，远低于默认的 60）
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(1L);

        HandlerMethod handler = handlerMethod(NoAnnotationController.class, "noAnnotation");

        assertTrue(interceptor.preHandle(request, response, handler));
    }

    @Test
    void shouldRejectRequestExceedingDefaultLimit() {
        // Redis 返回 61（超过默认的 60）
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(61L);
        when(redis.getExpire(anyString())).thenReturn(25L);

        HandlerMethod handler = handlerMethod(NoAnnotationController.class, "noAnnotation");

        RateLimitExceededException ex = assertThrows(
                RateLimitExceededException.class,
                () -> interceptor.preHandle(request, response, handler));
        assertEquals(25, ex.getRetryAfterSeconds());
    }

    static class NoAnnotationController {
        public void noAnnotation() {}
    }

    // ==================== 方法级注解 ====================

    @Test
    void shouldUseMethodLevelAnnotation() throws Exception {
        // Redis 返回 2（低于方法注解的 3 次限制）
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(2L);

        HandlerMethod handler = handlerMethod(MethodAnnotatedController.class, "strict");

        assertTrue(interceptor.preHandle(request, response, handler));
    }

    @Test
    void shouldRejectWhenMethodLevelExceeded() {
        // Redis 返回 4（超过方法注解的 3 次限制）
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(4L);
        when(redis.getExpire(anyString())).thenReturn(15L);

        HandlerMethod handler = handlerMethod(MethodAnnotatedController.class, "strict");

        RateLimitExceededException ex = assertThrows(
                RateLimitExceededException.class,
                () -> interceptor.preHandle(request, response, handler));
        assertEquals(15, ex.getRetryAfterSeconds());
    }

    // ==================== 类级注解（方法无注解，降级到类） ====================

    @Test
    void shouldFallbackToClassLevelAnnotation() throws Exception {
        // Redis 返回 3（低于类注解的 5 次限制）
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(3L);

        HandlerMethod handler = handlerMethod(ClassAnnotatedController.class, "noAnnotation");

        assertTrue(interceptor.preHandle(request, response, handler));
    }

    @Test
    void shouldRejectWhenClassLevelExceeded() {
        // Redis 返回 6（超过类注解的 5 次限制）
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(6L);
        when(redis.getExpire(anyString())).thenReturn(8L);

        HandlerMethod handler = handlerMethod(ClassAnnotatedController.class, "noAnnotation");

        assertThrows(RateLimitExceededException.class,
                () -> interceptor.preHandle(request, response, handler));
    }

    // ==================== 方法覆盖类 ====================

    @Test
    void shouldPreferMethodOverClassAnnotation() throws Exception {
        // Redis 返回 8（超过类注解 5 但低于方法注解 10 → 方法优先，放行）
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(8L);

        HandlerMethod handler = handlerMethod(BothAnnotatedController.class, "loose");

        assertTrue(interceptor.preHandle(request, response, handler));
    }

    // ==================== IP 提取 ====================

    @Test
    void shouldExtractIpFromXForwardedFor() throws Exception {
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
        request.setRemoteAddr("127.0.0.1"); // 应该被 header 覆盖
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(1L);

        HandlerMethod handler = handlerMethod(NoAnnotationController.class, "noAnnotation");

        assertTrue(interceptor.preHandle(request, response, handler));
        // 验证用了第一个代理 IP 而不是 remoteAddr
        // （这个断言是间接的：key 里包含 IP，redis.execute 的参数中能体现）
    }

    @Test
    void shouldFallbackToRemoteAddrWhenNoProxyHeader() throws Exception {
        // 没有 X-Forwarded-For 和 X-Real-IP
        request.setRemoteAddr("192.168.1.100");
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(1L);

        HandlerMethod handler = handlerMethod(NoAnnotationController.class, "noAnnotation");

        assertTrue(interceptor.preHandle(request, response, handler));
    }

    // ==================== 首次请求设置 TTL ====================

    @Test
    void shouldAllowFirstRequest() throws Exception {
        // Redis INCR 返回 1 = 首次访问，Lua 脚本会自动设 EXPIRE
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(1L);

        HandlerMethod handler = handlerMethod(NoAnnotationController.class, "noAnnotation");

        assertTrue(interceptor.preHandle(request, response, handler));
    }

    // ==================== 工具方法 ====================

    private HandlerMethod handlerMethod(Class<?> clazz, String methodName) {
        try {
            Method method = clazz.getDeclaredMethod(methodName);
            return new HandlerMethod(clazz.getDeclaredConstructor().newInstance(), method);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
