package cn.wanyj.auth.service.sender;

/**
 * 邮件发送接口（验证码）
 * <p>
 * configJson 由 LoginMethodConfigService.getEffectiveConfig 提供（已解密），
 * 结构因实现而异（AliyunMailSender: accessKeyId/accessKeySecret/accountName/fromAlias/region）。
 *
 * @author wanyj
 * @since 1.0.0
 */
public interface MailSender {

    /**
     * 发送验证码邮件
     *
     * @param to         收件人邮箱
     * @param code       验证码明文
     * @param configJson 已解密的服务商凭证 JSON
     */
    void send(String to, String code, String configJson);
}
