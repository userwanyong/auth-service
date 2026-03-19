package cn.wanyj.auth.service;

/**
 * Login Rate Limiter - 登录限流接口
 * @author wanyj
 */
public interface LoginRateLimiter {

    /**
     * Check if login attempt is allowed
     * 检查是否允许登录尝试
     *
     * @param tenantId 租户ID
     * @param username 用户名
     * @return true if allowed, false if rate limited
     */
    boolean allowAttempt(Long tenantId, String username);

    /**
     * Reset rate limit after successful login
     * 登录成功后重置限流
     *
     * @param tenantId 租户ID
     * @param username 用户名
     */
    void resetLimit(Long tenantId, String username);
}
