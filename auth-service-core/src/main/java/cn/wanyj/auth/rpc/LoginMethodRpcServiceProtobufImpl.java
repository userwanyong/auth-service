package cn.wanyj.auth.rpc;

import cn.wanyj.auth.api.protobuf.*;
import cn.wanyj.auth.dto.request.LoginMethodConfigSaveRequest;
import cn.wanyj.auth.dto.response.LoginMethodConfigVO;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.service.LoginMethodConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * 登录方式配置服务 RPC 实现 - Protobuf IDL 模式
 * <p>平台级/租户级二级开关与凭证配置（AES 加密、脱敏展示）全部复用
 * {@link LoginMethodConfigService}，本类只做参数转换，不直接访问 Mapper。</p>
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
public class LoginMethodRpcServiceProtobufImpl extends DubboLoginMethodRpcServiceProtobufTriple.LoginMethodRpcServiceProtobufImplBase {

    private final LoginMethodConfigService loginMethodConfigService;

    @Override
    public LoginMethodListRpcResponse listPlatformConfigs(Empty request) {
        log.info("RPC listPlatformConfigs");
        try {
            return toListResponse(loginMethodConfigService.listPlatformConfigs());
        } catch (Exception e) {
            log.error("List platform configs error", e);
            return LoginMethodListRpcResponse.getDefaultInstance();
        }
    }

    @Override
    public OperationResult savePlatformConfig(SavePlatformLoginMethodRpcRequest request) {
        log.info("RPC savePlatformConfig: method={}, enabled={}", request.getMethod(), request.getEnabled());
        try {
            LoginMethodConfigSaveRequest saveRequest = LoginMethodConfigSaveRequest.builder()
                .enabled(request.getEnabled())
                .configJson(emptyToNull(request.getConfigJson()))
                .build();

            loginMethodConfigService.savePlatformConfig(request.getMethod(), saveRequest);

            return OperationResult.newBuilder()
                .setSuccess(true)
                .setMessage("平台登录方式配置已保存")
                .build();
        } catch (BusinessException e) {
            log.warn("Save platform config failed: {}", e.getMessage());
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Save platform config error", e);
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage("平台登录方式配置保存失败")
                .build();
        }
    }

    @Override
    public LoginMethodListRpcResponse listTenantConfigs(TenantLoginMethodRpcRequest request) {
        log.info("RPC listTenantConfigs: tenantId={}", request.getTenantId());
        try {
            Long tenantId = Long.parseLong(request.getTenantId());
            return toListResponse(loginMethodConfigService.listTenantConfigs(tenantId));
        } catch (Exception e) {
            log.error("List tenant configs error", e);
            return LoginMethodListRpcResponse.getDefaultInstance();
        }
    }

    @Override
    public OperationResult saveTenantConfig(SaveTenantLoginMethodRpcRequest request) {
        log.info("RPC saveTenantConfig: tenantId={}, method={}, enabled={}",
            request.getTenantId(), request.getMethod(), request.getEnabled());
        try {
            Long tenantId = Long.parseLong(request.getTenantId());
            LoginMethodConfigSaveRequest saveRequest = LoginMethodConfigSaveRequest.builder()
                .enabled(request.getEnabled())
                .usePlatformConfig(request.getUsePlatformConfig())
                .configJson(emptyToNull(request.getConfigJson()))
                .build();

            loginMethodConfigService.saveTenantConfig(tenantId, request.getMethod(), saveRequest);

            return OperationResult.newBuilder()
                .setSuccess(true)
                .setMessage("租户登录方式配置已保存")
                .build();
        } catch (BusinessException e) {
            log.warn("Save tenant config failed: {}", e.getMessage());
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Save tenant config error", e);
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage("租户登录方式配置保存失败")
                .build();
        }
    }

    @Override
    public StringListResponse listEnabledMethods(EnabledLoginMethodsRpcRequest request) {
        log.info("RPC listEnabledMethods: tenantUid={}", request.getTenantUid());
        try {
            List<String> methods = loginMethodConfigService.listEnabledMethodsByTenantUid(request.getTenantUid());
            return StringListResponse.newBuilder().addAllValues(methods).build();
        } catch (Exception e) {
            log.error("List enabled methods error", e);
            return StringListResponse.getDefaultInstance();
        }
    }

    private LoginMethodListRpcResponse toListResponse(List<LoginMethodConfigVO> configs) {
        LoginMethodListRpcResponse.Builder builder = LoginMethodListRpcResponse.newBuilder();
        for (LoginMethodConfigVO vo : configs) {
            builder.addItems(LoginMethodRpcResponse.newBuilder()
                .setMethod(vo.getMethod() != null ? vo.getMethod() : "")
                .setCategory(vo.getCategory() != null ? vo.getCategory() : "")
                .setDisplayName(vo.getDisplayName() != null ? vo.getDisplayName() : "")
                .setEnabled(vo.getEnabled() != null ? vo.getEnabled() : 0)
                .setUsePlatformConfig(vo.getUsePlatformConfig() != null ? vo.getUsePlatformConfig() : 1)
                .setHasConfig(Boolean.TRUE.equals(vo.getHasConfig()))
                .setPlatformEnabled(Boolean.TRUE.equals(vo.getPlatformEnabled()))
                .setPlatformLocked(Boolean.TRUE.equals(vo.getPlatformLocked()))
                .build());
        }
        return builder.build();
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
