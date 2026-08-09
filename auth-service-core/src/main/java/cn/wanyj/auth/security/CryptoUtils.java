package cn.wanyj.auth.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 凭证加解密工具（AES/GCM/NoPadding）
 * <p>
 * 用于登录方式配置中 config_json 的整段加解密。密钥走环境变量 LOGIN_CONFIG_AES_KEY
 * （Base64 编码的 16/24/32 字节 AES key；生成：openssl rand -base64 32）。
 * <p>
 * 设计上允许密钥未配置时启动（password 等无凭证方式不受影响），
 * 仅在真正调用 encrypt/decrypt 时才抛异常提示配置。
 *
 * @author wanyj
 * @since 1.0.0
 */
@Slf4j
@Component
public class CryptoUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int IV_LENGTH = 12;       // bytes

    @Value("${login-config.aes-key:}")
    private String aesKeyBase64;

    private SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        if (aesKeyBase64 == null || aesKeyBase64.isBlank()) {
            log.warn("LOGIN_CONFIG_AES_KEY not configured; login-method credential encryption is disabled. "
                    + "Set it in .env (generate with: openssl rand -base64 32) before enabling email/sms/oauth methods.");
            return;
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(aesKeyBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("LOGIN_CONFIG_AES_KEY is not valid Base64.", e);
        }
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException("LOGIN_CONFIG_AES_KEY must decode to a 16/24/32-byte AES key. "
                    + "Generate with: openssl rand -base64 32");
        }
        this.keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
        log.info("Login-method credential encryption enabled (AES/GCM).");
    }

    /**
     * 加密明文，返回 Base64(iv + ciphertext+tag)。空串原样返回。
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        ensureKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt login method config", e);
        }
    }

    /**
     * 解密 Base64(iv + ciphertext+tag)，返回明文。空串原样返回。
     */
    public String decrypt(String cipherBase64) {
        if (cipherBase64 == null || cipherBase64.isEmpty()) {
            return cipherBase64;
        }
        ensureKey();
        try {
            byte[] combined = Base64.getDecoder().decode(cipherBase64);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt login method config", e);
        }
    }

    private void ensureKey() {
        if (keySpec == null) {
            throw new IllegalStateException("LOGIN_CONFIG_AES_KEY not configured. "
                    + "Set it in .env (generate with: openssl rand -base64 32) before using credential-based login methods.");
        }
    }
}
