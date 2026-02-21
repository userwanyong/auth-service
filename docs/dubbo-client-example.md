# Dubbo RPC 客户端调用指南

本文档说明如何在其他微服务中通过 Dubbo RPC 调用认证服务。

## 架构说明

```
┌─────────────────┐     Dubbo RPC (Triple)     ┌─────────────────┐
│   其他微服务      │ ──────────────────────────> │   认证服务        │
│ (订单/商品/库存...)│   Protobuf 序列化          │  auth-service   │
└─────────────────┘                           └─────────────────┘
        │                                              │
        │ 依赖 auth-service-api.jar                    │
        │ (包含 proto 生成的接口和消息类)               │
        └──────────────────────────────────────────────┘
```

## 快速开始

### 1. 添加依赖

```xml
<dependencies>
    <!-- 认证服务 API（包含 proto 生成的接口和消息类） -->
    <dependency>
        <groupId>cn.wanyj.auth</groupId>
        <artifactId>auth-service-api</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>

    <!-- Dubbo -->
    <dependency>
        <groupId>org.apache.dubbo</groupId>
        <artifactId>dubbo-spring-boot-starter</artifactId>
        <version>3.3.2</version>
    </dependency>

    <!-- Nacos 注册中心 -->
    <dependency>
        <groupId>org.apache.dubbo</groupId>
        <artifactId>dubbo-registry-nacos</artifactId>
        <version>3.3.2</version>
    </dependency>
    <dependency>
        <groupId>com.alibaba.nacos</groupId>
        <artifactId>nacos-client</artifactId>
        <version>2.4.2</version>
    </dependency>

    <!-- Protobuf -->
    <dependency>
        <groupId>com.google.protobuf</groupId>
        <artifactId>protobuf-java</artifactId>
        <version>3.25.2</version>
    </dependency>
    <dependency>
        <groupId>org.apache.dubbo</groupId>
        <artifactId>dubbo-rpc-triple</artifactId>
        <version>3.3.2</version>
    </dependency>
</dependencies>
```

### 2. 配置 Dubbo

```yaml
dubbo:
  application:
    name: your-service-name
  registry:
    address: nacos://localhost:8848
    username: nacos
    password: nacos
  consumer:
    timeout: 5000
    check: false
```

### 3. 启用 Dubbo

```java
@SpringBootApplication
@EnableDubbo
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

---

## API 接口总览

### 认证服务 (AuthRpcServiceProtobuf)

| 方法 | 功能 | 输入 | 输出 |
|------|------|------|------|
| `authenticate` | 用户登录验证 | `LoginRpcRequest` | `AuthResult` |
| `validateToken` | 验证令牌（含用户信息） | `StringValue` | `TokenValidationResult` |
| `getUserById` | 根据ID获取用户 | `Int64Value` | `UserRpcResponse` |
| `getUserByUsername` | 根据用户名获取用户 | `StringValue` | `UserRpcResponse` |
| `hasPermission` | 检查用户权限 | `PermissionCheckRequest` | `BoolValue` |
| `hasRole` | 检查用户角色 | `RoleCheckRequest` | `BoolValue` |
| `getUserPermissions` | 获取用户权限列表 | `Int64Value` | `StringListResponse` |
| `getUserRoles` | 获取用户角色列表 | `Int64Value` | `StringListResponse` |

### 令牌服务 (TokenRpcServiceProtobuf)

| 方法 | 功能 | 输入 | 输出 |
|------|------|------|------|
| `generateToken` | 为用户生成令牌 | `TokenGenerationRequest` | `TokenRpcResponse` |
| `parseToken` | 解析令牌获取用户ID | `StringValue` | `Int64Value` |
| `revokeAllTokens` | 撤销用户所有令牌 | `Int64Value` | `Empty` |

---

## 调用示例

### 注入服务

```java
import cn.wanyj.auth.api.protobuf.AuthRpcServiceProtobuf;
import cn.wanyj.auth.api.protobuf.TokenRpcServiceProtobuf;
import org.apache.dubbo.config.annotation.DubboReference;

@Service
public class OrderService {

