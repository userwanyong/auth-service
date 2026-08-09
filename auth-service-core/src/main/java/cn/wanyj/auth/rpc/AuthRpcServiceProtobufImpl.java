package cn.wanyj.auth.rpc;

import cn.wanyj.auth.api.protobuf.*;
import cn.wanyj.auth.dto.request.ChangePasswordRequest;
import cn.wanyj.auth.dto.request.LoginRequest;
import cn.wanyj.auth.dto.request.RegisterRequest;
import cn.wanyj.auth.dto.response.PageResponse;
import cn.wanyj.auth.dto.response.TokenResponse;
import cn.wanyj.auth.dto.response.UserResponse;
import cn.wanyj.auth.entity.Tenant;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.rpc.converter.UserProtobufConverter;
import cn.wanyj.auth.service.AuthService;
import cn.wanyj.auth.service.TenantService;
import cn.wanyj.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 认证服务 RPC 实现 - Protobuf IDL 模式
 * <p>查询类方法复用 {@link UserService}（带显式 tenantId），Protobuf 转换复用
 * {@link UserProtobufConverter}，本类不再直接访问 Mapper。</p>
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
    private final UserService userService;
    private final TenantService tenantService;

    @Override
    public RegisterRpcResult register(RegisterRpcRequest request) {
        log.info("RPC register: username={}, tenantId={}", request.getUsername(), request.getTenantId());
        try {
            TokenResponse tokenResponse = authService.register(
                RegisterRequest.builder()
                    .username(request.getUsername())
                    .password(request.getPassword())
                    .tenantId(Long.parseLong(request.getTenantId()))
                    .email(emptyToNull(request.getEmail()))
                    .phone(emptyToNull(request.getPhone()))
                    .nickname(emptyToNull(request.getNickname()))
                    .realName(emptyToNull(request.getRealName()))
                    .gender(request.getGender())
                    .birthday(request.getBirthday().isBlank() ? null : java.time.LocalDate.parse(request.getBirthday()))
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
                .setUser(UserProtobufConverter.convertToProtobuf(tokenResponse.getUser()))
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
            // RPC 内部按数字 tenantId 定位租户，转对外 uid 走统一登录入口
            Tenant tenant = tenantService.getTenantById(Long.parseLong(request.getTenantId()));
            if (tenant == null || tenant.getTenantUid() == null) {
                throw new BusinessException(ErrorCode.INVALID_TENANT);
            }
            TokenResponse tokenResponse = authService.login(
                LoginRequest.builder()
                    .username(request.getUsername())
                    .password(request.getPassword())
                    .tenantUid(tenant.getTenantUid())
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
            Long userId = Long.parseLong(request.getUserId());
            Long tenantId = Long.parseLong(request.getTenantId());

            UserResponse user = userService.getUserById(userId, tenantId);
            // 禁用用户对 RPC 调用方不可见（与改造前行为一致）
            if (user.getStatus() != null && user.getStatus() == 0) {
                log.warn("User disabled: userId={}, tenantId={}", userId, tenantId);
                return UserRpcResponse.getDefaultInstance();
            }
            return UserProtobufConverter.convertToProtobuf(user);
        } catch (BusinessException e) {
            log.warn("getUserById failed: {}", e.getMessage());
            return UserRpcResponse.getDefaultInstance();
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
            Long tenantId = Long.parseLong(request.getTenantId());

            UserResponse user = userService.getUserByUsername(request.getUsername(), tenantId);
            // 禁用用户对 RPC 调用方不可见（与改造前行为一致）
            if (user.getStatus() != null && user.getStatus() == 0) {
                log.warn("User disabled: username={}, tenantId={}", request.getUsername(), tenantId);
                return UserRpcResponse.getDefaultInstance();
            }
            return UserProtobufConverter.convertToProtobuf(user);
        } catch (BusinessException e) {
            log.warn("getUserByUsername failed: {}", e.getMessage());
            return UserRpcResponse.getDefaultInstance();
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
            Long userId = Long.parseLong(request.getUserId());
            Long tenantId = Long.parseLong(request.getTenantId());
            // Service 内部已处理：用户不存在/跨租户/禁用 → false
            boolean has = userService.hasPermission(userId, tenantId, request.getPermission());
            return BoolValue.newBuilder().setValue(has).build();
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
            Long userId = Long.parseLong(request.getUserId());
            Long tenantId = Long.parseLong(request.getTenantId());
            boolean has = userService.hasRole(userId, tenantId, request.getRole());
            return BoolValue.newBuilder().setValue(has).build();
        } catch (Exception e) {
            log.error("Failed to check role", e);
            return BoolValue.newBuilder().setValue(false).build();
        }
    }

    @Override
    public StringListResponse getUserPermissions(UserPermissionsRequest request) {
        log.info("RPC getUserPermissions: userId={}, tenantId={}", request.getUserId(), request.getTenantId());
        try {
            Long userId = Long.parseLong(request.getUserId());
            Long tenantId = Long.parseLong(request.getTenantId());
            // Service 对用户不存在/跨租户返回空列表
            List<String> permissions = userService.getUserPermissions(userId, tenantId);
            return StringListResponse.newBuilder().addAllValues(permissions).build();
        } catch (Exception e) {
            log.error("Failed to get user permissions", e);
            return StringListResponse.getDefaultInstance();
        }
    }

    @Override
    public StringListResponse getUserRoles(UserRolesRequest request) {
        log.info("RPC getUserRoles: userId={}, tenantId={}", request.getUserId(), request.getTenantId());
        try {
            Long userId = Long.parseLong(request.getUserId());
            Long tenantId = Long.parseLong(request.getTenantId());
            List<String> roles = userService.getUserRoles(userId, tenantId);
            return StringListResponse.newBuilder().addAllValues(roles).build();
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
            Long tenantId = Long.parseLong(request.getTenantId());

            PageResponse<UserResponse> pageResponse = userService.searchUsers(keyword, tenantId, page, size);

            UserPageResponse.Builder builder = UserPageResponse.newBuilder()
                .setTotal(pageResponse.getTotal() != null ? pageResponse.getTotal() : 0L)
                .setPage(pageResponse.getPage() != null ? pageResponse.getPage() : page)
                .setSize(pageResponse.getSize() != null ? pageResponse.getSize() : size);

            if (pageResponse.getItems() != null) {
                builder.addAllItems(pageResponse.getItems().stream()
                    .map(UserProtobufConverter::convertToProtobuf)
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

    @Override
    public TokenRpcResponse refreshToken(RefreshTokenRpcRequest request) {
        log.info("RPC refreshToken");
        try {
            TokenResponse tokenResponse = authService.refreshToken(request.getRefreshToken());

            return TokenRpcResponse.newBuilder()
                .setAccessToken(tokenResponse.getAccessToken())
                .setRefreshToken(tokenResponse.getRefreshToken())
                .setExpiresIn(tokenResponse.getExpiresIn())
                .build();
        } catch (BusinessException e) {
            log.warn("Refresh token failed: {}", e.getMessage());
            return TokenRpcResponse.getDefaultInstance();
        } catch (Exception e) {
            log.error("Refresh token error", e);
            return TokenRpcResponse.getDefaultInstance();
        }
    }

    @Override
    public OperationResult logout(LogoutRpcRequest request) {
        log.info("RPC logout");
        try {
            authService.logout(request.getAccessToken(), request.getRefreshToken());
            return OperationResult.newBuilder()
                .setSuccess(true)
                .setMessage("登出成功")
                .build();
        } catch (BusinessException e) {
            log.warn("Logout failed: {}", e.getMessage());
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Logout error", e);
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage("登出失败")
                .build();
        }
    }

    @Override
    public OperationResult changePassword(ChangePasswordRpcRequest request) {
        log.info("RPC changePassword: userId={}, tenantId={}", request.getUserId(), request.getTenantId());
        try {
            Long userId = Long.parseLong(request.getUserId());
            Long tenantId = Long.parseLong(request.getTenantId());

            ChangePasswordRequest changePasswordRequest = ChangePasswordRequest.builder()
                .oldPassword(request.getOldPassword())
                .newPassword(request.getNewPassword())
                .build();

            authService.changePassword(userId, tenantId, changePasswordRequest);

            return OperationResult.newBuilder()
                .setSuccess(true)
                .setMessage("密码修改成功")
                .build();
        } catch (BusinessException e) {
            log.warn("Change password failed: {}", e.getMessage());
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Change password error", e);
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage("密码修改失败")
                .build();
        }
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
