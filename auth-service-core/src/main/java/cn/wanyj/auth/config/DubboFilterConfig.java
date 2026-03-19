package cn.wanyj.auth.config;

import cn.wanyj.auth.filter.RpcAuthFilter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dubbo Filter Configuration
 * 将 Spring 管理的 RPC service token 注入到 Dubbo SPI Filter
 * @author wanyj
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DubboFilterConfig {

    @Value("${dubbo.rpc.service-token:}")
    private String serviceToken;

    @PostConstruct
    public void init() {
        RpcAuthFilter.setServiceToken(serviceToken);
        log.info("RPC service token configured: {}", serviceToken != null && !serviceToken.isBlank());
    }
}
