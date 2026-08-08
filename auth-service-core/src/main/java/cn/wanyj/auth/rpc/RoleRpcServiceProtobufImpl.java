package cn.wanyj.auth.rpc;

import cn.wanyj.auth.api.protobuf.*;
import cn.wanyj.auth.dto.request.AssignPermissionsRequest;
import cn.wanyj.auth.dto.response.RoleResponse;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.rpc.converter.RoleProtobufConverter;
import cn.wanyj.auth.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务 RPC 实现 - Protobuf IDL 模式
 * <p>查询类方法复用 {@link RoleService}（带显式 tenantId），Protobuf 转换复用
 * {@link RoleProtobufConverter}，本类不再直接访问 Mapper。</p>
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
public class RoleRpcServiceProtobufImpl extends DubboRoleRpcServiceProtobufTriple.RoleRpcServiceProtobufImplBase {

    private final RoleService roleService;

    @Override
    public RoleListResponse getAllRoles(GetAllRolesRequest request) {
        log.info("RPC getAllRoles: tenantId={}", request.getTenantId());
        try {
            Long tenantId = Long.parseLong(request.getTenantId());
            List<RoleResponse> roles = roleService.getAllRoles(tenantId);

            RoleListResponse.Builder builder = RoleListResponse.newBuilder();
            for (RoleResponse role : roles) {
                builder.addRoles(RoleProtobufConverter.convertToProtobuf(role));
            }
            return builder.build();
        } catch (Exception e) {
            log.error("Failed to get all roles for tenant: {}", request.getTenantId(), e);
            return RoleListResponse.getDefaultInstance();
        }
    }

    @Override
    public RoleRpcResponse getRoleByCode(GetRoleByCodeRequest request) {
        log.info("RPC getRoleByCode: code={}, tenantId={}", request.getCode(), request.getTenantId());
        try {
            Long tenantId = Long.parseLong(request.getTenantId());
            RoleResponse role = roleService.getRoleByCode(request.getCode(), tenantId);
            return RoleProtobufConverter.convertToProtobuf(role);
        } catch (BusinessException e) {
            log.warn("getRoleByCode failed: {}", e.getMessage());
            return RoleRpcResponse.getDefaultInstance();
        } catch (Exception e) {
            log.error("Failed to get role by code: code={}, tenantId={}",
                request.getCode(), request.getTenantId(), e);
            return RoleRpcResponse.getDefaultInstance();
        }
    }

    @Override
    public RoleRpcResponse getRoleById(GetRoleByIdRequest request) {
        log.info("RPC getRoleById: roleId={}, tenantId={}", request.getRoleId(), request.getTenantId());
        try {
            Long roleId = Long.parseLong(request.getRoleId());
            // tenantId 为空（旧客户端）时跳过归属校验，非空时强制校验
            Long tenantId = request.getTenantId().isBlank() ? null : Long.parseLong(request.getTenantId());

            RoleResponse role = roleService.getRoleById(roleId, tenantId);
            return RoleProtobufConverter.convertToProtobuf(role);
        } catch (BusinessException e) {
            log.warn("getRoleById failed: {}", e.getMessage());
            return RoleRpcResponse.getDefaultInstance();
        } catch (Exception e) {
            log.error("Failed to get role by id: roleId={}", request.getRoleId(), e);
            return RoleRpcResponse.getDefaultInstance();
        }
    }

    @Override
    public RoleRpcResponse createRole(CreateRoleRpcRequest request) {
        log.info("RPC createRole: code={}, tenantId={}", request.getCode(), request.getTenantId());
        try {
            RoleResponse roleResponse = roleService.createRole(
                request.getCode(),
                request.getName(),
                request.getDescription(),
                Long.parseLong(request.getTenantId())
            );
            return RoleProtobufConverter.convertToProtobuf(roleResponse);
        } catch (BusinessException e) {
            log.warn("Create role failed: {}", e.getMessage());
            return RoleRpcResponse.getDefaultInstance();
        } catch (Exception e) {
            log.error("Create role error", e);
            return RoleRpcResponse.getDefaultInstance();
        }
    }

    @Override
    public OperationResult updateRole(UpdateRoleRpcRequest request) {
        log.info("RPC updateRole: roleId={}", request.getRoleId());
        try {
            Long tenantId = request.getTenantId().isBlank() ? null : Long.parseLong(request.getTenantId());
            roleService.updateRole(Long.parseLong(request.getRoleId()), request.getName(), request.getDescription(), tenantId);
            return OperationResult.newBuilder()
                .setSuccess(true)
                .setMessage("角色更新成功")
                .build();
        } catch (BusinessException e) {
            log.warn("Update role failed: {}", e.getMessage());
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Update role error", e);
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage("角色更新失败")
                .build();
        }
    }

    @Override
    public OperationResult deleteRole(DeleteRoleRpcRequest request) {
        log.info("RPC deleteRole: roleId={}", request.getRoleId());
        try {
            Long tenantId = request.getTenantId().isBlank() ? null : Long.parseLong(request.getTenantId());
            roleService.deleteRole(Long.parseLong(request.getRoleId()), tenantId);
            return OperationResult.newBuilder()
                .setSuccess(true)
                .setMessage("角色删除成功")
                .build();
        } catch (BusinessException e) {
            log.warn("Delete role failed: {}", e.getMessage());
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Delete role error", e);
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage("角色删除失败")
                .build();
        }
    }

    @Override
    public OperationResult assignPermissions(AssignPermissionsRpcRequest request) {
        log.info("RPC assignPermissions: roleId={}, tenantId={}, permissionIds={}",
            request.getRoleId(), request.getTenantId(), request.getPermissionIdsList());
        try {
            Long roleId = Long.parseLong(request.getRoleId());
            Long tenantId = request.getTenantId().isBlank() ? null : Long.parseLong(request.getTenantId());
            AssignPermissionsRequest assignRequest = AssignPermissionsRequest.builder()
                .permissionIds(request.getPermissionIdsList().stream().map(Long::parseLong).collect(Collectors.toList()))
                .build();

            roleService.assignPermissions(roleId, assignRequest, tenantId);
            return OperationResult.newBuilder()
                .setSuccess(true)
                .setMessage("权限分配成功")
                .build();
        } catch (BusinessException e) {
            log.warn("Assign permissions failed: {}", e.getMessage());
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Assign permissions error", e);
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage("权限分配失败")
                .build();
        }
    }
}
