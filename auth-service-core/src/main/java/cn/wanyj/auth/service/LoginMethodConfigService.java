package cn.wanyj.auth.service;

import cn.wanyj.auth.dto.request.LoginMethodConfigSaveRequest;
import cn.wanyj.auth.dto.response.LoginMethodConfigVO;

import java.util.List;

/**
 * 登录方式配置服务（平台级 + 租户级二级开关、混合配置解析）
 *
 * @author wanyj
 * @since 1.0.0
 */
public interface LoginMethodConfigService {

    /**
     * 运行时判定：用户能否在指定租户使用某登录方式
     * = 平台开启 AND 租户开启（password 平台恒开）
     */
    boolean isEnabled(Long tenantId, String method);

    /**
     * 取生效凭证（解密后的 JSON）。无凭证方式（password）返回 null。
     * 租户行 usePlatformConfig=0 用自身凭证，否则用平台默认凭证。
     */
    String getEffectiveConfig(Long tenantId, String method);

    // ===== 平台级（平台管理员） =====

    /** 列出所有受支持的登录方式及其平台级配置 */
    List<LoginMethodConfigVO> listPlatformConfigs();

    /** 保存平台级开关与默认凭证（password 不可关闭） */
    void savePlatformConfig(String method, LoginMethodConfigSaveRequest request);

    // ===== 租户级（租户管理员，限本租户） =====

    /** 列出平台已开启的方式，以及本租户的开关与凭证来源 */
    List<LoginMethodConfigVO> listTenantConfigs(Long tenantId);

    /** 保存本租户开关与凭证来源（method 必须平台已开启） */
    void saveTenantConfig(Long tenantId, String method, LoginMethodConfigSaveRequest request);

    // ===== 公开（登录页） =====

    /** 按对外租户标识返回该租户对用户开放的登录方式列表（仅 method 名，不含凭证） */
    List<String> listEnabledMethodsByTenantUid(String tenantUid);
}
