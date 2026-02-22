package cn.wanyj.auth.mapper;

import cn.wanyj.auth.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 租户 Mapper
 *
 * @author wanyj
 * @since 1.0.0
 */
@Mapper
public interface TenantMapper {

    /**
     * 根据ID查询租户
     *
     * @param id 租户ID
     * @return 租户信息
     */
    Tenant findById(@Param("id") Long id);

    /**
     * 根据租户编码查询租户
     *
     * @param tenantCode 租户编码
     * @return 租户信息
     */
    Tenant findByCode(@Param("tenantCode") String tenantCode);

    /**
     * 查询所有租户
     *
     * @return 租户列表
     */
    List<Tenant> findAll();

    /**
     * 检查租户编码是否存在
     *
     * @param tenantCode 租户编码
     * @return 是否存在
     */
    boolean existsByCode(@Param("tenantCode") String tenantCode);

    /**
     * 插入租户
     *
     * @param tenant 租户信息
     * @return 影响行数
     */
    int insert(Tenant tenant);

    /**
     * 更新租户
     *
     * @param tenant 租户信息
     * @return 影响行数
     */
    int update(Tenant tenant);

    /**
     * 根据ID删除租户
     *
     * @param id 租户ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);

    /**
     * 统计租户下的用户数量
     *
     * @param tenantId 租户ID
     * @return 用户数量
     */
    long countUsersByTenantId(@Param("tenantId") Long tenantId);
}
