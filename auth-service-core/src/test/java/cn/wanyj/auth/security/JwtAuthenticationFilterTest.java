package cn.wanyj.auth.security;

import cn.wanyj.auth.entity.Permission;
import cn.wanyj.auth.entity.Role;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.service.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JwtAuthenticationFilter 单元测试
 */
class JwtAuthenticationFilterTest {

    @Mock
    private TokenService tokenService;

    private JwtTokenProvider jwtTokenProvider;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jwtTokenProvider = new JwtTokenProvider();
        org.springframework.test.util.ReflectionTestUtils.setField(jwtTokenProvider, "secret", "TestSecretKeyForUnitTesting1234567890123456=");
        org.springframework.test.util.ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenExpiration", 900000L);
        org.springframework.test.util.ReflectionTestUtils.setField(jwtTokenProvider, "refreshTokenExpiration", 604800000L);
        jwtTokenProvider.init();

        filter = new JwtAuthenticationFilter(jwtTokenProvider, tokenService);
        SecurityContextHolder.clearContext();
    }

    @Test
    void validToken_shouldSetAuthPrincipal() throws Exception {
        User user = createTestUser();
        String token = jwtTokenProvider.generateAccessToken(user);

        when(tokenService.isBlacklisted(any(), any())).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {});

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertInstanceOf(AuthPrincipal.class, auth.getPrincipal());
        AuthPrincipal principal = (AuthPrincipal) auth.getPrincipal();
        assertEquals(1L, principal.getUserId());
        assertEquals(100L, principal.getTenantId());
    }

    @Test
    void blacklistedToken_shouldSetTokenErrorAttribute() throws Exception {
        User user = createTestUser();
        String token = jwtTokenProvider.generateAccessToken(user);
        String jti = jwtTokenProvider.getJtiFromToken(token);

        when(tokenService.isBlacklisted(100L, jti)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {});

        assertEquals(ErrorCode.TOKEN_BLACKLISTED, request.getAttribute(JwtAuthenticationFilter.TOKEN_ERROR_ATTRIBUTE));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void noToken_shouldNotSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {});

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void expiredToken_shouldSetTokenErrorAttribute() throws Exception {
        // Use a provider with very short expiration
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider();
        org.springframework.test.util.ReflectionTestUtils.setField(shortLivedProvider, "secret", "TestSecretKeyForUnitTesting1234567890123456=");
        org.springframework.test.util.ReflectionTestUtils.setField(shortLivedProvider, "accessTokenExpiration", -1000L);
        org.springframework.test.util.ReflectionTestUtils.setField(shortLivedProvider, "refreshTokenExpiration", 604800000L);
        shortLivedProvider.init();

        JwtAuthenticationFilter shortLivedFilter = new JwtAuthenticationFilter(shortLivedProvider, tokenService);
        User user = createTestUser();
        String token = shortLivedProvider.generateAccessToken(user);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        shortLivedFilter.doFilterInternal(request, response, (req, res) -> {});

        assertNotNull(request.getAttribute(JwtAuthenticationFilter.TOKEN_ERROR_ATTRIBUTE));
    }

    private User createTestUser() {
        User user = new User();
        user.setId(1L);
        user.setTenantId(100L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");

        Role role = new Role();
        role.setId(1L);
        role.setCode("ROLE_USER");

        Permission perm = new Permission();
        perm.setId(1L);
        perm.setCode("user:read");
        role.setPermissions(Set.of(perm));

        user.setRoles(Set.of(role));
        return user;
    }
}
