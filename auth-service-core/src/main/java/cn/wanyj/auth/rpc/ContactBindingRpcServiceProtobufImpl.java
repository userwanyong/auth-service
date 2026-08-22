package cn.wanyj.auth.rpc;

import cn.wanyj.auth.api.protobuf.*;
import cn.wanyj.auth.dto.request.BindContactRequest;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.rpc.support.TenantUidResolver;
import cn.wanyj.auth.service.ContactBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 邮箱/手机号绑定服务 RPC 实现 - Protobuf IDL 模式
 * <p>绑定/解绑的校验链（验证码、唯一性、登录方式开关、租户隔离）全部复用
 * {@link ContactBindingService}，本类只做参数转换，不直接访问 Mapper。</p>
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
public class ContactBindingRpcServiceProtobufImpl extends DubboContactBindingRpcServiceProtobufTriple.ContactBindingRpcServiceProtobufImplBase {

    private final ContactBindingService contactBindingService;
    private final TenantUidResolver tenantUidResolver;

    @Override
    public OperationResult bindEmail(BindContactRpcRequest request) {
        log.info("RPC bindEmail: userId={}, tenantUid={}", request.getUserId(), request.getTenantUid());
        try {
            contactBindingService.bindEmail(
                Long.parseLong(request.getUserId()),
                tenantUidResolver.requireTenant(request.getTenantUid()).getId(),
                toBindRequest(request));

            return OperationResult.newBuilder()
                .setSuccess(true)
                .setMessage("邮箱绑定成功")
                .build();
        } catch (BusinessException e) {
            log.warn("Bind email failed: {}", e.getMessage());
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Bind email error", e);
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage("邮箱绑定失败")
                .build();
        }
    }

    @Override
    public OperationResult unbindEmail(ContactUnbindRpcRequest request) {
        log.info("RPC unbindEmail: userId={}, tenantUid={}", request.getUserId(), request.getTenantUid());
        try {
            contactBindingService.unbindEmail(
                Long.parseLong(request.getUserId()),
                tenantUidResolver.requireTenant(request.getTenantUid()).getId());

            return OperationResult.newBuilder()
                .setSuccess(true)
                .setMessage("邮箱解绑成功")
                .build();
        } catch (BusinessException e) {
            log.warn("Unbind email failed: {}", e.getMessage());
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Unbind email error", e);
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage("邮箱解绑失败")
                .build();
        }
    }

    @Override
    public OperationResult bindPhone(BindContactRpcRequest request) {
        log.info("RPC bindPhone: userId={}, tenantUid={}", request.getUserId(), request.getTenantUid());
        try {
            contactBindingService.bindPhone(
                Long.parseLong(request.getUserId()),
                tenantUidResolver.requireTenant(request.getTenantUid()).getId(),
                toBindRequest(request));

            return OperationResult.newBuilder()
                .setSuccess(true)
                .setMessage("手机号绑定成功")
                .build();
        } catch (BusinessException e) {
            log.warn("Bind phone failed: {}", e.getMessage());
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Bind phone error", e);
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage("手机号绑定失败")
                .build();
        }
    }

    @Override
    public OperationResult unbindPhone(ContactUnbindRpcRequest request) {
        log.info("RPC unbindPhone: userId={}, tenantUid={}", request.getUserId(), request.getTenantUid());
        try {
            contactBindingService.unbindPhone(
                Long.parseLong(request.getUserId()),
                tenantUidResolver.requireTenant(request.getTenantUid()).getId());

            return OperationResult.newBuilder()
                .setSuccess(true)
                .setMessage("手机号解绑成功")
                .build();
        } catch (BusinessException e) {
            log.warn("Unbind phone failed: {}", e.getMessage());
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Unbind phone error", e);
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage("手机号解绑失败")
                .build();
        }
    }

    private BindContactRequest toBindRequest(BindContactRpcRequest request) {
        return BindContactRequest.builder()
            .method(request.getMethod())
            .target(request.getTarget())
            .code(request.getCode())
            .build();
    }
}
