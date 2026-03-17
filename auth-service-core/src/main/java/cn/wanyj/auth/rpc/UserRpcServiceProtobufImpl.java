package cn.wanyj.auth.rpc;

import cn.wanyj.auth.api.protobuf.*;
import cn.wanyj.auth.dto.request.AssignRolesRequest;
import cn.wanyj.auth.dto.request.UpdateUserRequest;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.mapper.UserMapper;
import cn.wanyj.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.stream.Collectors;

/**
 * 用户管理服务 RPC 实现 - Protobuf IDL 模式
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
    private final UserMapper userMapper;

    @Override
    public OperationResult updateUser(UpdateUserRpcRequest request) {
        Long userId = Long.parseLong(request.getUserId());
        Long tenantId = Long.parseLong(request.getTenantId());
        log.info("RPC updateUser: userId={}, tenantId={}", userId, tenantId);
        try {
            UpdateUserRequest updateUserRequest = new UpdateUserRequest();
            if (!request.getUsername().isBlank()) {
                updateUserRequest.setUsername(request.getUsername());
            }
            if (!request.getPassword().isBlank()) {
                updateUserRequest.setPassword(request.getPassword());
            }
            if (!request.getEmail().isBlank()) {
                updateUserRequest.setEmail(request.getEmail());
            } else {
                updateUserRequest.setEmail("");
            }
            if (!request.getPhone().isBlank()) {
                updateUserRequest.setPhone(request.getPhone());
            }
            if (!request.getNickname().isBlank()) {
                updateUserRequest.setNickname(request.getNickname());
            } else {
                updateUserRequest.setNickname("");
            }
            if (!request.getAvatar().isBlank()) {
                updateUserRequest.setAvatar(request.getAvatar());
            }
            if (request.getStatus() != 0) {
                updateUserRequest.setStatus(request.getStatus());
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
            User user = userMapper.findById(userId);
            if (user == null) {
                return OperationResult.newBuilder()
                    .setSuccess(false)
                    .setMessage("用户不存在")
                    .build();
            }

            userService.updateUserStatus(userId, request.getStatus());

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

            userService.assignRoles(userId, assignRolesRequest);

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
            userService.deleteUser(userId);

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
