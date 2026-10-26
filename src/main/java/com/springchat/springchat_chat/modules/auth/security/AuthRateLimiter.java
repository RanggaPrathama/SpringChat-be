package com.springchat.springchat_chat.modules.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(AuthRateLimiter.class);
    private static final String REDIS_KEY_PREFIX = "springchat:rate-limit:v1:";
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final RedisRateLimitStore redisStore;
    private final String keySecret;
    private final AtomicBoolean redisUnavailable = new AtomicBoolean();

    public AuthRateLimiter(RedisRateLimitStore redisStore,
            @Value("${springchat.rate-limit.key-secret}") String keySecret) {
        if (keySecret == null || keySecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Rate-limit key secret must contain at least 32 UTF-8 bytes");
        }
        this.redisStore = redisStore;
        this.keySecret = keySecret;
    }

    public boolean acquire(String key, int maximum, Duration duration) {
        return acquireCounter(key, maximum, duration);
    }

    public boolean acquireLoginAttempt(String accountKey, int maximumAttempts, Duration duration) {
        return acquireCounter("login:" + accountKey, maximumAttempts, duration);
    }

    public void loginSucceeded(String accountKey) {
        String key = counterKey("login:" + accountKey);
        windows.remove(key);
        try {
            redisStore.delete(REDIS_KEY_PREFIX + key);
            markRedisAvailable();
        } catch (DataAccessException ex) {
            markRedisUnavailable(ex);
        }
    }

    @Scheduled(fixedDelay = 300000)
    public void removeExpiredWindows() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= Duration.ofHours(2).toMillis());
    }

    private boolean acquireCounter(String rawKey, int maximum, Duration duration) {
        String key = counterKey(rawKey);
        try {
            long count = redisStore.increment(REDIS_KEY_PREFIX + key, duration);
            markRedisAvailable();
            return count <= maximum;
        } catch (DataAccessException ex) {
            markRedisUnavailable(ex);
            return acquireLocally(key, maximum, duration);
        }
    }

    private boolean acquireLocally(String key, int maximum, Duration duration) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt >= duration.toMillis()) {
                return new Window(now, 1);
            }
            return new Window(current.startedAt, current.count + 1);
        });
        return window.count <= maximum;
    }

    private String counterKey(String rawKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to derive authentication rate-limit key", ex);
        }
    }

    private void markRedisUnavailable(DataAccessException ex) {
        if (redisUnavailable.compareAndSet(false, true)) {
            log.warn("Redis rate limiting unavailable; using per-instance fallback ({})",
                    ex.getClass().getSimpleName());
        }
    }

    private void markRedisAvailable() {
        if (redisUnavailable.compareAndSet(true, false)) {
            log.info("Redis rate limiting connection restored");
        }
    }

    private record Window(long startedAt, int count) {}
}
