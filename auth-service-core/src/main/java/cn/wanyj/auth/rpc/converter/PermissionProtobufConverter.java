package cn.wanyj.auth.rpc.converter;

import cn.wanyj.auth.api.protobuf.PermissionRpcResponse;
import cn.wanyj.auth.dto.response.PermissionResponse;

/**
 * Permission Protobuf 转换器
 * <p>集中管理 {@link PermissionResponse} 到 {@link PermissionRpcResponse} 的转换。</p>
 *
 * @author wanyj
 */
public final class PermissionProtobufConverter {

    private PermissionProtobufConverter() {
    }

    /**
     * PermissionResponse → PermissionRpcResponse
     */
    public static PermissionRpcResponse convertToProtobuf(PermissionResponse permission) {
        PermissionRpcResponse.Builder builder = PermissionRpcResponse.newBuilder()
                .setId(permission.getId())
                .setCode(permission.getCode())
                .setName(permission.getName())
                .setResource(permission.getResource() != null ? permission.getResource() : "")
                .setAction(permission.getAction() != null ? permission.getAction() : "");

        if (permission.getDescription() != null) {
            builder.setDescription(permission.getDescription());
        }

        return builder.build();
    }
}
