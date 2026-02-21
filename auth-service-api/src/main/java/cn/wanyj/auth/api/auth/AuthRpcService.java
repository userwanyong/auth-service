package cn.wanyj.auth.api.auth;

import cn.wanyj.auth.api.model.*;

/**
 * 认证服务 RPC 接口
 * 其他微服务通过此接口调用认证服务
 *
 * @author wanyj
 */
public interface AuthRpcService {

    /**
     * 验证用户凭据（内部服务调用）
     * 用于其他服务验证用户名密码
     *
     * @param request 登录请求
     * @return 认证结果
     */
    AuthResult authenticate(LoginRpcRequest request);

    /**
     * 验证访问令牌有效性
     *
     * @param accessToken 访问令牌
     * @return 令牌验证结果，包含用户信息
     */
    TokenValidationResult validateToken(String accessToken);

    /**
     * 根据用户ID获取用户信息
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    UserRpcResponse getUserById(Long userId);

    /**
     * 根据用户名获取用户信息
     *
     * @param username 用户名
     * @return 用户信息
     */
    UserRpcResponse getUserByUsername(String username);

    /**
     * 验证用户是否拥有指定权限
     *
     * @param userId 用户ID
     * @param permission 权限码
     * @return 是否拥有权限
     */
    boolean hasPermission(Long userId, String permission);

    /**
     * 验证用户是否拥有指定角色
     *
     * @param userId 用户ID
     * @param role 角色码
     * @return 是否拥有角色
     */
    boolean hasRole(Long userId, String role);

    /**
     * 获取用户的所有权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    java.util.Set<String> getUserPermissions(Long userId);

    /**
     * 获取用户的所有角色
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    java.util.Set<String> getUserRoles(Long userId);
}
