package cn.wanyj.auth.service.sender.impl;

import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.service.sender.MailSender;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dm.model.v20151123.SingleSendMailRequest;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 阿里云 DirectMail 邮件发送实现
 * <p>
 * configJson: {accessKeyId, accessKeySecret, accountName, fromAlias, region,
 *              codeTtlMinutes, subject, template}
 * <p>
 * subject/template 为可选自定义模板：平台级与租户级均可配置
 * （LoginMethodConfig 按 usePlatformConfig 取生效侧）。template 支持
 * {code}/{minutes} 占位符，须含 {code}，否则回退默认文案。
 * codeTtlMinutes（1~30 分钟）为验证码有效期，由 AuthService 解析后经
 * ttlMinutes 参数传入（同时用于 Redis TTL 与模板渲染）。
 *
 * @author wanyj
 * @since 1.0.0
 */
@Slf4j
@Service
public class AliyunMailSender implements MailSender {

    private static final String DEFAULT_SUBJECT = "登录验证码";
    private static final String CODE_PLACEHOLDER = "{code}";
    private static final String MINUTES_PLACEHOLDER = "{minutes}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void send(String to, String code, int ttlMinutes, String configJson) {
        Map<String, String> cfg = parseConfig(configJson);
        String accessKeyId = cfg.get("accessKeyId");
        String accessKeySecret = cfg.get("accessKeySecret");
        String accountName = cfg.get("accountName");
        String fromAlias = cfg.getOrDefault("fromAlias", "Auth Service");
        String region = cfg.getOrDefault("region", "cn-hangzhou");

        if (isBlank(accessKeyId) || isBlank(accessKeySecret) || isBlank(accountName)) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "邮件凭证配置不完整（需 accessKeyId/accessKeySecret/accountName）");
        }

        try {
            DefaultProfile profile = DefaultProfile.getProfile(region, accessKeyId, accessKeySecret);
            IAcsClient client = new DefaultAcsClient(profile);

            SingleSendMailRequest request = new SingleSendMailRequest();
            request.setAccountName(accountName);
            request.setFromAlias(fromAlias);
            request.setAddressType(1);
            // DirectMail 必填参数：false=不使用回信地址（缺失会报 MissingReplyToAddress）
            request.setReplyToAddress(false);
            request.setToAddress(to);
            request.setSubject(resolveSubject(cfg.get("subject")));
            request.setHtmlBody(renderBody(cfg.get("template"), code, ttlMinutes));

            client.getAcsResponse(request);
            log.info("Verification email sent to {}", to);
        } catch (ClientException e) {
            log.error("Aliyun DirectMail send failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "邮件发送失败: " + e.getMessage());
        }
    }

    /** 邮件主题：未配置或空串回退默认 */
    static String resolveSubject(String subject) {
        return subject == null || subject.isBlank() ? DEFAULT_SUBJECT : subject.trim();
    }

    /**
     * 渲染正文：模板须含 {code} 占位符（否则用户收不到验证码），缺失/非法时回退默认文案；
     * {minutes} 替换为实际有效期。code 为系统生成的数字串，直接替换无注入风险。
     */
    static String renderBody(String template, String code, int ttlMinutes) {
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

    private Map<String, String> parseConfig(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "邮件凭证 JSON 解析失败");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
