package cn.wanyj.auth.service.oauth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth 提供方返回的用户信息（标准化）
 *
 * @author wanyj
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthUserInfo {

    /**
     * 提供方用户唯一ID（用于绑定匹配）
     */
    private String providerUid;

    /**
     * 邮箱（可能为空，如 Gitee 未授权 user_info 邮箱）
     */
    private String email;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像 URL
     */
    private String avatar;
}
