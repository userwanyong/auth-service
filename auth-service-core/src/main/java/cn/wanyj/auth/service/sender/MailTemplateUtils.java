package cn.wanyj.auth.service.sender;

/**
 * 验证码邮件模板渲染工具（邮件发送实现共用）
 * <p>
 * subject/template 为可选自定义模板：平台级与租户级均可配置
 * （LoginMethodConfig 按 usePlatformConfig 取生效侧）。template 支持
 * {code}/{minutes} 占位符，须含 {code}，否则回退默认文案。
 *
 * @author wanyj
 * @since 1.0.0
 */
public final class MailTemplateUtils {

    private static final String DEFAULT_SUBJECT = "登录验证码";
    private static final String CODE_PLACEHOLDER = "{code}";
    private static final String MINUTES_PLACEHOLDER = "{minutes}";

    private MailTemplateUtils() {
    }

    /** 邮件主题：未配置或空串回退默认 */
    public static String resolveSubject(String subject) {
        return subject == null || subject.isBlank() ? DEFAULT_SUBJECT : subject.trim();
    }

    /**
     * 渲染正文：模板须含 {code} 占位符（否则用户收不到验证码），缺失/非法时回退默认文案；
     * {minutes} 替换为实际有效期。code 为系统生成的数字串，直接替换无注入风险。
     */
    public static String renderBody(String template, String code, int ttlMinutes) {
        if (template == null || template.isBlank() || !template.contains(CODE_PLACEHOLDER)) {
            if (template != null && !template.isBlank()) {
                // 配了模板但缺 {code} 占位符：按默认文案发送，避免用户收不到验证码
                return "<p>您的验证码是：<strong style=\"font-size:20px\">" + code
                        + "</strong>，" + ttlMinutes + " 分钟内有效。</p>"
                        + "<!-- template ignored: missing {code} placeholder -->";
            }
            return "<p>您的验证码是：<strong style=\"font-size:20px\">" + code
                    + "</strong>，" + ttlMinutes + " 分钟内有效。</p>";
        }
        return template.replace(CODE_PLACEHOLDER, code)
                .replace(MINUTES_PLACEHOLDER, String.valueOf(ttlMinutes));
    }
}
