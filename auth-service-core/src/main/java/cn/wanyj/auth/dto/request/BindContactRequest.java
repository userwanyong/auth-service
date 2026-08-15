package cn.wanyj.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 绑定/换绑邮箱或手机号请求（验证码验证后落库）
 *
 * @author wanyj
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BindContactRequest {

    /**
     * 登录方式：email:aliyun / email:smtp（绑定邮箱）/ sms:aliyun（绑定手机），须与发送验证码时的 method 一致
     */
    @NotBlank(message = "登录方式不能为空")
    private String method;

    /**
     * 接收目标：邮箱地址 或 手机号
     */
    @NotBlank(message = "目标不能为空")
    private String target;

    /**
     * 验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String code;
}
