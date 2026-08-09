package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.dto.request.LoginMethodConfigSaveRequest;
import cn.wanyj.auth.dto.response.LoginMethodConfigVO;
import cn.wanyj.auth.entity.LoginMethod;
import cn.wanyj.auth.entity.LoginMethodConfig;
import cn.wanyj.auth.entity.Tenant;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.mapper.LoginMethodConfigMapper;
import cn.wanyj.auth.security.CryptoUtils;
import cn.wanyj.auth.service.LoginMethodConfigService;
import cn.wanyj.auth.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 登录方式配置服务实现
 *
 * @author wanyj
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginMethodConfigServiceImpl implements LoginMethodConfigService {

    /** 平台级配置所在的 tenant_id */
    private static final Long PLATFORM_TENANT_ID = 0L;

    private final LoginMethodConfigMapper mapper;
    private final CryptoUtils cryptoUtils;
    private final TenantService tenantService;

    // ==================== 运行时判定 ====================

    @Override
    public boolean isEnabled(Long tenantId, String method) {
        if (method == null) {
            return false;
        }
        // 平台级开关（password 平台恒开）
        boolean platformEnabled = LoginMethod.PASSWORD.getCode().equals(method) || isPlatformRowEnabled(method);
        if (!platformEnabled) {
            return false;
        }
        // 租户级开关
        LoginMethodConfig tenantRow = mapper.findByTenantAndMethod(tenantId, method);
        if (tenantRow == null) {
            // 无租户记录：密码默认开，其他默认关
            return LoginMethod.PASSWORD.getCode().equals(method);
        }
        return tenantRow.getEnabled() != null && tenantRow.getEnabled() == 1;
    }

    @Override
    public String getEffectiveConfig(Long tenantId, String method) {
        LoginMethodConfig tenantRow = mapper.findByTenantAndMethod(tenantId, method);
        // 租户选择用自己的凭证且已配置
        if (tenantRow != null
                && tenantRow.getUsePlatformConfig() != null
                && tenantRow.getUsePlatformConfig() == 0
                && isNonEmpty(tenantRow.getConfigJson())) {
            return cryptoUtils.decrypt(tenantRow.getConfigJson());
        }
        // 否则用平台默认凭证
        LoginMethodConfig platformRow = mapper.findByTenantAndMethod(PLATFORM_TENANT_ID, method);
        if (platformRow == null || !isNonEmpty(platformRow.getConfigJson())) {
            return null;
        }
        return cryptoUtils.decrypt(platformRow.getConfigJson());
    }

    // ==================== 平台级 CRUD ====================

    @Override
    public List<LoginMethodConfigVO> listPlatformConfigs() {
        Map<String, LoginMethodConfig> rowMap = rowsToMap(PLATFORM_TENANT_ID);
        List<LoginMethodConfigVO> list = new ArrayList<>();
        for (LoginMethod dm : LoginMethod.values()) {
            LoginMethodConfig row = rowMap.get(dm.getCode());
            boolean isPassword = dm == LoginMethod.PASSWORD;
            int enabled = (row != null && row.getEnabled() != null) ? row.getEnabled() : (isPassword ? 1 : 0);
            list.add(LoginMethodConfigVO.builder()
                    .method(dm.getCode())
                    .category(dm.getCategory())
                    .displayName(dm.getDisplayName())
                    .enabled(enabled)
                    .hasConfig(hasCipher(row))
                    .platformLocked(isPassword)
                    .build());
        }
        return list;
    }

    @Override
    @Transactional
    public void savePlatformConfig(String method, LoginMethodConfigSaveRequest request) {
        LoginMethod.requireSupported(method);
        if (LoginMethod.PASSWORD.getCode().equals(method)
                && (request.getEnabled() == null || request.getEnabled() == 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "password 平台级不可关闭，避免锁死");
        }
        int enabled = request.getEnabled() != null ? request.getEnabled() : 0;
        String cipher = isNonEmpty(request.getConfigJson()) ? cryptoUtils.encrypt(request.getConfigJson()) : null;
        upsert(PLATFORM_TENANT_ID, method, enabled, 1, cipher);
    }

    // ==================== 租户级 CRUD ====================

    @Override
    public List<LoginMethodConfigVO> listTenantConfigs(Long tenantId) {
        Map<String, LoginMethodConfig> platformMap = rowsToMap(PLATFORM_TENANT_ID);
        Map<String, LoginMethodConfig> tenantMap = rowsToMap(tenantId);
        List<LoginMethodConfigVO> list = new ArrayList<>();
        for (LoginMethod dm : LoginMethod.values()) {
            boolean isPassword = dm == LoginMethod.PASSWORD;
            LoginMethodConfig platformRow = platformMap.get(dm.getCode());
            boolean platformEnabled = isPassword || (platformRow != null && platformRow.getEnabled() != null && platformRow.getEnabled() == 1);
            if (!platformEnabled) {
                continue; // 平台未开启，租户不可配也不展示
            }
            LoginMethodConfig tenantRow = tenantMap.get(dm.getCode());
            int tenantEnabled = (tenantRow != null && tenantRow.getEnabled() != null)
                    ? tenantRow.getEnabled() : (isPassword ? 1 : 0);
            int usePlatform = (tenantRow != null && tenantRow.getUsePlatformConfig() != null)
                    ? tenantRow.getUsePlatformConfig() : 1;
            boolean hasPlatformConfig = hasCipher(platformRow);
            boolean hasOwnConfig = hasCipher(tenantRow);
            boolean effectiveHasConfig = (usePlatform == 1) ? hasPlatformConfig : hasOwnConfig;
            list.add(LoginMethodConfigVO.builder()
                    .method(dm.getCode())
                    .category(dm.getCategory())
                    .displayName(dm.getDisplayName())
                    .enabled(tenantEnabled)
                    .usePlatformConfig(usePlatform)
                    .hasConfig(effectiveHasConfig)
                    .platformEnabled(true)
                    .build());
        }
        return list;
    }

    @Override
    @Transactional
    public void saveTenantConfig(Long tenantId, String method, LoginMethodConfigSaveRequest request) {
        LoginMethod.requireSupported(method);
        // 校验平台已开启该方式（平台未开则租户不可配）
        boolean platformEnabled = LoginMethod.PASSWORD.getCode().equals(method) || isPlatformRowEnabled(method);
        if (!platformEnabled) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_DISABLED, "平台未开启该登录方式");
        }
        int enabled = request.getEnabled() != null ? request.getEnabled() : 0;
        int usePlatform = request.getUsePlatformConfig() != null ? request.getUsePlatformConfig() : 1;
        // 仅当租户选择用自己的凭证时才保存 configJson
        String cipher = (usePlatform == 0 && isNonEmpty(request.getConfigJson()))
                ? cryptoUtils.encrypt(request.getConfigJson()) : null;
        upsert(tenantId, method, enabled, usePlatform, cipher);
    }

    // ==================== 公开（登录页） ====================

    @Override
    public List<String> listEnabledMethodsByTenantUid(String tenantUid) {
        if (tenantUid == null || tenantUid.isBlank()) {
            return Collections.emptyList();
        }
        Tenant tenant = tenantService.getTenantByUid(tenantUid);
        if (tenant == null || !tenant.isValid()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (LoginMethod dm : LoginMethod.values()) {
            if (isEnabled(tenant.getId(), dm.getCode())) {
                result.add(dm.getCode());
            }
        }
        return result;
    }

    // ==================== 内部辅助 ====================

    private boolean isPlatformRowEnabled(String method) {
        LoginMethodConfig row = mapper.findByTenantAndMethod(PLATFORM_TENANT_ID, method);
        return row != null && row.getEnabled() != null && row.getEnabled() == 1;
    }

    private void upsert(Long tenantId, String method, int enabled, int usePlatform, String cipher) {
        LoginMethodConfig existing = mapper.findByTenantAndMethod(tenantId, method);
        if (existing == null) {
            mapper.insert(LoginMethodConfig.builder()
                    .tenantId(tenantId)
                    .method(method)
                    .enabled(enabled)
                    .usePlatformConfig(usePlatform)
                    .configJson(cipher)
                    .build());
        } else {
            existing.setEnabled(enabled);
            existing.setUsePlatformConfig(usePlatform);
            if (cipher != null) {
                existing.setConfigJson(cipher); // 传了凭证才覆盖，否则保留原值
            }
            mapper.update(existing);
        }
    }

    private Map<String, LoginMethodConfig> rowsToMap(Long tenantId) {
        return mapper.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(LoginMethodConfig::getMethod, c -> c));
    }

    private static boolean isNonEmpty(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean hasCipher(LoginMethodConfig row) {
        return row != null && row.getConfigJson() != null && !row.getConfigJson().isEmpty();
    }
}
