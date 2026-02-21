# Dubbo RPC 客户端调用示例

本文档说明如何在其他微服务中调用认证服务的 RPC 接口。

## 1. 添加依赖

在需要调用认证服务的微服务 `pom.xml` 中添加：

```xml
<dependencies>
    <!-- 认证服务 API 模块 -->
    <dependency>
        <groupId>cn.wanyj.auth</groupId>
        <artifactId>auth-service-api</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>

    <!-- Dubbo Spring Boot Starter -->
    <dependency>
        <groupId>org.apache.dubbo</groupId>
        <artifactId>dubbo-spring-boot-starter</artifactId>
        <version>3.3.2</version>
    </dependency>

    <!-- Dubbo Nacos Registry -->
    <dependency>
        <groupId>org.apache.dubbo</groupId>
        <artifactId>dubbo-registry-nacos</artifactId>
        <version>3.3.2</version>
    </dependency>

    <!-- Nacos Client -->
    <dependency>
        <groupId>com.alibaba.nacos</groupId>
        <artifactId>nacos-client</artifactId>
        <version>2.4.2</version>
    </dependency>
</dependencies>
```

## 2. 配置 application.yaml

```yaml
dubbo:
  application:
    name: your-service-name
  registry:
    address: nacos://localhost:8848
  protocol:
    name: tri
  consumer:
    timeout: 5000
    check: false  # 启动时不检查服务提供者
```

## 3. 调用示例

### 3.1 认证服务调用

```java
package com.example.service;

import cn.wanyj.auth.api.auth.AuthRpcService;
import cn.wanyj.auth.api.model.*;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

/**
 * 订单服务调用认证服务示例
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl {

    // 使用 @DubboReference 注入 RPC 服务
    @DubboReference(version = "1.0.0", timeout = 5000)
    private AuthRpcService authRpcService;

    /**
     * 处理订单 - 验证用户和权限
     */
    public void processOrder(Long userId) {
        // 1. 获取用户信息
        UserRpcResponse user = authRpcService.getUserById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 2. 检查用户状态
        if (user.getStatus() == 0) {
            throw new RuntimeException("用户已被禁用");
        }

        // 3. 检查权限
        if (!authRpcService.hasPermission(userId, "order:create")) {
            throw new RuntimeException("无下单权限");
        }

        // 4. 业务处理...
        System.out.println("用户 " + user.getUsername() + " 创建订单成功");
    }

    /**
     * 验证令牌
     */
    public boolean validateToken(String token) {
        TokenValidationResult result = authRpcService.validateToken(token);
        return result.getValid();
    }

    /**
     * 获取用户权限列表
     */
    public Set<String> getUserPermissions(Long userId) {
        return authRpcService.getUserPermissions(userId);
    }
}
```

### 3.2 令牌服务调用

```java
package com.example.service;

import cn.wanyj.auth.api.token.TokenRpcService;
import cn.wanyj.auth.api.model.TokenRpcResponse;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

/**
 * 令牌服务调用示例
 */
@Service
@RequiredArgsConstructor
public class TokenServiceImpl {

    @DubboReference(version = "1.0.0", timeout = 3000)
    private TokenRpcService tokenRpcService;

    /**
     * 为用户生成令牌
     */
    public String generateTokenForUser(Long userId) {
        TokenRpcResponse token = tokenRpcService.generateToken(userId, 3600L);
        if (token != null) {
            return token.getAccessToken();
        }
        throw new RuntimeException("生成令牌失败");
    }

    /**
     * 解析令牌获取用户ID
     */
    public Long parseToken(String token) {
        return tokenRpcService.parseToken(token);
    }

    /**
     * 强制用户登出
     */
    public void forceLogout(Long userId) {
        tokenRpcService.revokeAllTokens(userId);
    }
}
```

## 4. 启动类配置

```java
package com.example;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

## 5. API 接口说明

### AuthRpcService

| 方法 | 说明 |
|------|------|
| `authenticate(LoginRpcRequest)` | 验证用户凭据 |
| `validateToken(String)` | 验证令牌有效性 |
| `getUserById(Long)` | 根据ID获取用户 |
| `getUserByUsername(String)` | 根据用户名获取用户 |
| `hasPermission(Long, String)` | 检查用户权限 |
| `hasRole(Long, String)` | 检查用户角色 |
| `getUserPermissions(Long)` | 获取用户所有权限 |
| `getUserRoles(Long)` | 获取用户所有角色 |

### TokenRpcService

| 方法 | 说明 |
|------|------|
| `generateToken(Long, Long)` | 生成访问令牌 |
| `parseToken(String)` | 解析令牌获取用户ID |
| `revokeAllTokens(Long)` | 撤销用户所有令牌 |

## 6. 异常处理

Dubbo RPC 调用可能会抛出以下异常：

```java
try {
    UserRpcResponse user = authRpcService.getUserById(userId);
} catch (org.apache.dubbo.rpc.RpcException e) {
    // RPC 调用异常
    logger.error("RPC 调用失败", e);
} catch (Exception e) {
    // 其他异常
    logger.error("未知异常", e);
}
```

## 7. 版本控制

使用 Dubbo 的版本机制来实现服务升级：

```java
// 调用指定版本的服务
@DubboReference(version = "1.0.0")
private AuthRpcService authRpcService;
```

## 8. 超时配置

```java
// 方法级超时配置
@DubboReference(timeout = 3000, retries = 1)
private TokenRpcService tokenRpcService;

// 或者在配置文件中全局配置
dubbo:
  consumer:
    timeout: 5000
    retries: 2
```
