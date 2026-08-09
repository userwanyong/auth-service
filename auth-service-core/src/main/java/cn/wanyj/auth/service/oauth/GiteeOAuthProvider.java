package cn.wanyj.auth.service.oauth;

import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Gitee OAuth 提供方
 * <p>
 * config: {clientId, clientSecret, scope(默认 user_info), redirectUri}
 *
 * @author wanyj
 * @since 1.0.0
 */
@Slf4j
@Component
public class GiteeOAuthProvider implements OAuthProvider {

    private static final String AUTHORIZE_URL = "https://gitee.com/oauth/authorize";
    private static final String TOKEN_URL = "https://gitee.com/oauth/token";
    private static final String USER_URL = "https://gitee.com/api/v5/user";

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getProvider() {
        return "gitee";
    }

    @Override
    public String buildAuthorizeUrl(Map<String, String> config, String redirectUri, String state) {
        String scope = config.getOrDefault("scope", "user_info");
        return AUTHORIZE_URL
                + "?client_id=" + enc(config.get("clientId"))
                + "&redirect_uri=" + enc(redirectUri)
                + "&response_type=code"
                + "&state=" + enc(state)
                + "&scope=" + enc(scope);
    }

    @Override
    public String exchangeAccessToken(String code, Map<String, String> config, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", config.get("clientId"));
        form.add("client_secret", config.get("clientSecret"));
        form.add("redirect_uri", redirectUri);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    TOKEN_URL, new HttpEntity<>(form, headers), Map.class);
            Map<?, ?> body = resp.getBody();
            Object token = body == null ? null : body.get("access_token");
            if (token == null) {
                throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "Gitee token 交换失败");
            }
            return token.toString();
        } catch (RestClientException e) {
            log.error("Gitee token exchange failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "Gitee token 交换失败: " + e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo fetchUser(String accessToken, Map<String, String> config) {
        try {
            Map<String, Object> body = restTemplate.getForObject(
                    USER_URL + "?access_token=" + enc(accessToken), Map.class);
            if (body == null) {
                throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "Gitee 用户信息获取失败");
            }
            return OAuthUserInfo.builder()
                    .providerUid(str(body.get("id")))
                    .email(str(body.get("email")))
                    .nickname(str(body.get("login")))
                    .avatar(str(body.get("avatar_url")))
                    .build();
        } catch (RestClientException e) {
            log.error("Gitee fetch user failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "Gitee 用户信息获取失败: " + e.getMessage());
        }
    }

    private static String enc(String s) {
        if (s == null) return "";
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
