package cn.wanyj.auth.config;

import io.github.xiapxx.starter.uidgenerator.properties.UGWorkerDataSourceConfig;
import io.github.xiapxx.starter.uidgenerator.worker.MysqlWorkerIdAssigner;
import io.github.xiapxx.uid.generator.api.UidGenerator;
import io.github.xiapxx.uid.generator.api.worker.WorkerIdAssigner;
import io.github.xiapxx.uid.generator.impl.core.CachedUidGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit UID generator configuration to avoid relying on starter auto-configuration compatibility.
 */
@Configuration
public class UidGeneratorConfig {

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Bean
    public WorkerIdAssigner workerIdAssigner() {
        UGWorkerDataSourceConfig dataSourceConfig = new UGWorkerDataSourceConfig();
        dataSourceConfig.setDriverClass(driverClassName);
        dataSourceConfig.setUrl(url);
        dataSourceConfig.setUsername(username);
        dataSourceConfig.setPassword(password);
        return new MysqlWorkerIdAssigner(dataSourceConfig);
    }

    @Bean(initMethod = "init", destroyMethod = "destroy")
    public UidGenerator uidGenerator(WorkerIdAssigner workerIdAssigner) {
        CachedUidGenerator uidGenerator = new CachedUidGenerator();
        uidGenerator.setTimeBits(28);
        uidGenerator.setWorkerBits(22);
        uidGenerator.setSeqBits(13);
        uidGenerator.setEpochStr("2025-03-07");
        uidGenerator.setBoostPower(3);
        uidGenerator.setPaddingFactor(50);
        uidGenerator.setWorkerIdAssigner(workerIdAssigner);
        return uidGenerator;
    }
}
