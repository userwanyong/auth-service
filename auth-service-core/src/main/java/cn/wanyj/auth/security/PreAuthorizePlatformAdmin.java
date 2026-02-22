package cn.wanyj.auth.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Platform Admin Permission Annotation
 * 平台管理员权限注解
 *
 * Requires the user to be a platform admin (tenantId=0 with ROLE_PLATFORM_ADMIN role)
 *
 * @author wanyj
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@platformSecurityEvaluator.isPlatformAdmin()")
public @interface PreAuthorizePlatformAdmin {
}
