package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.annotation.Auditable;
import cn.wanyj.auth.security.SecurityUtils;
import cn.wanyj.auth.dto.request.ChangePasswordRequest;
import cn.wanyj.auth.dto.request.LoginByCodeRequest;
import cn.wanyj.auth.dto.request.LoginRequest;
import cn.wanyj.auth.dto.request.RegisterRequest;
import cn.wanyj.auth.dto.request.SendCodeRequest;
import cn.wanyj.auth.dto.response.TokenResponse;
import cn.wanyj.auth.dto.response.UserResponse;
import cn.wanyj.auth.entity.LoginMethod;
import cn.wanyj.auth.entity.Permission;
import cn.wanyj.auth.entity.Role;
import cn.wanyj.auth.entity.Tenant;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.mapper.UserMapper;
import cn.wanyj.auth.security.JwtTokenProvider;
import cn.wanyj.auth.security.SecurityUtils;
import cn.wanyj.auth.service.AuthService;
import cn.wanyj.auth.service.CodeRateLimiter;
import cn.wanyj.auth.service.CodeService;
import cn.wanyj.auth.service.LoginMethodConfigService;
import cn.wanyj.auth.service.LoginRateLimiter;
import cn.wanyj.auth.service.TokenService;
import cn.wanyj.auth.service.TenantService;
import cn.wanyj.auth.service.sender.MailSender;
import cn.wanyj.auth.service.sender.SmsSender;
import cn.wanyj.auth.util.UserFieldValidator;
import io.github.xiapxx.uid.generator.api.UidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Auth Service Implementation - 认证服务实现
 * @author wanyj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenService tokenService;
    private final TenantService tenantService;
    private final LoginRateLimiter loginRateLimiter;
    private final UidGenerator uidGenerator;
    private final CodeService codeService;
    private final CodeRateLimiter codeRateLimiter;
    private final LoginMethodConfigService loginMethodConfigService;
    private final MailSender mailSender;
    private final SmsSender smsSender;
    /** 解析登录方式配置 JSON（codeTtlMinutes 等），无需 Spring 容器托管 */
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @Override
    @Transactional
    @Auditable(action = "REGISTER", resource = "User")
    public TokenResponse register(RegisterRequest request) {
        // tenantId is required, no default fallback
        Long tenantId = request.getTenantId();
        log.info("Registering user: {} in tenant: {}", request.getUsername(), tenantId);

        // Validate tenant is valid
        if (!tenantService.isValidTenant(tenantId)) {
            throw new BusinessException(ErrorCode.INVALID_TENANT);
        }

        // Check if user limit is reached
        if (tenantService.isUserLimitReached(tenantId)) {
            throw new BusinessException(ErrorCode.TENANT_USER_LIMIT_REACHED);
        }

        // Validate optional contact fields (email/phone) only if provided
        UserFieldValidator.validateContactFields(request.getEmail(), request.getPhone());

        // Check if username already exists in current tenant
        if (userMapper.existsByUsername(request.getUsername(), tenantId)) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        // Check if email already exists in current tenant (only if email is provided)
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (userMapper.existsByEmail(request.getEmail(), tenantId)) {
                throw new BusinessException(ErrorCode.EMAIL_EXISTS);
            }
        }

        // Create new user with tenantId
        User user = User.builder()
                .id(uidGenerator.getUID())
                .tenantId(tenantId)
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .phone(request.getPhone())
                .nickname(request.getNickname() != null && !request.getNickname().isBlank()
                        ? request.getNickname() : request.getUsername())
                .realName(request.getRealName())
                .gender(request.getGender())
                .birthday(request.getBirthday())
                .avatar(request.getAvatar())
                .status(1)
                .emailVerified(false)
                .phoneVerified(false)
                .roles(new HashSet<>())
                .build();

        // Insert user
        userMapper.insert(user);

        // Insert user role relationship (use ROLE_USER for this tenant)
        Role userRole = userMapper.findRoleByCodeAndTenantId("ROLE_USER", tenantId);
        if (userRole != null) {
            userMapper.insertUserRole(user.getId(), userRole.getId(), tenantId);
        } else {
            log.warn("ROLE_USER not found for tenant: {}, skipping role assignment", tenantId);
        }

        log.info("User registered successfully: {} in tenant: {}", user.getId(), tenantId);

        // Reload user with roles and permissions from database
        user = userMapper.findByIdWithRolesAndPermissions(user.getId(), tenantId);

        return issueTokens(user);
    }

    @Override
    @Auditable(action = "LOGIN", resource = "User")
    public TokenResponse login(LoginRequest request) {
        // 通过对外标识定位租户，再取内部 id（对外不暴露自增 id）
        Tenant tenant = tenantService.getTenantByUid(request.getTenantUid());
        if (tenant == null || !tenant.isValid()) {
            throw new BusinessException(ErrorCode.INVALID_TENANT);
        }
        Long tenantId = tenant.getId();
        // 校验密码登录是否启用（平台 + 租户两级开关）
        if (!loginMethodConfigService.isEnabled(tenantId, LoginMethod.PASSWORD.getCode())) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_DISABLED, "密码登录已被禁用");
        }
        log.info("User login attempt: {} in tenant: {}", request.getUsername(), tenantId);

        // Check login rate limit
        if (!loginRateLimiter.allowAttempt(tenantId, request.getUsername())) {
            throw new BusinessException(ErrorCode.LOGIN_RATE_LIMITED);
        }

        // Load user from database with roles and permissions
        User user = userMapper.findByUsernameOrEmailWithRolesAndPermissions(request.getUsername(), tenantId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Check if user is disabled
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // Update last login time
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.update(user);

        // Reset rate limit after successful login
        loginRateLimiter.resetLimit(tenantId, request.getUsername());
        log.info("User logged in successfully: {} in tenant: {}", user.getId(), tenantId);
        return issueTokens(user);
    }

    @Override
    public void sendCode(SendCodeRequest request) {
        LoginMethod method = LoginMethod.fromCode(request.getMethod());
        if (method == null || method == LoginMethod.PASSWORD) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_NOT_SUPPORTED);
        }
        String category = method.getCategory();
        if (!"email".equals(category) && !"sms".equals(category)) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_NOT_SUPPORTED);
        }
        Tenant tenant = tenantService.getTenantByUid(request.getTenantUid());
        if (tenant == null || !tenant.isValid()) {
            throw new BusinessException(ErrorCode.INVALID_TENANT);
        }
        Long tenantId = tenant.getId();
        if (!loginMethodConfigService.isEnabled(tenantId, request.getMethod())) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_DISABLED, "该登录方式未启用");
        }
        if (!codeRateLimiter.allowSend(request.getTarget())) {
            throw new BusinessException(ErrorCode.LOGIN_RATE_LIMITED, "验证码发送过于频繁，请稍后再试");
        }
        String config = loginMethodConfigService.getEffectiveConfig(tenantId, request.getMethod());
        if (config == null || config.isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "该登录方式未配置凭证");
        }
        // 有效期取生效配置的 codeTtlMinutes（平台/租户两级），缺失或非法回退默认值
        long ttlMinutes = resolveTtlMinutes(tenantId, request.getMethod(), config);
        String code = codeService.generateAndStore(tenantId, request.getMethod(), request.getTarget(), ttlMinutes);
        if ("email".equals(category)) {
            // 有效期（分钟）传给发送方渲染自定义模板的 {minutes} 占位符
            mailSender.send(request.getTarget(), code, (int) ttlMinutes, config);
        } else {
            smsSender.send(request.getTarget(), code, config);
        }
        log.info("Login code sent: tenant={}, method={}, ttl={}min", tenantId, request.getMethod(), ttlMinutes);
    }

    /** codeTtlMinutes 配置允许的范围（分钟），见 {@link CodeService} */
    private static final long TTL_MIN = CodeService.CODE_TTL_MIN;
    private static final long TTL_MAX = CodeService.CODE_TTL_MAX;

    /**
     * 从生效配置解析验证码有效期：缺失/非法取默认值，超范围收敛到边界（1~30 分钟）。
     * 不抛异常，避免管理员误配导致发码（登录）不可用。
     */
    private long resolveTtlMinutes(Long tenantId, String method, String config) {
        long ttl = codeService.getTtlMinutes();
        try {
            String raw = objectMapper.readValue(config,
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {})
                    .get("codeTtlMinutes");
            if (raw != null && !raw.isBlank()) {
                long parsed = Long.parseLong(raw.trim());
                if (parsed < TTL_MIN || parsed > TTL_MAX) {
                    long clamped = Math.max(TTL_MIN, Math.min(TTL_MAX, parsed));
                    log.warn("codeTtlMinutes out of range [{}-{}] for tenant={}, method={}: {} -> {}",
                            TTL_MIN, TTL_MAX, tenantId, method, parsed, clamped);
                    return clamped;
                }
                ttl = parsed;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid codeTtlMinutes for tenant={}, method={}, fallback to default {}min",
                    tenantId, method, ttl);
        } catch (Exception e) {
            log.warn("Failed to read codeTtlMinutes from config for tenant={}, method={}, fallback to default {}min",
                    tenantId, method, ttl, e);
        }
        return ttl;
    }

    @Override
    @Transactional
    public TokenResponse loginByCode(LoginByCodeRequest request) {
        LoginMethod method = LoginMethod.fromCode(request.getMethod());
        if (method == null || method == LoginMethod.PASSWORD) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_NOT_SUPPORTED);
        }
        String category = method.getCategory();
        if (!"email".equals(category) && !"sms".equals(category)) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_NOT_SUPPORTED);
        }
        Tenant tenant = tenantService.getTenantByUid(request.getTenantUid());
        if (tenant == null || !tenant.isValid()) {
            throw new BusinessException(ErrorCode.INVALID_TENANT);
        }
        Long tenantId = tenant.getId();
        if (!loginMethodConfigService.isEnabled(tenantId, request.getMethod())) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_DISABLED, "该登录方式未启用");
        }
        if (!codeService.verify(tenantId, request.getMethod(), request.getTarget(), request.getCode())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "验证码错误或已过期");
        }
        User user = "email".equals(category)
                ? userMapper.findByEmailWithRolesAndPermissions(request.getTarget(), tenantId)
                : userMapper.findByPhoneWithRolesAndPermissions(request.getTarget(), tenantId);
        if (user == null) {
            // 邮箱/手机号在租户内不存在：自动注册并登录（与 OAuth 新账号逻辑一致）
            user = createCodeLoginUser(tenantId, category, request.getTarget());
        } else {
            if (user.getStatus() == 0) {
                throw new BusinessException(ErrorCode.USER_DISABLED);
            }
            if ("email".equals(category)) {
                user.setEmailVerified(true);
            } else {
                user.setPhoneVerified(true);
            }
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.update(user);
        log.info("User logged in by code: {} in tenant: {}", user.getId(), tenantId);
        return issueTokens(user);
    }

    /**
     * 验证码登录时自动创建用户（邮箱/手机号在租户内不存在）。
     * 逻辑对齐 OAuth 新账号（OAuthLoginService#createOAuthUser）：随机密码（不可密码登录）、
     * 分配 ROLE_USER、校验租户人数上限；验证码验证通过即视为该联系方式已验证。
     */
    private User createCodeLoginUser(Long tenantId, String category, String target) {
        if (tenantService.isUserLimitReached(tenantId)) {
            throw new BusinessException(ErrorCode.TENANT_USER_LIMIT_REACHED);
        }
        boolean isEmail = "email".equals(category);
        String username = uniqueUsername(tenantId, (isEmail ? "email_" : "sms_") + target);

        User user = User.builder()
                .id(uidGenerator.getUID())
                .tenantId(tenantId)
                .username(username)
                // 随机密码，验证码登录创建的用户不可用密码登录
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .email(isEmail ? target : null)
                .phone(isEmail ? null : target)
                .nickname(username)
                .status(1)
                .emailVerified(isEmail)
                .phoneVerified(!isEmail)
                .roles(new HashSet<>())
                .build();
        userMapper.insert(user);

        Role userRole = userMapper.findRoleByCodeAndTenantId("ROLE_USER", tenantId);
        if (userRole != null) {
            userMapper.insertUserRole(user.getId(), userRole.getId(), tenantId);
        } else {
            log.warn("ROLE_USER not found for tenant: {}, skipping role assignment", tenantId);
        }
        log.info("User auto-registered by code login: {} ({}) in tenant: {}", username, category, tenantId);
        return userMapper.findByIdWithRolesAndPermissions(user.getId(), tenantId);
    }

    /**
     * 用户名超长截断到 50（user.username VARCHAR(50)）；截断或重名时追加 UID 后缀保证租户内唯一
     */
    private String uniqueUsername(Long tenantId, String base) {
        String username = base.length() > 50 ? base.substring(0, 50) : base;
        if (!userMapper.existsByUsername(username, tenantId)) {
            return username;
        }
        String suffix = "_" + uidGenerator.getUID();
        // 用户名可能短于预留长度（如未截断的手机号），取两者较小值避免越界
        int keep = Math.min(username.length(), 50 - suffix.length());
        return username.substring(0, keep) + suffix;
    }

    /**
     * 生成 access/refresh token 并构造 TokenResponse（login / register / loginByCode 公共逻辑）
     */
    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        tokenService.saveRefreshToken(user.getTenantId(), user.getId(), refreshToken);
        Set<String> roles = user.getRoles() == null ? Set.of()
                : user.getRoles().stream()
                        .map(Role::getCode)
                        .collect(Collectors.toSet());
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .user(TokenResponse.UserInfo.builder()
                        .id(user.getId())
                        .tenantId(user.getTenantId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .nickname(user.getNickname())
                        .avatar(user.getAvatar())
                        .roles(roles)
                        .build())
                .build();
    }

    @Override
    @Transactional
    public TokenResponse refreshToken(String refreshToken) {
        log.info("Refreshing token");

        // Validate refresh token
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // Get user ID and tenant ID from token
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        Long tenantId = jwtTokenProvider.getTenantIdFromToken(refreshToken);

        // Verify refresh token in Redis
        if (!tokenService.verifyRefreshToken(tenantId, userId, refreshToken)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // Load user with roles (enforce tenant isolation with token's tenantId)
        User user = userMapper.findByIdWithRoles(userId, tenantId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Check if user is disabled
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        // Generate new tokens
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user);

        // Update refresh token in Redis
        tokenService.saveRefreshToken(tenantId, userId, newRefreshToken);

        log.info("Token refreshed successfully for user: {} in tenant: {}", userId, tenantId);

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .build();
    }

    @Override
    @Transactional
    @Auditable(action = "LOGOUT", resource = "Token")
    public void logout(String accessToken, String refreshToken) {
        log.info("User logout");

        Long tenantId = null;
        Long userId = null;

        // Extract tenantId and userId from tokens
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                tenantId = jwtTokenProvider.getTenantIdFromToken(accessToken);
                userId = jwtTokenProvider.getUserIdFromToken(accessToken);
            } catch (Exception e) {
                log.warn("Failed to extract info from access token: {}", e.getMessage());
            }
        }

        if (tenantId == null && refreshToken != null && !refreshToken.isBlank()) {
            try {
                tenantId = jwtTokenProvider.getTenantIdFromToken(refreshToken);
                userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
            } catch (Exception e) {
                log.warn("Failed to extract info from refresh token: {}", e.getMessage());
            }
        }

        // Validate tenantId was extracted
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // Add accessToken jti to blacklist with remaining TTL
        if (accessToken != null && !accessToken.isBlank()) {
            if (jwtTokenProvider.validateAccessToken(accessToken)) {
                String jti = jwtTokenProvider.getJtiFromToken(accessToken);
                if (jti != null) {
                    long remainingTTL = jwtTokenProvider.getTokenRemainingTTL(accessToken);
                    if (remainingTTL > 0) {
                        tokenService.addToBlacklist(tenantId, jti, remainingTTL);
                    }
                }
            }
        }

        // Delete refreshToken from Redis
        if (refreshToken != null && !refreshToken.isBlank()) {
            if (jwtTokenProvider.validateRefreshToken(refreshToken)) {
                Long refreshUserId = jwtTokenProvider.getUserIdFromToken(refreshToken);
                tokenService.deleteRefreshToken(tenantId, refreshUserId);
                log.info("User logged out: tenant={}, user={}", tenantId, refreshUserId);
            }
        }

        SecurityUtils.clearAuthentication();
    }

    @Override
    public UserResponse getCurrentUser(Long userId) {
        // Get tenant ID from JWT token
        Long tenantId = SecurityUtils.getCurrentTenantId();
        log.info("GetCurrentUser: userId={}, tenantId={}", userId, tenantId);

        User user = userMapper.findByIdWithRolesAndPermissions(userId, tenantId);
        if (user == null) {
            log.error("User not found: userId={}, tenantId={}", userId, tenantId);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        log.info("User found: userId={}, roles={}, permissions={}",
                 userId, user.getRoles() != null ? user.getRoles().size() : 0,
                 user.getRoles() != null && !user.getRoles().isEmpty()
                     ? user.getRoles().stream().mapToInt(r -> r.getPermissions() != null ? r.getPermissions().size() : 0).sum()
                     : 0);

        UserResponse response = mapToUserResponse(user);
        // 附带租户名称供前端展示（左侧不再暴露数字 tenantId）
        if (tenantId != null) {
            Tenant tenant = tenantService.getTenantById(tenantId);
            if (tenant != null) {
                response.setTenantName(tenant.getTenantName());
                response.setTenantUid(tenant.getTenantUid());
            }
        }
        return response;
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        // Get tenant ID from JWT token
        Long tenantId = SecurityUtils.getCurrentTenantId();
        changePassword(userId, tenantId, request);
    }

    @Override
    @Transactional
    @Auditable(action = "CHANGE_PASSWORD", resource = "User")
    public void changePassword(Long userId, Long tenantId, ChangePasswordRequest request) {
        log.info("Changing password for user: {} in tenant: {}", userId, tenantId);

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Verify user belongs to current tenant
        if (tenantId != null && !user.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Verify old password
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_WRONG);
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.update(user);

        log.info("Password changed successfully for user: {} in tenant: {}", userId, tenantId);
    }

    /**
     * Map User entity to UserResponse DTO
     */
    private UserResponse mapToUserResponse(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getCode)
                .collect(Collectors.toSet());

        Set<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        return UserResponse.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .status(user.getStatus())
                .emailVerified(user.getEmailVerified())
                .phoneVerified(user.getPhoneVerified())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .roles(roles)
                .permissions(permissions)
                .build();
    }
}
