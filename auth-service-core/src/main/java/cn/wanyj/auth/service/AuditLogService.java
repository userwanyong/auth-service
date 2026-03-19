package cn.wanyj.auth.service;

/**
 * Audit Log Service - 审计日志服务接口
 * @author wanyj
 */
public interface AuditLogService {

    /**
     * Log audit event synchronously
     * 同步记录审计日志
     */
    void log(Long tenantId, Long userId, String username, String action, String resource, String detail, String ipAddress);

    /**
     * Log audit event asynchronously
     * 异步记录审计日志
     */
    void logAsync(Long tenantId, Long userId, String username, String action, String resource, String detail, String ipAddress);
}
