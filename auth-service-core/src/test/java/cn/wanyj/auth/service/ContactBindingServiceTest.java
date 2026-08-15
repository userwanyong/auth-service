package cn.wanyj.auth.service;

import cn.wanyj.auth.dto.request.BindContactRequest;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ContactBindingService 单元测试
 * <p>重点覆盖校验链顺序（验码先于唯一性检查，防目标枚举探测）。
 */
@ExtendWith(MockitoExtension.class)
class ContactBindingServiceTest {

    private static final Long USER_ID = 100L;
    private static final Long OTHER_USER_ID = 200L;
    private static final Long TENANT_ID = 1L;

    @Mock private UserMapper userMapper;
    @Mock private CodeService codeService;
    @Mock private LoginMethodConfigService loginMethodConfigService;

    private ContactBindingService contactBindingService;

    @BeforeEach
    void setUp() {
        contactBindingService = new ContactBindingService(userMapper, codeService, loginMethodConfigService);
    }

    private User user() {
        return User.builder().id(USER_ID).tenantId(TENANT_ID).username("testuser").status(1).build();
    }

    private BindContactRequest emailRequest(String target, String code) {
        return BindContactRequest.builder().method("email:aliyun").target(target).code(code).build();
    }

    private BindContactRequest phoneRequest(String target, String code) {
        return BindContactRequest.builder().method("sms:aliyun").target(target).code(code).build();
    }

    // ========== bindEmail ==========

