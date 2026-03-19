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
 * TokenServiceImpl 单元测试
 */
class TokenServiceImplTest {

    private TokenServiceImpl tokenService;
    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOperations;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        tokenService = new TokenServiceImpl(redisTemplate);
    }

    @Test
    void saveRefreshToken_shouldSaveToRedis() {
        tokenService.saveRefreshToken(100L, 1L, "refresh-token-123");

        verify(valueOperations).set(eq("refresh_token:100:1"), eq("refresh-token-123"), eq(7L), eq(TimeUnit.DAYS));
    }

    @Test
    void getRefreshToken_shouldReturnToken() {
        when(valueOperations.get("refresh_token:100:1")).thenReturn("refresh-token-123");

        String token = tokenService.getRefreshToken(100L, 1L);
        assertEquals("refresh-token-123", token);
    }

    @Test
    void getRefreshToken_shouldReturnNullWhenNotExists() {
        when(valueOperations.get("refresh_token:100:1")).thenReturn(null);

        assertNull(tokenService.getRefreshToken(100L, 1L));
    }

    @Test
    void deleteRefreshToken_shouldDeleteFromRedis() {
        tokenService.deleteRefreshToken(100L, 1L);

        verify(redisTemplate).delete("refresh_token:100:1");
    }

    @Test
    void verifyRefreshToken_shouldReturnTrueWhenMatch() {
        when(valueOperations.get("refresh_token:100:1")).thenReturn("refresh-token-123");

        assertTrue(tokenService.verifyRefreshToken(100L, 1L, "refresh-token-123"));
    }

    @Test
    void verifyRefreshToken_shouldReturnFalseWhenMismatch() {
        when(valueOperations.get("refresh_token:100:1")).thenReturn("refresh-token-123");

        assertFalse(tokenService.verifyRefreshToken(100L, 1L, "wrong-token"));
    }

    @Test
    void addToBlacklist_shouldSaveJtiWithTTL() {
        tokenService.addToBlacklist(100L, "jti-abc-123", 300L);

        verify(valueOperations).set(eq("blacklist:100:jti-abc-123"), eq("1"), eq(300L), eq(TimeUnit.SECONDS));
    }

    @Test
    void isBlacklisted_shouldReturnTrueWhenExists() {
        when(redisTemplate.hasKey("blacklist:100:jti-abc-123")).thenReturn(true);

        assertTrue(tokenService.isBlacklisted(100L, "jti-abc-123"));
    }

    @Test
    void isBlacklisted_shouldReturnFalseWhenNotExists() {
        when(redisTemplate.hasKey("blacklist:100:jti-abc-123")).thenReturn(false);

        assertFalse(tokenService.isBlacklisted(100L, "jti-abc-123"));
    }

    @Test
    void isBlacklisted_shouldReturnFalseWhenJtiIsNull() {
        assertFalse(tokenService.isBlacklisted(100L, null));
    }

    @Test
    void revokeAllTokens_shouldDeleteRefreshToken() {
        tokenService.revokeAllTokens(100L, 1L);

        verify(redisTemplate).delete("refresh_token:100:1");
    }
}
