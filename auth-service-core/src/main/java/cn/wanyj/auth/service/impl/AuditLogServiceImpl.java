package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.entity.AuditLog;
import cn.wanyj.auth.mapper.AuditLogMapper;
import cn.wanyj.auth.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Audit Log Service Implementation - 审计日志服务实现
 * @author wanyj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Override
    public void log(Long tenantId, Long userId, String username, String action, String resource, String detail, String ipAddress) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .username(username)
                    .action(action)
                    .resource(resource)
                    .detail(detail)
                    .ipAddress(ipAddress)
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: action={}, user={}", action, username, e);
        }
    }

    @Override
    @Async
    public void logAsync(Long tenantId, Long userId, String username, String action, String resource, String detail, String ipAddress) {
        log(tenantId, userId, username, action, resource, detail, ipAddress);
    }
}
