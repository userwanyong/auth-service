package cn.wanyj.auth.controller;

import cn.wanyj.auth.dto.request.ChangePasswordRequest;
import cn.wanyj.auth.dto.request.LoginByCodeRequest;
import cn.wanyj.auth.dto.request.LoginRequest;
import cn.wanyj.auth.dto.request.RegisterRequest;
import cn.wanyj.auth.dto.request.SendCodeRequest;
import cn.wanyj.auth.dto.response.TokenResponse;
import cn.wanyj.auth.dto.response.UserResponse;
import cn.wanyj.auth.exception.ApiResponse;
import cn.wanyj.auth.security.SecurityUtils;
import cn.wanyj.auth.service.AuthService;
import cn.wanyj.auth.entity.UserOauth;
import cn.wanyj.auth.service.oauth.OAuthCallbackResult;
import cn.wanyj.auth.service.oauth.OAuthLoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Auth Controller - 认证控制器
 * 处理用户注册、登录、登出等认证相关操作
 * @author wanyj
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuthLoginService oAuthLoginService;

    /**
     * User registration (auto-login)
     * 用户注册并自动登录
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TokenResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request for username: {}", request.getUsername());
        TokenResponse token = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "注册成功", token));
    }

    /**
     * User login
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request for username: {}", request.getUsername());
        TokenResponse token = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(200, "登录成功", token));
    }

    /**
     * 发送验证码（邮箱/手机）
     * POST /api/auth/send-code
     */
    @PostMapping("/send-code")
    public ResponseEntity<ApiResponse<Void>> sendCode(@Valid @RequestBody SendCodeRequest request) {
        log.info("Send code request: method={}", request.getMethod());
        authService.sendCode(request);
        return ResponseEntity.ok(ApiResponse.success(200, "验证码已发送", null));
    }

    /**
     * 验证码登录（邮箱/手机）
     * POST /api/auth/login-by-code
     */
    @PostMapping("/login-by-code")
    public ResponseEntity<ApiResponse<TokenResponse>> loginByCode(@Valid @RequestBody LoginByCodeRequest request) {
        log.info("Login by code: method={}", request.getMethod());
        TokenResponse token = authService.loginByCode(request);
        return ResponseEntity.ok(ApiResponse.success(200, "登录成功", token));
    }

    /**
     * OAuth 授权入口：校验开关 + 生成 state，302 重定向到提供方授权页
     * GET /api/auth/oauth/{provider}/authorize?tenantUid=xxx
     */
    @GetMapping("/oauth/{provider}/authorize")
    public ResponseEntity<Void> oauthAuthorize(@PathVariable String provider,
                                               @RequestParam String tenantUid) {
        String url = oAuthLoginService.buildAuthorizeUrl(tenantUid, provider);
        return ResponseEntity.status(302).header("Location", url).build();
    }

    /**
     * OAuth 回调：校验 state + 换 token + 匹配/建用户 + 签发 token，302 回前端
     * GET /api/auth/oauth/{provider}/callback?code=xxx&state=xxx
     */
    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<Void> oauthCallback(@PathVariable String provider,
                                              @RequestParam("code") String code,
                                              @RequestParam(value = "state", required = false) String state) {
        OAuthCallbackResult result = oAuthLoginService.handleCallback(provider, code, state);
        String redirect;
        if (result.isLogin()) {
            redirect = "/login.html#oauth=success&accessToken=" + result.getToken().getAccessToken()
                    + "&refreshToken=" + result.getToken().getRefreshToken();
        } else {
            redirect = "/index.html#bindings&bind=" + (result.isSuccess() ? "success" : "failed")
                    + "&msg=" + URLEncoder.encode(result.getMessage() == null ? "" : result.getMessage(), StandardCharsets.UTF_8);
        }
        return ResponseEntity.status(302).header("Location", redirect).build();
    }

    /**
     * 发起「绑定」授权（已登录用户把第三方账号绑到当前本地账号）
     * GET /api/auth/oauth/{provider}/bind
     */
    @GetMapping("/oauth/{provider}/bind")
    public ResponseEntity<ApiResponse<String>> oauthBind(@PathVariable String provider) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long tenantId = SecurityUtils.getCurrentTenantId();
        String url = oAuthLoginService.buildBindAuthorizeUrl(tenantId, provider, userId);
        return ResponseEntity.ok(ApiResponse.success(url));
    }

    /**
     * 当前用户的第三方绑定列表
     * GET /api/auth/me/oauth
     */
    @GetMapping("/me/oauth")
    public ResponseEntity<ApiResponse<List<UserOauth>>> myBindings() {
        Long userId = SecurityUtils.getCurrentUserId();
        Long tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(ApiResponse.success(oAuthLoginService.listBindings(tenantId, userId)));
    }

    /**
     * 解绑某第三方平台
     * DELETE /api/auth/me/oauth/{provider}
     */
    @DeleteMapping("/me/oauth/{provider}")
    public ResponseEntity<ApiResponse<Void>> unbind(@PathVariable String provider) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long tenantId = SecurityUtils.getCurrentTenantId();
        oAuthLoginService.unbind(tenantId, userId, provider);
        return ResponseEntity.ok(ApiResponse.success(200, "解绑成功", null));
    }

    /**
     * Refresh access token
     * 刷新访问令牌
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @RequestHeader("Authorization") String authorization) {
        String refreshToken = authorization.replace("Bearer ", "");
        log.info("Token refresh request");
        TokenResponse token = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.success(200, "令牌刷新成功", token));
    }

    /**
     * User logout
     * 用户登出
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String accessToken,
            @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {
        authService.logout(accessToken, refreshToken);
        return ResponseEntity.ok(ApiResponse.success(200, "登出成功", null));
    }

    /**
     * Get current user info
     * 获取当前用户信息
     * GET /api/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Get current user info for user id: {}", userId);
        UserResponse user = authService.getCurrentUser(userId);
        return ResponseEntity.ok(ApiResponse.success(200, "成功", user));
    }

    /**
     * Change password
     * 修改密码
     * PUT /api/auth/password
     */
    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Change password request for user id: {}", userId);
        authService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success(200, "密码修改成功", null));
    }
}
