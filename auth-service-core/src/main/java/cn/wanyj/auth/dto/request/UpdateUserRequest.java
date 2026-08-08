package cn.wanyj.auth.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户资料请求（部分更新语义）
 * <p>所有字段均可空：未提供（null）表示不修改；空字符串表示清空（如 email="" 清空邮箱）。
 * 字段长度上限在此校验；email/phone 的格式校验在 Service 层对非空值执行
 * （见 {@code UserFieldValidator}），以兼容「空串=清空」的语义。</p>
 *
 * @author wanyj
 */
@Data
public class UpdateUserRequest {

    @Size(max = 50, message = "用户名长度不能超过50")
    private String username;

    @Size(max = 50, message = "密码长度不能超过50")
    private String password;

    @Size(max = 100, message = "邮箱长度不能超过100")
    private String email;

    @Size(max = 20, message = "手机号长度不能超过20")
    private String phone;

    @Size(max = 50, message = "昵称长度不能超过50")
    private String nickname;

    @Size(max = 255, message = "头像URL长度不能超过255")
    private String avatar;

    @Min(value = 0, message = "状态值无效")
    @Max(value = 1, message = "状态值无效")
    private Integer status;
}
