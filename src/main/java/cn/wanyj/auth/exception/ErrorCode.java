package cn.wanyj.auth.exception;

import lombok.Getter;

/**
 * Error Code Enum - 错误码枚举
 * @author wanyj
 */
@Getter
public enum ErrorCode {

    // Common errors - 通用错误
    SUCCESS(200, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),

    // Authentication errors - 认证错误
    INVALID_CREDENTIALS(1001, "用户名或密码错误"),
    USER_NOT_FOUND(1002, "用户不存在"),
    USER_DISABLED(1003, "用户已被禁用"),
    USERNAME_EXISTS(1004, "用户名已存在"),
    EMAIL_EXISTS(1005, "邮箱已被使用"),
    INVALID_TOKEN(1006, "令牌无效或已过期"),
    TOKEN_MISSING(1007, "未提供认证令牌"),
    TOKEN_INVALID(1008, "令牌无效"),
    TOKEN_EXPIRED(1009, "令牌已过期"),
    TOKEN_BLACKLISTED(1010, "令牌已被注销"),
    REFRESH_TOKEN_INVALID(1011, "刷新令牌无效"),
    OLD_PASSWORD_WRONG(1012, "旧密码错误"),
    INVALID_EMAIL_FORMAT(1013, "邮箱格式不正确"),
    INVALID_PHONE_FORMAT(1014, "手机号格式不正确"),

    // Authorization errors - 授权错误
    ACCESS_DENIED(2001, "无权限访问"),
    ROLE_NOT_FOUND(2002, "角色不存在"),
    ROLE_CODE_EXISTS(2003, "角色编码已存在"),
    PERMISSION_NOT_FOUND(2004, "权限不存在"),
    PERMISSION_CODE_EXISTS(2005, "权限编码已存在");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
