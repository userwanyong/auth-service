package cn.wanyj.auth.service.sender.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AliyunMailSender 模板渲染单元测试（发送链路依赖阿里云 SDK，只测渲染纯函数）
 */
class AliyunMailSenderTest {

    @Test
    void renderBody_shouldUseCustomTemplateWithPlaceholders() {
        String body = AliyunMailSender.renderBody(
                "<p>欢迎，验证码 <b>{code}</b>，{minutes} 分钟内有效</p>", "123456", 5);
        assertEquals("<p>欢迎，验证码 <b>123456</b>，5 分钟内有效</p>", body);
    }

    @Test
    void renderBody_shouldFallbackToDefaultWhenTemplateMissing() {
        String body = AliyunMailSender.renderBody(null, "123456", 5);
        assertTrue(body.contains("123456"));
        assertTrue(body.contains("5 分钟内有效"));
    }

    @Test
    void renderBody_shouldFallbackWhenTemplateMissingCodePlaceholder() {
        // 模板缺 {code} 占位符 → 回退默认文案，验证码必须仍然可见
        String body = AliyunMailSender.renderBody("<p>你的验证码是 888888</p>", "123456", 5);
        assertTrue(body.contains("123456"), "回退文案必须包含真实验证码");
        assertFalse(body.contains("888888"), "不得使用模板里的假验证码");
    }

    @Test
    void renderBody_shouldFallbackWhenTemplateBlank() {
        String body = AliyunMailSender.renderBody("   ", "654321", 10);
        assertTrue(body.contains("654321"));
        assertTrue(body.contains("10 分钟内有效"));
    }

    @Test
    void resolveSubject_shouldFallbackWhenBlank() {
        assertEquals("登录验证码", AliyunMailSender.resolveSubject(null));
        assertEquals("登录验证码", AliyunMailSender.resolveSubject("  "));
        assertEquals("自定义主题", AliyunMailSender.resolveSubject(" 自定义主题 "));
    }
}
