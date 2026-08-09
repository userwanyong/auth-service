package cn.wanyj.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置（头像上传）
 * <p>通过环境变量注入：OSS_ENDPOINT / OSS_BUCKET / OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET。
 * 未配置 endpoint 时，OSS Bean 不创建，上传接口返回「OSS 未配置」。</p>
 *
 * @author wanyj
 */
@Data
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

    /** OSS endpoint，例如 https://oss-cn-shanghai.aliyuncs.com */
    private String endpoint;

    /** bucket 名称 */
    private String bucket;

    private String accessKeyId;

    private String accessKeySecret;

    /** object key 前缀，默认 avatar */
    private String objectPrefix = "avatar";
}
