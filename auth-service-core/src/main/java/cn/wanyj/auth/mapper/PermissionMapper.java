package cn.wanyj.auth.mapper;

import cn.wanyj.auth.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Permission Mapper - 权限数据访问层
 * @author wanyj
 */
@Mapper
public interface PermissionMapper {

    /**
     * Find permission by id
     * 根据ID查找权限
     */
    Permission findById(@Param("id") Long id);

    /**
     * Find permission by code and tenant id
     * 根据权限编码和租户ID查找权限
     */
    Permission findByCode(@Param("code") String code, @Param("tenantId") Long tenantId);

    /**
     * Find all permissions by tenant id
     * 根据租户ID查找所有权限
     */
    List<Permission> findAll(@Param("tenantId") Long tenantId);

    /**
     * Find permissions by resource and tenant id
     * 根据资源和租户ID查找权限
     */
    List<Permission> findByResource(@Param("resource") String resource, @Param("tenantId") Long tenantId);

    /**
     * Check if permission code exists in tenant
     * 检查权限编码在租户内是否存在
     */
    boolean existsByCode(@Param("code") String code, @Param("tenantId") Long tenantId);

    /**
     * Insert permission
     * 插入权限
     */
    int insert(Permission permission);

    /**
     * Update permission
     * 更新权限
     */
    int update(Permission permission);

    /**
     * Delete permission by id
     * 根据ID删除权限
     */
    int deleteById(@Param("id") Long id);

    /**
     * Find permissions by role id
     * 根据角色ID查找权限
     */
    List<Permission> findByRoleId(@Param("roleId") Long roleId);

    /**
     * Count permissions
     * 统计权限数量
     */
    long count();

    /**
     * Delete permissions by tenant id
     * 根据租户ID删除所有权限
     */
    int deleteByTenantId(@Param("tenantId") Long tenantId);
}
