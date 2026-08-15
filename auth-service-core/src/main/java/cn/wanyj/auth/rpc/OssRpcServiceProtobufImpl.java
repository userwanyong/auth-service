package cn.wanyj.auth.rpc;

import cn.wanyj.auth.api.protobuf.*;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.service.OssService;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 文件上传服务 RPC 实现 - Protobuf IDL 模式
 * <p>上传校验（大小、扩展名白名单）与 OSS 转存全部复用 {@link OssService}，
 * 本类只做参数转换，不直接访问外部存储。</p>
 *
 * @author wanyj
 */
@Slf4j
@DubboService(
    version = "1.0.0",
    timeout = 5000,
    retries = 2,
    protocol = "tri"
)
@RequiredArgsConstructor
public class OssRpcServiceProtobufImpl extends DubboOssRpcServiceProtobufTriple.OssRpcServiceProtobufImplBase {

    private final OssService ossService;

    @Override
    public UploadAvatarRpcResponse uploadAvatar(UploadAvatarRpcRequest request) {
        ByteString data = request.getData();
        log.info("RPC uploadAvatar: tenantId={}, userId={}, filename={}, size={}",
            request.getTenantId(), request.getUserId(), request.getFilename(),
            data != null ? data.size() : 0);
        try {
            Long tenantId = Long.parseLong(request.getTenantId());
            Long userId = Long.parseLong(request.getUserId());

            // contentType 缺省时退回通用二进制类型，避免 OSS 元数据为空
            String contentType = request.getContentType().isBlank()
                ? "application/octet-stream"
                : request.getContentType();

            String url = ossService.uploadAvatar(
                request.getFilename(),
                contentType,
                data != null ? data.toByteArray() : null,
                tenantId,
                userId);

            return UploadAvatarRpcResponse.newBuilder().setUrl(url).build();
        } catch (BusinessException e) {
            log.warn("Upload avatar failed: {}", e.getMessage());
            return UploadAvatarRpcResponse.getDefaultInstance();
        } catch (Exception e) {
            log.error("Upload avatar error", e);
            return UploadAvatarRpcResponse.getDefaultInstance();
        }
    }
}
