package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.dto.response.TokenResponse;
import cn.wanyj.auth.dto.response.ValidatedToken;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.mapper.UserMapper;
import cn.wanyj.auth.security.JwtTokenProvider;
import cn.wanyj.auth.service.TokenService;
import io.jsonwebtoken.Claims;
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
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public TokenResponse issueTokens(Long userId, Long tenantId, Long expirationSeconds) {
        // tenantId > 0 时强制租户隔离；=0 保持"不指定租户"语义
        User user = userMapper.findByIdWithRoles(userId, tenantId != null && tenantId > 0 ? tenantId : null);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        // If tenantId is provided in request, verify it matches user's tenant
        if (tenantId != null && tenantId > 0 && !user.getTenantId().equals(tenantId)) {
            log.warn("Tenant mismatch: user belongs to tenant {}, but request specified {}",
                user.getTenantId(), tenantId);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        saveRefreshToken(user.getTenantId(), user.getId(), refreshToken);

        return TokenResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(expirationSeconds != null && expirationSeconds > 0
                ? expirationSeconds
                : jwtTokenProvider.getAccessTokenExpirationSeconds())
            .build();
    }

    @Override
    public ValidatedToken validateAccessToken(String accessToken) {
        if (!jwtTokenProvider.validateAccessToken(accessToken)) {
            return null;
        }

        Claims claims = jwtTokenProvider.getClaimsFromToken(accessToken);
        Long tenantId = claims.get("tenant_id", Long.class);

        // Check blacklist by jti
        String jti = claims.getId();
        if (jti != null && isBlacklisted(tenantId, jti)) {
            log.warn("Token is blacklisted: tenant={}, jti={}", tenantId, jti);
            return null;
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(accessToken);
        User user = userMapper.findByIdWithRolesAndPermissions(userId, tenantId);
        if (user == null || user.getStatus() == 0) {
            return null;
        }

        long expiresAt = jwtTokenProvider.getAccessTokenExpirationSeconds() * 1000 + System.currentTimeMillis();
        return new ValidatedToken(user, expiresAt);
    }

    @Override
    public void revokeAllTokensForUser(Long userId, Long tenantId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 校验调用方声明的 tenantId 与用户实际归属一致（空则跳过，兼容旧客户端）
        if (tenantId != null && !tenantId.equals(user.getTenantId())) {
            log.warn("Tenant mismatch for revokeAllTokens: user belongs to {}, request specified {}",
                user.getTenantId(), tenantId);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        revokeAllTokens(user.getTenantId(), userId);
    }

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
    public void addToBlacklist(Long tenantId, String jti, long ttl) {
        String key = BLACKLIST_PREFIX + tenantId + ":" + jti;
        redisTemplate.opsForValue().set(key, "1", ttl, TimeUnit.SECONDS);
        log.info("Added token to blacklist: tenant:{}, jti:{}", tenantId, jti);
    }

    @Override
    public boolean isBlacklisted(Long tenantId, String jti) {
        if (jti == null) {
            return false;
        }
        String key = BLACKLIST_PREFIX + tenantId + ":" + jti;
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
