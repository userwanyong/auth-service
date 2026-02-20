package cn.wanyj.auth.service;

import cn.wanyj.auth.dto.request.AssignPermissionsRequest;
import cn.wanyj.auth.dto.response.RoleResponse;

import java.util.List;

/**
 * Role Service - 角色服务接口
 * @author wanyj
 */
public interface RoleService {

    /**
     * Get all roles
     * 获取所有角色
     */
    List<RoleResponse> getAllRoles();

    /**
     * Get role by id
     * 根据ID获取角色
     */
    RoleResponse getRoleById(Long id);

    /**
     * Get role by code
     * 根据编码获取角色
     */
    RoleResponse getRoleByCode(String code);

    /**
     * Create new role
     * 创建新角色
     */
    RoleResponse createRole(String code, String name, String description);

    /**
     * Update role
     * 更新角色
     */
    RoleResponse updateRole(Long id, String name, String description);

    /**
     * Delete role
     * 删除角色
     */
    void deleteRole(Long id);

    /**
     * Assign permissions to role
     * 为角色分配权限
     */
    void assignPermissions(Long roleId, AssignPermissionsRequest request);
}
