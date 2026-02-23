package cn.wanyj.auth.mapper;

import cn.wanyj.auth.entity.Role;
import cn.wanyj.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * User Mapper - 用户数据访问层
 * @author wanyj
 */
@Mapper
public interface UserMapper {

    /**
     * Find user by id
     * 根据ID查找用户
     */
    User findById(@Param("id") Long id);

    /**
     * Find user by username and tenant id
     * 根据用户名和租户ID查找用户
     */
    User findByUsername(@Param("username") String username, @Param("tenantId") Long tenantId);

    /**
     * Find user by email and tenant id
     * 根据邮箱和租户ID查找用户
     */
    User findByEmail(@Param("email") String email, @Param("tenantId") Long tenantId);

    /**
     * Find user by username or email and tenant id
     * 根据用户名或邮箱和租户ID查找用户
     */
    User findByUsernameOrEmail(@Param("identifier") String identifier, @Param("tenantId") Long tenantId);

    /**
     * Check if username exists in tenant
     * 检查用户名在租户内是否存在
     */
    boolean existsByUsername(@Param("username") String username, @Param("tenantId") Long tenantId);

    /**
     * Check if email exists in tenant
     * 检查邮箱在租户内是否存在
     */
    boolean existsByEmail(@Param("email") String email, @Param("tenantId") Long tenantId);

    /**
     * Find users by keyword (username or email) with pagination
     * 根据关键字搜索用户（分页）
     */
    List<User> findByKeyword(@Param("keyword") String keyword, @Param("tenantId") Long tenantId);

    /**
     * Count users by keyword
     * 统计用户数量
     */
    long countByKeyword(@Param("keyword") String keyword, @Param("tenantId") Long tenantId);

    /**
     * Find all users by tenant id
     * 根据租户ID查找所有用户
     */
    List<User> findAllByTenantId(@Param("tenantId") Long tenantId);

    /**
     * Find all users with roles by tenant id
     * 根据租户ID查找所有用户及其角色信息
     */
    List<User> findAllByTenantIdWithRoles(@Param("tenantId") Long tenantId);

    /**
     * Count all users by tenant id
     * 统计租户下的用户总数
     */
    long countAllByTenantId(@Param("tenantId") Long tenantId);

    /**
     * Insert user
     * 插入用户
     */
    int insert(User user);

    /**
     * Update user
     * 更新用户
     */
    int update(User user);

    /**
     * Delete user by id
     * 根据ID删除用户
     */
    int deleteById(@Param("id") Long id);

    /**
     * Find user with roles by id
     * 查找用户及其角色信息
     */
    User findByIdWithRoles(@Param("id") Long id);

    /**
     * Find user with roles and permissions by id and tenant id
     * 根据ID和租户ID查找用户及其角色和权限信息
     */
    User findByIdWithRolesAndPermissions(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /**
     * Find user with roles and permissions by username or email and tenant id
     * 根据用户名或邮箱和租户ID查找用户及其角色和权限信息
     */
    User findByUsernameOrEmailWithRolesAndPermissions(@Param("identifier") String identifier, @Param("tenantId") Long tenantId);

    /**
     * Find user with roles and permissions by username and tenant id
     * 根据用户名和租户ID查找用户及其角色和权限信息
     */
    User findByUsernameWithRolesAndPermissions(@Param("username") String username, @Param("tenantId") Long tenantId);

    /**
     * Insert user role
     * 插入用户角色关联
     */
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId, @Param("tenantId") Long tenantId);

    /**
     * Delete user roles by user id
     * 根据用户ID删除用户角色关联
     */
    int deleteUserRolesByUserId(@Param("userId") Long userId);

    /**
     * Find roles by user id
     * 根据用户ID查找角色
     */
    List<Long> findRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * Find role by code and tenant id
     * 根据角色编码和租户ID查找角色
     */
    Role findRoleByCodeAndTenantId(@Param("code") String code, @Param("tenantId") Long tenantId);

    /**
     * Delete users by tenant id
     * 根据租户ID删除所有用户
     */
    int deleteByTenantId(@Param("tenantId") Long tenantId);
}
