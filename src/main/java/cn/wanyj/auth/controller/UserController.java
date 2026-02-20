package cn.wanyj.auth.controller;

import cn.wanyj.auth.dto.request.AssignRolesRequest;
import cn.wanyj.auth.dto.response.PageResponse;
import cn.wanyj.auth.dto.response.UserResponse;
import cn.wanyj.auth.exception.ApiResponse;
import cn.wanyj.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * User Controller - 用户控制器
 * 处理用户管理相关操作
 * @author wanyj
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Get user by ID
     * 根据ID获取用户
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        log.info("Get user by id: {}", id);
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    /**
     * Search users with pagination
     * 分页搜索用户
     * GET /api/users?page=1&size=10&keyword=test
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> searchUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        log.info("Search users: page={}, size={}, keyword={}", page, size, keyword);

        PageResponse<UserResponse> users = userService.searchUsers(keyword, page, size);

        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /**
     * Assign roles to user
     * 为用户分配角色
     * POST /api/users/{id}/roles
     */
    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> assignRoles(
            @PathVariable Long id,
            @Valid @RequestBody AssignRolesRequest request) {
        log.info("Assign roles to user: {}", id);
        userService.assignRoles(id, request);
        return ResponseEntity.ok(ApiResponse.success(200, "角色分配成功", null));
    }

    /**
     * Update user status
     * 更新用户状态
     * PUT /api/users/{id}/status
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateUserStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {
        log.info("Update user status: {}, status={}", id, status);
        userService.updateUserStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success(200, "用户状态更新成功", null));
    }

    /**
     * Delete user
     * 删除用户
     * DELETE /api/users/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        log.info("Delete user: {}", id);
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success(200, "用户删除成功", null));
    }
}
