package cn.wanyj.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Audit Log Entity - 审计日志实体
 * @author wanyj
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    private Long id;
    private Long tenantId;
    private Long userId;
    private String username;
    private String action;
    private String resource;
    private String detail;
    private String ipAddress;
    private LocalDateTime createdAt;
}
