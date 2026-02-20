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
     * Find permission by code
     * 根据权限编码查找权限
     */
    Permission findByCode(@Param("code") String code);

    /**
     * Find all permissions
     * 查找所有权限
     */
    List<Permission> findAll();

    /**
     * Find permissions by resource
     * 根据资源查找权限
     */
    List<Permission> findByResource(@Param("resource") String resource);

    /**
     * Check if permission code exists
     * 检查权限编码是否存在
     */
    boolean existsByCode(@Param("code") String code);

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
}
