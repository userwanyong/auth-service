package cn.wanyj.auth.rpc;

import cn.wanyj.auth.api.auth.AuthRpcService;
import cn.wanyj.auth.api.model.*;
import cn.wanyj.auth.dto.request.LoginRequest;
import cn.wanyj.auth.dto.response.TokenResponse;
import cn.wanyj.auth.dto.response.UserResponse;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.mapper.UserMapper;
import cn.wanyj.auth.security.JwtTokenProvider;
import cn.wanyj.auth.service.AuthService;
import cn.wanyj.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证服务 RPC 实现
 * 提供给其他微服务调用的 RPC 接口实现
 *
 * @author wanyj
 */
@Slf4j
@DubboService(
    version = "1.0.0",
    timeout = 5000,
    retries = 2
)
@RequiredArgsConstructor
public class AuthRpcServiceImpl implements AuthRpcService {

    private final AuthService authService;
    private final TokenService tokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    @Override
    public AuthResult authenticate(LoginRpcRequest request) {
        log.info("RPC authenticate request for username: {}", request.getUsername());
        try {
            // 转换 RPC 请求为内部 DTO
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setUsername(request.getUsername());
            loginRequest.setPassword(request.getPassword());

            // 调用现有业务逻辑
            TokenResponse result = authService.login(loginRequest);

            return AuthResult.builder()
                .success(true)
                .message("认证成功")
                .userId(result.getUser().getId())
                .username(result.getUser().getUsername())
                .build();
        } catch (BusinessException e) {
            log.error("Authentication failed for username: {}", request.getUsername(), e);
            return AuthResult.builder()
                .success(false)
                .message(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Unexpected error during authentication for username: {}", request.getUsername(), e);
            return AuthResult.builder()
                .success(false)
                .message("认证失败: " + e.getMessage())
                .build();
        }
    }

    @Override
    public TokenValidationResult validateToken(String accessToken) {
        log.info("RPC validate token request");
        try {
            // 检查 token 是否在黑名单中
            if (tokenService.isBlacklisted(accessToken)) {
                return TokenValidationResult.builder()
                    .valid(false)
                    .build();
            }

            // 验证 token
            if (!jwtTokenProvider.validateAccessToken(accessToken)) {
                return TokenValidationResult.builder()
                    .valid(false)
                    .build();
            }

            // 获取用户ID
            Long userId = jwtTokenProvider.getUserIdFromToken(accessToken);

            // 获取用户详细信息
            UserResponse user = authService.getCurrentUser(userId);

            // 获取过期时间
            long expiresAt = System.currentTimeMillis() / 1000 +
                jwtTokenProvider.getTokenRemainingTTL(accessToken);

            return TokenValidationResult.builder()
                .valid(true)
                .userId(user.getId())
                .username(user.getUsername())
                .roles(user.getRoles().stream()
                    .map(r -> r.getCode())
                    .collect(Collectors.toSet()))
                .permissions(user.getPermissions())
                .expiresAt(expiresAt)
                .build();
        } catch (Exception e) {
            log.error("Token validation failed", e);
            return TokenValidationResult.builder()
                .valid(false)
                .build();
        }
    }

    @Override
    public UserRpcResponse getUserById(Long userId) {
        log.info("RPC get user by id: {}", userId);
        try {
            UserResponse user = authService.getCurrentUser(userId);
            return convertToRpcResponse(user);
        } catch (Exception e) {
            log.error("Failed to get user by id: {}", userId, e);
            return null;
        }
    }

    @Override
    public UserRpcResponse getUserByUsername(String username) {
        log.info("RPC get user by username: {}", username);
        try {
            var user = userMapper.findByUsernameOrEmailWithRolesAndPermissions(username);
            if (user == null) {
                return null;
            }

            // 使用 AuthService 的方法来获取完整的用户信息
            UserResponse userResponse = authService.getCurrentUser(user.getId());
            return convertToRpcResponse(userResponse);
        } catch (Exception e) {
            log.error("Failed to get user by username: {}", username, e);
            return null;
        }
    }

    @Override
    public boolean hasPermission(Long userId, String permission) {
        log.info("RPC check permission: userId={}, permission={}", userId, permission);
        try {
            UserResponse user = authService.getCurrentUser(userId);
            return user.getPermissions().contains(permission);
        } catch (Exception e) {
            log.error("Failed to check permission for userId: {}", userId, e);
            return false;
        }
    }

    @Override
    public boolean hasRole(Long userId, String role) {
        log.info("RPC check role: userId={}, role={}", userId, role);
        try {
            UserResponse user = authService.getCurrentUser(userId);
            return user.getRoles().stream()
                .anyMatch(r -> r.getCode().equals(role));
        } catch (Exception e) {
            log.error("Failed to check role for userId: {}", userId, e);
            return false;
        }
    }

    @Override
    public Set<String> getUserPermissions(Long userId) {
        log.info("RPC get user permissions: userId={}", userId);
        try {
            UserResponse user = authService.getCurrentUser(userId);
            return user.getPermissions();
        } catch (Exception e) {
            log.error("Failed to get permissions for userId: {}", userId, e);
            return Set.of();
        }
    }

    @Override
    public Set<String> getUserRoles(Long userId) {
        log.info("RPC get user roles: userId={}", userId);
        try {
            UserResponse user = authService.getCurrentUser(userId);
            return user.getRoles().stream()
                .map(r -> r.getCode())
                .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Failed to get roles for userId: {}", userId, e);
            return Set.of();
        }
    }

    /**
     * 将内部 UserResponse 转换为 RPC UserRpcResponse
     */
    private UserRpcResponse convertToRpcResponse(UserResponse user) {
        return UserRpcResponse.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .phone(user.getPhone())
            .nickname(user.getNickname())
            .avatar(user.getAvatar())
            .status(user.getStatus())
            .roles(user.getRoles().stream()
                .map(r -> r.getCode())
                .collect(Collectors.toSet()))
            .permissions(user.getPermissions())
            .build();
    }
}
