package cn.wanyj.auth.service;

import cn.wanyj.auth.config.OssProperties;
import cn.wanyj.auth.exception.BusinessException;
import cn.wanyj.auth.exception.ErrorCode;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.regex.Pattern;

/**
 * OSS 上传服务（头像）
 * <p>后端代理上传：前端选文件 → POST 到后端 → 本服务转存 OSS → 返回可访问 URL。
 * objectKey 规则：{prefix}/{tenantId}/{userId}/{timestamp}{ext}，天然按租户/用户隔离。</p>
 *
 * @author wanyj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssService {

    private final OssProperties props;
    /** 用 ObjectProvider 避免 OSS Bean 不存在（未配置）时启动失败 */
    private final ObjectProvider<OSS> ossProvider;

    private static final long MAX_SIZE = 2 * 1024 * 1024; // 2MB
    private static final Pattern IMG_EXT =
            Pattern.compile("\\.(jpg|jpeg|png|gif|webp)$", Pattern.CASE_INSENSITIVE);

    /**
     * 上传头像到 OSS，返回可访问 URL。
     *
     * @param userId 头像归属用户 ID（由调用方决定：编辑场景传目标用户，新建场景传操作者），
     *               objectKey 按此 id 隔离到 {prefix}/{tenantId}/{userId}/
     */
    public String uploadAvatar(MultipartFile file, Long tenantId, Long userId) {
        OSS oss = ossProvider.getIfAvailable();
        if (oss == null || props.getEndpoint() == null || props.getEndpoint().isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "OSS 未配置，无法上传文件");
        }

        validate(file);

        String ext = extOf(file.getOriginalFilename());
        String objectKey = props.getObjectPrefix() + "/" + tenantId + "/" + userId + "/"
                + System.currentTimeMillis() + ext;

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());
            oss.putObject(props.getBucket(), objectKey, file.getInputStream(), metadata);
            String url = publicUrl(objectKey);
            log.info("Avatar uploaded: tenant={}, user={}, url={}", tenantId, userId, url);
            return url;
        } catch (Exception e) {
            log.error("OSS upload failed", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "文件上传失败: " + e.getMessage());
        }
    }

    /** 拼接公开访问 URL：https://{bucket}.{endpoint去协议}/{objectKey} */
    private String publicUrl(String objectKey) {
        String host = props.getEndpoint().replaceFirst("^https?://", "");
        return "https://" + props.getBucket() + "." + host + "/" + objectKey;
    }

    /**
     * 删除指定头像 URL 对应的 OSS 对象（用于头像被替换时清理旧图）。
     * <p>仅当 URL 属于本系统 bucket 且位于头像前缀下时才删除，避免误删外部图床或越权删除。
     * OSS 未配置或删除失败时静默（仅记日志），不抛异常，不阻断业务流程。</p>
     *
     * @param url 旧头像的完整访问 URL，可为 null
     */
    public void deleteAvatarByUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String objectKey = resolveOwnObjectKey(url);
        if (objectKey == null) {
            return;
        }
        deleteObjectQuietly(objectKey);
    }

    /**
     * 解析本系统头像 URL 为 OSS objectKey。
     * <p>URL 必须以本 bucket 的公开访问前缀开头，且 objectKey 位于头像前缀下；
     * 否则返回 null（非本系统资源，不删除）。</p>
     */
    private String resolveOwnObjectKey(String url) {
        if (props.getEndpoint() == null || props.getEndpoint().isBlank()
                || props.getBucket() == null || props.getBucket().isBlank()) {
            return null;
        }
        String host = props.getEndpoint().replaceFirst("^https?://", "");
        String urlPrefix = "https://" + props.getBucket() + "." + host + "/";
        if (!url.startsWith(urlPrefix)) {
            return null;
        }
        String objectKey = url.substring(urlPrefix.length());
        if (!objectKey.startsWith(props.getObjectPrefix() + "/")) {
            return null;
        }
        return objectKey;
    }

    /** 安静删除单个 OSS 对象（失败仅记日志，不抛异常） */
    private void deleteObjectQuietly(String objectKey) {
        OSS oss = ossProvider.getIfAvailable();
        if (oss == null || props.getEndpoint() == null || props.getEndpoint().isBlank()) {
            return;
        }
        try {
            oss.deleteObject(props.getBucket(), objectKey);
            log.info("OSS object deleted: {}", objectKey);
        } catch (Exception e) {
            log.warn("Failed to delete OSS object {}: {}", objectKey, e.getMessage());
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件过大，最大 2MB");
        }
        String name = file.getOriginalFilename();
        if (name == null || !IMG_EXT.matcher(name).find()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 jpg/jpeg/png/gif/webp 格式");
        }
    }

    private String extOf(String name) {
        int i = name == null ? -1 : name.lastIndexOf('.');
        return i >= 0 ? name.substring(i).toLowerCase() : "";
    }
}
