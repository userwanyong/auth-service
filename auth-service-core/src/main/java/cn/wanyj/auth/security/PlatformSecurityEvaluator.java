package cn.wanyj.auth.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Platform Security Evaluator
 * 平台安全评估器
 *
 * Custom Spring Security expression evaluator for platform-level permission checks
 *
 * @author wanyj
 * @since 1.0.0
 */
@Slf4j
@Component("platformSecurityEvaluator")
@RequiredArgsConstructor
public class PlatformSecurityEvaluator {

    /**
     * Check if the current user is a platform admin
     * Platform admin must satisfy:
     * 1. tenantId = 0 (platform tenant)
     * 2. has ROLE_PLATFORM_ADMIN role
     *
     * @return true if user is platform admin
     */
    public boolean isPlatformAdmin() {
        Long currentTenantId = SecurityUtils.getCurrentTenantId();

        // Check if user is in platform tenant (tenantId = 0)
        if (currentTenantId == null || currentTenantId != 0L) {
            log.debug("User is not in platform tenant, current tenantId: {}", currentTenantId);
            return false;
        }

        // Check if user has ROLE_PLATFORM_ADMIN
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("User is not authenticated");
            return false;
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        boolean hasPlatformAdminRole = authorities.stream()
                .anyMatch(auth -> "ROLE_PLATFORM_ADMIN".equals(auth.getAuthority()));

        if (!hasPlatformAdminRole) {
            log.debug("User does not have ROLE_PLATFORM_ADMIN role");
        }

        return hasPlatformAdminRole;
    }
}
