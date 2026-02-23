package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.entity.Permission;
import cn.wanyj.auth.entity.Role;
import cn.wanyj.auth.entity.Tenant;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.mapper.PermissionMapper;
import cn.wanyj.auth.mapper.RoleMapper;
import cn.wanyj.auth.mapper.RolePermissionMapper;
import cn.wanyj.auth.mapper.TenantMapper;
import cn.wanyj.auth.mapper.UserMapper;
import cn.wanyj.auth.mapper.UserRoleMapper;
import cn.wanyj.auth.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PasswordEncoder passwordEncoder;

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
    @Transactional
    public void deleteTenant(Long tenantId) {
        // 不允许删除平台租户（tenantId=0）
        if (tenantId.equals(0L)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不允许删除平台租户");
        }

        // 检查租户是否存在
        Tenant tenant = getTenantById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND);
        }

        // 级联删除租户下的所有相关数据
        log.info("Deleting tenant and all related data: id={}", tenantId);

        // 1. 删除用户角色关联（user_role表有tenant_id字段）
        userRoleMapper.deleteByTenantId(tenantId);

        // 2. 删除角色权限关联（role_permission表有tenant_id字段）
        rolePermissionMapper.deleteByTenantId(tenantId);

        // 3. 删除权限
        permissionMapper.deleteByTenantId(tenantId);

        // 4. 删除角色
        roleMapper.deleteByTenantId(tenantId);

        // 5. 删除用户
        userMapper.deleteByTenantId(tenantId);

        // 6. 最后删除租户
        tenantMapper.deleteById(tenantId);

        log.info("Deleted tenant and all related data: id={}", tenantId);
    }

    @Override
    public List<Tenant> getAllTenants() {
        return tenantMapper.findAll();
    }

    @Override
    public List<Tenant> getActiveTenants() {
        return tenantMapper.findActive();
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
            // 用户权限
            Permission.builder().tenantId(tenantId).code("user:read").name("查看用户")
                .resource("user").action("read").description("查看用户信息").build(),
            Permission.builder().tenantId(tenantId).code("user:create").name("创建用户")
                .resource("user").action("create").description("创建用户").build(),
            Permission.builder().tenantId(tenantId).code("user:write").name("编辑用户")
                .resource("user").action("write").description("编辑用户信息").build(),
            Permission.builder().tenantId(tenantId).code("user:delete").name("删除用户")
                .resource("user").action("delete").description("删除用户").build(),
            // 角色权限
            Permission.builder().tenantId(tenantId).code("role:read").name("查看角色")
                .resource("role").action("read").description("查看角色信息").build(),
            Permission.builder().tenantId(tenantId).code("role:create").name("创建角色")
                .resource("role").action("create").description("创建角色").build(),
            Permission.builder().tenantId(tenantId).code("role:write").name("编辑角色")
                .resource("role").action("write").description("编辑角色信息").build(),
            Permission.builder().tenantId(tenantId).code("role:delete").name("删除角色")
                .resource("role").action("delete").description("删除角色").build(),
            // 权限权限
            Permission.builder().tenantId(tenantId).code("permission:read").name("查看权限")
                .resource("permission").action("read").description("查看权限信息").build(),
            Permission.builder().tenantId(tenantId).code("permission:create").name("创建权限")
                .resource("permission").action("create").description("创建权限").build(),
            Permission.builder().tenantId(tenantId).code("permission:write").name("编辑权限")
                .resource("permission").action("write").description("编辑权限信息").build(),
            Permission.builder().tenantId(tenantId).code("permission:delete").name("删除权限")
                .resource("permission").action("delete").description("删除权限").build()
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
                roleMapper.insertRolePermission(adminRole.getId(), existingPermission.getId(), tenantId);
            }
        }

        // 4. 为普通用户角色分配只读权限
        Permission userReadPermission = permissionMapper.findByCode("user:read", tenantId);
        if (userReadPermission != null) {
            roleMapper.insertRolePermission(userRole.getId(), userReadPermission.getId(), tenantId);
        }

        // 5. 创建租户管理员用户
        // 检查管理员用户是否已存在
        User existingAdmin = userMapper.findByUsername("admin", tenantId);
        if (existingAdmin == null) {
            User adminUser = User.builder()
                    .tenantId(tenantId)
                    .username("admin")
                    .password(passwordEncoder.encode("123456"))
                    .nickname("管理员")
                    .status(1)
                    .emailVerified(false)
                    .build();

            userMapper.insert(adminUser);

            // 分配管理员角色给管理员用户
            userMapper.insertUserRole(adminUser.getId(), adminRole.getId(), tenantId);

            log.info("Created admin user for tenant: {}, username: admin, password: 123456", tenantId);
        } else {
            // 如果管理员用户已存在但没有角色，分配管理员角色
            List<Long> existingRoleIds = userMapper.findRoleIdsByUserId(existingAdmin.getId());
            if (existingRoleIds.isEmpty() || !existingRoleIds.contains(adminRole.getId())) {
                userMapper.insertUserRole(existingAdmin.getId(), adminRole.getId(), tenantId);
                log.info("Assigned admin role to existing admin user for tenant: {}", tenantId);
            }
        }

        log.info("Initialized default roles and permissions for tenant: {}", tenantId);
    }
}
