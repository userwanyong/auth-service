package cn.wanyj.auth.service.sender.impl;

import cn.wanyj.auth.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SmtpMailSender 单元测试（配置解析纯函数：加密方式规范化/端口默认与校验/必填校验；
 * 真实 SMTP 发送链路不在单测范围）
 */
class SmtpMailSenderTest {

    private final SmtpMailSender sender = new SmtpMailSender();

    @Test
    void getVendor_shouldReturnSmtp() {
        assertEquals("smtp", sender.getVendor());
    }

    @Test
    void resolveEncryption_shouldFallbackToSslWhenMissingOrUnknown() {
        assertEquals("ssl", SmtpMailSender.resolveEncryption(null));
        assertEquals("ssl", SmtpMailSender.resolveEncryption("  "));
        assertEquals("ssl", SmtpMailSender.resolveEncryption("tls-typo"));
        // 已识别值规范化（大小写/空白）
        assertEquals("ssl", SmtpMailSender.resolveEncryption(" SSL "));
        assertEquals("starttls", SmtpMailSender.resolveEncryption("StartTLS"));
        assertEquals("none", SmtpMailSender.resolveEncryption("none"));
    }

    @Test
    void resolvePort_shouldDefaultByEncryption() {
        assertEquals(465, SmtpMailSender.resolvePort(null, "ssl"));
        assertEquals(465, SmtpMailSender.resolvePort("  ", "ssl"));
        assertEquals(587, SmtpMailSender.resolvePort(null, "starttls"));
        assertEquals(25, SmtpMailSender.resolvePort(null, "none"));
    }

    @Test
    void resolvePort_shouldUseConfiguredValue() {
        assertEquals(465, SmtpMailSender.resolvePort("465", "ssl"));
        assertEquals(587, SmtpMailSender.resolvePort("587", "ssl"));
        assertEquals(25, SmtpMailSender.resolvePort("25", "none"));
    }

    @Test
    void resolvePort_shouldRejectInvalidValue() {
        assertThrows(BusinessException.class, () -> SmtpMailSender.resolvePort("abc", "ssl"));
        assertThrows(BusinessException.class, () -> SmtpMailSender.resolvePort("0", "ssl"));
        assertThrows(BusinessException.class, () -> SmtpMailSender.resolvePort("70000", "ssl"));
    }

    @Test
    void send_shouldRejectWhenRequiredConfigMissing() {
        String config = "{\"host\":\"smtp.qq.com\",\"username\":\"a@qq.com\"}"; // 缺 password
        BusinessException ex = assertThrows(BusinessException.class,
                () -> sender.send("to@b.com", "123456", 5, config));
        assertTrue(ex.getMessage().contains("host/username/password"));
    }

    @Test
    void send_shouldRejectMalformedJson() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> sender.send("to@b.com", "123456", 5, "not-json"));
        assertTrue(ex.getMessage().contains("JSON"));
    }
}
