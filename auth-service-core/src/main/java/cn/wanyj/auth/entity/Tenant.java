package cn.wanyj.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 租户实体
 *
 * @author wanyj
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    /**
     * 租户ID
     */
    private Long id;

    /**
     * 租户编码（唯一标识）
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
     * 过期时间（NULL表示永不过期）
     */
    private LocalDateTime expiredAt;

    /**
     * 最大用户数限制
     */
    private Integer maxUsers;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 检查租户是否有效
     * 有效的条件：
     * 1. status = 1 (正常)
     * 2. expiredAt 为空 或 expiredAt > 当前时间
     *
     * @return 是否有效
     */
    public boolean isValid() {
        if (status == null || status != 1) {
            return false;
        }
        if (expiredAt != null && expiredAt.isBefore(LocalDateTime.now())) {
            return false;
        }
        return true;
    }

    /**
     * 检查是否为默认租户
     *
     * @return 是否为默认租户
     */
    public boolean isDefault() {
        return id != null && id.equals(1L);
    }
}
