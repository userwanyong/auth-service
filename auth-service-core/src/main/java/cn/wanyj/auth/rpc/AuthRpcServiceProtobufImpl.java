package cn.wanyj.auth.rpc;

import cn.wanyj.auth.api.protobuf.*;
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

import java.util.stream.Collectors;

/**
 * 认证服务 RPC 实现 - Protobuf IDL 模式
 * 使用 Protobuf 定义的消息类型进行序列化
 *
 * @author wanyj
 */
@Slf4j
@DubboService(
    version = "1.0.0",
    timeout = 5000,
    retries = 2,
    protocol = "tri"
)
@RequiredArgsConstructor
public class AuthRpcServiceProtobufImpl extends DubboAuthRpcServiceProtobufTriple.AuthRpcServiceProtobufImplBase {

    private final AuthService authService;
    private final TokenService tokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    @Override
    public AuthResult authenticate(LoginRpcRequest request) {
        log.info("RPC authenticate: username={}", request.getUsername());
        try {
            TokenResponse tokenResponse = authService.login(
                LoginRequest.builder()
                    .username(request.getUsername())
                    .password(request.getPassword())
                    .build()
            );

            return AuthResult.newBuilder()
                .setSuccess(true)
                .setMessage("登录成功")
                .setUserId(tokenResponse.getUser().getId())
                .setUsername(tokenResponse.getUser().getUsername())
                .build();
        } catch (BusinessException e) {
            log.warn("Authentication failed: {}", e.getMessage());
            return AuthResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Authentication error", e);
            return AuthResult.newBuilder()
                .setSuccess(false)
                .setMessage("认证失败")
                .build();
        }
    }

    @Override
    public TokenValidationResult validateToken(StringValue token) {
        log.info("RPC validateToken");
        try {
            String tokenValue = token.getValue();

            if (tokenService.isBlacklisted(tokenValue)) {
                return TokenValidationResult.newBuilder()
                    .setValid(false)
                    .build();
            }

            if (!jwtTokenProvider.validateAccessToken(tokenValue)) {
                return TokenValidationResult.newBuilder()
                    .setValid(false)
                    .build();
            }

            Long userId = jwtTokenProvider.getUserIdFromToken(tokenValue);
            // Use findByIdWithRolesAndPermissions to load permissions
            cn.wanyj.auth.entity.User user = userMapper.findByIdWithRolesAndPermissions(userId);

            if (user == null || user.getStatus() == 0) {
                return TokenValidationResult.newBuilder()
                    .setValid(false)
                    .build();
            }

            long expiresAt = jwtTokenProvider.getAccessTokenExpirationSeconds() * 1000 + System.currentTimeMillis();

            return TokenValidationResult.newBuilder()
                .setValid(true)
                .setUserId(user.getId())
                .setUsername(user.getUsername())
                .addAllRoles(user.getRoles().stream()
                    .map(r -> r.getCode())
                    .collect(Collectors.toList()))
                .addAllPermissions(user.getRoles().stream()
                    .flatMap(r -> r.getPermissions().stream())
                    .map(p -> p.getCode())
                    .distinct()
                    .collect(Collectors.toList()))
                .setExpiresAt(expiresAt)
                .build();
        } catch (Exception e) {
            log.error("Token validation error", e);
            return TokenValidationResult.newBuilder()
                .setValid(false)
                .build();
        }
    }

    @Override
    public UserRpcResponse getUserById(Int64Value userId) {
        log.info("RPC getUserById: userId={}", userId.getValue());
        try {
            UserResponse user = authService.getCurrentUser(userId.getValue());
            if (user == null) {
                return null;
            }
            return convertToProtobuf(user);
        } catch (Exception e) {
            log.error("Failed to get user by id: {}", userId.getValue(), e);
            return null;
        }
    }

    @Override
    public UserRpcResponse getUserByUsername(StringValue username) {
        log.info("RPC getUserByUsername: username={}", username.getValue());
        try {
            // Use findByUsernameWithRolesAndPermissions to load roles and permissions
            cn.wanyj.auth.entity.User user = userMapper.findByUsernameWithRolesAndPermissions(username.getValue());
            if (user == null || user.getStatus() == 0) {
                return null;
            }
            return convertToProtobuf(user);
        } catch (Exception e) {
            log.error("Failed to get user by username: {}", username.getValue(), e);
            return null;
        }
    }

