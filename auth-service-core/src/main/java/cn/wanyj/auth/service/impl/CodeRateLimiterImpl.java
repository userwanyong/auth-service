package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.service.CodeRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 验证码发送限流实现：同一目标 60 秒内只能发送一次（SETNX + TTL 占位）
 *
 * @author wanyj
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeRateLimiterImpl implements CodeRateLimiter {

    private static final String KEY_PREFIX = "rate_limit:code:";
    private static final long WINDOW_SECONDS = 60;

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean allowSend(String target) {
        if (target == null || target.isBlank()) {
            return false;
        }
        String key = KEY_PREFIX + target;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", WINDOW_SECONDS, TimeUnit.SECONDS);
        boolean allowed = Boolean.TRUE.equals(acquired);
        if (!allowed) {
            log.warn("Code send rate limited for target: {}", target);
        }
        return allowed;
    }
}
