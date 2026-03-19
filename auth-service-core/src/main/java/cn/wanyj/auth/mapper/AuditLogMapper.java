package cn.wanyj.auth.mapper;

import cn.wanyj.auth.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * Audit Log Mapper - 审计日志Mapper接口
 * @author wanyj
 */
@Mapper
public interface AuditLogMapper {

    void insert(AuditLog auditLog);
}
