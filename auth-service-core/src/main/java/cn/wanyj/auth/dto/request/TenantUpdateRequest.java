package cn.wanyj.auth.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tenant Update Request - 租户更新请求
 * @author wanyj
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUpdateRequest {

    /**
     * 租户名称
     */
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
