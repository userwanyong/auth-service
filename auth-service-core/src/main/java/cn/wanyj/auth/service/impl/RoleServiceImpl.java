package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.security.SecurityUtils;
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
        return getAllRoles(SecurityUtils.getCurrentTenantId());
    }

    @Override
    public List<RoleResponse> getAllRoles(Long tenantId) {
        return roleMapper.findAllWithPermissions(tenantId).stream()
                .map(this::mapToRoleResponse)
                .collect(Collectors.toList());
    }

    @Override
    public RoleResponse getRoleById(Long id) {
        return getRoleById(id, SecurityUtils.getCurrentTenantId());
    }

    @Override
    public RoleResponse getRoleById(Long id, Long tenantId) {
        Role role = roleMapper.findByIdWithPermissions(id);
        if (role == null || (tenantId != null && !tenantId.equals(role.getTenantId()))) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        return mapToRoleResponse(role);
    }

    @Override
    public RoleResponse getRoleByCode(String code) {
        return getRoleByCode(code, SecurityUtils.getCurrentTenantId());
    }

    @Override
    public RoleResponse getRoleByCode(String code, Long tenantId) {
        Role role = roleMapper.findByCode(code, tenantId);
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
        Long tenantId = SecurityUtils.getCurrentTenantId();
        return createRole(code, name, description, tenantId);
    }

    @Override
    @Transactional
    public RoleResponse createRole(String code, String name, String description, Long tenantId) {
        log.info("Creating new role: {} in tenant: {}", code, tenantId);

        // Check if role code already exists
        if (roleMapper.existsByCode(code, tenantId)) {
            throw new BusinessException(ErrorCode.ROLE_CODE_EXISTS);
        }

        Role role = Role.builder()
                .tenantId(tenantId)
                .code(code)
                .name(name)
                .description(description)
                .status(1)
                .permissions(new HashSet<>())
                .build();

        roleMapper.insert(role);

        log.info("Role created successfully: {} in tenant: {}", role.getId(), tenantId);
        return mapToRoleResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse updateRole(Long id, String name, String description) {
        return updateRole(id, name, description, SecurityUtils.getCurrentTenantId());
    }

    @Override
    @Transactional
    public RoleResponse updateRole(Long id, String name, String description, Long tenantId) {
        log.info("Updating role: {} in tenant: {}", id, tenantId);

        Role role = loadRoleAndVerifyTenant(id, tenantId);

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
        deleteRole(id, SecurityUtils.getCurrentTenantId());
    }

    @Override
    @Transactional
    public void deleteRole(Long id, Long tenantId) {
        log.info("Deleting role: {} in tenant: {}", id, tenantId);

        loadRoleAndVerifyTenant(id, tenantId);

        // Delete role permissions first
        roleMapper.deleteRolePermissionsByRoleId(id);

        // Delete role
        roleMapper.deleteById(id);

        log.info("Role deleted successfully: {}", id);
    }

    @Override
    @Transactional
    public void assignPermissions(Long roleId, AssignPermissionsRequest request) {
        assignPermissions(roleId, request, SecurityUtils.getCurrentTenantId());
    }

    @Override
    @Transactional
    public void assignPermissions(Long roleId, AssignPermissionsRequest request, Long tenantId) {
        log.info("Assigning permissions to role: {} in tenant: {}", roleId, tenantId);

        loadRoleAndVerifyTenant(roleId, tenantId);

        // Delete existing permission assignments
        roleMapper.deleteRolePermissionsByRoleId(roleId);

        // Create new permission assignments
        for (Long permissionId : request.getPermissionIds()) {
            Permission permission = permissionMapper.findById(permissionId);
            // 校验权限存在且属于同一租户（防止跨租户分配权限）
            if (permission == null || (tenantId != null && !tenantId.equals(permission.getTenantId()))) {
                throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
            }

            // Create RolePermission relationship（统一使用调用方租户）
            roleMapper.insertRolePermission(roleId, permissionId, tenantId);
        }

        log.info("Permissions assigned successfully to role: {}", roleId);
    }

    /**
     * 加载角色并校验租户归属
     * 角色不存在或不属于指定租户时抛 ROLE_NOT_FOUND（不泄露存在性）
     */
    private Role loadRoleAndVerifyTenant(Long roleId, Long tenantId) {
        Role role = roleMapper.findById(roleId);
        if (role == null || (tenantId != null && !tenantId.equals(role.getTenantId()))) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        return role;
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
