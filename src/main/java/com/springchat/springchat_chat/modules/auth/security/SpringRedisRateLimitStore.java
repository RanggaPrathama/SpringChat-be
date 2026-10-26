package com.springchat.springchat_chat.modules.auth.security;

import java.time.Duration;
import java.util.List;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Executes atomic fixed-window counter operations in Redis. */
@Component
@RequiredArgsConstructor
public class SpringRedisRateLimitStore implements RedisRateLimitStore {
    private static final DefaultRedisScript<Long> INCREMENT_WITH_FIRST_TTL = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public long increment(String key, Duration window) {
        Long count = redisTemplate.execute(INCREMENT_WITH_FIRST_TTL, List.of(key),
                Long.toString(window.toMillis()));
        if (count == null) {
            throw new DataAccessResourceFailureException("Redis rate limit script returned no counter value");
        }
        return count;
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