    @Test
    void bindEmail_shouldThrowWhenCategoryMismatch() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> contactBindingService.bindEmail(USER_ID, TENANT_ID, phoneRequest("13800138000", "123456")));
        assertEquals(ErrorCode.LOGIN_METHOD_NOT_SUPPORTED.getCode(), ex.getCode());
        verifyNoInteractions(codeService);
    }

    @Test
    void bindEmail_shouldThrowWhenUserNotFound() {
        when(userMapper.findByIdWithRolesAndPermissions(USER_ID, TENANT_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contactBindingService.bindEmail(USER_ID, TENANT_ID, emailRequest("a@b.com", "123456")));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verifyNoInteractions(codeService);
    }

    @Test
    void bindEmail_shouldThrowWhenMethodDisabled() {
        when(userMapper.findByIdWithRolesAndPermissions(USER_ID, TENANT_ID)).thenReturn(user());
        when(loginMethodConfigService.isEnabled(TENANT_ID, "email:aliyun")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contactBindingService.bindEmail(USER_ID, TENANT_ID, emailRequest("a@b.com", "123456")));
        assertEquals(ErrorCode.LOGIN_METHOD_DISABLED.getCode(), ex.getCode());
        verifyNoInteractions(codeService);
    }

    @Test
    void bindEmail_shouldThrowWhenEmailFormatInvalid() {
        when(userMapper.findByIdWithRolesAndPermissions(USER_ID, TENANT_ID)).thenReturn(user());
        when(loginMethodConfigService.isEnabled(TENANT_ID, "email:aliyun")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contactBindingService.bindEmail(USER_ID, TENANT_ID, emailRequest("not-an-email", "123456")));
        assertEquals(ErrorCode.INVALID_EMAIL_FORMAT.getCode(), ex.getCode());
        verifyNoInteractions(codeService);
    }

    @Test
    void bindEmail_shouldThrowWhenCodeWrongAndNotProbeUniqueness() {
        when(userMapper.findByIdWithRolesAndPermissions(USER_ID, TENANT_ID)).thenReturn(user());
        when(loginMethodConfigService.isEnabled(TENANT_ID, "email:aliyun")).thenReturn(true);
        when(codeService.verify(TENANT_ID, "email:aliyun", "a@b.com", "000000")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contactBindingService.bindEmail(USER_ID, TENANT_ID, emailRequest("a@b.com", "000000")));
        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), ex.getCode());
        // 验码失败时不得触达唯一性查询（防枚举探测）
        verify(userMapper, never()).findByEmail(anyString(), anyLong());
    }

    @Test
    void bindEmail_shouldThrowWhenEmailOwnedByOther() {
        when(userMapper.findByIdWithRolesAndPermissions(USER_ID, TENANT_ID)).thenReturn(user());
        when(loginMethodConfigService.isEnabled(TENANT_ID, "email:aliyun")).thenReturn(true);
        when(codeService.verify(TENANT_ID, "email:aliyun", "a@b.com", "123456")).thenReturn(true);
        when(userMapper.findByEmail("a@b.com", TENANT_ID))
                .thenReturn(User.builder().id(OTHER_USER_ID).build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contactBindingService.bindEmail(USER_ID, TENANT_ID, emailRequest("a@b.com", "123456")));
        assertEquals(ErrorCode.EMAIL_EXISTS.getCode(), ex.getCode());
        verify(userMapper, never()).update(any());
    }

    @Test
    void bindEmail_shouldAllowRebindSameEmailBySelf() {
        when(userMapper.findByIdWithRolesAndPermissions(USER_ID, TENANT_ID)).thenReturn(user());
        when(loginMethodConfigService.isEnabled(TENANT_ID, "email:aliyun")).thenReturn(true);
        when(codeService.verify(TENANT_ID, "email:aliyun", "a@b.com", "123456")).thenReturn(true);
        when(userMapper.findByEmail("a@b.com", TENANT_ID)).thenReturn(user());

        contactBindingService.bindEmail(USER_ID, TENANT_ID, emailRequest("a@b.com", "123456"));

        verify(userMapper).update(any(User.class));
    }

    @Test
    void bindEmail_shouldSetEmailAndVerifiedOnSuccess() {
        User user = user();
        when(userMapper.findByIdWithRolesAndPermissions(USER_ID, TENANT_ID)).thenReturn(user);
        when(loginMethodConfigService.isEnabled(TENANT_ID, "email:aliyun")).thenReturn(true);
        when(codeService.verify(TENANT_ID, "email:aliyun", "a@b.com", "123456")).thenReturn(true);
        when(userMapper.findByEmail("a@b.com", TENANT_ID)).thenReturn(null);

        contactBindingService.bindEmail(USER_ID, TENANT_ID, emailRequest("a@b.com", "123456"));

        assertEquals("a@b.com", user.getEmail());
        assertEquals(Boolean.TRUE, user.getEmailVerified());
        verify(userMapper).update(user);
    }

    // ========== bindPhone ==========

    @Test
    void bindPhone_shouldThrowWhenCategoryMismatch() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> contactBindingService.bindPhone(USER_ID, TENANT_ID, emailRequest("a@b.com", "123456")));
        assertEquals(ErrorCode.LOGIN_METHOD_NOT_SUPPORTED.getCode(), ex.getCode());
        verifyNoInteractions(codeService);
    }

    @Test
    void bindPhone_shouldThrowWhenPhoneFormatInvalid() {
        when(userMapper.findByIdWithRolesAndPermissions(USER_ID, TENANT_ID)).thenReturn(user());
        when(loginMethodConfigService.isEnabled(TENANT_ID, "sms:aliyun")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contactBindingService.bindPhone(USER_ID, TENANT_ID, phoneRequest("12345", "123456")));
        assertEquals(ErrorCode.INVALID_PHONE_FORMAT.getCode(), ex.getCode());
        verifyNoInteractions(codeService);
    }

    @Test
    void bindPhone_shouldThrowWhenPhoneOwnedByOther() {
        when(userMapper.findByIdWithRolesAndPermissions(USER_ID, TENANT_ID)).thenReturn(user());
        when(loginMethodConfigService.isEnabled(TENANT_ID, "sms:aliyun")).thenReturn(true);
        when(codeService.verify(TENANT_ID, "sms:aliyun", "13800138000", "123456")).thenReturn(true);
        when(userMapper.findByPhone("13800138000", TENANT_ID))
                .thenReturn(User.builder().id(OTHER_USER_ID).build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contactBindingService.bindPhone(USER_ID, TENANT_ID, phoneRequest("13800138000", "123456")));
        assertEquals(ErrorCode.PHONE_EXISTS.getCode(), ex.getCode());
        verify(userMapper, never()).update(any());
    }

    @Test
    void bindPhone_shouldSetPhoneAndVerifiedOnSuccess() {
        User user = user();
        when(userMapper.findByIdWithRolesAndPermissions(USER_ID, TENANT_ID)).thenReturn(user);
        when(loginMethodConfigService.isEnabled(TENANT_ID, "sms:aliyun")).thenReturn(true);
        when(codeService.verify(TENANT_ID, "sms:aliyun", "13800138000", "123456")).thenReturn(true);
        when(userMapper.findByPhone("13800138000", TENANT_ID)).thenReturn(null);

        contactBindingService.bindPhone(USER_ID, TENANT_ID, phoneRequest("13800138000", "123456"));

        assertEquals("13800138000", user.getPhone());
        assertEquals(Boolean.TRUE, user.getPhoneVerified());
        verify(userMapper).update(user);
    }

    // ========== unbind ==========

    @Test
    void unbindEmail_shouldThrowWhenNotBound() {
        when(userMapper.findByIdWithRolesAndPermissions(USER_ID, TENANT_ID))
                .thenReturn(User.builder().id(USER_ID).email(null).build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contactBindingService.unbindEmail(USER_ID, TENANT_ID));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        verify(userMapper, never()).update(any());
    }

    @Test
    void unbindEmail_shouldClearEmailAndVerified() {
        User user = User.builder().id(USER_ID).email("a@b.com").emailVerified(true).build();
        when(userMapper.findByIdWithRolesAndPermissions(USER_ID, TENANT_ID)).thenReturn(user);

        contactBindingService.unbindEmail(USER_ID, TENANT_ID);

        assertNull(user.getEmail());
        assertEquals(Boolean.FALSE, user.getEmailVerified());
        verify(userMapper).update(user);
    }

    @Test
    void unbindPhone_shouldThrowWhenNotBound() {
        when(userMapper.findByIdWithRolesAndPermissions(USER_ID, TENANT_ID))
                .thenReturn(User.builder().id(USER_ID).phone(null).build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contactBindingService.unbindPhone(USER_ID, TENANT_ID));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        verify(userMapper, never()).update(any());
    }

    @Test
    void unbindPhone_shouldClearPhoneAndVerified() {
        User user = User.builder().id(USER_ID).phone("13800138000").phoneVerified(true).build();
        when(userMapper.findByIdWithRolesAndPermissions(USER_ID, TENANT_ID)).thenReturn(user);

        contactBindingService.unbindPhone(USER_ID, TENANT_ID);

        assertNull(user.getPhone());
        assertEquals(Boolean.FALSE, user.getPhoneVerified());
        verify(userMapper).update(user);
    }
}
