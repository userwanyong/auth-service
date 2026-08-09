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
 * configJson: {accessKeyId, accessKeySecret, accountName, fromAlias, region}
 *
 * @author wanyj
 * @since 1.0.0
 */
@Slf4j
@Service
public class AliyunMailSender implements MailSender {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void send(String to, String code, String configJson) {
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
            request.setToAddress(to);
            request.setSubject("登录验证码");
            request.setHtmlBody("<p>您的登录验证码是：<strong style=\"font-size:20px\">" + code + "</strong>，5 分钟内有效。</p>");

            client.getAcsResponse(request);
            log.info("Verification email sent to {}", to);
        } catch (ClientException e) {
            log.error("Aliyun DirectMail send failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "邮件发送失败: " + e.getMessage());
        }
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
