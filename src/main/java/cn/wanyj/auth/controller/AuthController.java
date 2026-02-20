package cn.wanyj.auth.controller;

import cn.wanyj.auth.dto.request.ChangePasswordRequest;
import cn.wanyj.auth.dto.request.LoginRequest;
import cn.wanyj.auth.dto.request.RegisterRequest;
import cn.wanyj.auth.dto.response.TokenResponse;
import cn.wanyj.auth.dto.response.UserResponse;
import cn.wanyj.auth.exception.ApiResponse;
import cn.wanyj.auth.security.SecurityUtils;
import cn.wanyj.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    /**
     * User registration
     * 用户注册
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request for username: {}", request.getUsername());
        UserResponse user = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "注册成功", user));
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
