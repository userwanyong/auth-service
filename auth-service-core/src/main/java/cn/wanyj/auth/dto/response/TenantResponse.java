package cn.wanyj.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tenant Response - 租户响应
 * @author wanyj
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {

    /**
     * 租户ID
     */
    private Long id;

    /**
     * 租户编码
     */
    private String tenantCode;

    /**
     * 租户名称
     */
    private String tenantName;

    /**
     * 状态：0-禁用，1-正常
     */
    private Integer status;

    /**
     * 过期时间
     */
    private LocalDateTime expiredAt;

    /**
     * 最大用户数限制（NULL表示无限制）
     */
    private Integer maxUsers;

    /**
     * 当前用户数量
     */
    private Long currentUserCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
