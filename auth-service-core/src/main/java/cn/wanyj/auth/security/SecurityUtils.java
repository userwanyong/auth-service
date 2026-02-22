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

        if (authentication != null && authentication.getPrincipal() instanceof Object[]) {
            Object[] principal = (Object[]) authentication.getPrincipal();
            if (principal.length >= 1 && principal[0] instanceof Long) {
                return (Long) principal[0];
            }
        }

        // Fallback for old format (direct Long principal)
        if (authentication != null && authentication.getPrincipal() instanceof Long) {
            return (Long) authentication.getPrincipal();
        }

        return null;
    }

    /**
     * Get current tenant ID from security context
     * 从安全上下文中获取当前租户ID
     */
    public static Long getCurrentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Object[]) {
            Object[] principal = (Object[]) authentication.getPrincipal();
            if (principal.length >= 2 && principal[1] instanceof Long) {
                return (Long) principal[1];
            }
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

    /**
     * Check if current user is a platform admin
     * Platform admin must satisfy:
     * 1. tenantId = 0 (platform tenant)
     * 2. has ROLE_PLATFORM_ADMIN role
     *
     * @return true if current user is platform admin
     */
    public static boolean isPlatformAdmin() {
        Long currentTenantId = getCurrentTenantId();

        // Check if user is in platform tenant (tenantId = 0)
        if (currentTenantId == null || currentTenantId != 0L) {
            return false;
        }

        // Check if user has ROLE_PLATFORM_ADMIN
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(auth -> "ROLE_PLATFORM_ADMIN".equals(auth.getAuthority()));
    }
}
