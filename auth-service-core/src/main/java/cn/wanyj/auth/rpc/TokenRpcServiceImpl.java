package cn.wanyj.auth.rpc;

import cn.wanyj.auth.api.token.TokenRpcService;
import cn.wanyj.auth.api.model.TokenRpcResponse;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.mapper.UserMapper;
import cn.wanyj.auth.security.JwtTokenProvider;
import cn.wanyj.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 令牌服务 RPC 实现
 * 提供给其他微服务调用的令牌相关 RPC 接口实现
 *
 * @author wanyj
 */
@Slf4j
@DubboService(
    version = "1.0.0",
    timeout = 3000,
    retries = 1
)
@RequiredArgsConstructor
public class TokenRpcServiceImpl implements TokenRpcService {

    private final TokenService tokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    @Override
    public TokenRpcResponse generateToken(Long userId, Long expiration) {
        log.info("RPC generate token: userId={}, expiration={}", userId, expiration);
        try {
            // 加载用户信息
            User user = userMapper.findByIdWithRoles(userId);
            if (user == null) {
                log.error("User not found: {}", userId);
                return null;
            }

            // 检查用户状态
            if (user.getStatus() == 0) {
                log.error("User is disabled: {}", userId);
                return null;
            }

            // 生成访问令牌
            String accessToken = jwtTokenProvider.generateAccessToken(user);

            // 如果需要，也可以生成刷新令牌
            String refreshToken = jwtTokenProvider.generateRefreshToken(user);
            tokenService.saveRefreshToken(userId, refreshToken);

            return TokenRpcResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiration != null ? expiration : jwtTokenProvider.getAccessTokenExpirationSeconds())
                .build();
        } catch (Exception e) {
            log.error("Failed to generate token for userId: {}", userId, e);
            return null;
        }
    }

    @Override
    public Long parseToken(String token) {
        log.info("RPC parse token");
        try {
            // 检查 token 是否在黑名单中
            if (tokenService.isBlacklisted(token)) {
                log.warn("Token is blacklisted");
                return null;
            }

            // 验证并解析 token
            if (!jwtTokenProvider.validateAccessToken(token)) {
                log.warn("Token is invalid");
                return null;
            }

            return jwtTokenProvider.getUserIdFromToken(token);
        } catch (Exception e) {
            log.error("Failed to parse token", e);
            return null;
        }
    }

    @Override
    public void revokeAllTokens(Long userId) {
        log.info("RPC revoke all tokens: userId={}", userId);
        try {
            // 删除刷新令牌
            tokenService.deleteRefreshToken(userId);
            log.info("All tokens revoked for userId: {}", userId);
        } catch (Exception e) {
            log.error("Failed to revoke tokens for userId: {}", userId, e);
        }
    }
}
