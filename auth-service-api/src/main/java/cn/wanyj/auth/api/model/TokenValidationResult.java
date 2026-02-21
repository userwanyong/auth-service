package cn.wanyj.auth.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * RPC 响应 - 令牌验证结果
 *
 * @author wanyj
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenValidationResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 令牌是否有效
     */
    private Boolean valid;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户角色
     */
    private Set<String> roles;

    /**
     * 用户权限
     */
    private Set<String> permissions;

    /**
     * 过期时间戳
     */
    private Long expiresAt;
}
