package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Token Service Implementation - 令牌服务实现（多租户支持）
 * @author wanyj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis Key 格式（带租户隔离）
    // refresh_token:{tenant_id}:{user_id}
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    // blacklist:{tenant_id}:{token}
    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final long REFRESH_TOKEN_TTL_DAYS = 7;

    @Override
    public void saveRefreshToken(Long tenantId, Long userId, String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + tenantId + ":" + userId;
        redisTemplate.opsForValue().set(key, refreshToken, REFRESH_TOKEN_TTL_DAYS, TimeUnit.DAYS);
        log.debug("Saved refresh token for tenant:{}, user:{}", tenantId, userId);
    }

    @Override
    public String getRefreshToken(Long tenantId, Long userId) {
        String key = REFRESH_TOKEN_PREFIX + tenantId + ":" + userId;
        Object token = redisTemplate.opsForValue().get(key);
        return token != null ? token.toString() : null;
    }

    @Override
    public void deleteRefreshToken(Long tenantId, Long userId) {
        String key = REFRESH_TOKEN_PREFIX + tenantId + ":" + userId;
        redisTemplate.delete(key);
        log.debug("Deleted refresh token for tenant:{}, user:{}", tenantId, userId);
    }

    @Override
    public boolean verifyRefreshToken(Long tenantId, Long userId, String refreshToken) {
        String storedToken = getRefreshToken(tenantId, userId);
        return storedToken != null && storedToken.equals(refreshToken);
    }

    @Override
    public void addToBlacklist(Long tenantId, String token, long ttl) {
        String key = BLACKLIST_PREFIX + tenantId + ":" + token;
        redisTemplate.opsForValue().set(key, "1", ttl, TimeUnit.SECONDS);
        log.info("Added token to blacklist: tenant:{}, token:{}...", tenantId, token.substring(0, Math.min(20, token.length())));
    }

    @Override
    public boolean isBlacklisted(Long tenantId, String token) {
        // Check blacklist with tenant isolation
        String key = BLACKLIST_PREFIX + tenantId + ":" + token;
        Boolean exists = redisTemplate.hasKey(key);
        return exists != null && exists;
    }

    @Override
    public void revokeAllTokens(Long tenantId, Long userId) {
        // Delete refresh token
        deleteRefreshToken(tenantId, userId);

        // Note: Access tokens in blacklist will expire naturally based on TTL
        // To actively remove them, we would need to scan the blacklist
        // For now, we just rely on TTL expiration

        log.info("Revoked all tokens for tenant:{}, user:{}", tenantId, userId);
    }
}
