package cn.wanyj.auth.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录方式配置保存请求
 * <p>
 * configJson 为明文 JSON（结构因 method 而异），后端 AES 加密后入库；
 * 传 null/空表示本次不修改凭证。
 *
 * @author wanyj
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginMethodConfigSaveRequest {

    /**
     * 是否启用：0-否，1-是
     */
    private Integer enabled;

    /**
     * 仅租户级有效：1=使用平台默认凭证，0=使用自身 configJson。默认 1
     */
    private Integer usePlatformConfig;

    /**
     * 明文凭证 JSON（结构因 method 而异）；null/空表示不修改凭证
     */
    private String configJson;
}
