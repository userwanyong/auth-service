package cn.wanyj.auth.security;

import cn.wanyj.auth.exception.ErrorCode;
import cn.wanyj.auth.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * JWT Authentication Filter - JWT认证过滤器
 * 从请求头中提取JWT令牌并验证
 * @author wanyj
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenService tokenService;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    // Request attribute key for token error
    public static final String TOKEN_ERROR_ATTRIBUTE = "TOKEN_ERROR";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Skip JWT validation for refresh and logout endpoints
        // These endpoints use refresh token and will be validated in the controller
        if ("/api/auth/refresh".equals(requestPath) || "/api/auth/logout".equals(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractTokenFromRequest(request);

        // If token is provided, validate it
        if (token != null) {
            TokenValidationResult result = validateToken(token);

            if (result.isValid()) {
                // Check if token is blacklisted
                if (tokenService.isBlacklisted(token)) {
                    log.warn("Token is blacklisted: {}...", token.substring(0, Math.min(20, token.length())));
                    request.setAttribute(TOKEN_ERROR_ATTRIBUTE, ErrorCode.TOKEN_BLACKLISTED);
                } else {
                    // Get claims from token to extract user roles and permissions
                    Claims claims = jwtTokenProvider.getClaimsFromToken(token);
                    Long userId = Long.parseLong(claims.getSubject());

                    // Extract roles and permissions from JWT claims
                    // Note: JWT stores these as List, not Set
                    List<String> roles = claims.get("roles", List.class);
                    List<String> permissions = claims.get("permissions", List.class);

                    // Build GrantedAuthority list
                    List<GrantedAuthority> authorities = new ArrayList<>();
                    if (roles != null) {
                        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
                    }
                    if (permissions != null) {
                        permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
                    }

                    // Create authentication object with user ID and authorities
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, authorities);

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("Set authentication for user ID: {} with {} authorities: {}", userId, authorities.size(), authorities);
                }
            } else {
                // Store error in request attribute for AuthenticationEntryPoint
                request.setAttribute(TOKEN_ERROR_ATTRIBUTE, result.getErrorCode());
                log.warn("Token validation failed: {}", result.getErrorCode().getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract token from Authorization header
     * 从请求头中提取令牌
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        return null;
    }

    /**
     * Validate token and return detailed result
     * 验证令牌并返回详细结果
     */
    private TokenValidationResult validateToken(String token) {
        try {
            if (jwtTokenProvider.validateAccessToken(token)) {
                return TokenValidationResult.valid();
            } else {
                return TokenValidationResult.invalid(ErrorCode.TOKEN_INVALID);
            }
        } catch (Exception e) {
            String message = e.getMessage();
            log.debug("Token validation exception: {}", message);

            if (message != null && message.toLowerCase().contains("expired")) {
                return TokenValidationResult.invalid(ErrorCode.TOKEN_EXPIRED);
            } else if (message != null && message.toLowerCase().contains("malformed")) {
                return TokenValidationResult.invalid(ErrorCode.TOKEN_INVALID);
            } else {
                return TokenValidationResult.invalid(ErrorCode.TOKEN_INVALID);
            }
        }
    }

    /**
     * Token validation result holder
     * 令牌验证结果持有者
     */
    private static class TokenValidationResult {
        private final boolean valid;
        private final ErrorCode errorCode;

        private TokenValidationResult(boolean valid, ErrorCode errorCode) {
            this.valid = valid;
            this.errorCode = errorCode;
        }

        public static TokenValidationResult valid() {
            return new TokenValidationResult(true, null);
        }

        public static TokenValidationResult invalid(ErrorCode errorCode) {
            return new TokenValidationResult(false, errorCode);
        }

        public boolean isValid() {
            return valid;
        }

        public ErrorCode getErrorCode() {
            return errorCode;
        }
    }
}
