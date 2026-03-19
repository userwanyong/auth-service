package cn.wanyj.auth.annotation;

import java.lang.annotation.*;

/**
 * Auditable Annotation - 审计注解
 * 标记需要记录审计日志的方法
 * @author wanyj
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    /**
     * Action name (e.g., "LOGIN", "REGISTER", "LOGOUT")
     */
    String action();

    /**
     * Resource type (e.g., "User", "Token")
     */
    String resource() default "";
}
