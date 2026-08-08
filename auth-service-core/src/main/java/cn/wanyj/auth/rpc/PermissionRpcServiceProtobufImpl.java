package cn.wanyj.auth.rpc;

import cn.wanyj.auth.api.protobuf.*;
import cn.wanyj.auth.entity.Permission;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.mapper.PermissionMapper;
import cn.wanyj.auth.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * 权限服务 RPC 实现 - Protobuf IDL 模式
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

    private final PermissionMapper permissionMapper;
    private final PermissionService permissionService;

    @Override
    public PermissionListResponse getAllPermissions(GetAllPermissionsRequest request) {
        log.info("RPC getAllPermissions: tenantId={}", request.getTenantId());
        try {
            Long tenantId = Long.parseLong(request.getTenantId());
            List<Permission> permissions = permissionMapper.findAll(tenantId);

            PermissionListResponse.Builder builder = PermissionListResponse.newBuilder();
            for (Permission permission : permissions) {
                builder.addPermissions(convertToProtobuf(permission));
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

            Permission permission = permissionMapper.findById(permissionId);
            if (permission == null) {
                log.warn("Permission not found: permissionId={}", permissionId);
                return PermissionRpcResponse.getDefaultInstance();
            }
            if (tenantId != null && !tenantId.equals(permission.getTenantId())) {
                log.warn("Permission {} does not belong to tenant {}", permissionId, tenantId);
                return PermissionRpcResponse.getDefaultInstance();
            }
            return convertToProtobuf(permission);
        } catch (Exception e) {
            log.error("Failed to get permission by id: permissionId={}", request.getPermissionId(), e);
            return PermissionRpcResponse.getDefaultInstance();
        }
    }

    @Override
    public PermissionRpcResponse createPermission(CreatePermissionRpcRequest request) {
        log.info("RPC createPermission: code={}, tenantId={}", request.getCode(), request.getTenantId());
        try {
            cn.wanyj.auth.dto.response.PermissionResponse response = permissionService.createPermission(
                request.getCode(),
                request.getName(),
                request.getResource(),
                request.getAction(),
                request.getDescription(),
                Long.parseLong(request.getTenantId())
            );
            return PermissionRpcResponse.newBuilder()
                .setId(response.getId())
                .setCode(response.getCode())
                .setName(response.getName())
                .setResource(response.getResource() != null ? response.getResource() : "")
                .setAction(response.getAction() != null ? response.getAction() : "")
                .setDescription(response.getDescription() != null ? response.getDescription() : "")
                .build();
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

    private PermissionRpcResponse convertToProtobuf(Permission permission) {
        PermissionRpcResponse.Builder builder = PermissionRpcResponse.newBuilder()
            .setId(permission.getId())
            .setCode(permission.getCode())
            .setName(permission.getName())
            .setResource(permission.getResource() != null ? permission.getResource() : "")
            .setAction(permission.getAction() != null ? permission.getAction() : "");

        if (permission.getDescription() != null) {
            builder.setDescription(permission.getDescription());
        }

        return builder.build();
    }
}
