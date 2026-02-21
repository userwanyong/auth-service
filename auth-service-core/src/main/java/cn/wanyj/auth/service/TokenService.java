package cn.wanyj.auth.service;

/**
 * Token Service - 令牌服务接口
 *
 * @author wanyj
 */
public interface TokenService {

    /**
     * Save refresh token to Redis
     * 保存刷新令牌到Redis
     */
    void saveRefreshToken(Long userId, String refreshToken);

    /**
     * Get refresh token from Redis
     * 从Redis获取刷新令牌
     */
    String getRefreshToken(Long userId);

    /**
     * Delete refresh token from Redis
     * 从Redis删除刷新令牌
     */
    void deleteRefreshToken(Long userId);

    /**
     * Verify refresh token
     * 验证刷新令牌
     */
    boolean verifyRefreshToken(Long userId, String refreshToken);

    /**
     * Add access token to blacklist
     * 将 accessToken 加入黑名单
     *
     * @param token JWT token
     * @param ttl   TTL in seconds
     */
    void addToBlacklist(String token, long ttl);

    /**
     * Check if token is blacklisted
     * 检查 token 是否在黑名单中
     *
     * @param token JWT token
     * @return true if blacklisted, false otherwise
     */
    boolean isBlacklisted(String token);
}
