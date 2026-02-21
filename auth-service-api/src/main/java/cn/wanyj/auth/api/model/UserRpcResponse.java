package cn.wanyj.auth.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * RPC 响应 - 用户信息
 *
 * @author wanyj
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRpcResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 状态：0-禁用，1-正常
     */
    private Integer status;

    /**
     * 用户角色
     */
    private Set<String> roles;

    /**
     * 用户权限
     */
    private Set<String> permissions;
}
