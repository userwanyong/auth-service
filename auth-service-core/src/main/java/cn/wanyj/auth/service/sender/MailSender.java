package cn.wanyj.auth.service.sender;

/**
 * 邮件发送接口（验证码）
 * <p>
 * configJson 由 LoginMethodConfigService.getEffectiveConfig 提供（已解密，平台级/租户级
 * 按 usePlatformConfig 取生效侧），结构因实现而异：
 *
 * <p>AliyunMailSender（vendor = aliyun，method = email:aliyun）:
 * <pre>
 * {
 *   "accessKeyId":     "...",  // 必填
 *   "accessKeySecret": "...",  // 必填
 *   "accountName":     "...",  // 必填（发信地址）
 *   "fromAlias":       "...",  // 可选，默认 "Auth Service"
 *   "region":          "...",  // 可选，默认 "cn-hangzhou"
 *   "codeTtlMinutes":  "10",   // 可选，验证码有效期（分钟，1~30），默认 5；
 *   "subject":         "...",  // 可选，邮件主题，默认 "登录验证码"
 *   "template":        "..."   // 可选，HTML 正文模板，支持 {code}/{minutes} 占位符
 * }
 * </pre>
 *
 * <p>SmtpMailSender（vendor = smtp，method = email:smtp）:
 * <pre>
 * {
 *   "host":            "...",  // 必填（如 smtp.qq.com）
 *   "port":            "465",  // 可选，默认按 encryption 取 465/587
 *   "username":        "...",  // 必填（SMTP 认证账号）
 *   "password":        "...",  // 必填（授权码/密码）
 *   "from":            "...",  // 可选，发件地址，默认 username
 *   "fromAlias":       "...",  // 可选，发件人显示名，默认 "Auth Service"
 *   "encryption":      "...",  // 可选，ssl（默认）/ starttls / none
 *   "codeTtlMinutes":  "10",   // 可选，同上
 *   "subject":         "...",  // 可选，同上
 *   "template":        "..."   // 可选，同上
 * }
 * </pre>
 *
 * <p>codeTtlMinutes 由 AuthService 解析后经 ttlMinutes 参数传入（同时用于 Redis TTL
 * 与模板渲染）。
 *
 * @author wanyj
 * @since 1.0.0
 */
public interface MailSender {

    /**
     * 服务商标识：与 LoginMethod 的 vendor 段对应（aliyun / smtp），
     * AuthServiceImpl 按 method 的 vendor 分发到对应实现
     */
    String getVendor();

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
