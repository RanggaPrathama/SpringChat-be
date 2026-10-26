package com.springchat.springchat_chat.modules.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class AuthRateLimiterTest {
    private static final String KEY_SECRET = "test-rate-limit-key-secret-at-least-32-bytes";

    @Test
    void registrationWindowAllowsFiveAndRejectsSixthRequest() {
        AuthRateLimiter limiter = new AuthRateLimiter(new InMemoryRateLimitStore(), KEY_SECRET);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.acquire("register:127.0.0.1", 5, Duration.ofHours(1))).isTrue();
        }
        assertThat(limiter.acquire("register:127.0.0.1", 5, Duration.ofHours(1))).isFalse();
    }

    @Test
    void loginBlocksAfterFiveAttemptsAndSuccessResetsCounter() {
        AuthRateLimiter limiter = new AuthRateLimiter(new InMemoryRateLimitStore(), KEY_SECRET);
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.acquireLoginAttempt("alice", 5, Duration.ofMinutes(15))).isTrue();
        }
        assertThat(limiter.acquireLoginAttempt("alice", 5, Duration.ofMinutes(15))).isFalse();

        limiter.loginSucceeded("alice");
        assertThat(limiter.acquireLoginAttempt("alice", 5, Duration.ofMinutes(15))).isTrue();
    }

    @Test
    void separateLimiterInstancesShareCountersThroughRedisStore() {
        InMemoryRateLimitStore sharedStore = new InMemoryRateLimitStore();
        AuthRateLimiter firstInstance = new AuthRateLimiter(sharedStore, KEY_SECRET);
        AuthRateLimiter secondInstance = new AuthRateLimiter(sharedStore, KEY_SECRET);

        for (int i = 0; i < 5; i++) {
            assertThat(firstInstance.acquireLoginAttempt("alice", 5, Duration.ofMinutes(15))).isTrue();
        }
        assertThat(secondInstance.acquireLoginAttempt("alice", 5, Duration.ofMinutes(15))).isFalse();
        assertThat(sharedStore.values.keySet()).allMatch(key -> !key.contains("alice"));
    }

    @Test
    void fallsBackToLocalCounterWhenRedisIsUnavailable() {
        InMemoryRateLimitStore unavailableStore = new InMemoryRateLimitStore();
        unavailableStore.available = false;
        AuthRateLimiter limiter = new AuthRateLimiter(unavailableStore, KEY_SECRET);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.acquireLoginAttempt("alice", 5, Duration.ofMinutes(15))).isTrue();
        }
        assertThat(limiter.acquireLoginAttempt("alice", 5, Duration.ofMinutes(15))).isFalse();
    }

    private static final class InMemoryRateLimitStore implements RedisRateLimitStore {
        private final Map<String, Long> values = new HashMap<>();
        private boolean available = true;

        @Override
        public long increment(String key, Duration window) {
            ensureAvailable();
            return values.merge(key, 1L, Long::sum);
        }

        @Override
        public void delete(String key) {
            ensureAvailable();
            values.remove(key);
        }

        private void ensureAvailable() {
            if (!available) {
                throw new DataAccessResourceFailureException("Redis is unavailable");
            }
        }
    }
}
