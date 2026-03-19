package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.dto.request.ChangePasswordRequest;
import cn.wanyj.auth.dto.request.LoginRequest;
import cn.wanyj.auth.dto.request.RegisterRequest;
import cn.wanyj.auth.entity.Permission;
import cn.wanyj.auth.entity.Role;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.mapper.UserMapper;
import cn.wanyj.auth.security.JwtTokenProvider;
import cn.wanyj.auth.service.LoginRateLimiter;
import cn.wanyj.auth.service.TokenService;
import cn.wanyj.auth.service.TenantService;
import io.github.xiapxx.uid.generator.api.UidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TokenService tokenService;
    @Mock private TenantService tenantService;
    @Mock private LoginRateLimiter loginRateLimiter;
    @Mock private UidGenerator uidGenerator;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userMapper, passwordEncoder, jwtTokenProvider,
                tokenService, tenantService, loginRateLimiter, uidGenerator);
    }

    @Test
    void login_shouldThrowWhenRateLimited() {
        when(loginRateLimiter.allowAttempt(100L, "testuser")).thenReturn(false);

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password");
        request.setTenantId(100L);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ErrorCode.LOGIN_RATE_LIMITED.getCode(), ex.getCode());
    }

    @Test
    void login_shouldThrowWhenUserNotFound() {
        when(loginRateLimiter.allowAttempt(100L, "testuser")).thenReturn(true);
        when(userMapper.findByUsernameOrEmailWithRolesAndPermissions("testuser", 100L)).thenReturn(null);

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password");
        request.setTenantId(100L);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void login_shouldThrowWhenPasswordWrong() {
        when(loginRateLimiter.allowAttempt(100L, "testuser")).thenReturn(true);
        User user = createTestUser();
        when(userMapper.findByUsernameOrEmailWithRolesAndPermissions("testuser", 100L)).thenReturn(user);
        when(passwordEncoder.matches("wrongpass", "encoded")).thenReturn(false);

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("wrongpass");
        request.setTenantId(100L);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), ex.getCode());
    }

    @Test
    void login_shouldSucceedAndResetRateLimit() {
        when(loginRateLimiter.allowAttempt(100L, "testuser")).thenReturn(true);
        User user = createTestUser();
        when(userMapper.findByUsernameOrEmailWithRolesAndPermissions("testuser", 100L)).thenReturn(user);
        when(passwordEncoder.matches("password", "encoded")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password");
        request.setTenantId(100L);

        var result = authService.login(request);
        assertNotNull(result);
        assertEquals("access-token", result.getAccessToken());

        verify(loginRateLimiter).resetLimit(100L, "testuser");
    }

    @Test
    void register_shouldThrowWhenTenantInvalid() {
        when(tenantService.isValidTenant(100L)).thenReturn(false);

        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("password");
        request.setTenantId(100L);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals(ErrorCode.INVALID_TENANT.getCode(), ex.getCode());
    }

    @Test
    void logout_shouldBlacklistJti() {
        when(jwtTokenProvider.validateAccessToken(any())).thenReturn(true);
        when(jwtTokenProvider.getTenantIdFromToken(any())).thenReturn(100L);
        when(jwtTokenProvider.getUserIdFromToken(any())).thenReturn(1L);
        when(jwtTokenProvider.getJtiFromToken(any())).thenReturn("jti-123");
        when(jwtTokenProvider.getTokenRemainingTTL(any())).thenReturn(300L);

        authService.logout("access-token", null);

        verify(tokenService).addToBlacklist(eq(100L), eq("jti-123"), eq(300L));
    }

    private User createTestUser() {
        User user = new User();
        user.setId(1L);
        user.setTenantId(100L);
        user.setUsername("testuser");
        user.setPassword("encoded");
        user.setEmail("test@example.com");
        user.setStatus(1);
        user.setRoles(new HashSet<>());
        return user;
    }
}
