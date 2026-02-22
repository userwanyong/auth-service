package cn.wanyj.auth.service;

import cn.wanyj.auth.entity.Tenant;

import java.util.List;

/**
 * 租户服务接口
 *
 * @author wanyj
 * @since 1.0.0
 */
public interface TenantService {

    /**
     * 验证租户是否有效
     * 有效的条件：
     * 1. 租户存在
     * 2. status = 1 (正常)
     * 3. expiredAt 为空 或 expiredAt > 当前时间
     *
     * @param tenantId 租户ID
     * @return 是否有效
     */
    boolean isValidTenant(Long tenantId);

    /**
     * 根据租户编码获取租户ID
     *
     * @param tenantCode 租户编码
     * @return 租户ID，不存在返回 null
     */
    Long getTenantIdByCode(String tenantCode);

    /**
     * 根据ID获取租户
     *
     * @param tenantId 租户ID
     * @return 租户信息，不存在返回 null
     */
    Tenant getTenantById(Long tenantId);

    /**
     * 根据编码获取租户
     *
     * @param tenantCode 租户编码
     * @return 租户信息，不存在返回 null
     */
    Tenant getTenantByCode(String tenantCode);

    /**
     * 获取所有租户列表
     *
     * @return 租户列表
     */
    List<Tenant> getAllTenants();

    /**
     * 检查租户编码是否存在
     *
     * @param tenantCode 租户编码
     * @return 是否存在
     */
    boolean existsByCode(String tenantCode);

    /**
     * 创建租户
     *
     * @param tenant 租户信息
     * @return 创建的租户
     */
    Tenant createTenant(Tenant tenant);

    /**
     * 更新租户
     *
     * @param tenant 租户信息
     * @return 更新的租户
     */
    Tenant updateTenant(Tenant tenant);

    /**
     * 删除租户
     *
     * @param tenantId 租户ID
     */
    void deleteTenant(Long tenantId);

    /**
     * 检查租户是否已达到用户数量限制
     *
     * @param tenantId 租户ID
     * @return 是否已达到限制
     */
    boolean isUserLimitReached(Long tenantId);

    /**
     * 初始化租户的默认角色和权限
     * 为新创建的租户初始化 ROLE_ADMIN 和 ROLE_USER 角色
     *
     * @param tenantId 租户ID
     */
    void initializeDefaultRolesAndPermissions(Long tenantId);
}
