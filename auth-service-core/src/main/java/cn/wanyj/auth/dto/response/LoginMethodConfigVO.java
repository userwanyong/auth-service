package cn.wanyj.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录方式配置响应（凭证脱敏，仅返回是否已配置）
 *
 * @author wanyj
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginMethodConfigVO {

    /**
     * 登录方式 code，如 oauth:gitee
     */
    private String method;

    /**
     * 类别：password / email / sms / oauth
     */
    private String category;

    /**
     * 中文显示名
     */
    private String displayName;

    /**
     * 当前层级是否启用：平台级=平台总开关；租户级=本租户开关
     */
    private Integer enabled;

    /**
     * 仅租户级：1=使用平台默认凭证，0=使用自身凭证
     */
    private Integer usePlatformConfig;

    /**
     * 生效凭证是否已配置（不返回真实凭证内容）
     */
    private Boolean hasConfig;

    /**
     * 仅租户级：平台是否已开启该方式（平台未开则租户不可配）
     */
    private Boolean platformEnabled;

    /**
     * 仅平台级：是否为平台锁定不可关闭（password）
     */
    private Boolean platformLocked;
}
