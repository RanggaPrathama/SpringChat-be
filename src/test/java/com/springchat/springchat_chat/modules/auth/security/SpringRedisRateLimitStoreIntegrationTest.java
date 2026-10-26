package com.springchat.springchat_chat.modules.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class SpringRedisRateLimitStoreIntegrationTest {
    private static final String KEY = "springchat:rate-limit:test:atomic-window";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private SpringRedisRateLimitStore store;

    @BeforeEach
    void connectToRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        store = new SpringRedisRateLimitStore(redisTemplate);
        redisTemplate.delete(KEY);
    }

    @AfterEach
    void closeConnectionFactory() {
        connectionFactory.destroy();
    }

    @Test
    void incrementsAtomicallyAndSetsExpiryOnFirstAttempt() {
        assertThat(store.increment(KEY, Duration.ofSeconds(10))).isEqualTo(1);
        Long ttlAfterFirstIncrement = redisTemplate.getExpire(KEY, TimeUnit.MILLISECONDS);
        assertThat(ttlAfterFirstIncrement).isPositive().isLessThanOrEqualTo(10_000);

        assertThat(store.increment(KEY, Duration.ofSeconds(10))).isEqualTo(2);
        Long ttlAfterSecondIncrement = redisTemplate.getExpire(KEY, TimeUnit.MILLISECONDS);
        assertThat(ttlAfterSecondIncrement).isPositive().isLessThanOrEqualTo(ttlAfterFirstIncrement);
    }

    @Test
    void concurrentIncrementsShareOneCounter() {
        IntStream.range(0, 100).parallel()
                .forEach(ignored -> store.increment(KEY, Duration.ofSeconds(10)));

        assertThat(redisTemplate.opsForValue().get(KEY)).isEqualTo("100");
    }

    @Test
    void deleteClearsCounter() {
        store.increment(KEY, Duration.ofSeconds(10));

        store.delete(KEY);

        assertThat(redisTemplate.hasKey(KEY)).isFalse();
    }
}
