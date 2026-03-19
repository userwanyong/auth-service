package cn.wanyj.auth.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Auth Principal - 认证主体
 * 类型安全的用户身份信息，替代 Object[]
 * @author wanyj
 */
@Data
@AllArgsConstructor
public class AuthPrincipal {

    private Long userId;
    private Long tenantId;
}
