package cn.wanyj.auth.security;

import cn.wanyj.auth.entity.Permission;
import cn.wanyj.auth.entity.Role;
import cn.wanyj.auth.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT Token Provider - JWT令牌提供者
 * 负责生成和验证JWT令牌
 * @author wanyj
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret:Yo3bOIzQhkFc+lRvAEj90Hvx89IzgEC5FduXDPCTiB0=}")
    private String secret;

    @Value("${jwt.access-token-expiration:3600000}") // 1 hour in milliseconds
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:604800000}") // 7 days in milliseconds
    private Long refreshTokenExpiration;

    private SecretKey key;

    @PostConstruct
    public void init() {
        // Ensure the secret key is at least 256 bits (32 bytes) for HS256
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // If key is too short, pad it or generate a warning
            log.warn("JWT secret key is less than 256 bits. Consider using a longer secret key.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate Access Token
     * 生成访问令牌
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

        // Remove ROLE_ prefix when storing in JWT (filter will add it back)
        Set<String> roles = user.getRoles().stream()
                .map(role -> role.getCode().replace("ROLE_", ""))
                .collect(Collectors.toSet());

        Set<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("tenant_id", user.getTenantId())
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Generate Refresh Token
     * 生成刷新令牌
     */
    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("type", "refresh")
                .claim("tenant_id", user.getTenantId())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Get user ID from token
     * 从令牌中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.parseLong(claims.getSubject());
    }

    /**
     * Validate access token
     * 验证访问令牌
     */
    public boolean validateAccessToken(String token) {
        try {
            // Parse and validate token, get claims in one call
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Check if it's not a refresh token
            String type = claims.get("type", String.class);
            return !"refresh".equals(type);
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validate refresh token
     * 验证刷新令牌
     */
    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String type = claims.get("type", String.class);
            return "refresh".equals(type);
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid refresh token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get expiration time from access token
     * 获取访问令牌的过期时间（秒）
     */
    public Long getAccessTokenExpirationSeconds() {
        return accessTokenExpiration / 1000;
    }

    /**
     * Get token remaining TTL in seconds
     * 获取 token 的剩余有效期（秒）
     */
    public long getTokenRemainingTTL(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Date expiration = claims.getExpiration();
        Date now = new Date();
        return java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(expiration.getTime() - now.getTime());
    }

    /**
     * Get Claims from token
     * 从令牌中获取Claims（包含用户信息、角色、权限等）
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Get tenant ID from token
     * 从令牌中获取租户ID
     */
    public Long getTenantIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get("tenant_id", Long.class);
    }
}
