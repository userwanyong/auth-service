package cn.wanyj.auth.service;

import cn.wanyj.auth.dto.request.AssignRolesRequest;
import cn.wanyj.auth.dto.request.UpdateUserRequest;
import cn.wanyj.auth.dto.response.PageResponse;
import cn.wanyj.auth.dto.response.UserResponse;

import java.util.List;

/**
 * User Service - 用户服务接口
 *
 * @author wanyj
 */
public interface UserService {

    /**
     * Get user by id
     * 根据ID获取用户
     */
    UserResponse getUserById(Long id);

    /**
     * Get user by username
     * 根据用户名获取用户
     */
    UserResponse getUserByUsername(String username);

    /**
     * Search users with pagination
     * 分页搜索用户
     */
    PageResponse<UserResponse> searchUsers(String keyword, Integer page, Integer size);

    /**
     * Assign roles to user
     * 为用户分配角色
     */
    void assignRoles(Long userId, AssignRolesRequest request);

    /**
     * Update user status
     * 更新用户状态
     */
    void updateUserStatus(Long userId, Integer status);

    /**
     * Update user profile fields
     */
    void updateUser(Long userId, UpdateUserRequest request);

    /**
     * Update user profile fields with explicit tenantId (for RPC context)
     * 更新用户资料（带显式租户ID，用于RPC上下文）
     */
    void updateUser(Long userId, Long tenantId, UpdateUserRequest request);

    /**
     * Delete user
     * 删除用户
     */
    void deleteUser(Long userId);

    /**
     * Update user status with explicit tenantId (for RPC context)
     * 更新用户状态（带显式租户ID，含归属校验）
     */
    void updateUserStatus(Long userId, Long tenantId, Integer status);

    /**
     * Assign roles with explicit tenantId (for RPC context)
     * 分配角色（带显式租户ID，含用户与角色归属校验）
     */
    void assignRoles(Long userId, Long tenantId, AssignRolesRequest request);

    /**
     * Delete user with explicit tenantId (for RPC context)
     * 删除用户（带显式租户ID，含归属校验）
     */
    void deleteUser(Long userId, Long tenantId);

    /**
     * Get user by id with explicit tenantId (for RPC context)
     * 根据ID获取用户（带显式租户ID，含归属校验；不存在/跨租户抛 USER_NOT_FOUND）
     */
    UserResponse getUserById(Long userId, Long tenantId);

    /**
     * Get user by username with explicit tenantId (for RPC context)
     * 根据用户名获取用户（带显式租户ID，含归属校验；不存在/跨租户抛 USER_NOT_FOUND）
     */
    UserResponse getUserByUsername(String username, Long tenantId);

    /**
     * Check if user has permission (explicit tenantId, for RPC context)
     * 检查用户是否拥有权限（用户不存在/跨租户/禁用 → false）
     */
    boolean hasPermission(Long userId, Long tenantId, String permission);

    /**
     * Check if user has role (explicit tenantId, for RPC context)
     * 检查用户是否拥有角色（用户不存在/跨租户/禁用 → false）
     */
    boolean hasRole(Long userId, Long tenantId, String role);

    /**
     * Get user permission codes (explicit tenantId, for RPC context)
     * 获取用户权限编码列表（用户不存在/跨租户 → 空列表）
     */
    List<String> getUserPermissions(Long userId, Long tenantId);

    /**
     * Get user role codes (explicit tenantId, for RPC context)
     * 获取用户角色编码列表（用户不存在/跨租户 → 空列表）
     */
    List<String> getUserRoles(Long userId, Long tenantId);

    /**
     * Search users with explicit tenantId (for RPC context)
     * 分页搜索用户（带显式租户ID）
     */
    PageResponse<UserResponse> searchUsers(String keyword, Long tenantId, Integer page, Integer size);
}
