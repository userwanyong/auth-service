package cn.wanyj.auth.service.impl;

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
        return permissionMapper.findAll().stream()
                .map(this::mapToPermissionResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PermissionResponse getPermissionById(Long id) {
        Permission permission = permissionMapper.findById(id);
        if (permission == null) {
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
        }
        return mapToPermissionResponse(permission);
    }

    @Override
    public PermissionResponse createPermission(String code, String name, String resource, String action, String description) {
        log.info("Creating new permission: {}", code);

        // Check if permission code already exists
        if (permissionMapper.existsByCode(code)) {
            throw new BusinessException(ErrorCode.PERMISSION_CODE_EXISTS);
        }

        Permission permission = Permission.builder()
                .code(code)
                .name(name)
                .resource(resource)
                .action(action)
                .description(description)
                .build();

        permissionMapper.insert(permission);

        log.info("Permission created successfully: {}", permission.getId());
        return mapToPermissionResponse(permission);
    }

    @Override
    @Transactional
    public void deletePermission(Long id) {
        log.info("Deleting permission: {}", id);

        Permission permission = permissionMapper.findById(id);
        if (permission == null) {
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
        }

        // Delete role permissions first
        rolePermissionMapper.deleteByPermissionId(id);

        // Delete permission
        permissionMapper.deleteById(id);

        log.info("Permission deleted successfully: {}", id);
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
