package cn.wanyj.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * OAuth 账号绑定实体（多租户隔离）
 * <p>
 * 按 (tenantId, provider, providerUid) 匹配本地用户。
 *
 * @author wanyj
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOauth {

    private Long id;

    private Long tenantId;

    private Long userId;

    /**
     * OAuth 提供方：gitee / microsoft / github
     */
    private String provider;

    /**
     * 提供方用户唯一ID
     */
    private String providerUid;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
