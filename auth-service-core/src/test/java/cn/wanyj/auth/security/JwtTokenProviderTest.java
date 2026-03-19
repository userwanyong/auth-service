package cn.wanyj.auth.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtTokenProvider 单元测试
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "secret", "TestSecretKeyForUnitTesting1234567890123456=");
        ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenExpiration", 900000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshTokenExpiration", 604800000L);
        jwtTokenProvider.init();
    }

    @Test
    void init_shouldThrowWhenSecretIsEmpty() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", "");
        ReflectionTestUtils.setField(provider, "accessTokenExpiration", 900000L);
        ReflectionTestUtils.setField(provider, "refreshTokenExpiration", 604800000L);

        assertThrows(IllegalStateException.class, provider::init);
    }

    @Test
    void init_shouldThrowWhenSecretIsNull() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", null);
        ReflectionTestUtils.setField(provider, "accessTokenExpiration", 900000L);
        ReflectionTestUtils.setField(provider, "refreshTokenExpiration", 604800000L);

        assertThrows(IllegalStateException.class, provider::init);
    }

    @Test
    void generateAccessToken_shouldContainJti() {
        cn.wanyj.auth.entity.User user = createTestUser();
        String token = jwtTokenProvider.generateAccessToken(user);

        assertNotNull(token);
        String jti = jwtTokenProvider.getJtiFromToken(token);
        assertNotNull(jti);
        assertFalse(jti.isBlank());
    }

    @Test
    void generateAccessToken_shouldContainCorrectClaims() {
        cn.wanyj.auth.entity.User user = createTestUser();
        String token = jwtTokenProvider.generateAccessToken(user);

        assertTrue(jwtTokenProvider.validateAccessToken(token));
        assertEquals(1L, jwtTokenProvider.getUserIdFromToken(token));
        assertEquals(100L, jwtTokenProvider.getTenantIdFromToken(token));
    }

    @Test
    void validateAccessToken_shouldRejectRefreshToken() {
        cn.wanyj.auth.entity.User user = createTestUser();
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        assertFalse(jwtTokenProvider.validateAccessToken(refreshToken));
    }

    @Test
    void validateRefreshToken_shouldAcceptRefreshToken() {
        cn.wanyj.auth.entity.User user = createTestUser();
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        assertTrue(jwtTokenProvider.validateRefreshToken(refreshToken));
    }

    @Test
    void getAccessTokenExpirationSeconds_shouldReturnCorrectValue() {
        assertEquals(900L, jwtTokenProvider.getAccessTokenExpirationSeconds());
    }

    @Test
    void getTokenRemainingTTL_shouldReturnPositiveValue() {
        cn.wanyj.auth.entity.User user = createTestUser();
        String token = jwtTokenProvider.generateAccessToken(user);

        long ttl = jwtTokenProvider.getTokenRemainingTTL(token);
        assertTrue(ttl > 0);
        assertTrue(ttl <= 900);
    }

    @Test
    void getClaimsFromToken_shouldReturnAllClaims() {
        cn.wanyj.auth.entity.User user = createTestUser();
        String token = jwtTokenProvider.generateAccessToken(user);

        var claims = jwtTokenProvider.getClaimsFromToken(token);
        assertEquals("1", claims.getSubject());
        assertEquals("testuser", claims.get("username", String.class));
        assertEquals(100L, claims.get("tenant_id", Long.class));
    }

    @Test
    void getJtiFromToken_shouldReturnUniqueId() {
        cn.wanyj.auth.entity.User user = createTestUser();
        String token1 = jwtTokenProvider.generateAccessToken(user);
        String token2 = jwtTokenProvider.generateAccessToken(user);

        String jti1 = jwtTokenProvider.getJtiFromToken(token1);
        String jti2 = jwtTokenProvider.getJtiFromToken(token2);

        assertNotNull(jti1);
        assertNotNull(jti2);
        assertNotEquals(jti1, jti2);
    }

    private cn.wanyj.auth.entity.User createTestUser() {
        cn.wanyj.auth.entity.User user = new cn.wanyj.auth.entity.User();
        user.setId(1L);
        user.setTenantId(100L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setRoles(new HashSet<>());
        return user;
    }
}
