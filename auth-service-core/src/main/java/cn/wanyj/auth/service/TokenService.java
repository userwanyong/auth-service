package cn.wanyj.auth.service;

import cn.wanyj.auth.dto.response.TokenResponse;
import cn.wanyj.auth.dto.response.ValidatedToken;

/**
 * Token Service - 令牌服务接口
 *
 * @author wanyj
 */
public interface TokenService {

    /**
     * Issue access/refresh tokens for a user (for RPC context)
     * 为指定用户签发令牌（用户加载、状态与租户归属校验在 Service 层完成）
     *
     * @param userId 用户ID
     * @param tenantId 租户ID（&gt;0 强制租户隔离；=0 保持"不指定租户"语义）
     * @param expirationSeconds 有效期（秒，用于响应 expiresIn；&lt;=0 使用默认配置）
     * @return 令牌响应（不含用户信息）
     * @throws cn.wanyj.auth.exception.BusinessException 用户不存在/禁用/租户不匹配
     */
    TokenResponse issueTokens(Long userId, Long tenantId, Long expirationSeconds);

    /**
     * Validate access token and load the user with roles/permissions (for RPC context)
     * 校验 access token（签名、黑名单、用户存在性与状态）并加载用户
     *
     * @param accessToken 访问令牌
     * @return 校验结果（用户 + 过期时间）；token 无效/用户不存在或禁用返回 null
     */
    ValidatedToken validateAccessToken(String accessToken);

    /**
     * Revoke all tokens for a user with existence and tenant-ownership checks (for RPC context)
     * 撤销指定用户全部令牌（含用户存在性与租户归属校验）
     *
     * @param userId 用户ID
     * @param tenantId 租户ID（空则跳过归属校验，兼容旧客户端）
     * @throws cn.wanyj.auth.exception.BusinessException 用户不存在/租户不匹配
     */
    void revokeAllTokensForUser(Long userId, Long tenantId);


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
     * Add access token to blacklist using jti (with tenant isolation)
     * 将 accessToken 的 jti 加入黑名单（带租户隔离）
     *
     * @param tenantId 租户ID
     * @param jti JWT ID
     * @param ttl   TTL in seconds
     */
    void addToBlacklist(Long tenantId, String jti, long ttl);

    /**
     * Check if token is blacklisted by jti
     * 通过 jti 检查 token 是否在黑名单中
     *
     * @param tenantId 租户ID
     * @param jti JWT ID
     * @return true if blacklisted, false otherwise
     */
    boolean isBlacklisted(Long tenantId, String jti);

    /**
     * Delete all tokens for a user (refresh token and access tokens in blacklist)
     * 删除用户的所有令牌
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     */
    void revokeAllTokens(Long tenantId, Long userId);
}
