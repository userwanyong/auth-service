package cn.wanyj.auth.entity;

import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;

/**
 * 系统支持的登录方式注册表（method = category:vendor）
 * <p>
 * category 决定前端 UI 与后端处理链路：password / email / sms / oauth。
 * 新增登录方式只需在此枚举追加一项，并实现对应的 Sender/Provider。
 *
 * @author wanyj
 * @since 1.0.0
 */
public enum LoginMethod {

    PASSWORD("password", "password", "账号密码"),
    EMAIL_ALIYUN("email:aliyun", "email", "邮箱验证码（阿里云）"),
    SMS_ALIYUN("sms:aliyun", "sms", "手机验证码（阿里云）"),
    OAUTH_GITEE("oauth:gitee", "oauth", "Gitee 登录"),
    OAUTH_GITHUB("oauth:github", "oauth", "GitHub 登录");

    private final String code;
    private final String category;
    private final String displayName;

    LoginMethod(String code, String category, String displayName) {
        this.code = code;
        this.category = category;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getCategory() {
        return category;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 按 method code 查找枚举，未命中返回 null
     */
    public static LoginMethod fromCode(String code) {
        if (code == null) return null;
        for (LoginMethod m : values()) {
            if (m.code.equals(code)) return m;
        }
        return null;
    }

    /**
     * 校验 method 是否受支持，否则抛 LOGIN_METHOD_NOT_SUPPORTED
     */
    public static void requireSupported(String code) {
        if (fromCode(code) == null) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_NOT_SUPPORTED);
        }
    }
}
