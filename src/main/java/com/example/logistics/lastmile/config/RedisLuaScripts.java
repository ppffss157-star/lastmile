package com.example.logistics.lastmile.config;

import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 项目共享的 Redis Lua 脚本常量，避免多处重复定义。
 */
public final class RedisLuaScripts {

    private RedisLuaScripts() {
        // 工具类，禁止实例化
    }

    /**
     * 原子释放锁：GET 校验锁的所有者 + DEL 删除。
     * <pre>
     * KEYS[1]: 锁 key
     * ARGV[1]: 锁 value（UUID）
     * 返回值：1 = 删除成功，0 = key 不存在或 value 不匹配
     * </pre>
     */
    public static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
            "return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);
}
