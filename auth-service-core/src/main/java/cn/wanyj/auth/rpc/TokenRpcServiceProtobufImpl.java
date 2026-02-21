package cn.wanyj.auth.rpc;

import cn.wanyj.auth.api.protobuf.*;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.mapper.UserMapper;
import cn.wanyj.auth.security.JwtTokenProvider;
import cn.wanyj.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 令牌服务 RPC 实现 - Protobuf IDL 模式
 * 使用 Protobuf 定义的消息类型进行序列化
 *
 * @author wanyj
 */
@Slf4j
@DubboService(
    version = "1.0.0",
    timeout = 3000,
    retries = 1,
    protocol = "tri"
)
@RequiredArgsConstructor
public class TokenRpcServiceProtobufImpl extends DubboTokenRpcServiceProtobufTriple.TokenRpcServiceProtobufImplBase {

    private final TokenService tokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    @Override
    public TokenRpcResponse generateToken(TokenGenerationRequest request) {
        log.info("RPC generate token: userId={}, expiration={}", request.getUserId(), request.getExpiration());
        try {
            User user = userMapper.findByIdWithRoles(request.getUserId());
            if (user == null) {
                log.error("User not found: {}", request.getUserId());
                return null;
            }

            if (user.getStatus() == 0) {
                log.error("User is disabled: {}", request.getUserId());
                return null;
            }

            String accessToken = jwtTokenProvider.generateAccessToken(user);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user);
            tokenService.saveRefreshToken(request.getUserId(), refreshToken);

            long expiresIn = request.getExpiration() > 0
                ? request.getExpiration()
                : jwtTokenProvider.getAccessTokenExpirationSeconds();

            return TokenRpcResponse.newBuilder()
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .setExpiresIn(expiresIn)
                .build();
        } catch (Exception e) {
            log.error("Failed to generate token for userId: {}", request.getUserId(), e);
            return null;
        }
    }

    @Override
    public Int64Value parseToken(StringValue token) {
        log.info("RPC parse token");
        try {
            String tokenValue = token.getValue();

            if (tokenService.isBlacklisted(tokenValue)) {
                log.warn("Token is blacklisted");
                return Int64Value.newBuilder().setValue(0).build();
            }

            if (!jwtTokenProvider.validateAccessToken(tokenValue)) {
                log.warn("Token is invalid");
                return Int64Value.newBuilder().setValue(0).build();
            }

            Long userId = jwtTokenProvider.getUserIdFromToken(tokenValue);
            return Int64Value.newBuilder().setValue(userId != null ? userId : 0).build();
        } catch (Exception e) {
            log.error("Failed to parse token", e);
            return Int64Value.newBuilder().setValue(0).build();
        }
    }

    @Override
    public Empty revokeAllTokens(Int64Value userId) {
        log.info("RPC revoke all tokens: userId={}", userId.getValue());
        try {
            tokenService.deleteRefreshToken(userId.getValue());
            log.info("All tokens revoked for userId: {}", userId.getValue());
        } catch (Exception e) {
            log.error("Failed to revoke tokens for userId: {}", userId.getValue(), e);
        }
        return Empty.newBuilder().build();
    }
}
