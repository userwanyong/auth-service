package cn.wanyj.auth.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CodeServiceImpl 单元测试（自定义有效期写入 Redis）
 */
class CodeServiceImplTest {

    private CodeServiceImpl codeService;
    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOperations;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        codeService = new CodeServiceImpl(redisTemplate);
    }

    @Test
    void generateAndStore_shouldUseGivenTtlMinutes() {
        String code = codeService.generateAndStore(100L, "email:aliyun", "x@y.com", 10L);

        assertEquals(6, code.length());
        assertTrue(code.chars().allMatch(Character::isDigit));
        verify(valueOperations).set(eq("login:code:100:email:aliyun:x@y.com"), eq(code),
                eq(10L), eq(TimeUnit.MINUTES));
    }

    @Test
    void getTtlMinutes_shouldReturnDefault() {
        assertEquals(5L, codeService.getTtlMinutes());
    }

    @Test
    void verify_shouldDeleteCodeOnSuccess() {
        when(valueOperations.get("login:code:100:email:aliyun:x@y.com")).thenReturn("123456");

        assertTrue(codeService.verify(100L, "email:aliyun", "x@y.com", "123456"));
        verify(redisTemplate).delete("login:code:100:email:aliyun:x@y.com");
    }

    @Test
    void verify_shouldFailOnMismatch() {
        when(valueOperations.get("login:code:100:email:aliyun:x@y.com")).thenReturn("123456");

        assertFalse(codeService.verify(100L, "email:aliyun", "x@y.com", "654321"));
        verify(redisTemplate, never()).delete(anyString());
    }
}
