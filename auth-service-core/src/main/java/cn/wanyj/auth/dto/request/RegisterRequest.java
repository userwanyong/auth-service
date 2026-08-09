package cn.wanyj.auth.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Register Request - 用户注册请求
 * @author wanyj
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度必须在6-50之间")
    private String password;

    /**
     * 租户ID - 必填，指定注册到哪个租户
     */
    @NotNull(message = "租户ID不能为空")
    private Long tenantId;

    // 选填字段（格式在 Service 层校验）
    @Size(max = 100, message = "邮箱长度不能超过100")
    private String email;

    // 选填字段（格式在 Service 层校验）
    @Size(max = 20, message = "手机号长度不能超过20")
    private String phone;

    // 选填字段
    @Size(max = 50, message = "昵称长度不能超过50")
    private String nickname;

    /** 真实姓名（选填） */
    @Size(max = 50, message = "真实姓名长度不能超过50")
    private String realName;

    /** 性别（选填：0-未知，1-男，2-女） */
    @Min(value = 0, message = "性别值无效")
    @Max(value = 2, message = "性别值无效")
    private Integer gender;

    /** 生日（选填，yyyy-MM-dd） */
    private java.time.LocalDate birthday;

    /** 头像URL（选填，前端上传 OSS 后回填） */
    @Size(max = 255, message = "头像URL长度不能超过255")
    private String avatar;
}
