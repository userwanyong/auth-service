package cn.wanyj.auth.service.sender.impl;

import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.service.sender.MailSender;
import cn.wanyj.auth.service.sender.MailTemplateUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

/**
 * 通用 SMTP 邮件发送实现（兼容 QQ / 163 / 企业邮箱等任意 SMTP 服务）
 * <p>
 * configJson: {host, port, username, password, from, fromAlias, encryption,
 *              codeTtlMinutes, subject, template}
 * <ul>
 *   <li>host/username/password 必填（password 为邮箱授权码或登录密码）</li>
 *   <li>encryption：ssl（默认，端口默认 465）/ starttls（端口默认 587）/ none（端口默认 25）</li>
 *   <li>from 未配置时默认取 username；fromAlias 默认 "Auth Service"</li>
 * </ul>
 * 凭证按租户动态（configJson 运行时解密），故不使用 Spring 全局 JavaMailSender，
 * 每次发送构建 Session（与 AliyunMailSender 每次 new client 同模式）。
 * 主题/正文渲染规则见 {@link MailTemplateUtils}。
 *
 * @author wanyj
 * @since 1.0.0
 */
@Slf4j
@Service
public class SmtpMailSender implements MailSender {

    /** SMTP 网络超时（毫秒），避免发码请求被慢速 SMTP 服务挂死 */
    private static final String TIMEOUT_MS = "10000";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getVendor() {
        return "smtp";
    }

    @Override
    public void send(String to, String code, int ttlMinutes, String configJson) {
        Map<String, String> cfg = parseConfig(configJson);
        String host = cfg.get("host");
        String username = cfg.get("username");
        String password = cfg.get("password");

        if (isBlank(host) || isBlank(username) || isBlank(password)) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID,
                    "邮件凭证配置不完整（需 host/username/password）");
        }
        String encryption = resolveEncryption(cfg.get("encryption"));
        int port = resolvePort(cfg.get("port"), encryption);
        String from = cfg.getOrDefault("from", username);
        String fromAlias = cfg.getOrDefault("fromAlias", "Auth Service");

        try {
            Session session = Session.getInstance(buildProperties(host, port, encryption),
                    new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(username, password);
                        }
                    });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from, fromAlias, StandardCharsets.UTF_8.name()));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(MailTemplateUtils.resolveSubject(cfg.get("subject")),
                    StandardCharsets.UTF_8.name());
            message.setContent(MailTemplateUtils.renderBody(cfg.get("template"), code, ttlMinutes),
                    "text/html;charset=UTF-8");
            message.setSentDate(new Date());

            Transport.send(message);
            log.info("Verification email sent to {} via SMTP {}", to, host);
        } catch (MessagingException e) {
            log.error("SMTP send failed: host={}, to={}, error={}", host, to, e.getMessage());
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "邮件发送失败: " + e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 恒被 JVM 支持，理论不可达
            throw new IllegalStateException(e);
        }
    }

    /**
     * 规范化加密方式：缺失/未识别的值回退 ssl（国内邮箱绝大多数为 465 SSL），
     * 回退不阻断发码，与 codeTtlMinutes 的容错语义一致
     */
    static String resolveEncryption(String raw) {
        if (raw != null) {
            String normalized = raw.trim().toLowerCase();
            if ("ssl".equals(normalized) || "starttls".equals(normalized)
                    || "none".equals(normalized)) {
                return normalized;
            }
        }
        return "ssl";
    }

    /** 端口：未配置时按加密方式取默认（ssl=465 / starttls=587 / none=25），非法值早失败 */
    static int resolvePort(String raw, String encryption) {
        if (raw == null || raw.isBlank()) {
            return switch (encryption) {
                case "starttls" -> 587;
                case "none" -> 25;
                default -> 465;
            };
        }
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("out of range");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID,
                    "SMTP 端口配置非法（port=" + raw + "）");
        }
    }

    private static Properties buildProperties(String host, int port, String encryption) {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        props.put("mail.smtp.timeout", TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MS);
        switch (encryption) {
            case "ssl" -> props.put("mail.smtp.ssl.enable", "true");
            case "starttls" -> {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
            default -> { /* none：明文（仅限内网中继场景） */ }
        }
        return props;
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
