package cn.wanyj.auth.mapper;

import cn.wanyj.auth.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Role Mapper - 角色数据访问层
 * @author wanyj
 */
@Mapper
public interface RoleMapper {

    /**
     * Find role by id
     * 根据ID查找角色
     */
    Role findById(@Param("id") Long id);

    /**
     * Find role by code
     * 根据角色编码查找角色
     */
    Role findByCode(@Param("code") String code);

    /**
     * Find all roles
     * 查找所有角色
     */
    List<Role> findAll();

    /**
     * Find all roles with permissions
     * 查找所有角色及其权限信息
     */
    List<Role> findAllWithPermissions();

    /**
     * Check if role code exists
     * 检查角色编码是否存在
     */
    boolean existsByCode(@Param("code") String code);

    /**
     * Insert role
     * 插入角色
     */
    int insert(Role role);

    /**
     * Update role
     * 更新角色
     */
    int update(Role role);

    /**
     * Delete role by id
     * 根据ID删除角色
     */
    int deleteById(@Param("id") Long id);

    /**
     * Find roles by user id
     * 根据用户ID查找角色
     */
    List<Role> findByUserId(@Param("userId") Long userId);

    /**
     * Find role with permissions by id
     * 查找角色及其权限信息
     */
    Role findByIdWithPermissions(@Param("id") Long id);

    /**
     * Insert role permission
     * 插入角色权限关联
     */
    int insertRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    /**
     * Delete role permissions by role id
     * 根据角色ID删除角色权限关联
     */
    int deleteRolePermissionsByRoleId(@Param("roleId") Long roleId);

    /**
     * Find permission ids by role id
     * 根据角色ID查找权限ID列表
     */
    List<Long> findPermissionIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * Count roles by status
     * 统计角色数量
     */
    long countByStatus(@Param("status") Integer status);
}
