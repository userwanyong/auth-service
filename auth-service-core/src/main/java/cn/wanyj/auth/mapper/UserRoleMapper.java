package cn.wanyj.auth.mapper;

import cn.wanyj.auth.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * UserRole Mapper - 用户角色关联数据访问层
 * @author wanyj
 */
@Mapper
public interface UserRoleMapper {

    /**
     * Find user roles by user id
     * 根据用户ID查找用户角色关联
     */
    List<UserRole> findByUserId(@Param("userId") Long userId);

    /**
     * Find user roles by role id
     * 根据角色ID查找用户角色关联
     */
    List<UserRole> findByRoleId(@Param("roleId") Long roleId);

    /**
     * Find user role by user id and role id
     * 根据用户ID和角色ID查找用户角色关联
     */
    UserRole findByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * Insert user role
     * 插入用户角色关联
     */
    int insert(UserRole userRole);

    /**
     * Delete user role by user id and role id
     * 根据用户ID和角色ID删除用户角色关联
     */
    int deleteByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * Delete user roles by user id
     * 根据用户ID删除所有用户角色关联
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * Delete user roles by role id
     * 根据角色ID删除所有用户角色关联
     */
    int deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * Count user roles by user id
     * 统计用户的角色数量
     */
    long countByUserId(@Param("userId") Long userId);

    /**
     * Count user roles by role id
     * 统计角色的用户数量
     */
    long countByRoleId(@Param("roleId") Long roleId);

    /**
     * Delete user roles by tenant id
     * 根据租户ID删除所有用户角色关联
     */
    int deleteByTenantId(@Param("tenantId") Long tenantId);
}
