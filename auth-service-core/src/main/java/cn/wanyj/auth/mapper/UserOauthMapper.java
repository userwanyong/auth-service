package cn.wanyj.auth.mapper;

import cn.wanyj.auth.entity.UserOauth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * OAuth 账号绑定 Mapper
 *
 * @author wanyj
 * @since 1.0.0
 */
@Mapper
public interface UserOauthMapper {

    /**
     * 按租户+提供方+提供方UID 查找绑定（OAuth 登录匹配本地用户 / 绑定时冲突检查）
     */
    UserOauth findByTenantProviderUid(@Param("tenantId") Long tenantId,
                                     @Param("provider") String provider,
                                     @Param("providerUid") String providerUid);

    /**
     * 查某用户在某租户下的所有 OAuth 绑定（账号绑定页展示）
     */
    List<UserOauth> findByTenantUserId(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    /**
     * 查某用户是否已绑定某 provider（绑定前检查，避免重复绑定）
     */
    UserOauth findByTenantUserProvider(@Param("tenantId") Long tenantId,
                                      @Param("userId") Long userId,
                                      @Param("provider") String provider);

    int insert(UserOauth userOauth);

    /**
     * 解绑：删除某用户某 provider 的绑定
     */
    int deleteByTenantUserProvider(@Param("tenantId") Long tenantId,
                                  @Param("userId") Long userId,
                                  @Param("provider") String provider);

    /**
     * 删除用户时级联清理其全部 OAuth 绑定（防止孤儿绑定指向已删除用户）
     */
    int deleteByUserId(@Param("userId") Long userId);
}
