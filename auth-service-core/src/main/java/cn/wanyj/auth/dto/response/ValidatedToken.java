package cn.wanyj.auth.dto.response;

import cn.wanyj.auth.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 校验通过的 access token 及其用户快照（供 RPC/网关解析令牌用）
 *
 * @author wanyj
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidatedToken {

    /**
     * 令牌归属用户（含角色与权限）
     */
    private User user;

    /**
     * 访问令牌过期时间（epoch millis）
     */
    private long expiresAt;
}
