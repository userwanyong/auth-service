package cn.wanyj.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送验证码请求（邮箱/手机验证码登录）
 *
 * @author wanyj
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendCodeRequest {

    @NotBlank(message = "租户标识不能为空")
    private String tenantUid;

    /**
     * 登录方式：email:aliyun / sms:aliyun
     */
    @NotBlank(message = "登录方式不能为空")
    private String method;

    /**
     * 接收目标：邮箱地址 或 手机号
     */
    @NotBlank(message = "目标不能为空")
    private String target;
}
