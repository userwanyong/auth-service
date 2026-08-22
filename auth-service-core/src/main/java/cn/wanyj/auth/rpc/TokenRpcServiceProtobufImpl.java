package cn.wanyj.auth.rpc;

import cn.wanyj.auth.api.protobuf.*;
import cn.wanyj.auth.dto.response.TokenResponse;
import cn.wanyj.auth.dto.response.ValidatedToken;
import cn.wanyj.auth.entity.Permission;
import cn.wanyj.auth.entity.Role;
import cn.wanyj.auth.entity.Tenant;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.rpc.support.TenantUidResolver;
import cn.wanyj.auth.service.TenantService;
import cn.wanyj.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.stream.Collectors;

/**
 * 令牌服务 RPC 实现 - Protobuf IDL 模式
 * <p>签发/校验/撤销的完整业务逻辑（用户加载、状态与租户归属校验）复用
 * {@link TokenService}，本类不再直接访问 Mapper。</p>
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
    private final TenantService tenantService;
    private final TenantUidResolver tenantUidResolver;

    @Override
    public TokenRpcResponse generateToken(TokenGenerationRequest request) {
        log.info("RPC generate token: userId={}, expiration={}, tenantUid={}",
            request.getUserId(), request.getExpiration(), request.getTenantUid());
        try {
            Long userId = Long.parseLong(request.getUserId());
            Tenant tenant = tenantUidResolver.resolveTenantOrNull(request.getTenantUid());

            // tenantUid 留空保持"不指定租户"语义；非空时强制租户隔离（Service 层处理）
            TokenResponse tokenResponse = tokenService.issueTokens(
                userId, tenant != null ? tenant.getId() : null, request.getExpiration());

            return TokenRpcResponse.newBuilder()
                .setAccessToken(tokenResponse.getAccessToken())
                .setRefreshToken(tokenResponse.getRefreshToken())
                .setExpiresIn(tokenResponse.getExpiresIn())
                .build();
        } catch (BusinessException e) {
            log.warn("Generate token failed: {}", e.getMessage());
            return TokenRpcResponse.getDefaultInstance();
        } catch (Exception e) {
            log.error("Failed to generate token", e);
            return TokenRpcResponse.getDefaultInstance();
        }
    }

    @Override
    public TokenValidationResult parseToken(ParseTokenRpcRequest request) {
        log.info("RPC parseToken");
        try {
            ValidatedToken validated = tokenService.validateAccessToken(request.getAccessToken());
            if (validated == null) {
                log.warn("Token is invalid");
                return TokenValidationResult.newBuilder()
                    .setValid(false)
                    .build();
            }

            User user = validated.getUser();
            // 数字 tenantId 仅内部使用，对外回填 tenantUid（租户已删除则留空）
            Tenant tenant = tenantService.getTenantById(user.getTenantId());

            return TokenValidationResult.newBuilder()
                .setValid(true)
                .setUserId(user.getId())
                .setUsername(user.getUsername())
                .setTenantUid(tenant != null && tenant.getTenantUid() != null ? tenant.getTenantUid() : "")
                .addAllRoles(user.getRoles().stream()
                    .map(Role::getCode)
                    .collect(Collectors.toList()))
                .addAllPermissions(user.getRoles().stream()
                    .flatMap(r -> r.getPermissions().stream())
                    .map(Permission::getCode)
                    .distinct()
                    .collect(Collectors.toList()))
                .setExpiresAt(validated.getExpiresAt())
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
        log.info("RPC revoke all tokens: userId={}, tenantUid={}", userId, request.getTenantUid());
        try {
            // tenantUid 留空时跳过归属校验，由 Service 层处理
            Tenant tenant = tenantUidResolver.resolveTenantOrNull(request.getTenantUid());
            tokenService.revokeAllTokensForUser(userId, tenant != null ? tenant.getId() : null);
            log.info("All tokens revoked for user={}", userId);
        } catch (BusinessException e) {
            log.warn("Revoke tokens failed: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to revoke tokens for userId: {}", userId, e);
        }
        return Empty.getDefaultInstance();
    }
}
