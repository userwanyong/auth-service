package cn.wanyj.auth.service;

/**
 * Token Service - 令牌服务接口
 *
 * @author wanyj
 */
public interface TokenService {

    /**
     * Save refresh token to Redis with tenant isolation
     * 保存刷新令牌到Redis（带租户隔离）
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param refreshToken 刷新令牌
     */
    void saveRefreshToken(Long tenantId, Long userId, String refreshToken);

    /**
     * Get refresh token from Redis with tenant isolation
     * 从Redis获取刷新令牌（带租户隔离）
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @return 刷新令牌
     */
    String getRefreshToken(Long tenantId, Long userId);

    /**
     * Delete refresh token from Redis with tenant isolation
     * 从Redis删除刷新令牌（带租户隔离）
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     */
    void deleteRefreshToken(Long tenantId, Long userId);

    /**
     * Verify refresh token with tenant isolation
     * 验证刷新令牌（带租户隔离）
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param refreshToken 刷新令牌
     * @return 是否有效
     */
    boolean verifyRefreshToken(Long tenantId, Long userId, String refreshToken);

    /**
     * Add access token to blacklist (with tenant isolation)
     * 将 accessToken 加入黑名单（带租户隔离）
     *
     * @param tenantId 租户ID
     * @param token JWT token
     * @param ttl   TTL in seconds
     */
    void addToBlacklist(Long tenantId, String token, long ttl);

    /**
     * Check if token is blacklisted
     * 检查 token 是否在黑名单中
     *
     * @param tenantId 租户ID
     * @param token JWT token
     * @return true if blacklisted, false otherwise
     */
    boolean isBlacklisted(Long tenantId, String token);

    /**
     * Delete all tokens for a user (refresh token and access tokens in blacklist)
     * 删除用户的所有令牌
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     */
    void revokeAllTokens(Long tenantId, Long userId);
}
