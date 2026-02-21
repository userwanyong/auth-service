package cn.wanyj.auth.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RPC 响应 - 认证结果
 *
 * @author wanyj
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否认证成功
     */
    private Boolean success;

    /**
     * 结果消息
     */
    private String message;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;
}
