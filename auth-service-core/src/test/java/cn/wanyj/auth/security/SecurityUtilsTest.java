package cn.wanyj.auth.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityUtils 单元测试
 */
class SecurityUtilsTest {

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserId_fromAuthPrincipal() {
        AuthPrincipal principal = new AuthPrincipal(42L, 100L);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertEquals(42L, SecurityUtils.getCurrentUserId());
    }

    @Test
    void getCurrentTenantId_fromAuthPrincipal() {
        AuthPrincipal principal = new AuthPrincipal(42L, 100L);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertEquals(100L, SecurityUtils.getCurrentTenantId());
    }

    @Test
    void getCurrentUserId_fromObjectArrayFallback() {
        Object[] principal = new Object[]{99L, 200L};
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertEquals(99L, SecurityUtils.getCurrentUserId());
    }

    @Test
    void getCurrentTenantId_fromObjectArrayFallback() {
        Object[] principal = new Object[]{99L, 200L};
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertEquals(200L, SecurityUtils.getCurrentTenantId());
    }

    @Test
    void getCurrentUserId_fromLongFallback() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                77L, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertEquals(77L, SecurityUtils.getCurrentUserId());
    }

    @Test
    void getCurrentUserId_whenNoAuth_returnsNull() {
        assertNull(SecurityUtils.getCurrentUserId());
        assertNull(SecurityUtils.getCurrentTenantId());
    }

    @Test
    void isAuthenticated_whenAuthenticated_returnsTrue() {
        AuthPrincipal principal = new AuthPrincipal(1L, 0L);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertTrue(SecurityUtils.isAuthenticated());
    }

    @Test
    void isAuthenticated_whenNotAuthenticated_returnsFalse() {
        assertFalse(SecurityUtils.isAuthenticated());
    }

    @Test
    void isPlatformAdmin_whenPlatformAdmin_returnsTrue() {
        AuthPrincipal principal = new AuthPrincipal(1L, 0L);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertTrue(SecurityUtils.isPlatformAdmin());
    }

    @Test
    void isPlatformAdmin_whenRegularUser_returnsFalse() {
        AuthPrincipal principal = new AuthPrincipal(1L, 100L);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertFalse(SecurityUtils.isPlatformAdmin());
    }

    @Test
    void clearAuthentication_shouldClearContext() {
        AuthPrincipal principal = new AuthPrincipal(1L, 100L);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        SecurityUtils.clearAuthentication();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
