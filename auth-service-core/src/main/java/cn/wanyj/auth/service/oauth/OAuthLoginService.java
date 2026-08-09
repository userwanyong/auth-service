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
        Long tenantId = tenant.getId();
        if (!loginMethodConfigService.isEnabled(tenantId, method)) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_DISABLED, "该登录方式未启用");
        }
        Map<String, String> cfg = parseConfig(loginMethodConfigService.getEffectiveConfig(tenantId, method));
        OAuthProvider p = findProvider(provider);
        String state = buildState(Map.of("mode", "login", "tid", String.valueOf(tenantId), "p", provider));
        return p.buildAuthorizeUrl(cfg, requireRedirectUri(cfg), state);
    }

    /**
     * 发起「绑定」授权（已登录用户把第三方账号绑到当前本地账号）
     */
    public String buildBindAuthorizeUrl(Long tenantId, String provider, Long userId) {
        String method = "oauth:" + provider;
        if (!loginMethodConfigService.isEnabled(tenantId, method)) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_DISABLED, "该登录方式未启用");
        }
        if (userOauthMapper.findByTenantUserProvider(tenantId, userId, provider) != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "你已绑定该平台，无需重复绑定");
        }
        Map<String, String> cfg = parseConfig(loginMethodConfigService.getEffectiveConfig(tenantId, method));
        OAuthProvider p = findProvider(provider);
        String state = buildState(Map.of(
                "mode", "bind", "tid", String.valueOf(tenantId),
                "p", provider, "uid", String.valueOf(userId)));
        return p.buildAuthorizeUrl(cfg, requireRedirectUri(cfg), state);
    }

    /**
     * 处理回调：校验 state → 换 token → 拉用户 → 按 mode 分支（登录 / 绑定）
     */
    @Transactional
    public OAuthCallbackResult handleCallback(String provider, String code, String state) {
        // 1. 校验 state（取出后删除，一次性）
        Object raw = redisTemplate.opsForValue().get(STATE_PREFIX + state);
        if (raw == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "OAuth state 无效或已过期");
        }
        redisTemplate.delete(STATE_PREFIX + state);
        Map<String, String> sv;
        try {
            sv = objectMapper.readValue(raw.toString(), new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "OAuth state 无效");
        }
        if (!provider.equals(sv.get("p"))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "OAuth state 不匹配");
        }
        String mode = sv.getOrDefault("mode", "login");
        Long tenantId = parseLong(sv.get("tid"));
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "OAuth state 无效");
        }

        Tenant tenant = tenantService.getTenantById(tenantId);
        if (tenant == null || !tenant.isValid()) {
            throw new BusinessException(ErrorCode.INVALID_TENANT);
        }
        String method = "oauth:" + provider;
        if (!loginMethodConfigService.isEnabled(tenantId, method)) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_DISABLED, "该登录方式未启用");
        }

        Map<String, String> cfg = parseConfig(loginMethodConfigService.getEffectiveConfig(tenantId, method));
        OAuthProvider p = findProvider(provider);

        // 2. 换 token + 拉用户
        String accessToken = p.exchangeAccessToken(code, cfg, requireRedirectUri(cfg));
        OAuthUserInfo info = p.fetchUser(accessToken, cfg);
        if (info.getProviderUid() == null || info.getProviderUid().isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, provider + " 未返回用户唯一标识");
        }

        // 3. 按 mode 分支
        if ("bind".equals(mode)) {
            return handleBind(tenantId, provider, info, sv.get("uid"));
        }
        return handleLogin(tenantId, provider, info);
    }

    /** 登录分支：匹配已有绑定，否则建新用户，签发 token */
    private OAuthCallbackResult handleLogin(Long tenantId, String provider, OAuthUserInfo info) {
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
        return OAuthCallbackResult.builder().login(true).token(issueTokens(user)).build();
    }

    /** 绑定分支：把第三方账号绑到当前本地用户，含冲突检查 */
    private OAuthCallbackResult handleBind(Long tenantId, String provider, OAuthUserInfo info, String uidStr) {
        Long userId = parseLong(uidStr);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "绑定 state 缺少用户标识");
        }
        // 该第三方账号是否已绑到别的本地账号
        UserOauth occupied = userOauthMapper.findByTenantProviderUid(tenantId, provider, info.getProviderUid());
        if (occupied != null) {
            if (occupied.getUserId().equals(userId)) {
                return OAuthCallbackResult.builder().login(false).success(true).message("该账号已绑定此平台").build();
            }
            return OAuthCallbackResult.builder().login(false).success(false)
                    .message("该 " + provider + " 账号已绑定到其他本地账号，无法重复绑定").build();
        }
        // 当前用户是否已绑该 provider（并发兜底）
        if (userOauthMapper.findByTenantUserProvider(tenantId, userId, provider) != null) {
            return OAuthCallbackResult.builder().login(false).success(false).message("你已绑定该平台，无需重复绑定").build();
        }
        userOauthMapper.insert(UserOauth.builder()
                .tenantId(tenantId).userId(userId).provider(provider).providerUid(info.getProviderUid()).build());
        log.info("OAuth bind success: tenant={}, user={}, provider={}", tenantId, userId, provider);
        return OAuthCallbackResult.builder().login(false).success(true).message("绑定成功").build();
    }

    /** 列出当前用户已绑定的第三方平台 */
    public List<UserOauth> listBindings(Long tenantId, Long userId) {
        return userOauthMapper.findByTenantUserId(tenantId, userId);
    }

    /** 解绑某第三方平台 */
    public void unbind(Long tenantId, Long userId, String provider) {
        int n = userOauthMapper.deleteByTenantUserProvider(tenantId, userId, provider);
        if (n == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未绑定该平台");
        }
        log.info("OAuth unbind: tenant={}, user={}, provider={}", tenantId, userId, provider);
    }

    /** 生成 state 并存 Redis，返回 state 字符串 */
    private String buildState(Map<String, String> payload) {
        String state = UUID.randomUUID().toString().replace("-", "");
        String value;
        try {
            value = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "OAuth state 序列化失败");
        }
        redisTemplate.opsForValue().set(STATE_PREFIX + state, value, STATE_TTL_SECONDS, TimeUnit.SECONDS);
        return state;
    }

    private String requireRedirectUri(Map<String, String> cfg) {
        String redirectUri = cfg.get("redirectUri");
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "未配置 redirectUri（回调地址）");
        }
        return redirectUri;
    }

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.valueOf(s); } catch (Exception e) { return null; }
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
