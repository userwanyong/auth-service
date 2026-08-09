package cn.wanyj.auth.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 客户端配置
 * <p>仅在配置了 aliyun.oss.endpoint 时创建 {@link OSS} Bean，
 * 避免未配置 OSS 时应用启动失败。</p>
 *
 * @author wanyj
 */
@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "aliyun.oss", name = "endpoint")
    public OSS ossClient(OssProperties props) {
        return new OSSClientBuilder().build(
                props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());
    }
}
