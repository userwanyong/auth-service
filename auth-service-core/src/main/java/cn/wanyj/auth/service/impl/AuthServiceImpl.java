package cn.wanyj.auth.service.impl;

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

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.info("Registering new user: {}", request.getUsername());

        // Validate optional fields only if they are provided
        validateOptionalFields(request);

        // Check if username already exists
        if (userMapper.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        // Check if email already exists (only if email is provided)
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (userMapper.existsByEmail(request.getEmail())) {
                throw new BusinessException(ErrorCode.EMAIL_EXISTS);
            }
        }

        // Create new user
        User user = User.builder()
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

        // Insert user role relationship
        userMapper.insertUserRole(user.getId(), 2L);

        log.info("User registered successfully: {}", user.getId());

        return mapToUserResponse(user);
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
        log.info("User login attempt: {}", request.getUsername());

        // Load user from database with roles and permissions
        User user = userMapper.findByUsernameOrEmailWithRolesAndPermissions(request.getUsername());
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

        // Save refresh token to Redis
        tokenService.saveRefreshToken(user.getId(), refreshToken);

        log.info("User logged in successfully: {}", user.getId());

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

        // Get user ID from token
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        // Verify refresh token in Redis
        if (!tokenService.verifyRefreshToken(userId, refreshToken)) {
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
        tokenService.saveRefreshToken(user.getId(), newRefreshToken);

        log.info("Token refreshed successfully for user: {}", user.getId());

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

        // Add accessToken to blacklist with remaining TTL
        if (accessToken != null && !accessToken.isBlank()) {
            if (jwtTokenProvider.validateAccessToken(accessToken)) {
                long remainingTTL = jwtTokenProvider.getTokenRemainingTTL(accessToken);
                if (remainingTTL > 0) {
                    tokenService.addToBlacklist(accessToken, remainingTTL);
                }
            }
        }

        // Delete refreshToken from Redis
        if (refreshToken != null && !refreshToken.isBlank()) {
            if (jwtTokenProvider.validateRefreshToken(refreshToken)) {
                Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
                tokenService.deleteRefreshToken(userId);
                log.info("User logged out: {}", userId);
            }
        }

        SecurityUtils.clearAuthentication();
    }

    @Override
    public UserResponse getCurrentUser(Long userId) {
        User user = userMapper.findByIdWithRolesAndPermissions(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        log.info("Changing password for user: {}", userId);

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Verify old password
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_WRONG);
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.update(user);

        log.info("Password changed successfully for user: {}", userId);
    }

    /**
     * Map User entity to UserResponse DTO
     */
    private UserResponse mapToUserResponse(User user) {
        Set<UserResponse.RoleInfo> roles = user.getRoles().stream()
                .map(role -> UserResponse.RoleInfo.builder()
                        .id(role.getId())
                        .code(role.getCode())
                        .name(role.getName())
                        .build())
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
