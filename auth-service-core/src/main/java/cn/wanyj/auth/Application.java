package cn.wanyj.auth;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 认证服务启动类
 * 支持 REST API 和 Dubbo RPC
 *
 * @author wanyj
 */
@SpringBootApplication
@EnableDubbo
@MapperScan("cn.wanyj.auth.mapper")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
