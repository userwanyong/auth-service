package cn.wanyj.auth.rpc;

import cn.wanyj.auth.api.protobuf.*;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.mapper.UserMapper;
import cn.wanyj.auth.security.JwtTokenProvider;
import cn.wanyj.auth.service.TokenService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.stream.Collectors;

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
        log.info("RPC generate token: userId={}, expiration={}, tenantId={}",
            request.getUserId(), request.getExpiration(), request.getTenantId());
        try {
            User user = userMapper.findByIdWithRoles(request.getUserId());
            if (user == null) {
                log.error("User not found: {}", request.getUserId());
                return TokenRpcResponse.getDefaultInstance();
            }

            if (user.getStatus() == 0) {
                log.error("User is disabled: {}", request.getUserId());
                return TokenRpcResponse.getDefaultInstance();
            }

            // If tenantId is provided in request, verify it matches user's tenant
            if (request.getTenantId() > 0 && !user.getTenantId().equals(request.getTenantId())) {
                log.error("Tenant mismatch: user belongs to tenant {}, but request specified {}",
                    user.getTenantId(), request.getTenantId());
                return TokenRpcResponse.getDefaultInstance();
            }

            String accessToken = jwtTokenProvider.generateAccessToken(user);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user);
            tokenService.saveRefreshToken(user.getTenantId(), user.getId(), refreshToken);

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
            return TokenRpcResponse.getDefaultInstance();
        }
    }

    @Override
    public TokenValidationResult parseToken(StringValue token) {
        log.info("RPC parseToken");
        try {
            String tokenValue = token.getValue();

            if (!jwtTokenProvider.validateAccessToken(tokenValue)) {
                log.warn("Token is invalid");
                return TokenValidationResult.newBuilder()
                    .setValid(false)
                    .build();
            }

            // Extract tenant_id from JWT token
            Claims claims = jwtTokenProvider.getClaimsFromToken(tokenValue);
            Long tenantId = claims.get("tenant_id", Long.class);

            // Check blacklist
            if (tokenService.isBlacklisted(tenantId, tokenValue)) {
                log.warn("Token is blacklisted: tenant={}", tenantId);
                return TokenValidationResult.newBuilder()
                    .setValid(false)
                    .build();
            }

            Long userId = jwtTokenProvider.getUserIdFromToken(tokenValue);

            // Load user with roles and permissions
            User user = userMapper.findByIdWithRolesAndPermissions(userId, tenantId);

            if (user == null || user.getStatus() == 0) {
                return TokenValidationResult.newBuilder()
                    .setValid(false)
                    .build();
            }

            long expiresAt = jwtTokenProvider.getAccessTokenExpirationSeconds() * 1000 + System.currentTimeMillis();

            return TokenValidationResult.newBuilder()
                .setValid(true)
                .setUserId(user.getId())
                .setUsername(user.getUsername())
                .setTenantId(tenantId)
                .addAllRoles(user.getRoles().stream()
                    .map(r -> r.getCode())
                    .collect(Collectors.toList()))
                .addAllPermissions(user.getRoles().stream()
                    .flatMap(r -> r.getPermissions().stream())
                    .map(p -> p.getCode())
                    .distinct()
                    .collect(Collectors.toList()))
                .setExpiresAt(expiresAt)
                .build();
        } catch (Exception e) {
            log.error("Failed to parse token", e);
            return TokenValidationResult.newBuilder()
                .setValid(false)
                .build();
        }
    }

    @Override
    public Empty revokeAllTokens(Int64Value userId) {
        log.info("RPC revoke all tokens: userId={}", userId.getValue());
        try {
            // Get user to find tenantId
            User user = userMapper.findById(userId.getValue());
            if (user == null) {
                log.error("User not found: {}", userId.getValue());
                return Empty.getDefaultInstance();
            }

            tokenService.revokeAllTokens(user.getTenantId(), userId.getValue());
            log.info("All tokens revoked for tenant={}, user={}", user.getTenantId(), userId.getValue());
        } catch (Exception e) {
            log.error("Failed to revoke tokens for userId: {}", userId.getValue(), e);
        }
        return Empty.getDefaultInstance();
    }
}
