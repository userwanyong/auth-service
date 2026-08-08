package cn.wanyj.auth.rpc;

import cn.wanyj.auth.api.protobuf.*;
import cn.wanyj.auth.dto.response.PermissionResponse;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.rpc.converter.PermissionProtobufConverter;
import cn.wanyj.auth.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * 权限服务 RPC 实现 - Protobuf IDL 模式
 * <p>查询类方法复用 {@link PermissionService}（带显式 tenantId），Protobuf 转换复用
 * {@link PermissionProtobufConverter}，本类不再直接访问 Mapper。</p>
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
public class PermissionRpcServiceProtobufImpl extends DubboPermissionRpcServiceProtobufTriple.PermissionRpcServiceProtobufImplBase {

    private final PermissionService permissionService;

    @Override
    public PermissionListResponse getAllPermissions(GetAllPermissionsRequest request) {
        log.info("RPC getAllPermissions: tenantId={}", request.getTenantId());
        try {
            Long tenantId = Long.parseLong(request.getTenantId());
            List<PermissionResponse> permissions = permissionService.getAllPermissions(tenantId);

            PermissionListResponse.Builder builder = PermissionListResponse.newBuilder();
            for (PermissionResponse permission : permissions) {
                builder.addPermissions(PermissionProtobufConverter.convertToProtobuf(permission));
            }
            return builder.build();
        } catch (Exception e) {
            log.error("Failed to get all permissions for tenant: {}", request.getTenantId(), e);
            return PermissionListResponse.getDefaultInstance();
        }
    }

    @Override
    public PermissionRpcResponse getPermissionById(GetPermissionByIdRequest request) {
        log.info("RPC getPermissionById: permissionId={}, tenantId={}", request.getPermissionId(), request.getTenantId());
        try {
            Long permissionId = Long.parseLong(request.getPermissionId());
            // tenantId 为空（旧客户端）时跳过归属校验，非空时强制校验
            Long tenantId = request.getTenantId().isBlank() ? null : Long.parseLong(request.getTenantId());

            PermissionResponse permission = permissionService.getPermissionById(permissionId, tenantId);
            return PermissionProtobufConverter.convertToProtobuf(permission);
        } catch (BusinessException e) {
            log.warn("getPermissionById failed: {}", e.getMessage());
            return PermissionRpcResponse.getDefaultInstance();
        } catch (Exception e) {
            log.error("Failed to get permission by id: permissionId={}", request.getPermissionId(), e);
            return PermissionRpcResponse.getDefaultInstance();
        }
    }

    @Override
    public PermissionRpcResponse createPermission(CreatePermissionRpcRequest request) {
        log.info("RPC createPermission: code={}, tenantId={}", request.getCode(), request.getTenantId());
        try {
            PermissionResponse response = permissionService.createPermission(
                request.getCode(),
                request.getName(),
                request.getResource(),
                request.getAction(),
                request.getDescription(),
                Long.parseLong(request.getTenantId())
            );
            return PermissionProtobufConverter.convertToProtobuf(response);
        } catch (BusinessException e) {
            log.warn("Create permission failed: {}", e.getMessage());
            return PermissionRpcResponse.getDefaultInstance();
        } catch (Exception e) {
            log.error("Create permission error", e);
            return PermissionRpcResponse.getDefaultInstance();
        }
    }

    @Override
    public OperationResult deletePermission(DeletePermissionRpcRequest request) {
        log.info("RPC deletePermission: permissionId={}", request.getPermissionId());
        try {
            Long permissionId = Long.parseLong(request.getPermissionId());
            Long tenantId = request.getTenantId().isBlank() ? null : Long.parseLong(request.getTenantId());
            permissionService.deletePermission(permissionId, tenantId);
            return OperationResult.newBuilder()
                .setSuccess(true)
                .setMessage("权限删除成功")
                .build();
        } catch (BusinessException e) {
            log.warn("Delete permission failed: {}", e.getMessage());
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Delete permission error", e);
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage("权限删除失败")
                .build();
        }
    }
}
