package com.springchat.springchat_chat.modules.auth.security;

import java.time.Duration;

/** Counter operations used by the authentication rate limiter. */
public interface RedisRateLimitStore {
    long increment(String key, Duration window);

    void delete(String key);
}