    @DubboReference(version = "1.0.0", protocol = "tri", timeout = 5000)
    private AuthRpcServiceProtobuf authService;

    @DubboReference(version = "1.0.0", protocol = "tri", timeout = 3000)
    private TokenRpcServiceProtobuf tokenService;
}
```

### 1. 用户认证

```java
public boolean login(String username, String password) {
    LoginRpcRequest request = LoginRpcRequest.newBuilder()
        .setUsername(username)
        .setPassword(password)
        .build();

    AuthResult result = authService.authenticate(request);

    if (result.getSuccess()) {
        System.out.println("登录成功，用户ID: " + result.getUserId());
        return true;
    } else {
        System.out.println("登录失败: " + result.getMessage());
        return false;
    }
}
```

### 2. 验证令牌（含用户信息）

```java
public TokenInfo validateToken(String token) {
    StringValue tokenWrapper = StringValue.newBuilder().setValue(token).build();
    TokenValidationResult result = authService.validateToken(tokenWrapper);

    if (result.getValid()) {
        return new TokenInfo(
            result.getUserId(),
            result.getUsername(),
            result.getRolesList(),
            result.getPermissionsList(),
            result.getExpiresAt()
        );
    }
    return null;
}
```

### 3. 获取用户信息

```java
// 根据用户ID获取
public User getUserById(Long userId) {
    Int64Value userIdWrapper = Int64Value.newBuilder().setValue(userId).build();
    UserRpcResponse user = authService.getUserById(userIdWrapper);

    if (user == null) {
        throw new RuntimeException("用户不存在");
    }

    return new User(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.getPhone(),
        user.getNickname(),
        user.getAvatar(),
        user.getStatus(),
        user.getRolesList(),
        user.getPermissionsList()
    );
}

// 根据用户名获取
public User getUserByUsername(String username) {
    StringValue usernameWrapper = StringValue.newBuilder().setValue(username).build();
    UserRpcResponse user = authService.getUserByUsername(usernameWrapper);
    // ... 同上
}
```

### 4. 权限检查

```java
// 检查单个权限
public boolean checkPermission(Long userId, String permission) {
    PermissionCheckRequest request = PermissionCheckRequest.newBuilder()
        .setUserId(userId)
        .setPermission(permission)
        .build();

    BoolValue result = authService.hasPermission(request);
    return result.getValue();
}

// 获取所有权限
public List<String> getAllPermissions(Long userId) {
    Int64Value userIdWrapper = Int64Value.newBuilder().setValue(userId).build();
    StringListResponse result = authService.getUserPermissions(userIdWrapper);
    return result.getValuesList();
}

// 获取所有角色
public List<String> getAllRoles(Long userId) {
    Int64Value userIdWrapper = Int64Value.newBuilder().setValue(userId).build();
    StringListResponse result = authService.getUserRoles(userIdWrapper);
    return result.getValuesList();
}
```

### 5. 生成令牌

```java
public String generateToken(Long userId, long expirationSeconds) {
    TokenGenerationRequest request = TokenGenerationRequest.newBuilder()
        .setUserId(userId)
        .setExpiration(expirationSeconds)
        .build();

    TokenRpcResponse token = tokenService.generateToken(request);

    if (token != null) {
        return token.getAccessToken();
    }
    throw new RuntimeException("生成令牌失败");
}
```

### 6. 解析令牌

```java
public Long parseToken(String token) {
    StringValue tokenWrapper = StringValue.newBuilder().setValue(token).build();
    Int64Value result = tokenService.parseToken(tokenWrapper);
    return result.getValue();
}
```

### 7. 撤销令牌

```java
public void forceLogout(Long userId) {
    Int64Value userIdWrapper = Int64Value.newBuilder().setValue(userId).build();
    tokenService.revokeAllTokens(userIdWrapper);
}
```

---

## Protobuf 消息类型

### 基本类型包装

| Java 类型 | Protobuf 类型 | 创建方法 |
|-----------|---------------|----------|
| `String` | `StringValue` | `StringValue.newBuilder().setValue("xxx").build()` |
| `Long` | `Int64Value` | `Int64Value.newBuilder().setValue(123L).build()` |
| `Boolean` | `BoolValue` | `BoolValue.newBuilder().setValue(true).build()` |
| `void` | `Empty` | `Empty.newBuilder().build()` |

### 复杂类型

```java
// 列表获取使用 getXXXList()
List<String> roles = result.getRolesList();
List<String> permissions = result.getPermissionsList();
List<String> values = response.getValuesList();

