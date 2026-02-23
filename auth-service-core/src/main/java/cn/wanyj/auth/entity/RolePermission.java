package cn.wanyj.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * RolePermission Entity - 角色权限关联实体
 * @author wanyj
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermission {

    private Long id;

    private Long tenantId;

    private Long roleId;

    private Long permissionId;

    private LocalDateTime createdAt;
}
