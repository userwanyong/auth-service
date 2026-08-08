package cn.wanyj.auth.util;

import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;

/**
 * 用户字段校验工具
 * <p>集中管理 email / phone 等可选字段的格式校验，供注册、更新等场景复用。
 * 选填语义：字段为 null 或空白时跳过校验；提供了非空值才校验格式。</p>
 *
 * @author wanyj
 */
public final class UserFieldValidator {

    private UserFieldValidator() {
    }

    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    /**
     * 校验 email / phone 格式（选填：为 null 或空白时跳过）。
     * 格式不合法时抛出 {@link BusinessException}。
     *
     * @param email 邮箱（可空）
     * @param phone 手机号（可空）
     */
    public static void validateContactFields(String email, String phone) {
        if (email != null && !email.isBlank() && !email.matches(EMAIL_REGEX)) {
            throw new BusinessException(ErrorCode.INVALID_EMAIL_FORMAT);
        }
        if (phone != null && !phone.isBlank() && !phone.matches(PHONE_REGEX)) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_FORMAT);
        }
    }
}
