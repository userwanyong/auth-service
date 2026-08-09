package cn.wanyj.auth.service.sender.impl;

import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.service.sender.SmsSender;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsRequest;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 阿里云短信发送实现
 * <p>
 * configJson: {accessKeyId, accessKeySecret, signName, templateCode, region}
 * 短信模板需包含 ${code} 变量。
 *
 * @author wanyj
 * @since 1.0.0
 */
@Slf4j
@Service
public class AliyunSmsSender implements SmsSender {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void send(String phone, String code, String configJson) {
        Map<String, String> cfg = parseConfig(configJson);
        String accessKeyId = cfg.get("accessKeyId");
        String accessKeySecret = cfg.get("accessKeySecret");
        String signName = cfg.get("signName");
        String templateCode = cfg.get("templateCode");
        String region = cfg.getOrDefault("region", "cn-hangzhou");

        if (isBlank(accessKeyId) || isBlank(accessKeySecret) || isBlank(signName) || isBlank(templateCode)) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "短信凭证配置不完整（需 accessKeyId/accessKeySecret/signName/templateCode）");
        }

        try {
            DefaultProfile profile = DefaultProfile.getProfile(region, accessKeyId, accessKeySecret);
            IAcsClient client = new DefaultAcsClient(profile);

            SendSmsRequest request = new SendSmsRequest();
            request.setPhoneNumbers(phone);
            request.setSignName(signName);
            request.setTemplateCode(templateCode);
            request.setTemplateParam("{\"code\":\"" + code + "\"}");

            SendSmsResponse response = client.getAcsResponse(request);
            if (!"OK".equals(response.getCode())) {
                log.error("Aliyun SMS send failed: code={}, message={}", response.getCode(), response.getMessage());
                throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "短信发送失败: " + response.getMessage());
            }
            log.info("Verification SMS sent to {}", phone);
        } catch (ClientException e) {
            log.error("Aliyun SMS send failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "短信发送失败: " + e.getMessage());
        }
    }

    private Map<String, String> parseConfig(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_CONFIG_INVALID, "短信凭证 JSON 解析失败");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
