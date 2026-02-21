package cn.wanyj.auth.controller;

import cn.wanyj.auth.dto.response.PermissionResponse;
import cn.wanyj.auth.exception.ApiResponse;
import cn.wanyj.auth.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Permission Controller - 权限控制器
 * 处理权限管理相关操作
 * @author wanyj
 */
@Slf4j
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * Get all permissions
     * 获取所有权限
     * GET /api/permissions
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        log.info("Get all permissions");
        List<PermissionResponse> permissions = permissionService.getAllPermissions();
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }

    /**
     * Get permission by ID
     * 根据ID获取权限
     * GET /api/permissions/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PermissionResponse>> getPermissionById(@PathVariable Long id) {
        log.info("Get permission by id: {}", id);
        PermissionResponse permission = permissionService.getPermissionById(id);
        return ResponseEntity.ok(ApiResponse.success(permission));
    }

    /**
     * Create new permission
     * 创建新权限
     * POST /api/permissions
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(
            @RequestParam String code,
            @RequestParam String name,
            @RequestParam String resource,
            @RequestParam String action,
            @RequestParam(required = false) String description) {
        log.info("Create new permission: {}", code);
        PermissionResponse permission = permissionService.createPermission(code, name, resource, action, description);
        return ResponseEntity.ok(ApiResponse.success(201, "权限创建成功", permission));
    }

    /**
     * Delete permission
     * 删除权限
     * DELETE /api/permissions/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePermission(@PathVariable Long id) {
        log.info("Delete permission: {}", id);
        permissionService.deletePermission(id);
        return ResponseEntity.ok(ApiResponse.success(200, "权限删除成功", null));
    }
}
