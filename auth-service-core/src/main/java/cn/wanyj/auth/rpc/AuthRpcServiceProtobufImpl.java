package cn.wanyj.auth.rpc;

import cn.wanyj.auth.api.protobuf.*;
import cn.wanyj.auth.dto.request.LoginRequest;
import cn.wanyj.auth.dto.request.RegisterRequest;
import cn.wanyj.auth.dto.response.PageResponse;
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

import java.util.Collections;
import java.util.List;
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
    public RegisterRpcResult register(RegisterRpcRequest request) {
        log.info("RPC register: username={}, tenantId={}", request.getUsername(), request.getTenantId());
        try {
            TokenResponse tokenResponse = authService.register(
                RegisterRequest.builder()
                    .username(request.getUsername())
                    .password(request.getPassword())
                    .tenantId(request.getTenantId())
                    .email(emptyToNull(request.getEmail()))
                    .phone(emptyToNull(request.getPhone()))
                    .nickname(emptyToNull(request.getNickname()))
                    .build()
            );

            return RegisterRpcResult.newBuilder()
                .setSuccess(true)
                .setMessage("注册成功")
                .setToken(TokenRpcResponse.newBuilder()
                    .setAccessToken(tokenResponse.getAccessToken())
                    .setRefreshToken(tokenResponse.getRefreshToken())
                    .setExpiresIn(tokenResponse.getExpiresIn())
                    .build())
                .setUser(convertToProtobuf(tokenResponse.getUser()))
                .build();
        } catch (BusinessException e) {
            log.warn("Registration failed: {}", e.getMessage());
            return RegisterRpcResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Registration error", e);
            return RegisterRpcResult.newBuilder()
                .setSuccess(false)
                .setMessage("注册失败")
                .build();
        }
    }

    @Override
    public AuthResult authenticate(LoginRpcRequest request) {
        log.info("RPC authenticate: username={}, tenantId={}", request.getUsername(), request.getTenantId());
        try {
            TokenResponse tokenResponse = authService.login(
                LoginRequest.builder()
                    .username(request.getUsername())
                    .password(request.getPassword())
                    .tenantId(request.getTenantId())
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
    public UserRpcResponse getUserById(UserByIdRequest request) {
        log.info("RPC getUserById: userId={}, tenantId={}", request.getUserId(), request.getTenantId());
        try {
            // Load user with roles and permissions using provided tenantId
            cn.wanyj.auth.entity.User user = userMapper.findByIdWithRolesAndPermissions(
                request.getUserId(),
                request.getTenantId()
            );

            if (user == null || user.getStatus() == 0) {
                log.warn("User not found or disabled: userId={}, tenantId={}", request.getUserId(), request.getTenantId());
                return UserRpcResponse.getDefaultInstance();
            }

            // Verify user belongs to the specified tenant
            if (!user.getTenantId().equals(request.getTenantId())) {
                log.warn("User {} does not belong to tenant {}", request.getUserId(), request.getTenantId());
                return UserRpcResponse.getDefaultInstance();
            }

            return convertToProtobuf(user);
        } catch (Exception e) {
            log.error("Failed to get user by id: userId={}, tenantId={}",
                request.getUserId(), request.getTenantId(), e);
            return UserRpcResponse.getDefaultInstance();
        }
    }

    @Override
    public UserRpcResponse getUserByUsername(UserByUsernameRequest request) {
        log.info("RPC getUserByUsername: username={}, tenantId={}",
            request.getUsername(), request.getTenantId());
        try {
            // Load user with roles and permissions using username and tenantId
            cn.wanyj.auth.entity.User user = userMapper.findByUsernameWithRolesAndPermissions(
                request.getUsername(),
                request.getTenantId()
            );

            if (user == null || user.getStatus() == 0) {
                log.warn("User not found or disabled: username={}, tenantId={}",
                    request.getUsername(), request.getTenantId());
                return UserRpcResponse.getDefaultInstance();
            }

            return convertToProtobuf(user);
        } catch (Exception e) {
            log.error("Failed to get user by username: username={}, tenantId={}",
                request.getUsername(), request.getTenantId(), e);
            return UserRpcResponse.getDefaultInstance();
        }
    }

    @Override
    public BoolValue hasPermission(PermissionCheckRequest request) {
        log.info("RPC hasPermission: userId={}, permission={}, tenantId={}",
            request.getUserId(), request.getPermission(), request.getTenantId());
        try {
            // Use tenantId from request
            cn.wanyj.auth.entity.User user = userMapper.findByIdWithRolesAndPermissions(
                request.getUserId(),
                request.getTenantId()
            );

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
        log.info("RPC hasRole: userId={}, role={}, tenantId={}",
            request.getUserId(), request.getRole(), request.getTenantId());
        try {
            // Use tenantId from request
            cn.wanyj.auth.entity.User user = userMapper.findByIdWithRolesAndPermissions(
                request.getUserId(),
                request.getTenantId()
            );

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
    public StringListResponse getUserPermissions(UserPermissionsRequest request) {
        log.info("RPC getUserPermissions: userId={}, tenantId={}", request.getUserId(), request.getTenantId());
        try {
            // Use tenantId from request
            cn.wanyj.auth.entity.User user = userMapper.findByIdWithRolesAndPermissions(
                request.getUserId(),
                request.getTenantId()
            );

            if (user == null) {
                log.warn("User not found: userId={}, tenantId={}", request.getUserId(), request.getTenantId());
                return StringListResponse.getDefaultInstance();
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
            return StringListResponse.getDefaultInstance();
        }
    }

    @Override
    public StringListResponse getUserRoles(UserRolesRequest request) {
        log.info("RPC getUserRoles: userId={}, tenantId={}", request.getUserId(), request.getTenantId());
        try {
            // Use tenantId from request
            cn.wanyj.auth.entity.User user = userMapper.findByIdWithRolesAndPermissions(
                request.getUserId(),
                request.getTenantId()
            );

            if (user == null) {
                log.warn("User not found: userId={}, tenantId={}", request.getUserId(), request.getTenantId());
                return StringListResponse.getDefaultInstance();
            }

            return StringListResponse.newBuilder()
                .addAllValues(user.getRoles().stream()
                    .map(r -> r.getCode())
                    .collect(Collectors.toList()))
                .build();
        } catch (Exception e) {
            log.error("Failed to get user roles", e);
            return StringListResponse.getDefaultInstance();
        }
    }

    @Override
    public UserPageResponse searchUsers(SearchUsersRequest request) {
        int page = request.getPage() > 0 ? request.getPage() : 1;
        int size = request.getSize() > 0 ? request.getSize() : 10;
        String keyword = emptyToNull(request.getKeyword());

        log.info("RPC searchUsers: tenantId={}, page={}, size={}, keyword={}",
            request.getTenantId(), page, size, keyword);
        try {
            PageResponse<UserResponse> pageResponse = searchUsersByTenant(keyword, request.getTenantId(), page, size);

            UserPageResponse.Builder builder = UserPageResponse.newBuilder()
                .setTotal(pageResponse.getTotal() != null ? pageResponse.getTotal() : 0L)
                .setPage(pageResponse.getPage() != null ? pageResponse.getPage() : page)
                .setSize(pageResponse.getSize() != null ? pageResponse.getSize() : size);

            if (pageResponse.getItems() != null) {
                builder.addAllItems(pageResponse.getItems().stream()
                    .map(this::convertToProtobuf)
                    .collect(Collectors.toList()));
            }

            return builder.build();
        } catch (BusinessException e) {
            log.warn("Search users failed: {}", e.getMessage());
            return UserPageResponse.getDefaultInstance();
        } catch (Exception e) {
            log.error("Search users error", e);
            return UserPageResponse.getDefaultInstance();
        }
    }

    private PageResponse<UserResponse> searchUsersByTenant(String keyword, Long tenantId, int page, int size) {
        List<cn.wanyj.auth.entity.User> users;
        long total;

        if (keyword != null && !keyword.isBlank()) {
            users = userMapper.findByKeyword(keyword, tenantId);
            total = userMapper.countByKeyword(keyword, tenantId);
        } else {
            users = userMapper.findAllByTenantIdWithRoles(tenantId);
            total = userMapper.countAllByTenantId(tenantId);
        }

        int start = Math.max(0, (page - 1) * size);
        if (start >= users.size()) {
            return PageResponse.<UserResponse>builder()
                .total(total)
                .page(page)
                .size(size)
                .items(Collections.emptyList())
                .build();
        }

        int end = Math.min(start + size, users.size());
        List<UserResponse> items = users.subList(start, end).stream()
            .map(this::convertToSimpleUserResponse)
            .collect(Collectors.toList());

        return PageResponse.<UserResponse>builder()
            .total(total)
            .page(page)
            .size(size)
            .items(items)
            .build();
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
                ? user.getRoles()
                : java.util.Collections.emptyList())
            .addAllPermissions(user.getPermissions() != null ? user.getPermissions() : java.util.Collections.emptyList())
            .build();
    }

    private UserRpcResponse convertToProtobuf(TokenResponse.UserInfo user) {
        return UserRpcResponse.newBuilder()
            .setId(user.getId())
            .setUsername(user.getUsername())
            .setEmail(user.getEmail() != null ? user.getEmail() : "")
            .setNickname(user.getNickname() != null ? user.getNickname() : "")
            .setAvatar(user.getAvatar() != null ? user.getAvatar() : "")
            .addAllRoles(user.getRoles() != null ? user.getRoles() : java.util.Collections.emptySet())
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

    private UserResponse convertToSimpleUserResponse(cn.wanyj.auth.entity.User user) {
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
            .lastLoginAt(user.getLastLoginAt())
            .createdAt(user.getCreatedAt())
            .roles(user.getRoles() != null
                ? user.getRoles().stream().map(r -> r.getCode()).collect(Collectors.toSet())
                : Collections.emptySet())
            .permissions(Collections.emptySet())
            .build();
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