// 创建复杂对象使用 Builder
LoginRpcRequest request = LoginRpcRequest.newBuilder()
    .setUsername("admin")
    .setPassword("123456")
    .build();
```

---

## 完整业务示例

### 订单服务：下单前权限验证

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    @DubboReference(version = "1.0.0", protocol = "tri")
    private AuthRpcServiceProtobuf authService;

    public void createOrder(Long userId, OrderRequest orderReq) {
        // 1. 获取用户信息
        Int64Value userIdWrapper = Int64Value.newBuilder().setValue(userId).build();
        UserRpcResponse user = authService.getUserById(userIdWrapper);

        if (user == null || user.getStatus() == 0) {
            throw new BusinessException("用户不存在或已禁用");
        }

        // 2. 检查下单权限
        PermissionCheckRequest permReq = PermissionCheckRequest.newBuilder()
            .setUserId(userId)
            .setPermission("order:create")
            .build();
        BoolValue hasPermission = authService.hasPermission(permReq);

        if (!hasPermission.getValue()) {
            throw new BusinessException("无下单权限");
        }

        // 3. 执行业务逻辑
        // ...
    }
}
```

### 网关服务：令牌验证与用户信息获取

```java
@Component
public class AuthFilter implements GlobalFilter {

    @DubboReference(version = "1.0.0", protocol = "tri")
    private AuthRpcServiceProtobuf authService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);

            // 验证令牌并获取用户信息
            StringValue tokenWrapper = StringValue.newBuilder().setValue(token).build();
            TokenValidationResult result = authService.validateToken(tokenWrapper);

            if (result.getValid()) {
                // 将用户信息添加到请求头
                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", String.valueOf(result.getUserId()))
                    .header("X-Username", result.getUsername())
                    .header("X-Roles", String.join(",", result.getRolesList()))
                    .header("X-Permissions", String.join(",", result.getPermissionsList()))
                    .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            }
        }

        return chain.filter(exchange);
    }
}
```

---

## 注意事项

### 1. 参数必须包装

```java
// ❌ 错误
authService.getUserById(123L);

// ✅ 正确
Int64Value wrapper = Int64Value.newBuilder().setValue(123L).build();
authService.getUserById(wrapper);
```

### 2. 集合使用 getXXXList()

```java
// ✅ 正确
List<String> roles = result.getRolesList();
List<String> permissions = result.getPermissionsList();
```

### 3. 空值检查

```java
UserRpcResponse user = authService.getUserById(userIdWrapper);
if (user == null) {
    throw new RuntimeException("用户不存在");
}
```

### 4. revokeAllTokens 的限制

`revokeAllTokens` 只清除 Redis 中的 `refreshToken`，不会将已发放的 `accessToken` 加入黑名单。如需立即撤销 `accessToken`，请使用 HTTP 登出接口。

---

## 异常处理

```java
try {
    Int64Value userIdWrapper = Int64Value.newBuilder().setValue(userId).build();
    UserRpcResponse user = authService.getUserById(userIdWrapper);
} catch (RpcException e) {
    // RPC 调用失败
    log.error("RPC 调用失败: code={}, message={}", e.getCode(), e.getMessage());
} catch (Exception e) {
    // 其他异常
    log.error("未知异常", e);
}
```

---

## 配置选项

```yaml
dubbo:
  consumer:
    timeout: 5000          # 默认超时时间（毫秒）
    retries: 2             # 失败重试次数
    check: false           # 启动时不检查服务提供者

# 方法级配置
@DubboReference(timeout = 3000, retries = 1)
private TokenRpcServiceProtobuf tokenService;
```
