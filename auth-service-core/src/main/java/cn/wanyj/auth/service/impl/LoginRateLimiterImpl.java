package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.service.LoginRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Login Rate Limiter Implementation - Redis ZSET 滑动窗口限流
 * @author wanyj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginRateLimiterImpl implements LoginRateLimiter {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${auth.rate-limit.max-attempts:5}")
    private int maxAttempts;

    @Value("${auth.rate-limit.window-seconds:900}")
    private int windowSeconds;

    private static final String KEY_PREFIX = "rate_limit:login:";

    @Override
    public boolean allowAttempt(Long tenantId, String username) {
        String key = KEY_PREFIX + tenantId + ":" + username;
        long now = System.currentTimeMillis();
        long windowStart = now - windowSeconds * 1000L;

        // Remove expired entries
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        // Count attempts in current window
        Long count = redisTemplate.opsForZSet().zCard(key);

        if (count != null && count >= maxAttempts) {
            log.warn("Login rate limited for tenant:{}, user:{}, attempts:{}", tenantId, username, count);
            return false;
        }

        // Add new attempt
        redisTemplate.opsForZSet().add(key, String.valueOf(now), now);
        redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);

        return true;
    }

    @Override
    public void resetLimit(Long tenantId, String username) {
        String key = KEY_PREFIX + tenantId + ":" + username;
        redisTemplate.delete(key);
        log.debug("Reset login rate limit for tenant:{}, user:{}", tenantId, username);
    }
}
