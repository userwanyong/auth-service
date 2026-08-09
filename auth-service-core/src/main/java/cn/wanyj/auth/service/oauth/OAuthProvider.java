package cn.wanyj.auth.service.oauth;

import java.util.Map;

/**
 * OAuth 提供方接口（authorize / token / user 三步）
 * <p>
 * config 由 LoginMethodConfigService.getEffectiveConfig 提供（已解密），
 * 结构因 provider 而异：
 * <ul>
 *   <li>gitee: {clientId, clientSecret, scope, redirectUri}</li>
 *   <li>microsoft: {clientId, clientSecret, tenant, scope, redirectUri}</li>
 * </ul>
 *
 * @author wanyj
 * @since 1.0.0
 */
public interface OAuthProvider {

    /** 提供方标识：gitee / microsoft / github */
    String getProvider();

    /**
     * 构造授权页 URL（用户浏览器跳转到此 URL 完成授权）
     */
    String buildAuthorizeUrl(Map<String, String> config, String redirectUri, String state);

    /**
     * 用授权码换 access_token
     *
     * @return 提供方返回的 access_token
     */
    String exchangeAccessToken(String code, Map<String, String> config, String redirectUri);

    /**
     * 用 access_token 拉取用户信息
     */
    OAuthUserInfo fetchUser(String accessToken, Map<String, String> config);
}
