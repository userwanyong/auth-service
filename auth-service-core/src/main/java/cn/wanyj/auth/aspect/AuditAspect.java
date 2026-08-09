package cn.wanyj.auth.aspect;

import cn.wanyj.auth.annotation.Auditable;
import cn.wanyj.auth.dto.response.TokenResponse;
import cn.wanyj.auth.filter.RpcAuthFilter;
import cn.wanyj.auth.security.JwtTokenProvider;
import cn.wanyj.auth.security.SecurityUtils;
import cn.wanyj.auth.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * Audit Aspect - 审计日志AOP切面
 * 拦截 @Auditable 注解的方法，记录审计日志
 * 提取身份信息优先级：SecurityContext > 方法参数名匹配 > 对象 getter 反射 > JWT 解析
 * @author wanyj
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService auditLogService;
    private final JwtTokenProvider jwtTokenProvider;

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void afterReturning(JoinPoint joinPoint, Auditable auditable, Object result) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            Long tenantId = SecurityUtils.getCurrentTenantId();
            String username = userId != null ? String.valueOf(userId) : null;

            // 从方法参数中提取身份信息（支持 RPC 调用场景）
            MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = methodSignature.getParameterNames();
            Object[] args = joinPoint.getArgs();

            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    Object arg = args[i];
                    if (arg == null) {
                        continue;
                    }

                    // 1. 通过参数名匹配基本类型参数（Long userId, Long tenantId, String username）
                    String paramName = paramNames[i];

                    if ("userId".equals(paramName) && userId == null) {
                        userId = toLong(arg);
                    }
                    if ("tenantId".equals(paramName) && tenantId == null) {
                        tenantId = toLong(arg);
                    }
                    if ("username".equals(paramName) && (username == null || username.isBlank())) {
                        username = arg.toString();
                    }

                    // 2. 通过反射调用对象 getter（适用于 DTO、Protobuf Request 等）
                    if (userId == null) {
                        userId = invokeGetterLong(arg, "getUserId");
                    }
                    if (username == null || username.isBlank()) {
                        String extracted = invokeGetterString(arg, "getUsername");
                        if (extracted != null && !extracted.isBlank()) {
                            username = extracted;
                        }
                    }
                    if (tenantId == null) {
                        tenantId = invokeGetterLong(arg, "getTenantId");
                        if (tenantId == null) {
                            String tenantIdStr = invokeGetterString(arg, "getTenantId");
                            if (tenantIdStr != null && !tenantIdStr.isBlank()) {
                                tenantId = toLong(tenantIdStr);
                            }
                        }
                    }

                    // 3. 如果是 JWT 字符串，尝试解析提取身份信息（适用于 logout 等只有 token 参数的方法）
                    if ((tenantId == null || userId == null) && arg instanceof String) {
                        String str = (String) arg;
                        if (isJwt(str)) {
                            try {
                                if (tenantId == null) {
                                    Long parsed = jwtTokenProvider.getTenantIdFromToken(str);
                                    if (parsed != null) {
                                        tenantId = parsed;
                                    }
                                }
                                if (userId == null) {
                                    Long parsed = jwtTokenProvider.getUserIdFromToken(str);
                                    if (parsed != null) {
                                        userId = parsed;
                                    }
                                }
                            } catch (Exception ignored) {
                                // token 可能已过期或无效，跳过
                            }
                        }
                    }
                }
            }

            // 4. 从返回值提取（login/register/loginByCode 返回 TokenResponse，含 user.tenantId/userId/username）
            //    覆盖 login 这类公开端点：请求时 SecurityContext 无认证信息，需从返回值补全
            if (result instanceof TokenResponse) {
                TokenResponse.UserInfo u = ((TokenResponse) result).getUser();
                if (u != null) {
                    if (tenantId == null && u.getTenantId() != null) {
                        tenantId = u.getTenantId();
                    }
                    if (userId == null && u.getId() != null) {
                        userId = u.getId();
                    }
                    if ((username == null || username.isBlank()) && u.getUsername() != null) {
                        username = u.getUsername();
                    }
                }
            }

            if (username == null || username.isBlank()) {
                username = userId != null ? String.valueOf(userId) : "anonymous";
            }

            String action = auditable.action();
            String resource = auditable.resource();
            String detail = joinPoint.getSignature().toShortString();

            String ipAddress = null;
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                ipAddress = request.getRemoteAddr();
            } else {
                // RPC 调用没有 Servlet 上下文，从 Dubbo Filter 的 ThreadLocal 获取
                ipAddress = RpcAuthFilter.getClientIp();
            }

            auditLogService.logAsync(tenantId, userId, username, action, resource, detail, ipAddress);
        } catch (Exception e) {
            log.error("Failed to record audit log", e);
        }
    }

    private boolean isJwt(String value) {
        return value != null && value.contains(".") && value.split("\\.").length == 3;
    }

    private Long toLong(Object value) {
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Long invokeGetterLong(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object result = method.invoke(target);
            if (result instanceof Long) {
                return (Long) result;
            }
            if (result instanceof Number) {
                return ((Number) result).longValue();
            }
            if (result instanceof String) {
                try {
                    return Long.parseLong((String) result);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String invokeGetterString(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object result = method.invoke(target);
            return result != null ? result.toString() : null;
        } catch (Exception ignored) {
        }
        return null;
    }
}
