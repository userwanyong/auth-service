package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.security.SecurityUtils;
import cn.wanyj.auth.util.UserFieldValidator;
import cn.wanyj.auth.dto.request.AssignRolesRequest;
import cn.wanyj.auth.dto.request.UpdateUserRequest;
import cn.wanyj.auth.dto.response.PageResponse;
import cn.wanyj.auth.dto.response.UserResponse;
import cn.wanyj.auth.entity.Role;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.entity.UserRole;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.mapper.RoleMapper;
import cn.wanyj.auth.mapper.UserMapper;
import cn.wanyj.auth.mapper.UserRoleMapper;
import cn.wanyj.auth.service.OssService;
import cn.wanyj.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User Service Implementation - 用户服务实现
 * @author wanyj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final OssService ossService;

    @Override
    public UserResponse getUserById(Long id) {
        return getUserById(id, SecurityUtils.getCurrentTenantId());
    }

    @Override
    public UserResponse getUserById(Long userId, Long tenantId) {
        User user = userMapper.findByIdWithRolesAndPermissions(userId, tenantId);
        if (user == null || !user.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return mapToUserResponse(user);
    }

    @Override
    public UserResponse getUserByUsername(String username) {
        return getUserByUsername(username, SecurityUtils.getCurrentTenantId());
    }

    @Override
    public UserResponse getUserByUsername(String username, Long tenantId) {
        // 一次查出用户及其角色权限（替代旧的 N+1 逐条加载角色）
        User user = userMapper.findByUsernameWithRolesAndPermissions(username, tenantId);
        if (user == null || !user.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return mapToUserResponse(user);
    }

    @Override
    public PageResponse<UserResponse> searchUsers(String keyword, Integer page, Integer size) {
        return searchUsers(keyword, SecurityUtils.getCurrentTenantId(), page, size);
    }

    @Override
    public PageResponse<UserResponse> searchUsers(String keyword, Long tenantId, Integer page, Integer size) {
        log.info("searchUsers called with: keyword={}, page={}, size={}, tenantId={}", keyword, page, size, tenantId);
        List<User> users;
        long total;

        if (keyword != null && !keyword.trim().isEmpty()) {
            users = userMapper.findByKeywordWithRolesAndPermissions(keyword, tenantId);
            total = userMapper.countByKeyword(keyword, tenantId);
        } else {
            // Get all users with roles and permissions for the tenant when no keyword is provided
            users = userMapper.findAllByTenantIdWithRolesAndPermissions(tenantId);
            total = userMapper.countAllByTenantId(tenantId);
        }

        log.info("Found {} users for tenantId={}, total={}", users.size(), tenantId, total);

        // Apply pagination (guard against out-of-range start)
        int start = (page - 1) * size;
        if (start >= users.size()) {
            return PageResponse.<UserResponse>builder()
                    .total(total)
                    .page(page)
                    .size(size)
                    .items(Collections.emptyList())
                    .build();
        }
        int end = Math.min(start + size, users.size());
        List<User> pagedUsers = users.subList(start, end);

        return PageResponse.<UserResponse>builder()
                .total(total)
                .page(page)
                .size(size)
                .items(pagedUsers.stream()
                        .map(this::mapToSimpleUserResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, AssignRolesRequest request) {
        assignRoles(userId, SecurityUtils.getCurrentTenantId(), request);
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, Long tenantId, AssignRolesRequest request) {
        log.info("Assigning roles to user: {} in tenant: {}", userId, tenantId);

        loadUserAndVerifyTenant(userId, tenantId);

        // Delete existing role assignments
        userMapper.deleteUserRolesByUserId(userId);

        // Create new role assignments
        for (Long roleId : request.getRoleIds()) {
            Role role = roleMapper.findById(roleId);
            // 校验角色存在且属于同一租户（防止跨租户分配角色）
            if (role == null || (tenantId != null && !tenantId.equals(role.getTenantId()))) {
                throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
            }

            // Create UserRole relationship（统一使用调用方租户）
            userMapper.insertUserRole(userId, roleId, tenantId);
        }

        log.info("Roles assigned successfully to user: {}", userId);
    }

    @Override
    @Transactional
    public void updateUserStatus(Long userId, Integer status) {
        updateUserStatus(userId, SecurityUtils.getCurrentTenantId(), status);
    }

    @Override
    @Transactional
    public void updateUserStatus(Long userId, Long tenantId, Integer status) {
        log.info("Updating status for user: {} to {} in tenant: {}", userId, status, tenantId);

        User user = loadUserAndVerifyTenant(userId, tenantId);

        user.setStatus(status);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.update(user);

        log.info("User status updated successfully: {}", userId);
    }

    @Override
    @Transactional
    public void updateUser(Long userId, UpdateUserRequest request) {
        Long tenantId = SecurityUtils.getCurrentTenantId();
        updateUser(userId, tenantId, request);
    }

    @Override
    @Transactional
    public void updateUser(Long userId, Long tenantId, UpdateUserRequest request) {
        log.info("Updating user profile: {} in tenant: {}", userId, tenantId);

        // Validate contact field formats (email/phone) if provided
        UserFieldValidator.validateContactFields(request.getEmail(), request.getPhone());

        User user = userMapper.findById(userId);
        if (user == null || (tenantId != null && !tenantId.equals(user.getTenantId()))) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        String username = request.getUsername() != null ? request.getUsername().trim() : null;
        if (username != null && !username.isEmpty() && !username.equals(user.getUsername())) {
            if (tenantId != null && userMapper.existsByUsername(username, tenantId)) {
                throw new BusinessException(ErrorCode.USERNAME_EXISTS);
            }
            user.setUsername(username);
        }

        // email: null=不改，空串=清空，非空=更新（含租户内唯一性校验）
        if (request.getEmail() != null) {
            String email = request.getEmail().trim();
            email = email.isEmpty() ? null : email;
            if (email != null && !email.equals(user.getEmail()) && tenantId != null && userMapper.existsByEmail(email, tenantId)) {
                throw new BusinessException(ErrorCode.EMAIL_EXISTS);
            }
            user.setEmail(email);
        }

        if (request.getPhone() != null) {
            String phone = request.getPhone().trim();
            user.setPhone(phone.isEmpty() ? null : phone);
        }

        // nickname: null=不改，空串=清空，非空=更新
        if (request.getNickname() != null) {
            String nickname = request.getNickname().trim();
            user.setNickname(nickname.isEmpty() ? null : nickname);
        }

        // realName: null=不改，空串=清空，非空=更新
        if (request.getRealName() != null) {
            String realName = request.getRealName().trim();
            user.setRealName(realName.isEmpty() ? null : realName);
        }

        // gender: null=不改
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }

        // birthday: null=不改
        if (request.getBirthday() != null) {
            user.setBirthday(request.getBirthday());
        }

        String staleAvatar = null;
        if (request.getAvatar() != null) {
            String newAvatar = request.getAvatar().trim();
            newAvatar = newAvatar.isEmpty() ? null : newAvatar;
            // 头像被替换：记录旧值，DB 更新成功后再清理 OSS 旧对象
            if (user.getAvatar() != null && !user.getAvatar().equals(newAvatar)) {
                staleAvatar = user.getAvatar();
            }
            user.setAvatar(newAvatar);
        }

        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        String password = request.getPassword();
        if (password != null && !password.isBlank()) {
            user.setPassword(passwordEncoder.encode(password));
        }

        userMapper.update(user);

        // DB 更新成功后清理被替换的旧头像（OSS 删除失败不阻断业务，仅记日志）
        if (staleAvatar != null) {
            ossService.deleteAvatarByUrl(staleAvatar);
        }

        log.info("User profile updated successfully: {} in tenant: {}", userId, tenantId);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        deleteUser(userId, SecurityUtils.getCurrentTenantId());
    }

    @Override
    @Transactional
    public void deleteUser(Long userId, Long tenantId) {
        log.info("Deleting user: {} in tenant: {}", userId, tenantId);

        User user = loadUserAndVerifyTenant(userId, tenantId);
        // 头像 objectKey 由上传者（操作人）决定，未必落在该用户前缀下，
        // 因此按 DB 中实际存储的头像 URL 删除，而不是按用户前缀猜测
        String avatarUrl = user.getAvatar();

        // Delete user roles first
        userMapper.deleteUserRolesByUserId(userId);

        // Delete user
        userMapper.deleteById(userId);

        // DB 删除成功后，清理该用户的头像 OSS 对象（失败不阻断业务，仅记日志）
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            ossService.deleteAvatarByUrl(avatarUrl);
        }

        log.info("User deleted successfully: {}", userId);
    }

    @Override
    public boolean hasPermission(Long userId, Long tenantId, String permission) {
        User user = loadUserOrNull(userId, tenantId);
        if (user == null || user.getStatus() == 0) {
            return false;
        }
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(p -> p.getCode().equals(permission));
    }

    @Override
    public boolean hasRole(Long userId, Long tenantId, String role) {
        User user = loadUserOrNull(userId, tenantId);
        if (user == null || user.getStatus() == 0) {
            return false;
        }
        return user.getRoles().stream()
                .anyMatch(r -> r.getCode().equals(role));
    }

    @Override
    public List<String> getUserPermissions(Long userId, Long tenantId) {
        User user = loadUserOrNull(userId, tenantId);
        if (user == null) {
            return Collections.emptyList();
        }
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(p -> p.getCode())
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getUserRoles(Long userId, Long tenantId) {
        User user = loadUserOrNull(userId, tenantId);
        if (user == null) {
            return Collections.emptyList();
        }
        return user.getRoles().stream()
                .map(r -> r.getCode())
                .collect(Collectors.toList());
    }

    /**
     * 加载用户（含角色权限）并校验租户归属
     * 用户不存在或不属于指定租户时返回 null（供权限/角色查询安全降级，不抛异常）
     */
    private User loadUserOrNull(Long userId, Long tenantId) {
        User user = userMapper.findByIdWithRolesAndPermissions(userId, tenantId);
        if (user == null || !user.getTenantId().equals(tenantId)) {
            return null;
        }
        return user;
    }

    /**
     * 加载用户并校验租户归属
     * 用户不存在或不属于指定租户时抛 USER_NOT_FOUND（不泄露资源存在性）
     */
    private User loadUserAndVerifyTenant(Long userId, Long tenantId) {
        User user = userMapper.findById(userId);
        if (user == null || (tenantId != null && !tenantId.equals(user.getTenantId()))) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * Map User entity to UserResponse DTO (with all details)
     */
    private UserResponse mapToUserResponse(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getCode)
                .collect(Collectors.toSet());

        Set<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getCode())
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
                .realName(user.getRealName())
                .gender(user.getGender())
                .birthday(user.getBirthday())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    /**
     * Map User entity to simplified UserResponse DTO (for list view)
     */
    private UserResponse mapToSimpleUserResponse(User user) {
        Set<String> roles = user.getRoles() != null ?
                user.getRoles().stream()
                        .map(Role::getCode)
                        .collect(Collectors.toSet()) :
                new HashSet<>();

        return UserResponse.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .realName(user.getRealName())
                .gender(user.getGender())
                .birthday(user.getBirthday())
                .status(user.getStatus())
                .emailVerified(user.getEmailVerified())
                .phoneVerified(user.getPhoneVerified())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .roles(roles)
                .build();
    }
}
