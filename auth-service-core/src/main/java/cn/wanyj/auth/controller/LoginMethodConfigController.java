package cn.wanyj.auth.controller;

import cn.wanyj.auth.dto.request.LoginMethodConfigSaveRequest;
import cn.wanyj.auth.dto.response.LoginMethodConfigVO;
import cn.wanyj.auth.exception.ApiResponse;
import cn.wanyj.auth.security.PreAuthorizePlatformAdmin;
import cn.wanyj.auth.security.SecurityUtils;
import cn.wanyj.auth.service.LoginMethodConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 登录方式配置接口（平台级 / 租户级 / 公开三层）
 *
 * @author wanyj
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class LoginMethodConfigController {

    private final LoginMethodConfigService loginMethodConfigService;

    // ==================== 平台级（仅平台管理员） ====================

    /**
     * 列出所有登录方式及其平台级开关与默认凭证配置状态
     */
    @GetMapping("/api/platform/login-methods")
    @PreAuthorizePlatformAdmin
    public ResponseEntity<ApiResponse<List<LoginMethodConfigVO>>> listPlatformConfigs() {
        return ResponseEntity.ok(ApiResponse.success(loginMethodConfigService.listPlatformConfigs()));
    }

    /**
     * 保存平台级开关与默认凭证（password 不可关闭）
     */
    @PutMapping("/api/platform/login-methods/{method}")
    @PreAuthorizePlatformAdmin
    public ResponseEntity<ApiResponse<Void>> savePlatformConfig(
            @PathVariable String method,
            @Valid @RequestBody LoginMethodConfigSaveRequest request) {
        log.info("Platform login-method config update: method={}, enabled={}", method, request.getEnabled());
        loginMethodConfigService.savePlatformConfig(method, request);
        return ResponseEntity.ok(ApiResponse.success(200, "平台登录方式配置已保存", null));
    }

    // ==================== 租户级（租户管理员，限本租户） ====================

    /**
     * 列出本租户可配置的登录方式（平台已开启的子集）及本租户开关与凭证来源
     */
    @GetMapping("/api/tenant/login-methods")
    @PreAuthorize("hasAnyRole('ADMIN','PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<List<LoginMethodConfigVO>>> listTenantConfigs() {
        Long tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(ApiResponse.success(loginMethodConfigService.listTenantConfigs(tenantId)));
    }

    /**
     * 保存本租户开关与凭证来源（method 必须平台已开启）
     */
    @PutMapping("/api/tenant/login-methods/{method}")
    @PreAuthorize("hasAnyRole('ADMIN','PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> saveTenantConfig(
            @PathVariable String method,
            @Valid @RequestBody LoginMethodConfigSaveRequest request) {
        Long tenantId = SecurityUtils.getCurrentTenantId();
        log.info("Tenant login-method config update: tenantId={}, method={}, enabled={}",
                tenantId, method, request.getEnabled());
        loginMethodConfigService.saveTenantConfig(tenantId, method, request);
        return ResponseEntity.ok(ApiResponse.success(200, "租户登录方式配置已保存", null));
    }

    // ==================== 公开（登录页发现可用登录方式） ====================

    /**
     * 按对外租户标识返回该租户对用户开放的登录方式列表
     * 仅返回 method 名（不含任何凭证），供登录页动态渲染
     */
    @GetMapping("/api/auth/login-methods")
    public ResponseEntity<ApiResponse<List<String>>> listEnabledMethods(
            @RequestParam(name = "tenantUid", required = false) String tenantUid) {
        return ResponseEntity.ok(ApiResponse.success(loginMethodConfigService.listEnabledMethodsByTenantUid(tenantUid)));
    }
}
