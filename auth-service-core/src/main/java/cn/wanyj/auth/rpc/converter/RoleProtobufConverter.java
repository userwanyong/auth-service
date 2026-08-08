package cn.wanyj.auth.rpc.converter;

import cn.wanyj.auth.api.protobuf.RoleRpcResponse;
import cn.wanyj.auth.dto.response.RoleResponse;

/**
 * Role Protobuf 转换器
 * <p>集中管理 {@link RoleResponse} 到 {@link RoleRpcResponse} 的转换。</p>
 *
 * @author wanyj
 */
public final class RoleProtobufConverter {

    private RoleProtobufConverter() {
    }

    /**
     * RoleResponse → RoleRpcResponse
     */
    public static RoleRpcResponse convertToProtobuf(RoleResponse role) {
        RoleRpcResponse.Builder builder = RoleRpcResponse.newBuilder()
                .setId(role.getId())
                .setCode(role.getCode())
                .setName(role.getName())
                .setStatus(role.getStatus());

        if (role.getDescription() != null) {
            builder.setDescription(role.getDescription());
        }

        if (role.getPermissions() != null) {
            builder.addAllPermissions(role.getPermissions());
        }

        return builder.build();
    }
}
