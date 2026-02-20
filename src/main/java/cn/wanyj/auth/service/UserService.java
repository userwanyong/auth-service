package cn.wanyj.auth.service;

import cn.wanyj.auth.dto.request.AssignRolesRequest;
import cn.wanyj.auth.dto.response.PageResponse;
import cn.wanyj.auth.dto.response.UserResponse;

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
     * Delete user
     * 删除用户
     */
    void deleteUser(Long userId);
}
