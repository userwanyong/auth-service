package cn.wanyj.auth.mapper;

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
     * Find user by username
     * 根据用户名查找用户
     */
    User findByUsername(@Param("username") String username);

    /**
     * Find user by email
     * 根据邮箱查找用户
     */
    User findByEmail(@Param("email") String email);

    /**
     * Find user by username or email
     * 根据用户名或邮箱查找用户
     */
    User findByUsernameOrEmail(@Param("identifier") String identifier);

    /**
     * Check if username exists
     * 检查用户名是否存在
     */
    boolean existsByUsername(@Param("username") String username);

    /**
     * Check if email exists
     * 检查邮箱是否存在
     */
    boolean existsByEmail(@Param("email") String email);

    /**
     * Find users by keyword (username or email) with pagination
     * 根据关键字搜索用户（分页）
     */
    List<User> findByKeyword(@Param("keyword") String keyword);

    /**
     * Count users by keyword
     * 统计用户数量
     */
    long countByKeyword(@Param("keyword") String keyword);

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
     * Find user with roles and permissions by id
     * 查找用户及其角色和权限信息
     */
    User findByIdWithRolesAndPermissions(@Param("id") Long id);

    /**
     * Find user with roles and permissions by username or email
     * 查找用户及其角色和权限信息
     */
    User findByUsernameOrEmailWithRolesAndPermissions(@Param("identifier") String identifier);

    /**
     * Insert user role
     * 插入用户角色关联
     */
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

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
}
