package cn.wanyj.auth.service;

/**
 * 验证码服务（生成、存储、校验）
 * <p>
 * 验证码存 Redis（带租户+方式+目标隔离），校验成功即删除（一次性）。
 *
 * @author wanyj
 * @since 1.0.0
 */
public interface CodeService {

    /** 登录方式配置 codeTtlMinutes 允许的最小值（分钟） */
    long CODE_TTL_MIN = 1;
    /** 登录方式配置 codeTtlMinutes 允许的最大值（分钟） */
    long CODE_TTL_MAX = 30;

    /**
     * 生成 6 位数字验证码并存入 Redis，返回明文验证码（由调用方负责发送）
     *
     * @param ttlMinutes 有效期（分钟），取自登录方式配置 codeTtlMinutes（已按范围收敛）
     */
    String generateAndStore(Long tenantId, String method, String target, long ttlMinutes);

    /**
     * 默认验证码有效期（分钟）：登录方式配置未提供 codeTtlMinutes 时的取值，
     * 亦用于渲染邮件模板 {minutes} 占位符的兜底值
     */
    long getTtlMinutes();

    /**
     * 校验验证码：匹配则删除（一次性）并返回 true；不匹配或已过期返回 false
     */
    boolean verify(Long tenantId, String method, String target, String code);
}
