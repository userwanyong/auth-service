package cn.wanyj.auth.service.sender;

/**
 * 短信发送接口（验证码）
 * <p>
 * configJson 由 LoginMethodConfigService.getEffectiveConfig 提供（已解密），
 * 结构因实现而异（AliyunSmsSender: accessKeyId/accessKeySecret/signName/templateCode/region）。
 *
 * @author wanyj
 * @since 1.0.0
 */
public interface SmsSender {

    /**
     * 发送验证码短信
     *
     * @param phone      手机号
     * @param code       验证码明文
     * @param configJson 已解密的服务商凭证 JSON
     */
    void send(String phone, String code, String configJson);
}
