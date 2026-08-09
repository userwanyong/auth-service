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
 * Microsoft (Azure AD v2.0 / OIDC) OAuth 提供方
 * <p>
 * config: {clientId, clientSecret, tenant(默认 common), scope(默认 openid profile email), redirectUri}
 *
 * @author wanyj
 * @since 1.0.0
 */
@Slf4j
@Component
public class MicrosoftOAuthProvider implements OAuthProvider {

    @Override
    public String getProvider() {
        return "microsoft";
    }

    private final RestTemplate restTemplate = new RestTemplate();

    private String authorizeUrl(String tenant) {
        return "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/authorize";
    }

    private String tokenUrl(String tenant) {
        return "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token";
    }

    @Override
    public String buildAuthorizeUrl(Map<String, String> config, String redirectUri, String state) {
        String tenant = config.getOrDefault("tenant", "common");
        String scope = "openid profile email";
        return authorizeUrl(tenant)
                + "?client_id=" + enc(config.get("clientId"))
                + "&redirect_uri=" + enc(redirectUri)
                + "&response_type=code"
                + "&response_mode=query"
                + "&state=" + enc(state)
                + "&scope=" + enc(scope);
    }

    @Override
    public String exchangeAccessToken(String code, Map<String, String> config, String redirectUri) {
        String tenant = config.getOrDefault("tenant", "common");
        String scope = "openid profile email";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", config.get("clientId"));
        form.add("client_secret", config.get("clientSecret"));
        form.add("redirect_uri", redirectUri);
        form.add("scope", scope);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    tokenUrl(tenant), new HttpEntity<>(form, headers), Map.class);
            Map<?, ?> body = resp.getBody();
            Object token = body == null ? null : body.get("access_token");
            if (token == null) {
                throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "Microsoft token 交换失败");
            }
            return token.toString();
        } catch (RestClientException e) {
            log.error("Microsoft token exchange failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "Microsoft token 交换失败: " + e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo fetchUser(String accessToken, Map<String, String> config) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            org.springframework.http.HttpEntity<Void> req = new org.springframework.http.HttpEntity<>(headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    "https://graph.microsoft.com/oidc/userinfo",
                    org.springframework.http.HttpMethod.GET, req, Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) {
                throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "Microsoft 用户信息获取失败");
            }
            return OAuthUserInfo.builder()
                    .providerUid(str(body.get("sub")))
                    .email(str(body.get("email")))
                    .nickname(str(body.get("name")))
                    .avatar(str(body.get("picture")))
                    .build();
        } catch (RestClientException e) {
            log.error("Microsoft fetch user failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "Microsoft 用户信息获取失败: " + e.getMessage());
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
