package cn.wanyj.auth.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LoginRateLimiterImpl 单元测试
 */
class LoginRateLimiterImplTest {

    private LoginRateLimiterImpl rateLimiter;
    private RedisTemplate<String, Object> redisTemplate;
    private ZSetOperations<String, Object> zSetOperations;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        rateLimiter = new LoginRateLimiterImpl(redisTemplate);
        ReflectionTestUtils.setField(rateLimiter, "maxAttempts", 5);
        ReflectionTestUtils.setField(rateLimiter, "windowSeconds", 900);
    }

    @Test
    void allowAttempt_shouldReturnTrueUnderLimit() {
        when(zSetOperations.zCard(anyString())).thenReturn(3L);

        assertTrue(rateLimiter.allowAttempt(100L, "testuser"));
        verify(zSetOperations).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void allowAttempt_shouldReturnFalseAtLimit() {
        when(zSetOperations.zCard(anyString())).thenReturn(5L);

        assertFalse(rateLimiter.allowAttempt(100L, "testuser"));
        verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void allowAttempt_shouldReturnFalseOverLimit() {
        when(zSetOperations.zCard(anyString())).thenReturn(10L);

        assertFalse(rateLimiter.allowAttempt(100L, "testuser"));
    }

    @Test
    void allowAttempt_shouldReturnTrueWhenNoPreviousAttempts() {
        when(zSetOperations.zCard(anyString())).thenReturn(null);

        assertTrue(rateLimiter.allowAttempt(100L, "testuser"));
    }

    @Test
    void resetLimit_shouldDeleteKey() {
        rateLimiter.resetLimit(100L, "testuser");

        verify(redisTemplate).delete("rate_limit:login:100:testuser");
    }
}
