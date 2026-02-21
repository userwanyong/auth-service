package cn.wanyj.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Security Utilities - 安全工具类
 * 提供获取当前用户信息等工具方法
 * @author wanyj
 */
public class SecurityUtils {

    /**
     * Get current user ID from security context
     * 从安全上下文中获取当前用户ID
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Long) {
            return (Long) authentication.getPrincipal();
        }

        return null;
    }

    /**
     * Check if user is authenticated
     * 检查用户是否已认证
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }

    /**
     * Clear authentication
     * 清除认证信息
     */
    public static void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }
}
