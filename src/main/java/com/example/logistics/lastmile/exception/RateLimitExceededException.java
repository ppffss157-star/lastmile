package com.example.logistics.lastmile.exception;

/**
 * 限流异常 — 请求太频繁被拦截时抛出。
 * {@code retryAfterSeconds} 告诉客户端多少秒后可以重试。
 */
public class RateLimitExceededException extends RuntimeException {

    private final int retryAfterSeconds;

    public RateLimitExceededException(int retryAfterSeconds) {
        super("请求太频繁，请 " + retryAfterSeconds + " 秒后重试");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
