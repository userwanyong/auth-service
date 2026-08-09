package cn.wanyj.auth.mapper;

import cn.wanyj.auth.entity.UserOauth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * OAuth 账号绑定 Mapper
 *
 * @author wanyj
 * @since 1.0.0
 */
@Mapper
public interface UserOauthMapper {

    /**
     * 按租户+提供方+提供方UID 查找绑定（OAuth 登录匹配本地用户）
     */
    UserOauth findByTenantProviderUid(@Param("tenantId") Long tenantId,
                                     @Param("provider") String provider,
                                     @Param("providerUid") String providerUid);

    int insert(UserOauth userOauth);
}
