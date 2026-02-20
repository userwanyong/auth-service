package cn.wanyj.auth.service;

import cn.wanyj.auth.dto.response.PermissionResponse;

import java.util.List;

/**
 * Permission Service - 权限服务接口
 * @author wanyj
 */
public interface PermissionService {

    /**
     * Get all permissions
     * 获取所有权限
     */
    List<PermissionResponse> getAllPermissions();

    /**
     * Get permission by id
     * 根据ID获取权限
     */
    PermissionResponse getPermissionById(Long id);

    /**
     * Create new permission
     * 创建新权限
     */
    PermissionResponse createPermission(String code, String name, String resource, String action, String description);

    /**
     * Delete permission by id
     * 根据ID删除权限
     */
    void deletePermission(Long id);
}
