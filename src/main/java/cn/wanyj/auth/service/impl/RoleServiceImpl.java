package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.dto.request.AssignPermissionsRequest;
import cn.wanyj.auth.dto.response.RoleResponse;
import cn.wanyj.auth.entity.Permission;
import cn.wanyj.auth.entity.Role;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.mapper.PermissionMapper;
import cn.wanyj.auth.mapper.RoleMapper;
import cn.wanyj.auth.mapper.RolePermissionMapper;
import cn.wanyj.auth.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Role Service Implementation - 角色服务实现
 * @author wanyj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    @Override
    public List<RoleResponse> getAllRoles() {
        return roleMapper.findAllWithPermissions().stream()
                .map(this::mapToRoleResponse)
                .collect(Collectors.toList());
    }

    @Override
    public RoleResponse getRoleById(Long id) {
        Role role = roleMapper.findByIdWithPermissions(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        return mapToRoleResponse(role);
    }

    @Override
    public RoleResponse getRoleByCode(String code) {
        Role role = roleMapper.findByCode(code);
        if (role == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        // Load permissions
        List<Long> permissionIds = roleMapper.findPermissionIdsByRoleId(role.getId());
        Set<Permission> permissions = new HashSet<>();
        for (Long permissionId : permissionIds) {
            Permission permission = permissionMapper.findById(permissionId);
            if (permission != null) {
                permissions.add(permission);
            }
        }
        role.setPermissions(permissions);
        return mapToRoleResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse createRole(String code, String name, String description) {
        log.info("Creating new role: {}", code);

        // Check if role code already exists
        if (roleMapper.existsByCode(code)) {
            throw new BusinessException(ErrorCode.ROLE_CODE_EXISTS);
        }

        Role role = Role.builder()
                .code(code)
                .name(name)
                .description(description)
                .status(1)
                .permissions(new HashSet<>())
                .build();

        roleMapper.insert(role);

        log.info("Role created successfully: {}", role.getId());
        return mapToRoleResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse updateRole(Long id, String name, String description) {
        log.info("Updating role: {}", id);

        Role role = roleMapper.findById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }

        role.setName(name);
        role.setDescription(description);
        role.setUpdatedAt(LocalDateTime.now());

        roleMapper.update(role);

        log.info("Role updated successfully: {}", id);
        return mapToRoleResponse(role);
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        log.info("Deleting role: {}", id);

        Role role = roleMapper.findById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }

        // Delete role permissions first
        roleMapper.deleteRolePermissionsByRoleId(id);

        // Delete role
        roleMapper.deleteById(id);

        log.info("Role deleted successfully: {}", id);
    }

    @Override
    @Transactional
    public void assignPermissions(Long roleId, AssignPermissionsRequest request) {
        log.info("Assigning permissions to role: {}", roleId);

        Role role = roleMapper.findById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }

        // Delete existing permission assignments
        roleMapper.deleteRolePermissionsByRoleId(roleId);

        // Create new permission assignments
        for (Long permissionId : request.getPermissionIds()) {
            Permission permission = permissionMapper.findById(permissionId);
            if (permission == null) {
                throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
            }

            // Create RolePermission relationship
            roleMapper.insertRolePermission(roleId, permissionId);
        }

        log.info("Permissions assigned successfully to role: {}", roleId);
    }

    /**
     * Map Role entity to RoleResponse DTO
     */
    private RoleResponse mapToRoleResponse(Role role) {
        Set<String> permissions = role.getPermissions().stream()
                .map(cn.wanyj.auth.entity.Permission::getCode)
                .collect(Collectors.toSet());

        return RoleResponse.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .status(role.getStatus())
                .createdAt(role.getCreatedAt())
                .permissions(permissions)
                .build();
    }
}
