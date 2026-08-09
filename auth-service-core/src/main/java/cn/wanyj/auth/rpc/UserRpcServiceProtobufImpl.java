package cn.wanyj.auth.rpc;

import cn.wanyj.auth.api.protobuf.*;
import cn.wanyj.auth.dto.request.AssignRolesRequest;
import cn.wanyj.auth.dto.request.UpdateUserRequest;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.stream.Collectors;

/**
 * 用户管理服务 RPC 实现 - Protobuf IDL 模式
 * <p>所有写操作复用 {@link UserService}（带显式 tenantId）；用户存在性与租户归属
 * 由 Service 层 loadUserAndVerifyTenant 统一校验，本类不再直接访问 Mapper。</p>
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
public class UserRpcServiceProtobufImpl extends DubboUserRpcServiceProtobufTriple.UserRpcServiceProtobufImplBase {

    private final UserService userService;

    @Override
    public OperationResult updateUser(UpdateUserRpcRequest request) {
        Long userId = Long.parseLong(request.getUserId());
        Long tenantId = Long.parseLong(request.getTenantId());
        log.info("RPC updateUser: userId={}, tenantId={}, fields={}", userId, tenantId, request.getFieldsToUpdateList());
        try {
            UpdateUserRequest updateUserRequest = new UpdateUserRequest();
            // 基于 fields_to_update 字段掩码设置待更新字段
            // 仅出现在掩码中的字段才更新（含 status=0 禁用、空字符串清空），其余保持 null（不改）
            for (String field : request.getFieldsToUpdateList()) {
                switch (field) {
                    case "username" -> updateUserRequest.setUsername(request.getUsername());
                    case "password" -> updateUserRequest.setPassword(request.getPassword());
                    case "email" -> updateUserRequest.setEmail(request.getEmail());
                    case "phone" -> updateUserRequest.setPhone(request.getPhone());
                    case "nickname" -> updateUserRequest.setNickname(request.getNickname());
                    case "avatar" -> updateUserRequest.setAvatar(request.getAvatar());
                    case "status" -> updateUserRequest.setStatus(request.getStatus());
                    case "realName" -> updateUserRequest.setRealName(request.getRealName());
                    case "gender" -> updateUserRequest.setGender(request.getGender());
                    case "birthday" -> updateUserRequest.setBirthday(
                        request.getBirthday().isBlank() ? null : java.time.LocalDate.parse(request.getBirthday()));
                    default -> log.warn("Unknown field in fields_to_update: {}", field);
                }
            }

            userService.updateUser(userId, tenantId, updateUserRequest);

            return OperationResult.newBuilder()
                .setSuccess(true)
                .setMessage("用户更新成功")
                .build();
        } catch (BusinessException e) {
            log.warn("Update user failed: {}", e.getMessage());
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Update user error", e);
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage("用户更新失败")
                .build();
        }
    }

    @Override
    public OperationResult updateUserStatus(UpdateUserStatusRpcRequest request) {
        Long userId = Long.parseLong(request.getUserId());
        Long tenantId = Long.parseLong(request.getTenantId());
        log.info("RPC updateUserStatus: userId={}, tenantId={}, status={}", userId, tenantId, request.getStatus());
        try {
            // 用户存在性与租户归属由 Service 层 loadUserAndVerifyTenant 统一校验
            //（不存在/跨租户 → USER_NOT_FOUND，由 catch 转为失败响应）
            userService.updateUserStatus(userId, tenantId, request.getStatus());

            return OperationResult.newBuilder()
                .setSuccess(true)
                .setMessage("用户状态更新成功")
                .build();
        } catch (BusinessException e) {
            log.warn("Update user status failed: {}", e.getMessage());
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Update user status error", e);
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage("用户状态更新失败")
                .build();
        }
    }

    @Override
    public OperationResult assignRoles(AssignRolesRpcRequest request) {
        Long userId = Long.parseLong(request.getUserId());
        Long tenantId = Long.parseLong(request.getTenantId());
        log.info("RPC assignRoles: userId={}, tenantId={}, roleIds={}", userId, tenantId, request.getRoleIdsList());
        try {
            AssignRolesRequest assignRolesRequest = AssignRolesRequest.builder()
                .roleIds(request.getRoleIdsList().stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toList()))
                .build();

            userService.assignRoles(userId, tenantId, assignRolesRequest);

            return OperationResult.newBuilder()
                .setSuccess(true)
                .setMessage("角色分配成功")
                .build();
        } catch (BusinessException e) {
            log.warn("Assign roles failed: {}", e.getMessage());
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Assign roles error", e);
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage("角色分配失败")
                .build();
        }
    }

    @Override
    public OperationResult deleteUser(DeleteUserRpcRequest request) {
        Long userId = Long.parseLong(request.getUserId());
        Long tenantId = Long.parseLong(request.getTenantId());
        log.info("RPC deleteUser: userId={}, tenantId={}", userId, tenantId);
        try {
            userService.deleteUser(userId, tenantId);

            return OperationResult.newBuilder()
                .setSuccess(true)
                .setMessage("用户删除成功")
                .build();
        } catch (BusinessException e) {
            log.warn("Delete user failed: {}", e.getMessage());
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();
        } catch (Exception e) {
            log.error("Delete user error", e);
            return OperationResult.newBuilder()
                .setSuccess(false)
                .setMessage("用户删除失败")
                .build();
        }
    }
}
