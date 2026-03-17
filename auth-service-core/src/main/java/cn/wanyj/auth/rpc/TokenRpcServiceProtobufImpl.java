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
            Long userId = Long.parseLong(request.getUserId());
            Long tenantId = Long.parseLong(request.getTenantId());

            User user = userMapper.findByIdWithRoles(userId);
            if (user == null) {
                log.error("User not found: {}", userId);
                return TokenRpcResponse.getDefaultInstance();
            }

            if (user.getStatus() == 0) {
                log.error("User is disabled: {}", userId);
                return TokenRpcResponse.getDefaultInstance();
            }

            // If tenantId is provided in request, verify it matches user's tenant
            if (tenantId > 0 && !user.getTenantId().equals(tenantId)) {
                log.error("Tenant mismatch: user belongs to tenant {}, but request specified {}",
                    user.getTenantId(), tenantId);
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
            log.error("Failed to generate token", e);
            return TokenRpcResponse.getDefaultInstance();
        }
    }

    @Override
    public TokenValidationResult parseToken(ParseTokenRpcRequest request) {
        log.info("RPC parseToken");
        try {
            String accessToken = request.getAccessToken();

            if (!jwtTokenProvider.validateAccessToken(accessToken)) {
                log.warn("Token is invalid");
                return TokenValidationResult.newBuilder()
                    .setValid(false)
                    .build();
            }

            // Extract tenant_id from JWT token
            Claims claims = jwtTokenProvider.getClaimsFromToken(accessToken);
            Long tenantId = claims.get("tenant_id", Long.class);

            // Check blacklist
            if (tokenService.isBlacklisted(tenantId, accessToken)) {
                log.warn("Token is blacklisted: tenant={}", tenantId);
                return TokenValidationResult.newBuilder()
                    .setValid(false)
                    .build();
            }

            Long userId = jwtTokenProvider.getUserIdFromToken(accessToken);

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
    public Empty revokeAllTokens(RevokeAllTokensRpcRequest request) {
        Long userId = Long.parseLong(request.getUserId());
        log.info("RPC revoke all tokens: userId={}", userId);
        try {
            User user = userMapper.findById(userId);
            if (user == null) {
                log.error("User not found: {}", userId);
                return Empty.getDefaultInstance();
            }
            tokenService.revokeAllTokens(user.getTenantId(), userId);
            log.info("All tokens revoked for tenant={}, user={}", user.getTenantId(), userId);
        } catch (Exception e) {
            log.error("Failed to revoke tokens for userId: {}", userId, e);
        }
        return Empty.getDefaultInstance();
    }
}
