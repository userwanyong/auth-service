package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.dto.request.LoginMethodConfigSaveRequest;
import cn.wanyj.auth.entity.LoginMethodConfig;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.mapper.LoginMethodConfigMapper;
import cn.wanyj.auth.security.CryptoUtils;
import cn.wanyj.auth.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LoginMethodConfigServiceImpl 单元测试（configJson 按键合并：部分更新不清凭证）
 */
@ExtendWith(MockitoExtension.class)
class LoginMethodConfigServiceImplTest {

    @Mock private LoginMethodConfigMapper mapper;
    @Mock private CryptoUtils cryptoUtils;
    @Mock private TenantService tenantService;

    private LoginMethodConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LoginMethodConfigServiceImpl(mapper, cryptoUtils, tenantService);
    }

    private void stubExistingRow(Long tenantId, String method, String plainJson) {
        LoginMethodConfig row = LoginMethodConfig.builder()
                .tenantId(tenantId).method(method).enabled(1).usePlatformConfig(1)
                .configJson("cipher:" + method)
                .build();
        when(mapper.findByTenantAndMethod(tenantId, method)).thenReturn(row);
        when(cryptoUtils.decrypt("cipher:" + method)).thenReturn(plainJson);
        when(cryptoUtils.encrypt(anyString())).thenAnswer(inv -> "merged-cipher");
    }

    @Test
    void savePlatformConfig_shouldMergeTemplateOnlyAndKeepCredentials() {
        // 旧配置：完整凭证；本次只提交 template
        stubExistingRow(0L, "email:aliyun",
                "{\"accessKeyId\":\"ak\",\"accountName\":\"noreply@x.com\",\"fromAlias\":\"Auth\"}");

        service.savePlatformConfig("email:aliyun", LoginMethodConfigSaveRequest.builder()
                .enabled(1).configJson("{\"template\":\"<p>码：{code}</p>\"}").build());

        ArgumentCaptor<LoginMethodConfig> captor = ArgumentCaptor.forClass(LoginMethodConfig.class);
        verify(mapper).update(captor.capture());
        assertEquals("merged-cipher", captor.getValue().getConfigJson());

        // 合并结果：新 template + 旧凭证全部保留
        String mergedPlain = captureEncryptedSource();
        assertTrue(mergedPlain.contains("\"template\""));
        assertTrue(mergedPlain.contains("\"accessKeyId\":\"ak\""));
        assertTrue(mergedPlain.contains("\"accountName\":\"noreply@x.com\""));
    }

    @Test
    void savePlatformConfig_shouldIgnoreBlankKeysWhenMerging() {
        stubExistingRow(0L, "email:aliyun",
                "{\"accessKeyId\":\"ak\",\"subject\":\"旧主题\"}");
        when(cryptoUtils.encrypt(anyString())).thenReturn("merged-cipher");

        // subject 传空串 → 不覆盖旧值
        service.savePlatformConfig("email:aliyun", LoginMethodConfigSaveRequest.builder()
                .enabled(1).configJson("{\"subject\":\"\",\"template\":\"<p>{code}</p>\"}").build());

        String mergedPlain = captureEncryptedSource();
        assertTrue(mergedPlain.contains("\"subject\":\"旧主题\""), "空串不应覆盖旧主题");
        assertTrue(mergedPlain.contains("\"template\":\"<p>{code}</p>\""));
    }

    @Test
    void savePlatformConfig_shouldStoreAsIsWhenNoExistingRow() {
        when(mapper.findByTenantAndMethod(0L, "email:aliyun")).thenReturn(null);
        when(cryptoUtils.encrypt("{\"accessKeyId\":\"ak\"}")).thenReturn("new-cipher");

        service.savePlatformConfig("email:aliyun", LoginMethodConfigSaveRequest.builder()
                .enabled(1).configJson("{\"accessKeyId\":\"ak\"}").build());

        ArgumentCaptor<LoginMethodConfig> captor = ArgumentCaptor.forClass(LoginMethodConfig.class);
        verify(mapper).insert(captor.capture());
        assertEquals("new-cipher", captor.getValue().getConfigJson());
    }

    @Test
    void savePlatformConfig_shouldRejectDisablingPassword() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.savePlatformConfig("password", LoginMethodConfigSaveRequest.builder()
                        .enabled(0).build()));
        assertNotNull(ex.getMessage());
    }

    @Test
    void saveTenantConfig_shouldMergeTenantOwnTemplate() {
        // saveTenantConfig 先校验平台级行已开启该方式
        when(mapper.findByTenantAndMethod(0L, "email:aliyun")).thenReturn(
                LoginMethodConfig.builder().tenantId(0L).method("email:aliyun")
                        .enabled(1).usePlatformConfig(1).build());
        stubExistingRow(100L, "email:aliyun",
                "{\"accessKeyId\":\"tenant-ak\",\"accountName\":\"noreply@t.com\"}");

        service.saveTenantConfig(100L, "email:aliyun", LoginMethodConfigSaveRequest.builder()
                .enabled(1).usePlatformConfig(0)
                .configJson("{\"subject\":\"租户主题\"}").build());

        String mergedPlain = captureEncryptedSource();
        assertTrue(mergedPlain.contains("\"subject\":\"租户主题\""));
        assertTrue(mergedPlain.contains("\"accessKeyId\":\"tenant-ak\""), "租户凭证不应被部分更新清除");
    }

    /** 从 encrypt 调用中取回合并后的明文 JSON */
    private String captureEncryptedSource() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(cryptoUtils, atLeastOnce()).encrypt(captor.capture());
        return captor.getValue();
    }

    @Test
    void savePlatformConfig_shouldRejectNonNumericTtl() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.savePlatformConfig("email:aliyun", LoginMethodConfigSaveRequest.builder()
                        .enabled(1).configJson("{\"codeTtlMinutes\":\"abc\"}").build()));
        assertTrue(ex.getMessage().contains("codeTtlMinutes"));
    }

    @Test
    void savePlatformConfig_shouldRejectOutOfRangeTtl() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.savePlatformConfig("email:aliyun", LoginMethodConfigSaveRequest.builder()
                        .enabled(1).configJson("{\"codeTtlMinutes\":\"99\"}").build()));
        assertTrue(ex.getMessage().contains("codeTtlMinutes"));
    }

    @Test
    void savePlatformConfig_shouldAcceptValidTtl() {
        when(mapper.findByTenantAndMethod(0L, "email:aliyun")).thenReturn(null);
        when(cryptoUtils.encrypt(anyString())).thenReturn("new-cipher");

        service.savePlatformConfig("email:aliyun", LoginMethodConfigSaveRequest.builder()
                .enabled(1).configJson("{\"codeTtlMinutes\":\"10\"}").build());

        verify(mapper).insert(any(LoginMethodConfig.class));
    }
}
