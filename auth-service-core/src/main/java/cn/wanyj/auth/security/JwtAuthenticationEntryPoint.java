package cn.wanyj.auth.security;

import cn.wanyj.auth.exception.ApiResponse;
import cn.wanyj.auth.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JWT Authentication Entry Point - JWT认证入口点
 * 处理认证失败的情况（如令牌无效、过期等）
 * @author wanyj
 */
@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // Try to get error code from request attribute set by JwtAuthenticationFilter
        ErrorCode errorCode = (ErrorCode) request.getAttribute(JwtAuthenticationFilter.TOKEN_ERROR_ATTRIBUTE);

        if (errorCode == null) {
            // Determine error code based on request context
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || authHeader.isBlank()) {
                errorCode = ErrorCode.TOKEN_MISSING;
            } else {
                errorCode = ErrorCode.TOKEN_INVALID;
            }
        }

        log.warn("Authentication failed: {} - URI: {} - Error: {}",
                 authException.getMessage(), request.getRequestURI(), errorCode.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<Void> apiResponse = ApiResponse.error(errorCode);
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
