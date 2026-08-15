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
import cn.wanyj.auth.service.CodeService;
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
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    // ==================== 运行时判定 ====================

    @Override
    public boolean isEnabled(Long tenantId, String method) {
        if (method == null) {
            return false;
        }
        // 平台租户（tenant_id=0）是系统管理租户，仅允许账号密码登录，其他方式一律禁用
        if (tenantId != null && tenantId.equals(0L) && !LoginMethod.PASSWORD.getCode().equals(method)) {
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
                    // password 内置于系统、无需外部凭证，视为已配置
                    .hasConfig(isPassword || hasCipher(row))
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
        String cipher = isNonEmpty(request.getConfigJson())
                ? cryptoUtils.encrypt(mergeConfigJson(PLATFORM_TENANT_ID, method, request.getConfigJson()))
                : null;
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
                    // password 内置于系统、无需外部凭证，视为已配置
                    .hasConfig(isPassword || effectiveHasConfig)
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
        // 仅当租户选择用自己的凭证时才保存 configJson（与已有明文按键合并）
        String cipher = (usePlatform == 0 && isNonEmpty(request.getConfigJson()))
                ? cryptoUtils.encrypt(mergeConfigJson(tenantId, method, request.getConfigJson())) : null;
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

    /**
     * 按键合并配置：传入 JSON 的非空键覆盖旧值，未出现的键沿用旧值。
     * <p>支持部分更新（如只改邮件模板 subject/template 而不动已脱敏的 AK 凭证），
     * 避免整组覆盖误清凭证。旧值缺失或解析失败时直接使用传入 JSON。</p>
     */
    private String mergeConfigJson(Long tenantId, String method, String incomingJson) {
        try {
            java.util.Map<String, String> incoming = objectMapper.readValue(
                    incomingJson,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});
            validateCodeTtlMinutes(incoming);
            LoginMethodConfig existing = mapper.findByTenantAndMethod(tenantId, method);
            if (existing == null || !isNonEmpty(existing.getConfigJson())) {
                return incomingJson;
            }
            java.util.Map<String, String> merged = objectMapper.readValue(
                    cryptoUtils.decrypt(existing.getConfigJson()),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});
            incoming.forEach((k, v) -> {
                if (isNonEmpty(v)) {
                    merged.put(k, v); // 空串视为未提供，不覆盖旧值
                }
            });
            return objectMapper.writeValueAsString(merged);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Merge configJson failed, fallback to incoming: tenant={}, method={}", tenantId, method, e);
            return incomingJson;
        }
    }

    /**
     * 校验 codeTtlMinutes（验证码有效期，分钟）：传了该键则必须是 1~30 的整数，
     * 保存时早失败，避免管理员误配后运行时静默回退默认值而无人察觉
     */
    private void validateCodeTtlMinutes(java.util.Map<String, String> incoming) {
        String ttl = incoming.get("codeTtlMinutes");
        if (ttl == null || ttl.isBlank()) {
            return;
        }
        try {
            long value = Long.parseLong(ttl.trim());
            if (value < CodeService.CODE_TTL_MIN || value > CodeService.CODE_TTL_MAX) {
                throw new NumberFormatException("out of range");
            }
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID,
                    "codeTtlMinutes 须为 " + CodeService.CODE_TTL_MIN + "~"
                            + CodeService.CODE_TTL_MAX + " 的整数（分钟）");
        }
    }

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
