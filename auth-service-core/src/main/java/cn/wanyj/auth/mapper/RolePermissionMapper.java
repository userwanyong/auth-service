package cn.wanyj.auth.mapper;

import cn.wanyj.auth.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * RolePermission Mapper - 角色权限关联数据访问层
 * @author wanyj
 */
@Mapper
public interface RolePermissionMapper {

    /**
     * Find role permissions by role id
     * 根据角色ID查找角色权限关联
     */
    List<RolePermission> findByRoleId(@Param("roleId") Long roleId);

    /**
     * Find role permissions by permission id
     * 根据权限ID查找角色权限关联
     */
    List<RolePermission> findByPermissionId(@Param("permissionId") Long permissionId);

    /**
     * Find role permission by role id and permission id
     * 根据角色ID和权限ID查找角色权限关联
     */
    RolePermission findByRoleIdAndPermissionId(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    /**
     * Insert role permission
     * 插入角色权限关联
     */
    int insert(RolePermission rolePermission);

    /**
     * Delete role permission by role id and permission id
     * 根据角色ID和权限ID删除角色权限关联
     */
    int deleteByRoleIdAndPermissionId(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    /**
     * Delete role permissions by role id
     * 根据角色ID删除所有角色权限关联
     */
    int deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * Delete role permissions by permission id
     * 根据权限ID删除所有角色权限关联
     */
    int deleteByPermissionId(@Param("permissionId") Long permissionId);

    /**
     * Count role permissions by role id
     * 统计角色的权限数量
     */
    long countByRoleId(@Param("roleId") Long roleId);

    /**
     * Count role permissions by permission id
     * 统计权限的角色数量
     */
    long countByPermissionId(@Param("permissionId") Long permissionId);
}
