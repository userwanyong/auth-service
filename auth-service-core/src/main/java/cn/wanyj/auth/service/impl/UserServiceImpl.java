package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.security.SecurityUtils;
import cn.wanyj.auth.dto.request.AssignRolesRequest;
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
import cn.wanyj.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    @Override
    public UserResponse getUserById(Long id) {
        Long tenantId = SecurityUtils.getCurrentTenantId();
        User user = userMapper.findByIdWithRolesAndPermissions(id, tenantId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return mapToUserResponse(user);
    }

    @Override
    public UserResponse getUserByUsername(String username) {
        Long tenantId = SecurityUtils.getCurrentTenantId();
        User user = userMapper.findByUsername(username, tenantId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // Load roles
        List<Long> roleIds = userMapper.findRoleIdsByUserId(user.getId());
        Set<Role> roles = new HashSet<>();
        for (Long roleId : roleIds) {
            Role role = roleMapper.findById(roleId);
            if (role != null) {
                roles.add(role);
            }
        }
        user.setRoles(roles);
        return mapToUserResponse(user);
    }

    @Override
    public PageResponse<UserResponse> searchUsers(String keyword, Integer page, Integer size) {
        Long tenantId = SecurityUtils.getCurrentTenantId();
        List<User> users;
        long total;

        if (keyword != null && !keyword.trim().isEmpty()) {
            users = userMapper.findByKeyword(keyword, tenantId);
            total = userMapper.countByKeyword(keyword, tenantId);
        } else {
            // For simplicity, returning all users with pagination done in code
            // In production, you should implement proper pagination in MyBatis
            users = List.of(); // Empty list for now
            total = 0;
        }

        // Apply pagination
        int start = (page - 1) * size;
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
        log.info("Assigning roles to user: {}", userId);

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Delete existing role assignments
        userMapper.deleteUserRolesByUserId(userId);

        // Create new role assignments
        for (Long roleId : request.getRoleIds()) {
            Role role = roleMapper.findById(roleId);
            if (role == null) {
                throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
            }

            // Create UserRole relationship
            userMapper.insertUserRole(userId, roleId);
        }

        log.info("Roles assigned successfully to user: {}", userId);
    }

    @Override
    @Transactional
    public void updateUserStatus(Long userId, Integer status) {
        log.info("Updating status for user: {} to {}", userId, status);

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        user.setStatus(status);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.update(user);

        log.info("User status updated successfully: {}", userId);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        log.info("Deleting user: {}", userId);

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Delete user roles first
        userMapper.deleteUserRolesByUserId(userId);

        // Delete user
        userMapper.deleteById(userId);

        log.info("User deleted successfully: {}", userId);
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

    /**
     * Map User entity to simplified UserResponse DTO (for list view)
     */
    private UserResponse mapToSimpleUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
