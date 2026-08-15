package cn.wanyj.auth.service.sender;

/**
 * 邮件发送接口（验证码）
 * <p>
 * configJson 由 LoginMethodConfigService.getEffectiveConfig 提供（已解密，平台级/租户级
 * 按 usePlatformConfig 取生效侧），结构因实现而异。AliyunMailSender:
 * <pre>
 * {
 *   "accessKeyId":     "...",  // 必填
 *   "accessKeySecret": "...",  // 必填
 *   "accountName":     "...",  // 必填（发信地址）
 *   "fromAlias":       "...",  // 可选，默认 "Auth Service"
 *   "region":          "...",  // 可选，默认 "cn-hangzhou"
 *   "codeTtlMinutes":  "10",   // 可选，验证码有效期（分钟，1~30），默认 5；
 *                              // 已由 AuthService 按此值写入 Redis 并作为 ttlMinutes 参数传入
 *   "subject":         "...",  // 可选，邮件主题，默认 "登录验证码"
 *   "template":        "..."   // 可选，HTML 正文模板，支持 {code}（验证码）与
 *                              // {minutes}（有效分钟数）占位符；须含 {code}，
 *                              // 否则回退默认文案
 * }
 * </pre>
 *
 * @author wanyj
 * @since 1.0.0
 */
public interface MailSender {

    /**
     * 发送验证码邮件
     *
     * @param to          收件人邮箱
     * @param code        验证码明文
     * @param ttlMinutes  验证码有效期（分钟），用于渲染模板 {minutes} 占位符
     * @param configJson  已解密的服务商凭证与模板 JSON
     */
    void send(String to, String code, int ttlMinutes, String configJson);
}
