package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.security.SecurityUtils;
import cn.wanyj.auth.dto.request.ChangePasswordRequest;
import cn.wanyj.auth.dto.request.LoginRequest;
import cn.wanyj.auth.dto.request.RegisterRequest;
import cn.wanyj.auth.dto.response.TokenResponse;
import cn.wanyj.auth.dto.response.UserResponse;
import cn.wanyj.auth.entity.Permission;
import cn.wanyj.auth.entity.Role;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.mapper.UserMapper;
import cn.wanyj.auth.security.JwtTokenProvider;
import cn.wanyj.auth.security.SecurityUtils;
import cn.wanyj.auth.service.AuthService;
import cn.wanyj.auth.service.TokenService;
import cn.wanyj.auth.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
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

    @Override
    @Transactional
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

        // Validate optional fields only if they are provided
        validateOptionalFields(request);

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
                .tenantId(tenantId)
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .phone(request.getPhone())
                .nickname(request.getNickname() != null && !request.getNickname().isBlank()
                        ? request.getNickname() : request.getUsername())
                .status(1)
                .emailVerified(false)
                .roles(new HashSet<>())
                .build();

        // Insert user
        userMapper.insert(user);

        // Insert user role relationship (use ROLE_USER for this tenant)
        Role userRole = userMapper.findRoleByCodeAndTenantId("ROLE_USER", tenantId);
        if (userRole != null) {
            userMapper.insertUserRole(user.getId(), userRole.getId());
        } else {
            log.warn("ROLE_USER not found for tenant: {}, skipping role assignment", tenantId);
        }

        log.info("User registered successfully: {} in tenant: {}", user.getId(), tenantId);

        // Reload user with roles and permissions from database
        user = userMapper.findByIdWithRolesAndPermissions(user.getId(), tenantId);

        // Generate tokens (auto-login after registration)
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        // Save refresh token to Redis
        tokenService.saveRefreshToken(user.getTenantId(), user.getId(), refreshToken);

        // Build response
        Set<String> roles = user.getRoles().stream()
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

    /**
     * Validate optional fields (email, phone) only if they are provided
     * 验证选填字段，只在提供了值的情况下进行校验
     */
    private void validateOptionalFields(RegisterRequest request) {
        // Validate email format if provided
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (!request.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                throw new BusinessException(ErrorCode.INVALID_EMAIL_FORMAT);
            }
        }

        // Validate phone format if provided
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            if (!request.getPhone().matches("^1[3-9]\\d{9}$")) {
                throw new BusinessException(ErrorCode.INVALID_PHONE_FORMAT);
            }
        }
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        // tenantId is required, no default fallback
        Long tenantId = request.getTenantId();
        log.info("User login attempt: {} in tenant: {}", request.getUsername(), tenantId);

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

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        // Save refresh token to Redis (with tenant isolation)
        tokenService.saveRefreshToken(user.getTenantId(), user.getId(), refreshToken);

        log.info("User logged in successfully: {} in tenant: {}", user.getId(), tenantId);

        // Build response
        Set<String> roles = user.getRoles().stream()
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

        // Load user with roles and permissions
        User user = userMapper.findByIdWithRoles(userId);
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

        // Add accessToken to blacklist with remaining TTL
        if (accessToken != null && !accessToken.isBlank()) {
            if (jwtTokenProvider.validateAccessToken(accessToken)) {
                long remainingTTL = jwtTokenProvider.getTokenRemainingTTL(accessToken);
                if (remainingTTL > 0) {
                    tokenService.addToBlacklist(tenantId, accessToken, remainingTTL);
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

        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        // Get tenant ID from JWT token
        Long tenantId = SecurityUtils.getCurrentTenantId();
        log.info("Changing password for user: {} in tenant: {}", userId, tenantId);

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Verify user belongs to current tenant
        if (!user.getTenantId().equals(tenantId)) {
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
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .status(user.getStatus())
                .emailVerified(user.getEmailVerified())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .roles(roles)
                .permissions(permissions)
                .build();
    }
}
