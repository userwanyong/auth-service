package cn.wanyj.auth.service;

import cn.wanyj.auth.annotation.Auditable;
import cn.wanyj.auth.dto.request.BindContactRequest;
import cn.wanyj.auth.entity.LoginMethod;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.mapper.UserMapper;
import cn.wanyj.auth.util.UserFieldValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 邮箱/手机号绑定服务（账号绑定页）
 * <p>
 * 绑定/换绑需先通过 POST /auth/send-code 向新目标发码，再凭验证码落库；
 * 校验链中验码先于唯一性检查（防止无验证码探测某目标是否已被占用）。
 * 解绑即清空对应字段并重置验证标记。
 *
 * @author wanyj
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactBindingService {

    private final UserMapper userMapper;
    private final CodeService codeService;
    private final LoginMethodConfigService loginMethodConfigService;

    /**
     * 绑定/换绑邮箱（验证码验证通过后覆盖旧值，同值重绑视为重新验证）
     */
    @Transactional
    @Auditable(action = "BIND_EMAIL", resource = "User")
    public void bindEmail(Long userId, Long tenantId, BindContactRequest request) {
        requireCategory(request.getMethod(), "email");
        User user = loadUser(userId, tenantId);
        checkEnabled(tenantId, request.getMethod());
        UserFieldValidator.validateContactFields(request.getTarget(), null);
        verifyCode(tenantId, request.getMethod(), request.getTarget(), request.getCode());
        User owner = userMapper.findByEmail(request.getTarget(), tenantId);
        if (owner != null && !owner.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }
        user.setEmail(request.getTarget());
        user.setEmailVerified(true);
        userMapper.update(user);
        log.info("Email bound: tenant={}, user={}", tenantId, userId);
    }

    /**
     * 解绑邮箱（清空 email 并重置 email_verified）
     */
    @Transactional
    @Auditable(action = "UNBIND_EMAIL", resource = "User")
    public void unbindEmail(Long userId, Long tenantId) {
        User user = loadUser(userId, tenantId);
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前未绑定邮箱");
        }
        user.setEmail(null);
        user.setEmailVerified(false);
        userMapper.update(user);
        log.info("Email unbound: tenant={}, user={}", tenantId, userId);
    }

    /**
     * 绑定/换绑手机号（验证码验证通过后覆盖旧值，同值重绑视为重新验证）
     */
    @Transactional
    @Auditable(action = "BIND_PHONE", resource = "User")
    public void bindPhone(Long userId, Long tenantId, BindContactRequest request) {
        requireCategory(request.getMethod(), "sms");
        User user = loadUser(userId, tenantId);
        checkEnabled(tenantId, request.getMethod());
        UserFieldValidator.validateContactFields(null, request.getTarget());
        verifyCode(tenantId, request.getMethod(), request.getTarget(), request.getCode());
        User owner = userMapper.findByPhone(request.getTarget(), tenantId);
        if (owner != null && !owner.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.PHONE_EXISTS);
        }
        user.setPhone(request.getTarget());
        user.setPhoneVerified(true);
        userMapper.update(user);
        log.info("Phone bound: tenant={}, user={}", tenantId, userId);
    }

    /**
     * 解绑手机号（清空 phone 并重置 phone_verified）
     */
    @Transactional
    @Auditable(action = "UNBIND_PHONE", resource = "User")
    public void unbindPhone(Long userId, Long tenantId) {
        User user = loadUser(userId, tenantId);
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前未绑定手机号");
        }
        user.setPhone(null);
        user.setPhoneVerified(false);
        userMapper.update(user);
        log.info("Phone unbound: tenant={}, user={}", tenantId, userId);
    }

    /**
     * 校验 method 支持且 category 匹配（/me/email 只收 email 类，/me/phone 只收 sms 类，
     * 防止用邮箱验证码改绑手机号）
     */
    private void requireCategory(String methodCode, String category) {
        LoginMethod method = LoginMethod.fromCode(methodCode);
        if (method == null || !category.equals(method.getCategory())) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_NOT_SUPPORTED);
        }
    }

    /**
     * 加载当前用户（update 的 WHERE 无租户条件，租户隔离在此强制）
     */
    private User loadUser(Long userId, Long tenantId) {
        User user = userMapper.findByIdWithRolesAndPermissions(userId, tenantId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private void checkEnabled(Long tenantId, String method) {
        if (!loginMethodConfigService.isEnabled(tenantId, method)) {
            throw new BusinessException(ErrorCode.LOGIN_METHOD_DISABLED, "该登录方式未启用");
        }
    }

    private void verifyCode(Long tenantId, String method, String target, String code) {
        if (!codeService.verify(tenantId, method, target, code)) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "验证码错误或已过期");
        }
    }
}
