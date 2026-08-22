package cn.wanyj.auth.rpc.converter;

import cn.wanyj.auth.api.protobuf.UserRpcResponse;
import cn.wanyj.auth.dto.response.TokenResponse;
import cn.wanyj.auth.dto.response.UserResponse;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;

/**
 * User Protobuf 转换器
 * <p>集中管理 {@link UserResponse} / {@link TokenResponse.UserInfo} 到 {@link UserRpcResponse}
 * 的转换，供 RPC 实现层复用，避免转换逻辑在各 RPC impl 中重复。</p>
 *
 * @author wanyj
 */
public final class UserProtobufConverter {

    private UserProtobufConverter() {
    }

    /**
     * UserResponse（完整用户信息）→ UserRpcResponse
     * <p>tenantUid 取 {@link UserResponse#getTenantUid()}（可能为空）；
     * RPC 层已解析出租户时请用 {@link #convertToProtobuf(UserResponse, String)}。</p>
     */
    public static UserRpcResponse convertToProtobuf(UserResponse user) {
        return convertToProtobuf(user, user.getTenantUid());
    }

    /**
     * UserResponse（完整用户信息）→ UserRpcResponse，显式指定租户标识
     *
     * @param tenantUid 租户对外标识（RPC 层按入参 tenantUid 解析得到），可为 null
     */
    public static UserRpcResponse convertToProtobuf(UserResponse user, String tenantUid) {
        return UserRpcResponse.newBuilder()
                .setId(user.getId())
                .setUsername(user.getUsername())
                .setEmail(user.getEmail() != null ? user.getEmail() : "")
                .setPhone(user.getPhone() != null ? user.getPhone() : "")
                .setNickname(user.getNickname() != null ? user.getNickname() : "")
                .setAvatar(user.getAvatar() != null ? user.getAvatar() : "")
                .setStatus(user.getStatus())
                .addAllRoles(user.getRoles() != null ? user.getRoles() : Collections.emptyList())
                .addAllPermissions(user.getPermissions() != null ? user.getPermissions() : Collections.emptyList())
                .setTenantUid(tenantUid != null ? tenantUid : "")
                .setEmailVerified(user.getEmailVerified() != null && user.getEmailVerified())
                .setPhoneVerified(user.getPhoneVerified() != null && user.getPhoneVerified())
                .setRealName(user.getRealName() != null ? user.getRealName() : "")
                .setGender(user.getGender() != null ? user.getGender() : 0)
                .setBirthday(user.getBirthday() != null ? user.getBirthday().toString() : "")
                .setLastLoginAt(toEpochMilli(user.getLastLoginAt()))
                .setCreatedAt(toEpochMilli(user.getCreatedAt()))
                .setUpdatedAt(toEpochMilli(user.getUpdatedAt()))
                .build();
    }

    /**
     * TokenResponse.UserInfo（注册/登录返回的精简用户信息）→ UserRpcResponse
     */
    public static UserRpcResponse convertToProtobuf(TokenResponse.UserInfo user) {
        return UserRpcResponse.newBuilder()
                .setId(user.getId())
                .setUsername(user.getUsername())
                .setEmail(user.getEmail() != null ? user.getEmail() : "")
                .setNickname(user.getNickname() != null ? user.getNickname() : "")
                .setAvatar(user.getAvatar() != null ? user.getAvatar() : "")
                .addAllRoles(user.getRoles() != null ? user.getRoles() : Collections.emptySet())
                .build();
    }

    private static long toEpochMilli(LocalDateTime ldt) {
        return ldt != null ? ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : 0L;
    }
}
