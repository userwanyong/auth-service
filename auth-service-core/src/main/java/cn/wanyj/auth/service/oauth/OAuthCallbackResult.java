package cn.wanyj.auth.service.oauth;

import cn.wanyj.auth.dto.response.TokenResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth 回调结果（区分登录流程与绑定流程）
 *
 * @author wanyj
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthCallbackResult {

    /**
     * true = 登录流程（签发 token）；false = 绑定流程（绑到已有账号）
     */
    private boolean login;

    /**
     * 登录流程：签发的令牌
     */
    private TokenResponse token;

    /**
     * 绑定流程：是否成功
     */
    private boolean success;

    /**
     * 绑定流程：提示消息（成功/失败原因）
     */
    private String message;
}
