package com.example.campussecondhand.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 登录失败频控：用内存 Map 模拟 Redis 的 INCR / SET EX / TTL / DEL。
 */
class LoginAttemptServiceTest {

    private final Map<String, Long> counters = new HashMap<>();
    private final Map<String, Long> ttl = new HashMap<>();
    private LoginAttemptService service;
    private StringRedisTemplate redis;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenAnswer(inv -> counters.merge(inv.getArgument(0), 1L, Long::sum));
        when(redis.expire(anyString(), any(Duration.class))).thenAnswer(inv -> {
            ttl.put(inv.getArgument(0), ((Duration) inv.getArgument(1)).getSeconds());
            return true;
        });
        org.mockito.Mockito.doAnswer(inv -> {
            ttl.put(inv.getArgument(0), ((Duration) inv.getArgument(2)).getSeconds());
            return null;
        }).when(ops).set(anyString(), anyString(), any(Duration.class));
        when(redis.delete(anyString())).thenAnswer(inv -> {
            counters.remove((String) inv.getArgument(0));
            return true;
        });
        when(redis.getExpire(anyString())).thenAnswer(inv -> ttl.getOrDefault((String) inv.getArgument(0), -2L));
        service = new LoginAttemptService(redis, 5, 30, 15, 15);
    }

    @Test
    @DisplayName("同一用户名连续失败 5 次后锁定 15 分钟")
    void locksAfterFiveFailures() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("Alice", "1.1.1.1");
            assertThat(service.lockedSeconds("alice", "1.1.1.1")).isZero();
        }
        service.recordFailure("alice ", "1.1.1.1");
        assertThat(service.lockedSeconds("ALICE", "9.9.9.9")).isEqualTo(15 * 60);
    }

    @Test
    @DisplayName("成功登录清零用户名计数")
    void successResetsCounter() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("bob", "1.1.1.1");
        }
        service.recordSuccess("bob");
        service.recordFailure("bob", "1.1.1.1");
        assertThat(service.lockedSeconds("bob", "1.1.1.1")).isZero();
    }

    @Test
    @DisplayName("同一 IP 喷洒多个账号达到阈值后锁定该 IP")
    void locksIpAfterSpraying() {
        for (int i = 0; i < 30; i++) {
            service.recordFailure("user" + i, "6.6.6.6");
        }
        assertThat(service.lockedSeconds("someone-else", "6.6.6.6")).isEqualTo(15 * 60);
        assertThat(service.lockedSeconds("someone-else", "7.7.7.7")).isZero();
    }

    @Test
    @DisplayName("Redis 故障时放行而不是让所有人无法登录")
    void failsOpenWhenRedisDown() {
        when(redis.getExpire(anyString())).thenThrow(new RuntimeException("redis down"));
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));
        service.recordFailure("carol", "1.1.1.1");
        assertThat(service.lockedSeconds("carol", "1.1.1.1")).isZero();
    }
}
