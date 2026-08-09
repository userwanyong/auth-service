package cn.wanyj.auth.mapper;

import cn.wanyj.auth.entity.LoginMethodConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 登录方式配置 Mapper
 *
 * @author wanyj
 * @since 1.0.0
 */
@Mapper
public interface LoginMethodConfigMapper {

    /**
     * 按租户ID与方式查询（tenant_id=0 查平台级）
     */
    LoginMethodConfig findByTenantAndMethod(@Param("tenantId") Long tenantId, @Param("method") String method);

    /**
     * 查某租户（含平台 tenant_id=0）下的所有配置
     */
    List<LoginMethodConfig> findByTenantId(@Param("tenantId") Long tenantId);

    int insert(LoginMethodConfig config);

    int update(LoginMethodConfig config);

    int deleteByTenantAndMethod(@Param("tenantId") Long tenantId, @Param("method") String method);
}
