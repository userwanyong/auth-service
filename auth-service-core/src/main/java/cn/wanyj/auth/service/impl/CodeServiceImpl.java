package cn.wanyj.auth.service.impl;

import cn.wanyj.auth.service.CodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务实现（Redis 存储，多租户+方式+目标隔离）
 *
 * @author wanyj
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeServiceImpl implements CodeService {

    private static final String CODE_PREFIX = "login:code:";
    private static final long CODE_TTL_MINUTES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public String generateAndStore(Long tenantId, String method, String target) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(key(tenantId, method, target), code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("Login code stored: tenant={}, method={}", tenantId, method);
        return code;
    }

    @Override
    public boolean verify(Long tenantId, String method, String target, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String k = key(tenantId, method, target);
        Object stored = redisTemplate.opsForValue().get(k);
        if (stored != null && code.equals(stored.toString())) {
            redisTemplate.delete(k); // 一次性
            return true;
        }
        return false;
    }

    private String key(Long tenantId, String method, String target) {
        return CODE_PREFIX + tenantId + ":" + method + ":" + target;
    }
}
