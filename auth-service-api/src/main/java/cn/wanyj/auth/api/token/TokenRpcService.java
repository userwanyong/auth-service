package cn.wanyj.auth.api.token;

import cn.wanyj.auth.api.model.TokenRpcResponse;

/**
 * 令牌服务 RPC 接口
 * 用于其他服务生成和验证令牌
 *
 * @author wanyj
 */
public interface TokenRpcService {

    /**
     * 为用户生成访问令牌
     * 用于服务间调用时生成临时令牌
     *
     * @param userId 用户ID
     * @param expiration 过期时间（秒）
     * @return 令牌响应
     */
    TokenRpcResponse generateToken(Long userId, Long expiration);

    /**
     * 解析令牌获取用户信息
     *
     * @param token 令牌
     * @return 用户ID
     */
    Long parseToken(String token);

    /**
     * 撤销用户的所有令牌（强制登出）
     *
     * @param userId 用户ID
     */
    void revokeAllTokens(Long userId);
}
