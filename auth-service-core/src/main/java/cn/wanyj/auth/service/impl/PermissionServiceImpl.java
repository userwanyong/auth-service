package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.security.SecurityUtils;
import cn.wanyj.auth.dto.response.PermissionResponse;
import cn.wanyj.auth.entity.Permission;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.mapper.PermissionMapper;
import cn.wanyj.auth.mapper.RolePermissionMapper;
import cn.wanyj.auth.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Permission Service Implementation - 权限服务实现
 * @author wanyj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    @Override
    public List<PermissionResponse> getAllPermissions() {
        Long tenantId = SecurityUtils.getCurrentTenantId();
        return permissionMapper.findAll(tenantId).stream()
                .map(this::mapToPermissionResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PermissionResponse getPermissionById(Long id) {
        return getPermissionById(id, SecurityUtils.getCurrentTenantId());
    }

    @Override
    public PermissionResponse getPermissionById(Long id, Long tenantId) {
        Permission permission = loadPermissionAndVerifyTenant(id, tenantId);
        return mapToPermissionResponse(permission);
    }

    @Override
    public PermissionResponse createPermission(String code, String name, String resource, String action, String description) {
        Long tenantId = SecurityUtils.getCurrentTenantId();
        return createPermission(code, name, resource, action, description, tenantId);
    }

    @Override
    public PermissionResponse createPermission(String code, String name, String resource, String action, String description, Long tenantId) {
        log.info("Creating new permission: {} in tenant: {}", code, tenantId);

        // Check if permission code already exists
        if (permissionMapper.existsByCode(code, tenantId)) {
            throw new BusinessException(ErrorCode.PERMISSION_CODE_EXISTS);
        }

        Permission permission = Permission.builder()
                .tenantId(tenantId)
                .code(code)
                .name(name)
                .resource(resource)
                .action(action)
                .description(description)
                .build();

        permissionMapper.insert(permission);

        log.info("Permission created successfully: {} in tenant: {}", permission.getId(), tenantId);
        return mapToPermissionResponse(permission);
    }

    @Override
    @Transactional
    public void deletePermission(Long id) {
        deletePermission(id, SecurityUtils.getCurrentTenantId());
    }

    @Override
    @Transactional
    public void deletePermission(Long id, Long tenantId) {
        log.info("Deleting permission: {} in tenant: {}", id, tenantId);

        loadPermissionAndVerifyTenant(id, tenantId);

        // Delete role permissions first
        rolePermissionMapper.deleteByPermissionId(id);

        // Delete permission
        permissionMapper.deleteById(id);

        log.info("Permission deleted successfully: {}", id);
    }

    /**
     * 加载权限并校验租户归属
     * 权限不存在或不属于指定租户时抛 PERMISSION_NOT_FOUND（不泄露存在性）
     */
    private Permission loadPermissionAndVerifyTenant(Long permissionId, Long tenantId) {
        Permission permission = permissionMapper.findById(permissionId);
        if (permission == null || (tenantId != null && !tenantId.equals(permission.getTenantId()))) {
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
        }
        return permission;
    }

    /**
     * Map Permission entity to PermissionResponse DTO
     */
    private PermissionResponse mapToPermissionResponse(Permission permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .code(permission.getCode())
                .name(permission.getName())
                .resource(permission.getResource())
                .action(permission.getAction())
                .description(permission.getDescription())
                .createdAt(permission.getCreatedAt())
                .build();
    }
}
