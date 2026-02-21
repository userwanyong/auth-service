package cn.wanyj.auth.controller;

import cn.wanyj.auth.dto.request.AssignPermissionsRequest;
import cn.wanyj.auth.dto.response.RoleResponse;
import cn.wanyj.auth.exception.ApiResponse;
import cn.wanyj.auth.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Role Controller - 角色控制器
 * 处理角色管理相关操作
 * @author wanyj
 */
@Slf4j
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * Get all roles
     * 获取所有角色
     * GET /api/roles
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        log.info("Get all roles");
        List<RoleResponse> roles = roleService.getAllRoles();
        return ResponseEntity.ok(ApiResponse.success(roles));
    }

    /**
     * Get role by ID
     * 根据ID获取角色
     * GET /api/roles/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable Long id) {
        log.info("Get role by id: {}", id);
        RoleResponse role = roleService.getRoleById(id);
        return ResponseEntity.ok(ApiResponse.success(role));
    }

    /**
     * Get role by code
     * 根据编码获取角色
     * GET /api/roles/code/{code}
     */
    @GetMapping("/code/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleByCode(@PathVariable String code) {
        log.info("Get role by code: {}", code);
        RoleResponse role = roleService.getRoleByCode(code);
        return ResponseEntity.ok(ApiResponse.success(role));
    }

    /**
     * Create new role
     * 创建新角色
     * POST /api/roles
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @RequestParam String code,
            @RequestParam String name,
            @RequestParam(required = false) String description) {
        log.info("Create new role: {}", code);
        RoleResponse role = roleService.createRole(code, name, description);
        return ResponseEntity.ok(ApiResponse.success(201, "角色创建成功", role));
    }

    /**
     * Update role
     * 更新角色
     * PUT /api/roles/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description) {
        log.info("Update role: {}", id);
        RoleResponse role = roleService.updateRole(id, name, description);
        return ResponseEntity.ok(ApiResponse.success(200, "角色更新成功", role));
    }

    /**
     * Assign permissions to role
     * 为角色分配权限
     * POST /api/roles/{id}/permissions
     */
    @PostMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> assignPermissions(
            @PathVariable Long id,
            @Valid @RequestBody AssignPermissionsRequest request) {
        log.info("Assign permissions to role: {}", id);
        roleService.assignPermissions(id, request);
        return ResponseEntity.ok(ApiResponse.success(200, "权限分配成功", null));
    }

    /**
     * Delete role
     * 删除角色
     * DELETE /api/roles/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable Long id) {
        log.info("Delete role: {}", id);
        roleService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.success(200, "角色删除成功", null));
    }
}
