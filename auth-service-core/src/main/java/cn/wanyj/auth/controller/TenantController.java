package cn.wanyj.auth.controller;

import cn.wanyj.auth.dto.request.TenantCreateRequest;
import cn.wanyj.auth.dto.request.TenantUpdateRequest;
import cn.wanyj.auth.dto.response.TenantResponse;
import cn.wanyj.auth.entity.Tenant;
import cn.wanyj.auth.exception.ApiResponse;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.mapper.TenantMapper;
import cn.wanyj.auth.service.TenantService;
import cn.wanyj.auth.security.PreAuthorizePlatformAdmin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tenant Controller - 租户管理接口
 * 仅管理员可访问
 * @author wanyj
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;
    private final TenantMapper tenantMapper;

    /**
     * 创建租户
     * 仅管理员可访问
     */
    @PostMapping
    @PreAuthorizePlatformAdmin
    public ResponseEntity<ApiResponse<TenantResponse>> createTenant(@Valid @RequestBody TenantCreateRequest request) {
        log.info("Creating tenant: {}", request.getTenantCode());

        Tenant tenant = Tenant.builder()
                .tenantCode(request.getTenantCode())
                .tenantName(request.getTenantName())
                .status(request.getStatus() != null ? request.getStatus() : 1)
                .expiredAt(request.getExpiredAt())
                .maxUsers(request.getMaxUsers() != null ? request.getMaxUsers() : Integer.MAX_VALUE)
                .isPlatform(false)  // 通过API创建的租户都不是平台租户
                .build();

        Tenant created = tenantService.createTenant(tenant);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "租户创建成功", mapToResponse(created)));
    }

    /**
     * 更新租户
     * 仅管理员可访问
     */
    @PutMapping("/{id}")
    @PreAuthorizePlatformAdmin
    public ResponseEntity<ApiResponse<TenantResponse>> updateTenant(
            @PathVariable Long id,
            @Valid @RequestBody TenantUpdateRequest request) {

        log.info("Updating tenant: {}", id);

        Tenant tenant = tenantService.getTenantById(id);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND);
        }

        // Update fields
        if (request.getTenantName() != null) {
            tenant.setTenantName(request.getTenantName());
        }
        if (request.getStatus() != null) {
            tenant.setStatus(request.getStatus());
        }
        if (request.getExpiredAt() != null) {
            tenant.setExpiredAt(request.getExpiredAt());
        }
        if (request.getMaxUsers() != null) {
            tenant.setMaxUsers(request.getMaxUsers());
        }
        tenant.setId(id);

        Tenant updated = tenantService.updateTenant(tenant);
        return ResponseEntity.ok(ApiResponse.success(200, "租户更新成功", mapToResponse(updated)));
    }

    /**
     * 获取租户详情
     * 仅管理员可访问
     */
    @GetMapping("/{id}")
    @PreAuthorizePlatformAdmin
    public ResponseEntity<ApiResponse<TenantResponse>> getTenant(@PathVariable Long id) {
        log.info("Getting tenant: {}", id);

        Tenant tenant = tenantService.getTenantById(id);
        if (tenant == null) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND);
        }

        return ResponseEntity.ok(ApiResponse.success(200, "成功", mapToResponse(tenant)));
    }

    /**
     * 获取所有租户列表
     * 仅管理员可访问
     */
    @GetMapping
    @PreAuthorizePlatformAdmin
    public ResponseEntity<ApiResponse<List<TenantResponse>>> listTenants() {
        log.info("Listing all tenants");

        List<cn.wanyj.auth.entity.Tenant> tenants = tenantService.getAllTenants();
        List<TenantResponse> responses = tenants.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(200, "成功", responses));
    }

    /**
     * 删除租户
     * 仅管理员可访问
     */
    @DeleteMapping("/{id}")
    @PreAuthorizePlatformAdmin
    public ResponseEntity<ApiResponse<Void>> deleteTenant(@PathVariable Long id) {
        log.info("Deleting tenant: {}", id);

        // 不允许删除平台租户（id=0）
        if (id.equals(0L)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不允许删除平台租户");
        }

        tenantService.deleteTenant(id);
        return ResponseEntity.ok(ApiResponse.success(200, "租户删除成功", null));
    }

    /**
     * 检查租户编码是否可用
     */
    @GetMapping("/check-code")
    public ResponseEntity<ApiResponse<Boolean>> checkCodeAvailable(@RequestParam String code) {
        boolean available = !tenantService.existsByCode(code);
        return ResponseEntity.ok(ApiResponse.success(200, "成功", available));
    }

    /**
     * 获取可用的租户列表（公开接口，用于登录页面）
     * 只返回激活状态的租户
     */
    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<TenantResponse>>> getAvailableTenants() {
        log.info("Getting available tenants for login");

        List<cn.wanyj.auth.entity.Tenant> tenants = tenantService.getActiveTenants();
        List<TenantResponse> responses = tenants.stream()
                .map(this::mapToSimpleResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(200, "成功", responses));
    }

    /**
     * 映射 Tenant 到简单的 TenantResponse（用于登录页面）
     */
    private TenantResponse mapToSimpleResponse(cn.wanyj.auth.entity.Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .tenantCode(tenant.getTenantCode())
                .tenantName(tenant.getTenantName())
                .status(tenant.getStatus())
                .build();
    }

    /**
     * 映射 Tenant 到 TenantResponse
     */
    private TenantResponse mapToResponse(cn.wanyj.auth.entity.Tenant tenant) {
        long userCount = tenantMapper.countUsersByTenantId(tenant.getId());

        return TenantResponse.builder()
                .id(tenant.getId())
                .tenantCode(tenant.getTenantCode())
                .tenantName(tenant.getTenantName())
                .status(tenant.getStatus())
                .expiredAt(tenant.getExpiredAt())
                .maxUsers(tenant.getMaxUsers())
                .currentUserCount(userCount)
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }
}
