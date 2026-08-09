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

    /**
     * 生成 6 位数字验证码并存入 Redis（TTL 5 分钟），返回明文验证码（由调用方负责发送）
     */
    String generateAndStore(Long tenantId, String method, String target);

    /**
     * 校验验证码：匹配则删除（一次性）并返回 true；不匹配或已过期返回 false
     */
    boolean verify(Long tenantId, String method, String target, String code);
}
