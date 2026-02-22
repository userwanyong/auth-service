package cn.wanyj.auth.service;

import cn.wanyj.auth.dto.request.ChangePasswordRequest;
import cn.wanyj.auth.dto.request.LoginRequest;
import cn.wanyj.auth.dto.request.RegisterRequest;
import cn.wanyj.auth.dto.response.TokenResponse;
import cn.wanyj.auth.dto.response.UserResponse;

/**
 * Auth Service - 认证服务接口
 * @author wanyj
 */
public interface AuthService {

    /**
     * Register new user and auto-login
     * 用户注册并自动登录
     */
    TokenResponse register(RegisterRequest request);

    /**
     * User login
     * 用户登录
     */
    TokenResponse login(LoginRequest request);

    /**
     * Refresh access token
     * 刷新访问令牌
     */
    TokenResponse refreshToken(String refreshToken);

    /**
     * User logout
     * 用户登出
     * @param accessToken Access token to blacklist
     * @param refreshToken Refresh token to revoke
     */
    void logout(String accessToken, String refreshToken);

    /**
     * Get current user info
     * 获取当前用户信息
     */
    UserResponse getCurrentUser(Long userId);

    /**
     * Change password
     * 修改密码
     */
    void changePassword(Long userId, ChangePasswordRequest request);
}
