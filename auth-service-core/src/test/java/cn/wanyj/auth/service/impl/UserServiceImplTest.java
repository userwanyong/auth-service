package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.dto.request.AssignRolesRequest;
import cn.wanyj.auth.dto.request.UpdateUserRequest;
import cn.wanyj.auth.entity.Role;
import cn.wanyj.auth.entity.User;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.mapper.RoleMapper;
import cn.wanyj.auth.mapper.UserMapper;
import cn.wanyj.auth.mapper.UserRoleMapper;
import cn.wanyj.auth.service.OssService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserServiceImpl 单元测试
 * <p>重点覆盖多租户归属校验（越权防护）、字段格式校验与部分更新语义（对应 RPC 字段掩码）。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserMapper userMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OssService ossService;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userMapper, roleMapper, userRoleMapper, passwordEncoder, ossService);
    }

    // ===== 越权防护：跨租户操作必须被拒（返回 NOT_FOUND，不泄露存在性） =====

    @Test
    void updateUserStatus_shouldRejectCrossTenant() {
        when(userMapper.findById(1L)).thenReturn(userInTenant(1L, 100L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateUserStatus(1L, 200L, 0));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(userMapper, never()).update(any());
    }

    @Test
    void updateUser_shouldRejectCrossTenant() {
        when(userMapper.findById(1L)).thenReturn(userInTenant(1L, 100L));
        UpdateUserRequest req = new UpdateUserRequest();
        req.setNickname("newnick");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateUser(1L, 200L, req));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(userMapper, never()).update(any());
    }

    @Test
    void assignRoles_shouldRejectCrossTenantUser() {
        when(userMapper.findById(1L)).thenReturn(userInTenant(1L, 100L));
        AssignRolesRequest req = AssignRolesRequest.builder().roleIds(List.of(10L)).build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.assignRoles(1L, 200L, req));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(userMapper, never()).insertUserRole(anyLong(), anyLong(), anyLong());
    }

    @Test
    void assignRoles_shouldRejectCrossTenantRole() {
        when(userMapper.findById(1L)).thenReturn(userInTenant(1L, 100L));
        when(roleMapper.findById(10L)).thenReturn(roleInTenant(10L, 200L)); // 角色属于其他租户
        AssignRolesRequest req = AssignRolesRequest.builder().roleIds(List.of(10L)).build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.assignRoles(1L, 100L, req));
        assertEquals(ErrorCode.ROLE_NOT_FOUND.getCode(), ex.getCode());
        verify(userMapper, never()).insertUserRole(anyLong(), anyLong(), anyLong());
    }

    @Test
    void deleteUser_shouldRejectCrossTenant() {
        when(userMapper.findById(1L)).thenReturn(userInTenant(1L, 100L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.deleteUser(1L, 200L));
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(userMapper, never()).deleteById(anyLong());
    }

    // ===== 同租户正常流程 =====

    @Test
    void assignRoles_shouldSucceedWithinSameTenant() {
        when(userMapper.findById(1L)).thenReturn(userInTenant(1L, 100L));
        when(roleMapper.findById(10L)).thenReturn(roleInTenant(10L, 100L));
        AssignRolesRequest req = AssignRolesRequest.builder().roleIds(List.of(10L)).build();

        userService.assignRoles(1L, 100L, req);

        verify(userMapper).deleteUserRolesByUserId(1L);
        // 关联记录统一使用调用方租户（而非 role.getTenantId()）
        verify(userMapper).insertUserRole(1L, 10L, 100L);
    }

    // ===== 字段格式校验 =====

    @Test
    void updateUser_shouldRejectInvalidEmailFormat() {
        UpdateUserRequest req = new UpdateUserRequest();
        req.setEmail("not-an-email");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateUser(1L, 100L, req));
        assertEquals(ErrorCode.INVALID_EMAIL_FORMAT.getCode(), ex.getCode());
        verify(userMapper, never()).findById(anyLong());
    }

    @Test
    void updateUser_shouldRejectInvalidPhoneFormat() {
        UpdateUserRequest req = new UpdateUserRequest();
        req.setPhone("123");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateUser(1L, 100L, req));
        assertEquals(ErrorCode.INVALID_PHONE_FORMAT.getCode(), ex.getCode());
    }

    // ===== 部分更新语义（对应 RPC 字段掩码）：未提供的字段保持不变 =====

    @Test
    void updateUser_shouldKeepUnspecifiedFieldsUntouched() {
        User existing = userInTenant(1L, 100L);
        existing.setUsername("original");
        existing.setPassword("original-pwd");
        existing.setEmail("orig@example.com");
        when(userMapper.findById(1L)).thenReturn(existing);

        UpdateUserRequest req = new UpdateUserRequest();
        req.setNickname("newnick"); // 只改昵称

        userService.updateUser(1L, 100L, req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).update(captor.capture());
        User saved = captor.getValue();
        assertEquals("newnick", saved.getNickname());       // 已改
        assertEquals("original", saved.getUsername());       // 未提供 → 不改
        assertEquals("original-pwd", saved.getPassword());   // 未提供 → 不改
        assertEquals("orig@example.com", saved.getEmail());  // 未提供 → 不改（null=不改，非清空）
    }

    // ===== 联系方式绑定语义：填写即已验证，清空即未绑定 =====

    @Test
    void updateUser_shouldMarkVerifiedWhenContactProvided() {
        User existing = userInTenant(1L, 100L);
        when(userMapper.findById(1L)).thenReturn(existing);
        when(userMapper.existsByEmail("a@b.com", 100L)).thenReturn(false);
        when(userMapper.existsByPhone("13800138000", 100L)).thenReturn(false);

        UpdateUserRequest req = new UpdateUserRequest();
        req.setEmail("a@b.com");
        req.setPhone("13800138000");

        userService.updateUser(1L, 100L, req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).update(captor.capture());
        User saved = captor.getValue();
        assertEquals("a@b.com", saved.getEmail());
        assertEquals(Boolean.TRUE, saved.getEmailVerified());
        assertEquals("13800138000", saved.getPhone());
        assertEquals(Boolean.TRUE, saved.getPhoneVerified());
    }

    @Test
    void updateUser_shouldResetVerifiedWhenContactCleared() {
        User existing = userInTenant(1L, 100L);
        existing.setEmail("a@b.com");
        existing.setEmailVerified(true);
        existing.setPhone("13800138000");
        existing.setPhoneVerified(true);
        when(userMapper.findById(1L)).thenReturn(existing);

        UpdateUserRequest req = new UpdateUserRequest();
        req.setEmail("");
        req.setPhone("");

        userService.updateUser(1L, 100L, req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).update(captor.capture());
        User saved = captor.getValue();
        assertNull(saved.getEmail());
        assertEquals(Boolean.FALSE, saved.getEmailVerified());
        assertNull(saved.getPhone());
        assertEquals(Boolean.FALSE, saved.getPhoneVerified());
    }

    @Test
    void updateUser_shouldRejectPhoneOwnedByOtherInTenant() {
        User existing = userInTenant(1L, 100L);
        when(userMapper.findById(1L)).thenReturn(existing);
        when(userMapper.existsByPhone("13800138000", 100L)).thenReturn(true);

        UpdateUserRequest req = new UpdateUserRequest();
        req.setPhone("13800138000");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateUser(1L, 100L, req));
        assertEquals(ErrorCode.PHONE_EXISTS.getCode(), ex.getCode());
        verify(userMapper, never()).update(any());
    }

    // ===== helpers =====

    private User userInTenant(Long id, Long tenantId) {
        User u = new User();
        u.setId(id);
        u.setTenantId(tenantId);
        u.setUsername("user" + id);
        u.setPassword("pwd");
        u.setStatus(1);
        u.setRoles(new HashSet<>());
        return u;
    }

    private Role roleInTenant(Long id, Long tenantId) {
        return Role.builder().id(id).tenantId(tenantId).code("ROLE_X").build();
    }
}
