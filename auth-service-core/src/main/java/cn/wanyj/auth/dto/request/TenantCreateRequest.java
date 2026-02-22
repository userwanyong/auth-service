package cn.wanyj.auth.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tenant Create Request - 租户创建请求
 * @author wanyj
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantCreateRequest {

    /**
     * 租户编码（唯一标识）
     */
    @NotBlank(message = "租户编码不能为空")
    @Size(min = 2, max = 50, message = "租户编码长度必须在2-50之间")
    private String tenantCode;

    /**
     * 租户名称
     */
    @NotBlank(message = "租户名称不能为空")
    @Size(max = 100, message = "租户名称长度不能超过100")
    private String tenantName;

    /**
     * 状态：0-禁用，1-正常
     */
    @Min(value = 0, message = "状态值不合法")
    @Max(value = 1, message = "状态值不合法")
    private Integer status;

    /**
     * 过期时间（NULL表示永不过期）
     */
    private LocalDateTime expiredAt;

    /**
     * 最大用户数限制（NULL表示无限制）
     */
    @Min(value = 1, message = "最大用户数必须大于0")
    @Max(value = 2147483647, message = "最大用户数不能超过2147483647")
    private Integer maxUsers;
}
