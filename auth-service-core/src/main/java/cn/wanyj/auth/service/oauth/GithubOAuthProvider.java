package cn.wanyj.auth.service.oauth;

import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * GitHub OAuth 提供方
 * <p>
 * config: {clientId, clientSecret, scope(默认 read:user), redirectUri}
 * <p>
 * 注意：GitHub /user 默认不返回邮箱（用户邮箱私有时为 null），
 * 如需邮箱可加 scope user:email 并扩展调用 /user/emails。当前 email 可空，不影响登录。
 *
 * @author wanyj
 * @since 1.0.0
 */
@Slf4j
@Component
public class GithubOAuthProvider implements OAuthProvider {

    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getProvider() {
        return "github";
    }

    @Override
    public String buildAuthorizeUrl(Map<String, String> config, String redirectUri, String state) {
        String scope = "read:user";
        return AUTHORIZE_URL
                + "?client_id=" + enc(config.get("clientId"))
                + "&redirect_uri=" + enc(redirectUri)
                + "&state=" + enc(state)
                + "&scope=" + enc(scope);
    }

    @Override
    public String exchangeAccessToken(String code, Map<String, String> config, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", config.get("clientId"));
        form.add("client_secret", config.get("clientSecret"));
        form.add("redirect_uri", redirectUri);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON)); // GitHub 默认返回 text/plain，强制 JSON

        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    TOKEN_URL, new HttpEntity<>(form, headers), Map.class);
            Map<?, ?> body = resp.getBody();
            Object token = body == null ? null : body.get("access_token");
            if (token == null) {
                throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "GitHub token 交换失败");
            }
            return token.toString();
        } catch (RestClientException e) {
            log.error("GitHub token exchange failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "GitHub token 交换失败: " + e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo fetchUser(String accessToken, Map<String, String> config) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> req = new HttpEntity<>(headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    USER_URL, HttpMethod.GET, req, Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) {
                throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "GitHub 用户信息获取失败");
            }
            return OAuthUserInfo.builder()
                    .providerUid(str(body.get("id")))
                    .email(str(body.get("email")))
                    .nickname(str(body.get("login")))
                    .avatar(str(body.get("avatar_url")))
                    .build();
        } catch (RestClientException e) {
            log.error("GitHub fetch user failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "GitHub 用户信息获取失败: " + e.getMessage());
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
