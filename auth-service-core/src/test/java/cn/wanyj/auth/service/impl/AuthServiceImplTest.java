package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.dto.request.ChangePasswordRequest;
import cn.wanyj.auth.dto.request.LoginByCodeRequest;
import cn.wanyj.auth.dto.request.LoginRequest;
import cn.wanyj.auth.dto.request.RegisterRequest;
import cn.wanyj.auth.entity.Permission;
import cn.wanyj.auth.entity.Role;
import cn.wanyj.auth.entity.Tenant;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.mapper.UserMapper;
import cn.wanyj.auth.security.JwtTokenProvider;
import cn.wanyj.auth.service.CodeRateLimiter;
import cn.wanyj.auth.service.CodeService;
import cn.wanyj.auth.service.LoginMethodConfigService;
import cn.wanyj.auth.service.LoginRateLimiter;
import cn.wanyj.auth.service.TokenService;
import cn.wanyj.auth.service.TenantService;
import cn.wanyj.auth.service.sender.MailSender;
import cn.wanyj.auth.service.sender.SmsSender;
import io.github.xiapxx.uid.generator.api.UidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @Mock private CodeService codeService;
    @Mock private CodeRateLimiter codeRateLimiter;
    @Mock private LoginMethodConfigService loginMethodConfigService;
    @Mock private MailSender mailSender;
    @Mock private SmsSender smsSender;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userMapper, passwordEncoder, jwtTokenProvider,
                tokenService, tenantService, loginRateLimiter, uidGenerator,
                codeService, codeRateLimiter, loginMethodConfigService, mailSender, smsSender);
    }

    /** login 前置链：tenantUid 定位租户 + 密码登录开关 */
    private void stubLoginPrerequisites() {
        when(tenantService.getTenantByUid("t-uid"))
                .thenReturn(Tenant.builder().id(100L).status(1).build());
        when(loginMethodConfigService.isEnabled(100L, "password")).thenReturn(true);
    }

    @Test
    void login_shouldThrowWhenRateLimited() {
        stubLoginPrerequisites();
        when(loginRateLimiter.allowAttempt(100L, "testuser")).thenReturn(false);

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password");
        request.setTenantUid("t-uid");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ErrorCode.LOGIN_RATE_LIMITED.getCode(), ex.getCode());
    }

    @Test
    void login_shouldThrowWhenUserNotFound() {
        stubLoginPrerequisites();
        when(loginRateLimiter.allowAttempt(100L, "testuser")).thenReturn(true);
        when(userMapper.findByUsernameOrEmailWithRolesAndPermissions("testuser", 100L)).thenReturn(null);

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password");
        request.setTenantUid("t-uid");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void login_shouldThrowWhenPasswordWrong() {
        stubLoginPrerequisites();
        when(loginRateLimiter.allowAttempt(100L, "testuser")).thenReturn(true);
        User user = createTestUser();
        when(userMapper.findByUsernameOrEmailWithRolesAndPermissions("testuser", 100L)).thenReturn(user);
        when(passwordEncoder.matches("wrongpass", "encoded")).thenReturn(false);

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("wrongpass");
        request.setTenantUid("t-uid");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), ex.getCode());
    }

    @Test
    void login_shouldSucceedAndResetRateLimit() {
        stubLoginPrerequisites();
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
        request.setTenantUid("t-uid");

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

    /** loginByCode 前置链：tenantUid 定位租户 + email:aliyun 开启 + 验证码通过 */
    private void stubCodeLoginPrerequisites() {
        when(tenantService.getTenantByUid("t-uid"))
                .thenReturn(Tenant.builder().id(100L).status(1).build());
        when(loginMethodConfigService.isEnabled(100L, "email:aliyun")).thenReturn(true);
        when(codeService.verify(100L, "email:aliyun", "new@example.com", "123456")).thenReturn(true);
    }

    /** sendCode 前置链：租户有效 + 方式开启 + 未限流，返回生效配置 JSON */
    private void stubSendCodePrerequisites(String configJson) {
        when(tenantService.getTenantByUid("t-uid"))
                .thenReturn(Tenant.builder().id(100L).status(1).build());
        when(loginMethodConfigService.isEnabled(100L, "email:aliyun")).thenReturn(true);
        when(codeRateLimiter.allowSend("x@y.com")).thenReturn(true);
        when(loginMethodConfigService.getEffectiveConfig(100L, "email:aliyun")).thenReturn(configJson);
        when(codeService.generateAndStore(eq(100L), eq("email:aliyun"), eq("x@y.com"), anyLong()))
                .thenReturn("123456");
    }

    private cn.wanyj.auth.dto.request.SendCodeRequest sendCodeRequest() {
        return cn.wanyj.auth.dto.request.SendCodeRequest.builder()
                .tenantUid("t-uid").method("email:aliyun").target("x@y.com").build();
    }

    @Test
    void sendCode_shouldUseConfiguredTtlMinutes() {
        stubSendCodePrerequisites("{\"accessKeyId\":\"ak\",\"codeTtlMinutes\":\"10\"}");

        authService.sendCode(sendCodeRequest());

        // Redis TTL 与邮件模板渲染都用配置的 10 分钟
        verify(codeService).generateAndStore(100L, "email:aliyun", "x@y.com", 10L);
        verify(mailSender).send(eq("x@y.com"), eq("123456"), eq(10), anyString());
    }

    @Test
    void sendCode_shouldFallbackToDefaultTtlWhenKeyMissing() {
        stubSendCodePrerequisites("{\"accessKeyId\":\"ak\"}");
        when(codeService.getTtlMinutes()).thenReturn(5L);

        authService.sendCode(sendCodeRequest());

        verify(codeService).generateAndStore(100L, "email:aliyun", "x@y.com", 5L);
        verify(mailSender).send(eq("x@y.com"), eq("123456"), eq(5), anyString());
    }

    @Test
    void sendCode_shouldClampOutOfRangeTtl() {
        stubSendCodePrerequisites("{\"accessKeyId\":\"ak\",\"codeTtlMinutes\":\"99\"}");

        authService.sendCode(sendCodeRequest());

        verify(codeService).generateAndStore(100L, "email:aliyun", "x@y.com", 30L);
    }

    @Test
    void sendCode_shouldFallbackWhenTtlNotNumeric() {
        stubSendCodePrerequisites("{\"accessKeyId\":\"ak\",\"codeTtlMinutes\":\"abc\"}");
        when(codeService.getTtlMinutes()).thenReturn(5L);

        authService.sendCode(sendCodeRequest());

        verify(codeService).generateAndStore(100L, "email:aliyun", "x@y.com", 5L);
    }

    @Test
    void loginByCode_shouldAutoRegisterWhenEmailNotExists() {
        stubCodeLoginPrerequisites();
        when(userMapper.findByEmailWithRolesAndPermissions("new@example.com", 100L)).thenReturn(null);
        // 自动注册链：人数上限未到 + 用户名可用 + ROLE_USER 存在
        when(tenantService.isUserLimitReached(100L)).thenReturn(false);
        when(uidGenerator.getUID()).thenReturn(9999L);
        when(userMapper.existsByUsername("email_new@example.com", 100L)).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        Role role = new Role();
        role.setId(2L);
        role.setCode("ROLE_USER");
        when(userMapper.findRoleByCodeAndTenantId("ROLE_USER", 100L)).thenReturn(role);
        User created = createTestUser();
        created.setUsername("email_new@example.com");
        when(userMapper.findByIdWithRolesAndPermissions(9999L, 100L)).thenReturn(created);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);

        LoginByCodeRequest request = LoginByCodeRequest.builder()
                .tenantUid("t-uid").method("email:aliyun")
                .target("new@example.com").code("123456").build();

        var result = authService.loginByCode(request);
        assertNotNull(result);
        assertEquals("access-token", result.getAccessToken());

        // 自动注册落库 + 分配 ROLE_USER + 更新最后登录时间
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User inserted = captor.getValue();
        assertEquals("email_new@example.com", inserted.getUsername());
        assertEquals("new@example.com", inserted.getEmail());
        assertTrue(inserted.getEmailVerified());
        verify(userMapper).insertUserRole(eq(9999L), eq(2L), eq(100L));
        verify(userMapper).update(any(User.class));
    }

    @Test
    void loginByCode_shouldAutoRegisterWithUniqueSuffixWhenUsernameTruncated() {
        stubCodeLoginPrerequisites();
        when(userMapper.findByEmailWithRolesAndPermissions("new@example.com", 100L)).thenReturn(null);
        when(tenantService.isUserLimitReached(100L)).thenReturn(false);
        when(uidGenerator.getUID()).thenReturn(9999L);
        // 截断后的用户名已被占用 → 追加 UID 后缀
        when(userMapper.existsByUsername(anyString(), eq(100L))).thenReturn(true);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userMapper.findRoleByCodeAndTenantId("ROLE_USER", 100L)).thenReturn(null);
        User created = createTestUser();
        when(userMapper.findByIdWithRolesAndPermissions(9999L, 100L)).thenReturn(created);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);

        LoginByCodeRequest request = LoginByCodeRequest.builder()
                .tenantUid("t-uid").method("email:aliyun")
                .target("new@example.com").code("123456").build();

        assertNotNull(authService.loginByCode(request));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        String username = captor.getValue().getUsername();
        assertTrue(username.endsWith("_9999"));
        assertTrue(username.length() <= 50);
    }

    @Test
    void loginByCode_shouldThrowWhenTenantUserLimitReached() {
        stubCodeLoginPrerequisites();
        when(userMapper.findByEmailWithRolesAndPermissions("new@example.com", 100L)).thenReturn(null);
        when(tenantService.isUserLimitReached(100L)).thenReturn(true);

        LoginByCodeRequest request = LoginByCodeRequest.builder()
                .tenantUid("t-uid").method("email:aliyun")
                .target("new@example.com").code("123456").build();

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.loginByCode(request));
        assertEquals(ErrorCode.TENANT_USER_LIMIT_REACHED.getCode(), ex.getCode());
    }

    @Test
    void loginByCode_shouldThrowWhenUserDisabled() {
        stubCodeLoginPrerequisites();
        User user = createTestUser();
        user.setStatus(0);
        when(userMapper.findByEmailWithRolesAndPermissions("new@example.com", 100L)).thenReturn(user);

        LoginByCodeRequest request = LoginByCodeRequest.builder()
                .tenantUid("t-uid").method("email:aliyun")
                .target("new@example.com").code("123456").build();

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.loginByCode(request));
        assertEquals(ErrorCode.USER_DISABLED.getCode(), ex.getCode());
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