    @Override
    public BoolValue hasPermission(PermissionCheckRequest request) {
        log.info("RPC hasPermission: userId={}, permission={}", request.getUserId(), request.getPermission());
        try {
            // Use findByIdWithRolesAndPermissions to load permissions
            cn.wanyj.auth.entity.User user = userMapper.findByIdWithRolesAndPermissions(request.getUserId());
            if (user == null || user.getStatus() == 0) {
                return BoolValue.newBuilder().setValue(false).build();
            }

            boolean hasPermission = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .anyMatch(p -> p.getCode().equals(request.getPermission()));

            return BoolValue.newBuilder().setValue(hasPermission).build();
        } catch (Exception e) {
            log.error("Failed to check permission", e);
            return BoolValue.newBuilder().setValue(false).build();
        }
    }

    @Override
    public BoolValue hasRole(RoleCheckRequest request) {
        log.info("RPC hasRole: userId={}, role={}", request.getUserId(), request.getRole());
        try {
            cn.wanyj.auth.entity.User user = userMapper.findByIdWithRoles(request.getUserId());
            if (user == null || user.getStatus() == 0) {
                return BoolValue.newBuilder().setValue(false).build();
            }

            boolean hasRole = user.getRoles().stream()
                .anyMatch(r -> r.getCode().equals(request.getRole()));

            return BoolValue.newBuilder().setValue(hasRole).build();
        } catch (Exception e) {
            log.error("Failed to check role", e);
            return BoolValue.newBuilder().setValue(false).build();
        }
    }

    @Override
    public StringListResponse getUserPermissions(Int64Value userId) {
        log.info("RPC getUserPermissions: userId={}", userId.getValue());
        try {
            // Use findByIdWithRolesAndPermissions to load permissions
            cn.wanyj.auth.entity.User user = userMapper.findByIdWithRolesAndPermissions(userId.getValue());
            if (user == null) {
                return StringListResponse.newBuilder().build();
            }

            return StringListResponse.newBuilder()
                .addAllValues(user.getRoles().stream()
                    .flatMap(r -> r.getPermissions().stream())
                    .map(p -> p.getCode())
                    .distinct()
                    .collect(Collectors.toList()))
                .build();
        } catch (Exception e) {
            log.error("Failed to get user permissions", e);
            return StringListResponse.newBuilder().build();
        }
    }

    @Override
    public StringListResponse getUserRoles(Int64Value userId) {
        log.info("RPC getUserRoles: userId={}", userId.getValue());
        try {
            cn.wanyj.auth.entity.User user = userMapper.findByIdWithRoles(userId.getValue());
            if (user == null) {
                return StringListResponse.newBuilder().build();
            }

            return StringListResponse.newBuilder()
                .addAllValues(user.getRoles().stream()
                    .map(r -> r.getCode())
                    .collect(Collectors.toList()))
                .build();
        } catch (Exception e) {
            log.error("Failed to get user roles", e);
            return StringListResponse.newBuilder().build();
        }
    }

    private UserRpcResponse convertToProtobuf(UserResponse user) {
        return UserRpcResponse.newBuilder()
            .setId(user.getId())
            .setUsername(user.getUsername())
            .setEmail(user.getEmail() != null ? user.getEmail() : "")
            .setPhone(user.getPhone() != null ? user.getPhone() : "")
            .setNickname(user.getNickname() != null ? user.getNickname() : "")
            .setAvatar(user.getAvatar() != null ? user.getAvatar() : "")
            .setStatus(user.getStatus())
            .addAllRoles(user.getRoles() != null
                ? user.getRoles().stream().map(UserResponse.RoleInfo::getCode).collect(Collectors.toList())
                : java.util.Collections.emptyList())
            .addAllPermissions(user.getPermissions() != null ? user.getPermissions() : java.util.Collections.emptyList())
            .build();
    }

    private UserRpcResponse convertToProtobuf(cn.wanyj.auth.entity.User user) {
        return UserRpcResponse.newBuilder()
            .setId(user.getId())
            .setUsername(user.getUsername())
            .setEmail(user.getEmail() != null ? user.getEmail() : "")
            .setPhone(user.getPhone() != null ? user.getPhone() : "")
            .setNickname(user.getNickname() != null ? user.getNickname() : "")
            .setAvatar(user.getAvatar() != null ? user.getAvatar() : "")
            .setStatus(user.getStatus())
            .addAllRoles(user.getRoles().stream()
                .map(r -> r.getCode())
                .collect(Collectors.toList()))
            .addAllPermissions(user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getCode())
                .distinct()
                .collect(Collectors.toList()))
            .build();
    }
}
