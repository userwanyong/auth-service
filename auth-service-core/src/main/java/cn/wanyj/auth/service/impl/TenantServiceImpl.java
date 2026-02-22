package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.entity.Permission;
import cn.wanyj.auth.entity.Role;
import cn.wanyj.auth.entity.Tenant;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.mapper.PermissionMapper;
import cn.wanyj.auth.mapper.RoleMapper;
import cn.wanyj.auth.mapper.TenantMapper;
import cn.wanyj.auth.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 租户服务实现
 *
 * @author wanyj
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final TenantMapper tenantMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    @Override
    public boolean isValidTenant(Long tenantId) {
        if (tenantId == null) {
            return false;
        }
        Tenant tenant = tenantMapper.findById(tenantId);
        return tenant != null && tenant.isValid();
    }

    @Override
    public Long getTenantIdByCode(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return null;
        }
        Tenant tenant = tenantMapper.findByCode(tenantCode);
        return tenant != null ? tenant.getId() : null;
    }

    @Override
    public Tenant getTenantById(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        return tenantMapper.findById(tenantId);
    }

    @Override
    public Tenant getTenantByCode(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return null;
        }
        return tenantMapper.findByCode(tenantCode);
    }

    @Override
    public boolean existsByCode(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return false;
        }
        return tenantMapper.existsByCode(tenantCode);
    }

    @Override
    @Transactional
    public Tenant createTenant(Tenant tenant) {
        // 检查租户编码是否已存在
        if (existsByCode(tenant.getTenantCode())) {
            throw new BusinessException(ErrorCode.TENANT_CODE_EXISTS);
        }

        // 设置默认值
        if (tenant.getStatus() == null) {
            tenant.setStatus(1);
        }
        if (tenant.getMaxUsers() == null) {
            tenant.setMaxUsers(Integer.MAX_VALUE);
        }

        tenantMapper.insert(tenant);

        // 初始化默认角色和权限
        initializeDefaultRolesAndPermissions(tenant.getId());

        log.info("Created tenant with default roles: code={}, name={}", tenant.getTenantCode(), tenant.getTenantName());
        return tenant;
    }

    @Override
    public Tenant updateTenant(Tenant tenant) {
        // 检查租户是否存在
        Tenant existing = getTenantById(tenant.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND);
        }

        // 如果修改租户编码，检查是否冲突
        if (!existing.getTenantCode().equals(tenant.getTenantCode()) &&
            existsByCode(tenant.getTenantCode())) {
            throw new BusinessException(ErrorCode.TENANT_CODE_EXISTS);
        }

        tenantMapper.update(tenant);
        log.info("Updated tenant: id={}, code={}", tenant.getId(), tenant.getTenantCode());
        return tenant;
    }

    @Override
    public void deleteTenant(Long tenantId) {
        // 检查租户是否存在
        Tenant tenant = getTenantById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND);
        }

        // 不允许删除默认租户
        if (tenantId.equals(1L)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不允许删除默认租户");
        }

        // 检查是否还有用户
        long userCount = tenantMapper.countUsersByTenantId(tenantId);
        if (userCount > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "租户下还有 " + userCount + " 个用户，无法删除");
        }

        tenantMapper.deleteById(tenantId);
        log.info("Deleted tenant: id={}", tenantId);
    }

    @Override
    public List<Tenant> getAllTenants() {
        return tenantMapper.findAll();
    }

    @Override
    public boolean isUserLimitReached(Long tenantId) {
        Tenant tenant = getTenantById(tenantId);
        if (tenant == null) {
            return true;
        }

        Integer maxUsers = tenant.getMaxUsers();
        if (maxUsers == null || maxUsers <= 0) {
            return false; // 无限制
        }

        long currentUserCount = tenantMapper.countUsersByTenantId(tenantId);
        return currentUserCount >= maxUsers;
    }

    @Override
    @Transactional
    public void initializeDefaultRolesAndPermissions(Long tenantId) {
        log.info("Initializing default roles and permissions for tenant: {}", tenantId);

        // 1. 创建默认权限
        Permission[] defaultPermissions = {
            Permission.builder().tenantId(tenantId).code("user:read").name("查看用户")
                .resource("user").action("read").description("查看用户信息").build(),
            Permission.builder().tenantId(tenantId).code("user:write").name("编辑用户")
                .resource("user").action("write").description("编辑用户信息").build(),
            Permission.builder().tenantId(tenantId).code("user:delete").name("删除用户")
                .resource("user").action("delete").description("删除用户").build(),
            Permission.builder().tenantId(tenantId).code("role:read").name("查看角色")
                .resource("role").action("read").description("查看角色信息").build(),
            Permission.builder().tenantId(tenantId).code("role:write").name("编辑角色")
                .resource("role").action("write").description("编辑角色信息").build(),
            Permission.builder().tenantId(tenantId).code("permission:read").name("查看权限")
                .resource("permission").action("read").description("查看权限信息").build(),
            Permission.builder().tenantId(tenantId).code("tenant:read").name("查看租户")
                .resource("tenant").action("read").description("查看租户信息").build(),
            Permission.builder().tenantId(tenantId).code("tenant:write").name("编辑租户")
                .resource("tenant").action("write").description("编辑租户信息").build()
        };

        for (Permission permission : defaultPermissions) {
            if (!permissionMapper.existsByCode(permission.getCode(), tenantId)) {
                permissionMapper.insert(permission);
            }
        }

        // 2. 创建默认角色
        Role adminRole = Role.builder()
                .tenantId(tenantId)
                .code("ROLE_ADMIN")
                .name("系统管理员")
                .description("拥有所有权限")
                .status(1)
                .build();

        Role userRole = Role.builder()
                .tenantId(tenantId)
                .code("ROLE_USER")
                .name("普通用户")
                .description("基础用户权限")
                .status(1)
                .build();

        if (!roleMapper.existsByCode("ROLE_ADMIN", tenantId)) {
            roleMapper.insert(adminRole);
        } else {
            // 如果已存在，重新获取ID
            adminRole = roleMapper.findByCode("ROLE_ADMIN", tenantId);
        }

        if (!roleMapper.existsByCode("ROLE_USER", tenantId)) {
            roleMapper.insert(userRole);
        } else {
            // 如果已存在，重新获取ID
            userRole = roleMapper.findByCode("ROLE_USER", tenantId);
        }

        // 3. 为管理员角色分配所有权限
        for (Permission permission : defaultPermissions) {
            Permission existingPermission = permissionMapper.findByCode(permission.getCode(), tenantId);
            if (existingPermission != null) {
                roleMapper.insertRolePermission(adminRole.getId(), existingPermission.getId());
            }
        }

        // 4. 为普通用户角色分配只读权限
        Permission userReadPermission = permissionMapper.findByCode("user:read", tenantId);
        if (userReadPermission != null) {
            roleMapper.insertRolePermission(userRole.getId(), userReadPermission.getId());
        }

        log.info("Initialized default roles and permissions for tenant: {}", tenantId);
    }
}
