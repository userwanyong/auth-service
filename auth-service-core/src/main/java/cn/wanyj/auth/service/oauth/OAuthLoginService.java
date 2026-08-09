package cn.wanyj.auth.service.oauth;

import cn.wanyj.auth.dto.response.TokenResponse;
import cn.wanyj.auth.entity.Role;
import cn.wanyj.auth.entity.Tenant;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.entity.UserOauth;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.mapper.UserMapper;
import cn.wanyj.auth.mapper.UserOauthMapper;
import cn.wanyj.auth.security.JwtTokenProvider;
import cn.wanyj.auth.service.LoginMethodConfigService;
import cn.wanyj.auth.service.TenantService;
import cn.wanyj.auth.service.TokenService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiapxx.uid.generator.api.UidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * OAuth 登录编排：state 防 CSRF + 换 token + 匹配/建用户 + 签 token
 *
 * @author wanyj
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthLoginService {

    private static final String STATE_PREFIX = "oauth:state:";
    private static final long STATE_TTL_SECONDS = 600;

    private final RedisTemplate<String, Object> redisTemplate;
    private final LoginMethodConfigService loginMethodConfigService;
    private final TenantService tenantService;
    private final UserMapper userMapper;
    private final UserOauthMapper userOauthMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenService tokenService;
    private final UidGenerator uidGenerator;
    private final PasswordEncoder passwordEncoder;
    private final List<OAuthProvider> providers;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造授权页 URL：校验开关 → 生成 state 存 Redis → 返回授权 URL
     */
    public String buildAuthorizeUrl(String tenantUid, String provider) {
        String method = "oauth:" + provider;
        Tenant tenant = tenantService.getTenantByUid(tenantUid);
        if (tenant == null || !tenant.isValid()) {
            throw new BusinessException(ErrorCode.INVALID_TENANT);
        }
        if (!loginMethodConfigService.isEnabled(tenant.getId(), method)) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_DISABLED, "该登录方式未启用");
        }
        Map<String, String> cfg = parseConfig(loginMethodConfigService.getEffectiveConfig(tenant.getId(), method));
        String redirectUri = cfg.get("redirectUri");
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "未配置 redirectUri");
        }
        OAuthProvider p = findProvider(provider);
        String state = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(STATE_PREFIX + state, tenantUid + ":" + provider,
                STATE_TTL_SECONDS, TimeUnit.SECONDS);
        return p.buildAuthorizeUrl(cfg, redirectUri, state);
    }

    /**
     * 处理回调：校验 state → 换 token → 拉用户 → 匹配/建用户 → 签 token
     */
    @Transactional
    public TokenResponse handleCallback(String provider, String code, String state) {
        // 1. 校验 state（取出后删除，一次性）
        Object raw = redisTemplate.opsForValue().get(STATE_PREFIX + state);
        if (raw == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "OAuth state 无效或已过期");
        }
        redisTemplate.delete(STATE_PREFIX + state);
        String[] parts = raw.toString().split(":", 2);
        if (parts.length < 2 || !provider.equals(parts[1])) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "OAuth state 不匹配");
        }
        String tenantUid = parts[0];
        String method = "oauth:" + provider;

        Tenant tenant = tenantService.getTenantByUid(tenantUid);
        if (tenant == null || !tenant.isValid()) {
            throw new BusinessException(ErrorCode.INVALID_TENANT);
        }
        Long tenantId = tenant.getId();
        if (!loginMethodConfigService.isEnabled(tenantId, method)) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_DISABLED, "该登录方式未启用");
        }

        Map<String, String> cfg = parseConfig(loginMethodConfigService.getEffectiveConfig(tenantId, method));
        String redirectUri = cfg.get("redirectUri");
        OAuthProvider p = findProvider(provider);

        // 2. 换 token + 拉用户
        String accessToken = p.exchangeAccessToken(code, cfg, redirectUri);
        OAuthUserInfo info = p.fetchUser(accessToken, cfg);
        if (info.getProviderUid() == null || info.getProviderUid().isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, provider + " 未返回用户唯一标识");
        }

        // 3. 匹配本地绑定
        UserOauth binding = userOauthMapper.findByTenantProviderUid(tenantId, provider, info.getProviderUid());
        User user;
        if (binding != null) {
            user = userMapper.findByIdWithRolesAndPermissions(binding.getUserId(), tenantId);
            if (user == null) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
        } else {
            user = createOAuthUser(tenantId, provider, info);
        }

        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.update(user);
        log.info("OAuth login success: tenant={}, provider={}, user={}", tenantId, provider, user.getId());
        return issueTokens(user);
    }

    /**
     * 自动创建 OAuth 用户（含随机密码、ROLE_USER、oauth 绑定）。在 handleCallback 事务内执行。
     */
    private User createOAuthUser(Long tenantId, String provider, OAuthUserInfo info) {
        if (tenantService.isUserLimitReached(tenantId)) {
            throw new BusinessException(ErrorCode.TENANT_USER_LIMIT_REACHED);
        }
        String username = (provider + "_" + info.getProviderUid());
        if (username.length() > 50) {
            username = username.substring(0, 50); // user.username VARCHAR(50)
        }
        String email = (info.getEmail() == null || info.getEmail().isBlank()) ? null : info.getEmail();
        if (email != null && userMapper.existsByEmail(email, tenantId)) {
            email = null; // 邮箱冲突则不绑定，避免唯一键冲突
        }

        User user = User.builder()
                .id(uidGenerator.getUID())
                .tenantId(tenantId)
                .username(username)
                .password(passwordEncoder.encode(UUID.randomUUID().toString())) // 随机密码，OAuth 用户不可用密码登录
                .email(email)
                .nickname((info.getNickname() != null && !info.getNickname().isBlank()) ? info.getNickname() : username)
                .avatar(info.getAvatar())
                .status(1)
                .emailVerified(email != null)
                .phoneVerified(false)
                .roles(new HashSet<>())
                .build();
        userMapper.insert(user);

        Role userRole = userMapper.findRoleByCodeAndTenantId("ROLE_USER", tenantId);
        if (userRole != null) {
            userMapper.insertUserRole(user.getId(), userRole.getId(), tenantId);
        }

        userOauthMapper.insert(UserOauth.builder()
                .tenantId(tenantId)
                .userId(user.getId())
                .provider(provider)
                .providerUid(info.getProviderUid())
                .build());

        return userMapper.findByIdWithRolesAndPermissions(user.getId(), tenantId);
    }

    private OAuthProvider findProvider(String provider) {
        return providers.stream()
                .filter(p -> p.getProvider().equals(provider))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_METHOD_NOT_SUPPORTED));
    }

    private Map<String, String> parseConfig(String json) {
        if (json == null || json.isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "未配置 OAuth 凭证");
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "OAuth 凭证 JSON 解析失败");
        }
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        tokenService.saveRefreshToken(user.getTenantId(), user.getId(), refreshToken);
        Set<String> roles = user.getRoles() == null ? Set.of()
                : user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .user(TokenResponse.UserInfo.builder()
                        .id(user.getId())
                        .tenantId(user.getTenantId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .nickname(user.getNickname())
                        .avatar(user.getAvatar())
                        .roles(roles)
                        .build())
                .build();
    }
}
