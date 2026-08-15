package cn.wanyj.auth.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LoginMethod 枚举单元测试（vendor 段解析，用于发送器分发）
 */
class LoginMethodTest {

    @Test
    void getVendor_shouldReturnVendorSegment() {
        assertEquals("aliyun", LoginMethod.EMAIL_ALIYUN.getVendor());
        assertEquals("smtp", LoginMethod.EMAIL_SMTP.getVendor());
        assertEquals("aliyun", LoginMethod.SMS_ALIYUN.getVendor());
        assertEquals("gitee", LoginMethod.OAUTH_GITEE.getVendor());
    }

    @Test
    void getVendor_shouldReturnSelfForPassword() {
        // password 无 vendor 段，返回自身
        assertEquals("password", LoginMethod.PASSWORD.getVendor());
    }

    @Test
    void fromCode_shouldResolveEmailSmtp() {
        LoginMethod method = LoginMethod.fromCode("email:smtp");
        assertNotNull(method);
        assertEquals("email", method.getCategory());
        assertEquals("邮箱验证码（SMTP 自有邮箱）", method.getDisplayName());
    }
}
