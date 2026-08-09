package cn.wanyj.auth.service;

/**
 * 验证码发送限流（防刷）
 *
 * @author wanyj
 * @since 1.0.0
 */
public interface CodeRateLimiter {

    /**
     * 检查同一目标（邮箱/手机号）是否允许发送验证码
     * 默认同一目标 60 秒内只允许一次
     *
     * @return true 允许发送（并占用配额）；false 已被限流
     */
    boolean allowSend(String target);
}
