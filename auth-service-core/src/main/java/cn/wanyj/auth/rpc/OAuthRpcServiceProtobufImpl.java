package cn.wanyj.auth.rpc;

import cn.wanyj.auth.api.protobuf.*;
import cn.wanyj.auth.dto.response.TokenResponse;
import cn.wanyj.auth.entity.UserOauth;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.rpc.converter.UserProtobufConverter;
import cn.wanyj.auth.rpc.support.TenantUidResolver;
import cn.wanyj.auth.service.oauth.OAuthCallbackResult;
import cn.wanyj.auth.service.oauth.OAuthLoginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.ZoneId;
import java.util.List;

/**
 * OAuth 登录/绑定编排服务 RPC 实现 - Protobuf IDL 模式
 * <p>全部复用 {@link OAuthLoginService}（state 管理、换 token、匹配/建用户、签发令牌），
 * 本类只做参数转换，不直接访问 Mapper。authorize/callback 的 302 跳转语义由调用方
 * （HTTP 网关/BFF）基于返回的 URL 与结果自行完成。</p>
 *
 * @author wanyj
 */
@Slf4j
@DubboService(
    version = "1.0.0",
    timeout = 5000,
    retries = 2,
    protocol = "tri"
)
@RequiredArgsConstructor
public class OAuthRpcServiceProtobufImpl extends DubboOAuthRpcServiceProtobufTriple.OAuthRpcServiceProtobufImplBase {

    private final OAuthLoginService oAuthLoginService;
    private final TenantUidResolver tenantUidResolver;

    @Override
    public OAuthUrlRpcResponse buildAuthorizeUrl(OAuthAuthorizeUrlRpcRequest request) {
        log.info("RPC buildAuthorizeUrl: tenantUid={}, provider={}", request.getTenantUid(), request.getProvider());
        try {
            String url = oAuthLoginService.buildAuthorizeUrl(request.getTenantUid(), request.getProvider());
            return OAuthUrlRpcResponse.newBuilder().setUrl(url).build();
        } catch (BusinessException e) {
            log.warn("Build authorize url failed: {}", e.getMessage());
            return OAuthUrlRpcResponse.getDefaultInstance();
        } catch (Exception e) {
            log.error("Build authorize url error", e);
            return OAuthUrlRpcResponse.getDefaultInstance();
        }
    }

    @Override
    public OAuthUrlRpcResponse buildBindAuthorizeUrl(OAuthBindUrlRpcRequest request) {
        log.info("RPC buildBindAuthorizeUrl: tenantUid={}, userId={}, provider={}",
            request.getTenantUid(), request.getUserId(), request.getProvider());
        try {
            Long tenantId = tenantUidResolver.requireTenant(request.getTenantUid()).getId();
            Long userId = Long.parseLong(request.getUserId());
            String url = oAuthLoginService.buildBindAuthorizeUrl(tenantId, request.getProvider(), userId);
            return OAuthUrlRpcResponse.newBuilder().setUrl(url).build();
        } catch (BusinessException e) {
            log.warn("Build bind authorize url failed: {}", e.getMessage());
            return OAuthUrlRpcResponse.getDefaultInstance();
        } catch (Exception e) {
            log.error("Build bind authorize url error", e);
            return OAuthUrlRpcResponse.getDefaultInstance();
        }
    }

    @Override
    public OAuthCallbackRpcResult handleCallback(OAuthCallbackRpcRequest request) {
        log.info("RPC handleCallback: provider={}", request.getProvider());
        try {
            OAuthCallbackResult result = oAuthLoginService.handleCallback(
                request.getProvider(), request.getCode(), request.getState());

            OAuthCallbackRpcResult.Builder builder = OAuthCallbackRpcResult.newBuilder()
                .setLogin(result.isLogin())
                .setSuccess(result.isSuccess())
                .setMessage(result.getMessage() == null ? "" : result.getMessage());

            if (result.isLogin() && result.getToken() != null) {
                TokenResponse token = result.getToken();
                builder.setToken(TokenRpcResponse.newBuilder()
                    .setAccessToken(token.getAccessToken())
                    .setRefreshToken(token.getRefreshToken())
                    .setExpiresIn(token.getExpiresIn())
                    .build());
                if (token.getUser() != null) {
                    builder.setUser(UserProtobufConverter.convertToProtobuf(token.getUser()));
                }
            }
            return builder.build();
        } catch (BusinessException e) {
            log.warn("OAuth callback failed: {}", e.getMessage());
            // 与 HTTP 回调一致：失败信息通过 message 带回，由调用方决定展示方式
            return OAuthCallbackRpcResult.newBuilder()
                .setLogin(false)
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("OAuth callback error", e);
            return OAuthCallbackRpcResult.newBuilder()
                .setLogin(false)
                .setSuccess(false)
                .setMessage("OAuth 登录失败")
                .build();
        }
    }

    @Override
    public OAuthBindingListRpcResponse listBindings(OAuthBindingsRpcRequest request) {
        log.info("RPC listBindings: tenantUid={}, userId={}", request.getTenantUid(), request.getUserId());
        try {
            Long tenantId = tenantUidResolver.requireTenant(request.getTenantUid()).getId();
            Long userId = Long.parseLong(request.getUserId());
            List<UserOauth> bindings = oAuthLoginService.listBindings(tenantId, userId);

            OAuthBindingListRpcResponse.Builder builder = OAuthBindingListRpcResponse.newBuilder();
            for (UserOauth binding : bindings) {
                builder.addBindings(OAuthBindingRpcResponse.newBuilder()
                    .setId(binding.getId() != null ? binding.getId() : 0L)
                    .setProvider(binding.getProvider() != null ? binding.getProvider() : "")
                    .setProviderUid(binding.getProviderUid() != null ? binding.getProviderUid() : "")
                    .setCreatedAt(binding.getCreatedAt() != null
                        ? binding.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : 0L)
                    .build());
            }
            return builder.build();
        } catch (BusinessException e) {
            log.warn("List bindings failed: {}", e.getMessage());
            return OAuthBindingListRpcResponse.getDefaultInstance();
        } catch (Exception e) {
            log.error("List bindings error", e);
            return OAuthBindingListRpcResponse.getDefaultInstance();
        }
    }

    @Override
    public OperationResult unbind(OAuthUnbindRpcRequest request) {
        log.info("RPC unbind: tenantUid={}, userId={}, provider={}",
            request.getTenantUid(), request.getUserId(), request.getProvider());
        try {
            Long tenantId = tenantUidResolver.requireTenant(request.getTenantUid()).getId();
            Long userId = Long.parseLong(request.getUserId());
            oAuthLoginService.unbind(tenantId, userId, request.getProvider());

            return OperationResult.newBuilder()
                .setSuccess(true)
                .setMessage("解绑成功")
                .build();
        } catch (BusinessException e) {
            log.warn("Unbind failed: {}", e.getMessage());
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Unbind error", e);
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage("解绑失败")
                .build();
        }
    }
}
