package cn.wanyj.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 登录方式配置实体（平台级 + 租户级二级开关）
 * <p>
 * tenant_id = 0 表示平台级默认开关与凭证；其他为租户级开关（可在平台允许范围内覆盖凭证）。
 * 用户能否使用某方式 = 平台行 enabled AND 租户行 enabled。
 *
 * @author wanyj
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginMethodConfig {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 租户ID（0=平台级默认配置）
     */
    private Long tenantId;

    /**
     * 登录方式：password / email:aliyun / sms:aliyun / oauth:gitee / oauth:microsoft / oauth:github
     */
    private String method;

    /**
     * 是否启用：0-否，1-是
     */
    private Integer enabled;

    /**
     * 仅租户行有效：1=使用平台默认凭证，0=使用自身 config_json
     */
    private Integer usePlatformConfig;

    /**
     * 该方式的凭证配置（整段 AES 加密密文，结构因 method 而异）
     */
    private String configJson;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
