# auth-service RPC 集成指南（AI 专用文档）

> **本文档为自包含文档**：外部系统的开发者或 AI 助手只需阅读本文档，即可完成对 auth-service 的 RPC 集成与调用，**无需分析 auth-service 的源码**。
>
> 本文档与 `auth-service-api/src/main/proto/auth/auth_service.proto`（接口唯一定义源）保持同步。若两者出现不一致，以 proto 文件为准并提 Issue。

---

## 目录

- [0. 30 秒摘要](#0-30-秒摘要)
- [1. 服务定位信息](#1-服务定位信息)
- [2. 获取 API 依赖](#2-获取-api-依赖)
- [3. 消费方工程配置（完整可复制）](#3-消费方工程配置完整可复制)
- [4. 服务鉴权（rpc-service-token）](#4-服务鉴权rpc-service-token)
- [5. 必须遵守的调用约定（8 条规则）](#5-必须遵守的调用约定8-条规则)
- [6. 常用消息字典](#6-常用消息字典)
- [7. 服务与方法详解](#7-服务与方法详解)
- [8. 端到端集成场景](#8-端到端集成场景)
- [9. 调用失败排查表](#9-调用失败排查表)
- [10. 集成自检清单](#10-集成自检清单)

---

## 0. 30 秒摘要

auth-service 是一个多租户认证授权微服务，通过 **Dubbo 3 Triple 协议（HTTP/2 + Protobuf）** 对外提供 9 个 RPC 服务，注册在 **Nacos**。

外部系统接入只需 5 步：

1. 把 `auth-service-api` 安装到你的 Maven 仓库并引入依赖（[第 2 节](#2-获取-api-依赖)）
2. 消费方工程加入 Dubbo + Nacos 依赖与配置（[第 3 节](#3-消费方工程配置完整可复制)）
3. 每次调用前在 Dubbo attachment 中设置 `rpc-service-token`（[第 4 节](#4-服务鉴权rpc-service-token)）
4. 用 `@DubboReference(version = "1.0.0")` 注入接口（如 `AuthRpcServiceProtobuf`）
5. 按第 5 节的约定构造请求、判断成败（**租户一律传 `tenantUid` 随机串，其余 ID 传数字字符串**；失败不抛业务异常，按响应字段判断）

## 1. 服务定位信息

| 项 | 值 |
|----|----|
| 服务应用名（Nacos 注册名） | `auth-service` |
| RPC 框架 | Apache Dubbo 3.3.2 |
| 协议 | Triple（`tri`，基于 HTTP/2） |
| 序列化 | Protobuf（由接口自动协商，无需配置） |
| RPC 端口 | 20880（消费方通过 Nacos 自动发现，无需直连） |
| 服务版本 | **`1.0.0`**（所有服务的 `@DubboReference` 必须带 `version = "1.0.0"`） |
| 服务分组 | 无（不要设置 `group`） |
| 注册中心 | Nacos，地址示例 `nacos://<nacos-host>:8848` |
| 接口包 | `cn.wanyj.auth.api.protobuf` |

### 9 个服务一览

| 接口（完整类名 = `cn.wanyj.auth.api.protobuf.` + 下表名称） | 能力域 |
|---|---|
| `AuthRpcServiceProtobuf` | 注册 / 登录 / 用户与权限查询 / 令牌刷新登出 / 改密 / 验证码登录 |
| `TokenRpcServiceProtobuf` | 令牌生成 / 解析（网关鉴权首选）/ 撤销 |
| `UserRpcServiceProtobuf` | 用户更新 / 状态 / 分配角色 / 删除 |
| `RoleRpcServiceProtobuf` | 角色 CRUD / 分配权限 |
| `PermissionRpcServiceProtobuf` | 权限查询 / 创建 / 删除 |
| `OAuthRpcServiceProtobuf` | OAuth 登录与绑定编排 |
| `ContactBindingRpcServiceProtobuf` | 邮箱 / 手机绑定与解绑 |
| `LoginMethodRpcServiceProtobuf` | 登录方式配置（平台 / 租户 / 公开三层） |
| `OssRpcServiceProtobuf` | 头像上传 |

每个接口的所有方法都有**同步**与**异步**两种形态：`getUserById(request)` 与 `getUserByIdAsync(request)`（返回 `CompletableFuture<T>`）。下文仅列同步形态。

## 2. 获取 API 依赖

`auth-service-api` **不在 Maven 中央仓库**，须先安装到本地仓库或你的私服（Nexus/Artifactory）：

```bash
# 方式一：克隆本仓库后本地安装（消费方开发者执行一次）
git clone <本仓库地址>
cd auth-service
mvn install -pl auth-service-api -am -DskipTests
```

然后在消费方 `pom.xml` 中引入：

```xml
<dependency>
    <groupId>cn.wanyj.auth</groupId>
    <artifactId>auth-service-api</artifactId>
    <version>1.1</version>
</dependency>
```

> 该 jar 已包含：proto 生成的全部消息类、9 个服务接口（如 `AuthRpcServiceProtobuf`）、Triple Stub。传递依赖含 `dubbo`、`dubbo-rpc-triple`、`protobuf-java`，无需重复声明这三项。

## 3. 消费方工程配置（完整可复制）

### 3.1 Maven 依赖

```xml
<dependencies>
    <!-- auth-service API（见第 2 节先安装） -->
    <dependency>
        <groupId>cn.wanyj.auth</groupId>
        <artifactId>auth-service-api</artifactId>
        <version>1.0</version>
    </dependency>

    <!-- Dubbo Spring Boot -->
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
</dependencies>
```

### 3.2 application.yaml

```yaml
dubbo:
  application:
    name: your-service-name          # 你的服务名（必改）
  registry:
    address: nacos://192.168.1.10:8848   # 与 auth-service 相同的 Nacos（必改）
    username: nacos
    password: nacos
  consumer:
    timeout: 5000                    # 毫秒
    check: false                     # 启动时不强制检查提供者在线（推荐）
    retries: 0                       # 全局关闭重试，写操作安全（见规则 8）
```

### 3.3 启动类

```java
@SpringBootApplication
@EnableDubbo   // org.apache.dubbo.config.spring.context.annotation.EnableDubbo
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### 3.4 注入服务

```java
import cn.wanyj.auth.api.protobuf.AuthRpcServiceProtobuf;
import org.apache.dubbo.config.annotation.DubboReference;

@Service
public class AuthClient {

    @DubboReference(version = "1.0.0", timeout = 5000)   // version 必须是 1.0.0
    private AuthRpcServiceProtobuf authRpcService;
}
```

## 4. 服务鉴权（rpc-service-token）

auth-service 的提供端 Filter 会校验每次调用的 attachment **`rpc-service-token`**，其值须与 auth-service 部署时设置的环境变量 `RPC_SERVICE_TOKEN` 完全一致。

- **未携带或不一致** → 调用抛出 `org.apache.dubbo.rpc.RpcException`（FORBIDDEN_EXCEPTION 类型），message 为 `RPC service token is invalid or missing`
- **auth-service 未配置 `RPC_SERVICE_TOKEN`**（开发环境）→ 跳过鉴权，可不传
- 该 token 由 auth-service 运维方提供给你，请通过环境变量注入你的服务，**不要硬编码**

### 每次调用前设置（标准做法）

```java
import org.apache.dubbo.rpc.RpcContext;

// 在发起 RPC 调用前（同一线程）：
RpcContext.getClientAttachment().setAttachment("rpc-service-token", rpcToken);
UserRpcResponse user = authRpcService.getUserById(request);
```

> Dubbo 3.x 用 `RpcContext.getClientAttachment()`；旧 API `RpcContext.getContext().setAttachment(...)` 也兼容。attachment 是 ThreadLocal 的，建议封装成统一入口（见 4.1）。

### 4.1 推荐封装：集中设置 token 与错误日志

```java
@Component
public class AuthGateway {

    @Value("${auth.rpc-token}")
    private String rpcToken;

    @DubboReference(version = "1.0.0", timeout = 5000)
    private AuthRpcServiceProtobuf authRpc;

    public UserRpcResponse getUserById(long userId, String tenantUid) {
        RpcContext.getClientAttachment().setAttachment("rpc-service-token", rpcToken);
        try {
            return authRpc.getUserById(UserByIdRequest.newBuilder()
                    .setUserId(String.valueOf(userId))
                    .setTenantUid(tenantUid)
                    .build());
        } catch (RpcException e) {
            // 网络失败 / 鉴权失败 / 超时：只有这一类是"抛异常"
            throw new IllegalStateException("auth-service RPC 调用失败: " + e.getMessage(), e);
        }
    }
}
```

## 5. 必须遵守的调用约定（8 条规则）

**这 8 条规则覆盖了所有已知的"调用失败/结果误判"陷阱，集成前请完整阅读。**

### 规则 1：业务 ID 传「数字字符串」，租户传 `tenantUid` 随机串

proto 中 `userId`、`roleId`、`permissionId` 等业务 ID 均为 `string` 类型（防止 JS 大整数精度丢失），服务端按 `Long.parseLong` 解析；租户统一用对外标识 `tenantUid`（8 位 [a-z0-9] 随机串，如 `"dm3a9x1f"`），服务端自行解析为内部数字 ID。

```java
// ✅ 正确
.setUserId("123456789012345")   // 业务 ID：数字的字符串形式
.setTenantUid("dm3a9x1f")       // 租户：对外随机串

// ❌ 错误：会按"失败"语义返回
.setUserId("")                  // 空串
.setUserId(null)                // proto3 string 无 null，等价于空串
.setTenantUid("1")              // 这不是 tenantUid！数字 ID 已不再对外使用
```

**除规则 3 列出的少数允许 `tenantUid` 留空的方法外，所有字段必填。** 传空串的后果是 `NumberFormatException` 被服务端捕获，按"失败"语义返回（见规则 4），不会抛异常给你，容易误判为"用户不存在"。

### 规则 2：租户唯一标识是 `tenantUid`（随机串）

所有 RPC 方法的租户参数统一为 `tenantUid`——8 位 [a-z0-9] 对外随机标识（如 `"dm3a9x1f"`）。内部数字 `tenantId` 已**不再出现在任何 RPC 入参/出参**中，服务端在内部自行完成 `tenantUid → tenantId` 解析。

获取 tenantUid：租户创建接口（HTTP 管理端）返回，或向 auth-service 运维方查询。传错/传空（必填方法）→ 按失败语义返回（message「租户无效或不存在」，错误码 1015）。

### 规则 3：`tenantUid` 非空即触发归属校验（防跨租户）

按 ID 操作资源的方法，服务端会校验「该资源是否属于你传的 `tenantUid` 对应的租户」。不属于时**不报错，按不存在处理**（返回空实例或 `success=false`），避免泄露资源存在性。

少数方法允许 `tenantUid` 留空：`revokeAllTokens`、`generateToken`、`getRoleById`/`updateRole`/`deleteRole`/`assignPermissions`、`getPermissionById`/`deletePermission`——留空=跳过归属校验，非空则必须有效。传错租户的表现与资源不存在完全相同，排查"明明有这个用户却查不到"时先核对 tenantUid。

### 规则 4：业务失败不抛异常，按响应形态判断成败

服务端已捕获所有业务异常（用户不存在、密码错误、租户无效等），转换为下表中的"失败响应"。**你只需要捕获 `RpcException`（网络/超时/鉴权），业务成败看响应字段：**

| 响应类型 | 判断成功 | 判断失败 | 覆盖的方法 |
|----------|----------|----------|-----------|
| `OperationResult` | `getSuccess() == true` | `success == false`，原因看 `getMessage()` | updateUser、updateUserStatus、assignRoles、deleteUser、updateRole、deleteRole、assignPermissions、deletePermission、logout、changePassword、sendCode、unbind、bindEmail、unbindEmail、bindPhone、unbindPhone、savePlatformConfig、saveTenantConfig |
| `RegisterRpcResult` / `AuthResult` / `LoginByCodeRpcResult` | `getSuccess() == true`，令牌/用户在字段中 | `success == false` + `message` | register、authenticate、loginByCode |
| `UserRpcResponse` | `getId() != 0` | **失败/不存在/跨租户/已禁用 → 默认空实例**：`id == 0` 且 `username` 为空串 | getUserById、getUserByUsername |
| `RoleRpcResponse` / `PermissionRpcResponse` | `getId() != 0` | 同上：`id == 0` | getRoleById、getRoleByCode、getPermissionById |
| `TokenRpcResponse`（generateToken / refreshToken） | `!getAccessToken().isEmpty()` | 失败 → 空实例（accessToken 为空串） | generateToken、refreshToken |
| `TokenValidationResult` | `getValid() == true` | `valid == false` | parseToken |
| `BoolValue` | `getValue() == true` | 任何失败 → `false`（含义=没有该权限） | hasPermission、hasRole |
| 列表型（`RoleListResponse` 等） | 列表可能为空也代表"无数据" | 失败 → 空列表（无法与无数据区分） | getAllRoles、getAllPermissions、getUserPermissions、getUserRoles、listBindings、searchUsers |
| `OAuthUrlRpcResponse` | `!getUrl().isEmpty()` | 失败 → 空实例（url 空串） | buildAuthorizeUrl、buildBindAuthorizeUrl |
| `OAuthCallbackRpcResult` | 登录流：`getLogin() == true`；绑定流：`getLogin() == false && getSuccess() == true` | `login == false` 且 `success == false`，原因看 `message` | handleCallback |
| `Empty` | 无成败信息（**静默执行，失败也不报**） | 无法判断 | revokeAllTokens |

### 规则 5：`UserRpcResponse` 中**已禁用用户不可见**

`getUserById` / `getUserByUsername` / `searchUsers` 对 `status == 0`（禁用）的用户返回空结果（与不存在同表现）。如需查询禁用用户，使用 HTTP 管理接口（需 ADMIN 权限）。

### 规则 6：`updateUser` 必须传字段掩码 `fields_to_update`

proto3 无法区分「未设置」与「零值」。`UpdateUserRpcRequest` 只更新出现在 `fields_to_update` 列表中的字段，其余保持不变：

```java
UpdateUserRpcRequest.newBuilder()
    .setUserId("123456")
    .setTenantUid("dm3a9x1f")
    .setNickname("新昵称")
    .setStatus(0)                    // 0 = 禁用。不进掩码则会被当作"未提供"而不生效
    .addFieldsToUpdate("nickname")   // 可选值见 7.3 节
    .addFieldsToUpdate("status")
    .build();
```

### 规则 7：令牌相关语义

- `parseToken` 只校验**Access Token**（黑名单+签名+过期），适合网关/微服务对每个请求鉴权；返回 `userId`、`tenantUid`、`roles`、`permissions`、`expiresAt`（epoch 毫秒）
- `refreshToken` 校验 Refresh Token 且与 Redis 单点存储匹配，成功**轮换**返回新令牌对（旧的立即失效）
- `generateToken` 的 `expiration` 单位是**秒**，`<= 0` 时使用服务端默认（900 秒）；`tenantUid` 留空表示不限定租户
- `revokeAllTokens` **只删除 Redis 中的 Refresh Token，不拉黑已发放的 Access Token**（Access Token 剩余生命周期内仍可解析），且静默执行不报失败

### 规则 8：写操作必须关闭重试

provider 默认 `retries = 2`（Token 服务为 1）。自动重试会导致**写操作重复执行**（如 register 重试撞用户名唯一约束、deleteUser 重复删除）。消费方必须：

```java
@DubboReference(version = "1.0.0", retries = 0)   // 所有写操作
private AuthRpcServiceProtobuf authRpcService;

// 或全局（推荐，见 3.2 配置）：
// dubbo.consumer.retries: 0
```

读操作（getUserById、hasPermission、parseToken 等）可以保留重试。

## 6. 常用消息字典

### UserRpcResponse（用户信息，多个方法返回）

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| `id` | `long` | 用户 ID（雪花 ID；**空实例时为 0**） |
| `username` / `email` / `phone` / `nickname` / `avatar` | `String` | 未设置时为空串 |
| `status` | `int` | 1-正常，0-禁用 |
| `roles` | `List<String>` | 角色编码，如 `ROLE_ADMIN`；用 `getRolesList()` |
| `permissions` | `List<String>` | 权限编码，如 `user:read`；用 `getPermissionsList()` |
| `tenantUid` | `String` | 所属租户对外标识（8 位随机串） |
| `emailVerified` / `phoneVerified` | `boolean` | 联系方式验证标记 |
| `lastLoginAt` / `createdAt` / `updatedAt` | `long` | epoch 毫秒，0=未知 |
| `realName` | `String` | 真实姓名 |
| `gender` | `int` | 0-未知，1-男，2-女 |
| `birthday` | `String` | ISO 日期 `yyyy-MM-dd`，空=未设置 |

### TokenRpcResponse（令牌对）

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| `accessToken` | `String` | 访问令牌（15 分钟）；**失败时空串** |
| `refreshToken` | `String` | 刷新令牌（7 天） |
| `expiresIn` | `long` | Access Token 有效秒数（默认 900） |

### TokenValidationResult（parseToken 返回）

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| `valid` | `boolean` | 令牌是否有效（唯一成败标志） |
| `userId` | `long` | 用户 ID |
| `username` | `String` | 用户名 |
| `tenantUid` | `String` | 租户对外标识（8 位随机串） |
| `roles` / `permissions` | `List<String>` | 角色 / 权限编码 |
| `expiresAt` | `long` | 过期时间（epoch 毫秒） |

### OperationResult（通用操作结果）

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| `success` | `boolean` | 操作是否成功 |
| `message` | `String` | 失败原因（中文），如「用户名已存在」 |

### LoginMethodRpcResponse（登录方式配置项）

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| `method` | `String` | 方式编码，如 `oauth:gitee` |
| `category` | `String` | password / email / sms / oauth |
| `displayName` | `String` | 中文显示名 |
| `enabled` | `int` | 平台级=平台开关；租户级=本租户开关 |
| `usePlatformConfig` | `int` | 仅租户级：1=用平台凭证，0=用自有凭证 |
| `hasConfig` | `boolean` | 生效凭证是否已配置 |
| `platformEnabled` | `boolean` | 仅租户级：平台是否已开启 |
| `platformLocked` | `boolean` | 仅平台级：是否锁定（password 恒真） |

### OAuthCallbackRpcResult（OAuth 回调结果）

| 字段 | Java 类型 | 说明 |
|------|-----------|------|
| `login` | `boolean` | true=登录流程（看 token/user）；false=绑定流程（看 success/message） |
| `success` | `boolean` | 绑定流程是否成功 |
| `message` | `String` | 提示信息 |
| `token` | `TokenRpcResponse` | 登录流程签发的令牌 |
| `user` | `UserRpcResponse` | 登录流程的用户信息 |

### 系统支持的登录方式编码（method 取值）

| method | 类别 | 说明 |
|--------|------|------|
| `password` | password | 账号密码（平台恒开，不可关） |
| `email:aliyun` | email | 邮箱验证码（阿里云 DirectMail） |
| `email:smtp` | email | 邮箱验证码（通用 SMTP） |
| `sms:aliyun` | sms | 手机验证码（阿里云短信） |
| `oauth:gitee` | oauth | Gitee OAuth 登录 |
| `oauth:github` | oauth | GitHub OAuth 登录 |

## 7. 服务与方法详解

> 请求字段表仅列语义与约束；所有方法均不抛业务异常（见规则 4），网络层异常为 `RpcException`。

### 7.1 AuthRpcServiceProtobuf —— 认证与查询（最常用）

#### `register(RegisterRpcRequest) → RegisterRpcResult`

注册用户并自动登录（分配 `ROLE_USER`）。

| 请求字段 | 必填 | 约束 |
|----------|------|------|
| `username` | 是 | 3~50 位，仅字母/数字/下划线，租户内唯一 |
| `password` | 是 | 6~50 位 |
| `tenantUid` | 是 | 对外租户标识（8 位随机串，如 `"dm3a9x1f"`） |
| `email` / `phone` | 否 | 提供则校验格式与租户内唯一 |
| `nickname` / `realName` | 否 | ≤50 字符 |
| `gender` | 否 | 0/1/2 |
| `birthday` | 否 | `yyyy-MM-dd`，空串=不设置 |

成功：`success=true` + `token`（令牌对）+ `user`。失败示例：用户名已存在、租户无效（1015）、租户人数达上限（1016）。

#### `authenticate(LoginRpcRequest) → AuthResult`

账号密码登录校验（租户由 `tenantUid` 标识）。

| 请求字段 | 必填 | 说明 |
|----------|------|------|
| `username` | 是 | 用户名 |
| `password` | 是 | 密码 |
| `tenantUid` | 是 | 对外租户标识（随机串） |

成功：`success=true` + `userId` + `username`。**此方法只做校验，不返回令牌**；需要令牌用 `register` / `loginByCode`（直接返回令牌）或 `generateToken`（已登录用户补发）。

#### `getUserById(UserByIdRequest) → UserRpcResponse`

| 请求字段 | 必填 | 说明 |
|----------|------|------|
| `userId` | 是 | 数字字符串 |
| `tenantUid` | 是 | 对外租户标识（随机串），触发归属校验 |

失败/不存在/跨租户/禁用 → `id == 0`（规则 4、5）。

#### `getUserByUsername(UserByUsernameRequest) → UserRpcResponse`

字段：`username`（必填）、`tenantUid`（必填，随机串）。行为同上。

#### `hasPermission(PermissionCheckRequest) → BoolValue`

| 请求字段 | 必填 | 说明 |
|----------|------|------|
| `userId` | 是 | 数字字符串 |
| `permission` | 是 | 权限编码，如 `user:read`（注意：权限按租户隔离，需是**该租户已定义**的编码） |
| `tenantUid` | 是 | 对外租户标识（随机串） |

用户不存在/跨租户/禁用/无此权限 → `value=false`。

#### `hasRole(RoleCheckRequest) → BoolValue`

字段：`userId`、`role`（如 `ROLE_ADMIN`）、`tenantUid`（均必填；userId 数字字符串，tenantUid 随机串）。行为同上。

#### `getUserPermissions(UserPermissionsRequest) → StringListResponse`

字段：`userId`、`tenantUid`（必填）。返回 `getValuesList()` 权限编码列表；失败/无权限 → 空列表。

#### `getUserRoles(UserRolesRequest) → StringListResponse`

字段：`userId`、`tenantUid`（必填）。返回角色编码列表。

#### `searchUsers(SearchUsersRequest) → UserPageResponse`

| 请求字段 | 必填 | 说明 |
|----------|------|------|
| `tenantUid` | 是 | 对外租户标识（随机串） |
| `keyword` | 否 | 匹配用户名/昵称/邮箱 |
| `page` | 否 | **从 1 开始**，`<=0` 修正为 1 |
| `size` | 否 | 默认 10 |

返回：`total`（总数）、`page`、`size`、`items`（`getItemsList()`，元素为 `UserRpcResponse`；禁用用户不出现）。失败 → 空实例（total=0）。

#### `refreshToken(RefreshTokenRpcRequest) → TokenRpcResponse`

| 请求字段 | 必填 | 说明 |
|----------|------|------|
| `refreshToken` | 是 | 之前签发的刷新令牌 |

成功轮换返回**新的令牌对**（旧 refresh 立即失效，请立即替换存储）。失败（无效/已撤销/用户禁用）→ 空实例（accessToken 空串）。

#### `logout(LogoutRpcRequest) → OperationResult`

| 请求字段 | 必填 | 说明 |
|----------|------|------|
| `accessToken` | 否 | 传入则拉黑（jti 进黑名单，剩余有效期） |
| `refreshToken` | 否 | 传入则删除 Redis 中的刷新令牌 |

两者至少传一个，都传效果最完整。成功 `success=true`。

#### `changePassword(ChangePasswordRpcRequest) → OperationResult`

字段：`userId`、`tenantUid`（随机串）、`oldPassword`、`newPassword`（6~50 位）。旧密码错误 → `success=false`（message「旧密码错误」）。**改密不撤销已有令牌**，如需强制下线另行调用 `revokeAllTokens`。

#### `sendCode(SendCodeRpcRequest) → OperationResult`

向邮箱/手机发送 6 位验证码（默认 5 分钟有效）。

| 请求字段 | 必填 | 说明 |
|----------|------|------|
| `tenantUid` | 是 | **随机串**（如 `"dm3a9x1f"`），不是数字！ |
| `method` | 是 | `email:aliyun` / `email:smtp` / `sms:aliyun`，须该租户已启用 |
| `target` | 是 | 邮箱地址或手机号 |

常见失败 message：「该登录方式未启用」「该登录方式未配置凭证」「验证码发送过于频繁」（同一目标 60 秒 1 次）。

#### `loginByCode(LoginByCodeRpcRequest) → LoginByCodeRpcResult`

| 请求字段 | 必填 | 说明 |
|----------|------|------|
| `tenantUid` | 是 | 随机串 |
| `method` | 是 | 同 sendCode，且须与发码时一致 |
| `target` | 是 | 同发码的目标 |
| `code` | 是 | 6 位验证码（验证一次即失效） |

成功 `success=true` + `token` + `user`。**target 在租户内不存在时自动注册并登录**（随机密码，不可密码登录）。验证码错误 → `success=false`。

### 7.2 TokenRpcServiceProtobuf —— 令牌（网关鉴权首选）

#### `parseToken(ParseTokenRpcRequest) → TokenValidationResult`

| 请求字段 | 必填 | 说明 |
|----------|------|------|
| `accessToken` | 是 | 待校验的 JWT |

校验签名、有效期、黑名单。有效 → `valid=true` + 用户身份与角色权限。无效/过期/已登出 → `valid=false`。**推荐用法：网关/BFF 在每个请求中调用本方法完成鉴权**（见 8.1）。

#### `generateToken(TokenGenerationRequest) → TokenRpcResponse`

| 请求字段 | 必填 | 说明 |
|----------|------|------|
| `userId` | 是 | 数字字符串，须存在且未禁用 |
| `tenantUid` | 否 | 对外租户标识；留空=不限定租户（跳过归属校验） |
| `expiration` | 否 | Access Token 有效**秒数**，`<=0` 用默认 900 |

为已存在用户直接签发令牌对（跳过密码校验，适用于内部系统受信场景）。失败 → 空实例。

#### `revokeAllTokens(RevokeAllTokensRpcRequest) → Empty`

| 请求字段 | 必填 | 说明 |
|----------|------|------|
| `userId` | 是 | 数字字符串 |
| `tenantUid` | 否 | 对外租户标识；**留空=跳过归属校验**（规则 3） |

删除该用户的 Refresh Token。**静默执行**：任何失败都不反映在响应中（规则 7）。

### 7.3 UserRpcServiceProtobuf —— 用户管理

#### `updateUser(UpdateUserRpcRequest) → OperationResult`

| 请求字段 | 必填 | 说明 |
|----------|------|------|
| `userId` / `tenantUid` | 是 | userId 数字字符串；tenantUid 对外随机串 |
| `fields_to_update` | 是 | **字段掩码**（规则 6），可重复 |
| `username` / `password` / `email` / `phone` / `nickname` / `avatar` / `status` / `realName` / `gender` / `birthday` | 否 | 仅掩码内字段生效 |

`fields_to_update` 合法取值：`username`、`password`、`email`、`phone`、`nickname`、`avatar`、`status`、`realName`、`gender`、`birthday`。

语义：邮箱/手机**填非空值 = 管理员代为绑定并视为已验证**；传空串进掩码 = 清空（解绑）。

#### `updateUserStatus(UpdateUserStatusRpcRequest) → OperationResult`

字段：`userId`、`tenantUid`（随机串）、`status`（int，1-正常 0-禁用）。

#### `assignRoles(AssignRolesRpcRequest) → OperationResult`

字段：`userId`、`tenantUid`（随机串）、`roleIds`（**全量覆盖**：传最终目标角色 ID 列表，可重复 add；空列表=清空角色）。

#### `deleteUser(DeleteUserRpcRequest) → OperationResult`

字段：`userId`、`tenantUid`（随机串）。

### 7.4 RoleRpcServiceProtobuf —— 角色管理

| 方法 | 请求关键字段（业务 ID 数字字符串；tenantUid 为随机串） | 失败语义 |
|------|-------------------------------|----------|
| `getAllRoles(GetAllRolesRequest)` | `tenantUid` | 失败/无数据 → 空列表 |
| `getRoleByCode(GetRoleByCodeRequest)` | `code`、`tenantUid` | 失败 → `id==0` 空实例 |
| `getRoleById(GetRoleByIdRequest)` | `roleId`、`tenantUid`（可空，规则 3） | 同上 |
| `createRole(CreateRoleRpcRequest)` | `code`、`name`、`description`、`tenantUid` | 成功返回新建角色；重复编码 → `id==0` |
| `updateRole(UpdateRoleRpcRequest)` | `roleId`、`name`、`description`、`tenantUid`（可空） | OperationResult |
| `deleteRole(DeleteRoleRpcRequest)` | `roleId`、`tenantUid`（可空） | OperationResult |
| `assignPermissions(AssignPermissionsRpcRequest)` | `roleId`、`tenantUid`（可空）、`permissionIds`（全量覆盖） | OperationResult |

### 7.5 PermissionRpcServiceProtobuf —— 权限管理

| 方法 | 请求关键字段（业务 ID 数字字符串；tenantUid 为随机串） | 失败语义 |
|------|--------------|----------|
| `getAllPermissions(GetAllPermissionsRequest)` | `tenantUid` | 空列表 |
| `getPermissionById(GetPermissionByIdRequest)` | `permissionId`、`tenantUid`（可空，规则 3） | `id==0` |
| `createPermission(CreatePermissionRpcRequest)` | `code`、`name`、`resource`、`action`、`description`、`tenantUid` | 成功返回新建权限；失败 → `id==0` |
| `deletePermission(DeletePermissionRpcRequest)` | `permissionId`、`tenantUid`（可空） | OperationResult |

### 7.6 OAuthRpcServiceProtobuf —— 第三方登录编排

#### `buildAuthorizeUrl(OAuthAuthorizeUrlRpcRequest) → OAuthUrlRpcResponse`

| 请求字段 | 必填 | 说明 |
|----------|------|------|
| `tenantUid` | 是 | **随机串** |
| `provider` | 是 | `gitee` / `github` |

返回提供方授权页 URL（内部已生成一次性 state）。失败（方式未启用/无凭证）→ `url` 空串。**调用方须把 URL 交给浏览器跳转，并自行接收提供方回调取得 `code` 与 `state`。**

#### `buildBindAuthorizeUrl(OAuthBindUrlRpcRequest) → OAuthUrlRpcResponse`

字段：`tenantUid`（随机串）、`userId`（数字字符串）、`provider`。为已登录用户发起第三方账号绑定授权。失败 → `url` 空串。

#### `handleCallback(OAuthCallbackRpcRequest) → OAuthCallbackRpcResult`

| 请求字段 | 必填 | 说明 |
|----------|------|------|
| `provider` | 是 | `gitee` / `github` |
| `code` | 是 | 提供方回调带回的授权码 |
| `state` | 是 | 提供方回调带回的 state（10 分钟一次性） |

服务端完成：校验 state → 换 token → 拉用户 → 登录（匹配/自动建用户+签发令牌）或绑定。结果判读见消息字典（`login` 字段分流两种流程）。

#### `listBindings(OAuthBindingsRpcRequest) → OAuthBindingListRpcResponse`

字段：`tenantUid`（随机串）、`userId`（数字字符串）。返回 `getBindingsList()`，元素含 `id`、`provider`、`providerUid`、`createdAt`（epoch 毫秒）。

#### `unbind(OAuthUnbindRpcRequest) → OperationResult`

字段：`tenantUid`（随机串）、`userId`（数字字符串）、`provider`。未绑定 → `success=false`。

### 7.7 ContactBindingRpcServiceProtobuf —— 邮箱/手机绑定

四个方法均为 `OperationResult` 返回：

| 方法 | 请求字段 | 前置条件 |
|------|----------|----------|
| `bindEmail(BindContactRpcRequest)` | `userId`、`tenantUid`（随机串）、`method`（email 类）、`target`（新邮箱）、`code` | 先对该 target 调 `sendCode`；target 未被同租户他人占用 |
| `unbindEmail(ContactUnbindRpcRequest)` | `userId`、`tenantUid` | 当前已绑定邮箱 |
| `bindPhone(BindContactRpcRequest)` | 同上，`method` 为 sms 类、`target` 为新手机号 | 同上 |
| `unbindPhone(ContactUnbindRpcRequest)` | `userId`、`tenantUid` | 当前已绑定手机号 |

`bindEmail` 的 `method` 只能是 `email:aliyun`/`email:smtp`；`bindPhone` 只能是 `sms:aliyun`（类别不匹配 → `success=false`）。

### 7.8 LoginMethodRpcServiceProtobuf —— 登录方式配置

| 方法 | 请求字段 | 说明 |
|------|----------|------|
| `listPlatformConfigs(Empty)` | 无 | 返回全部 6 种方式的平台开关/凭证状态（`LoginMethodListRpcResponse.getItemsList()`） |
| `savePlatformConfig(SavePlatformLoginMethodRpcRequest)` | `method`、`enabled`（password 传 0 会被拒绝）、`configJson`（明文凭证 JSON，空=不改） | OperationResult |
| `listTenantConfigs(TenantLoginMethodRpcRequest)` | `tenantUid`（随机串） | 仅返回平台已开启的方式 |
| `saveTenantConfig(SaveTenantLoginMethodRpcRequest)` | `tenantUid`、`method`（须平台已开启）、`enabled`、`usePlatformConfig`（1=平台凭证 0=自有）、`configJson` | 邮箱类互斥：同租户启用第二种 email 方式 → `success=false` |
| `listEnabledMethods(EnabledLoginMethodsRpcRequest)` | `tenantUid`（**随机串**，空 → 空列表） | 返回 `StringListResponse`，供登录页渲染 |

`configJson` 结构（各方式字段，均为字符串值）：

```jsonc
// email:aliyun —— accessKeyId / accessKeySecret / accountName 必填
{ "accessKeyId": "...", "accessKeySecret": "...", "accountName": "noreply@your.com",
  "fromAlias": "Auth Service", "region": "cn-hangzhou", "codeTtlMinutes": "5",
  "subject": "登录验证码", "template": "<p>验证码 {code}，{minutes} 分钟内有效</p>" }

// email:smtp —— host / username / password 必填
{ "host": "smtp.qq.com", "port": "465", "username": "you@qq.com", "password": "授权码",
  "encryption": "ssl", "codeTtlMinutes": "5" }

// sms:aliyun —— accessKeyId / accessKeySecret / signName / templateCode 必填
{ "accessKeyId": "...", "accessKeySecret": "...", "signName": "签名", "templateCode": "SMS_xxx" }

// oauth:gitee / oauth:github
{ "clientId": "...", "clientSecret": "...", "redirectUri": "https://你的域名/api/auth/oauth/gitee/callback" }
```

保存时按**非空键合并**到已有配置（只传要改的键即可，空串不覆盖）。

### 7.9 OssRpcServiceProtobuf —— 头像上传

#### `uploadAvatar(UploadAvatarRpcRequest) → UploadAvatarRpcResponse`

| 请求字段 | 必填 | 说明 |
|----------|------|------|
| `tenantUid` / `userId` | 是 | tenantUid 随机串、userId 数字字符串；头像归属该用户（编辑他人传目标 id） |
| `filename` | 是 | 原始文件名，扩展名限 jpg/jpeg/png/gif/webp |
| `contentType` | 是 | MIME 类型 |
| `data` | 是 | 文件字节（`ByteString.copyFrom(bytes)`），≤ 2MB |

成功返回 `url`；服务端未配置 OSS → 失败响应（url 空串）。

## 8. 端到端集成场景

### 8.1 场景 A：微服务网关用 parseToken 鉴权（最典型）

```java
@Component
public class AuthGuard {

    @Value("${auth.rpc-token}")
    private String rpcToken;

    @DubboReference(version = "1.0.0", timeout = 3000)
    private TokenRpcServiceProtobuf tokenRpc;

    /** 每个业务请求调用：传入 Authorization 头去掉 "Bearer " 后的 token */
    public TokenValidationResult authenticate(String accessToken) {
        RpcContext.getClientAttachment().setAttachment("rpc-service-token", rpcToken);
        TokenValidationResult r = tokenRpc.parseToken(
                ParseTokenRpcRequest.newBuilder().setAccessToken(accessToken).build());
        if (!r.getValid()) {
            throw new UnauthorizedException("令牌无效或已过期");
        }
        return r;   // r.getUserId() / r.getTenantUid() / r.getRolesList() / r.getPermissionsList()
    }

    /** 细粒度权限判断 */
    public void requirePermission(String accessToken, String permission) {
        TokenValidationResult t = authenticate(accessToken);
        if (!t.getPermissionsList().contains(permission)) {
            throw new ForbiddenException("无权限: " + permission);
        }
    }
}
```

### 8.2 场景 B：业务服务验证码登录（手机/邮箱）

```java
// 1. 发码（tenantUid 是随机串！）
RpcContext.getClientAttachment().setAttachment("rpc-service-token", rpcToken);
OperationResult send = authRpc.sendCode(SendCodeRpcRequest.newBuilder()
        .setTenantUid("dm3a9x1f")
        .setMethod("email:smtp")
        .setTarget("alice@example.com")
        .build());
if (!send.getSuccess()) { throw new BizException(send.getMessage()); }

// 2. 用户收到 6 位验证码后登录
LoginByCodeRpcResult login = authRpc.loginByCode(LoginByCodeRpcRequest.newBuilder()
        .setTenantUid("dm3a9x1f")
        .setMethod("email:smtp")
        .setTarget("alice@example.com")
        .setCode(userInputCode)
        .build());
if (login.getSuccess()) {
    String accessToken  = login.getToken().getAccessToken();   // 下发给前端
    String refreshToken = login.getToken().getRefreshToken();
    long   userId       = login.getUser().getId();
}
```

### 8.3 场景 C：服务端代用户签发令牌（内部受信系统）

```java
TokenRpcResponse token = tokenRpc.generateToken(TokenGenerationRequest.newBuilder()
        .setUserId("123456789")     // 数字字符串
        .setTenantUid("dm3a9x1f")   // 留空 = 不限定租户
        .setExpiration(3600)        // 秒
        .build());
if (token.getAccessToken().isEmpty()) {   // 失败=空实例
    throw new BizException("签发失败（用户不存在或已禁用）");
}
```

### 8.4 场景 D：OAuth 登录（Gitee 为例）

```java
// 1. 生成授权页 URL，返回给前端跳转
OAuthUrlRpcResponse urlResp = oauthRpc.buildAuthorizeUrl(OAuthAuthorizeUrlRpcRequest.newBuilder()
        .setTenantUid("dm3a9x1f")
        .setProvider("gitee")
        .build());
if (urlResp.getUrl().isEmpty()) { throw new BizException("gitee 登录未启用或未配置"); }
// → 前端 window.location = urlResp.getUrl()

// 2. 提供方回调你的系统（redirectUri 指向你的回调端点），拿到 code+state 后：
OAuthCallbackRpcResult result = oauthRpc.handleCallback(OAuthCallbackRpcRequest.newBuilder()
        .setProvider("gitee")
        .setCode(code)
        .setState(state)
        .build());
if (result.getLogin() && result.getToken().getAccessToken().length() > 0) {
    // 登录成功（首次自动注册）
} else if (result.getLogin()) {
    // 登录流程失败
} else {
    // 绑定流程：result.getSuccess() / result.getMessage()
}
```

### 8.5 场景 E：管理用户（禁用 + 改角色）

```java
// 禁用用户（注意字段掩码）
userRpc.updateUser(UpdateUserRpcRequest.newBuilder()
        .setUserId("123456789").setTenantUid("dm3a9x1f")
        .setStatus(0)
        .addFieldsToUpdate("status")
        .build());

// 全量覆盖角色（先查租户角色列表拿到目标 roleId）
RoleListResponse roles = roleRpc.getAllRoles(
        GetAllRolesRequest.newBuilder().setTenantUid("dm3a9x1f").build());
AssignRolesRpcRequest.Builder ab = AssignRolesRpcRequest.newBuilder()
        .setUserId("123456789").setTenantUid("dm3a9x1f");
roles.getRolesList().stream()
        .filter(r -> "ROLE_ADMIN".equals(r.getCode()))
        .forEach(r -> ab.addRoleIds(String.valueOf(r.getId())));
OperationResult r = userRpc.assignRoles(ab.build());
```

## 9. 调用失败排查表

| 症状 | 原因 | 解决 |
|------|------|------|
| 启动报 `No provider available for the service ... AuthRpcServiceProtobuf` | Nacos 地址不一致 / auth-service 未启动 / version 不匹配 | 核对 `dubbo.registry.address` 与 auth-service 一致；确认 auth-service 已注册到 Nacos；`@DubboReference` 加 `version = "1.0.0"` |
| 抛 `RpcException: RPC service token is invalid or missing` | 未设置 attachment 或 token 不一致 | 见第 4 节；向 auth-service 运维方索取正确的 `RPC_SERVICE_TOKEN` |
| 抛 `RpcException`（timeout） | auth-service 无响应 | 检查 auth-service 20880 端口连通性与服务健康 |
| 依赖解析失败 `Could not find artifact cn.wanyj.auth:auth-service-api` | 未安装 API jar | 见第 2 节 `mvn install` |
| 查询用户返回 `id == 0`，但用户确实存在 | ① tenantUid 传错/传了数字 ② 用户被禁用 ③ 跨租户归属校验 | 逐一核对规则 1/2/3/5 |
| `parseToken` 恒 `valid=false` | 令牌被登出拉黑 / 过期 / 传了 refreshToken | 确认传的是 Access Token；过期就走 refreshToken 流程 |
| 写操作报「用户名已存在」但你是首次调用 | retries>0 导致重试重复执行 | 写操作 `retries = 0`（规则 8） |
| `sendCode` 报「该登录方式未启用」 | 租户未启用该方式或平台未开启 | 走 HTTP 管理端或 `LoginMethodRpcService` 开启并配置凭证 |
| `updateUser` 改 `status`/清空字段不生效 | 字段没进 `fields_to_update` 掩码 | 规则 6 |
| 序列化异常 / 类型不匹配 | API jar 版本与服务端 proto 不一致 | 使用与服务端同版本的 `auth-service-api`（当前 1.1） |

## 10. 集成自检清单

集成完成后逐项确认：

- [ ] `auth-service-api:1.0` 已安装并引入
- [ ] 消费方 `dubbo.registry.address` 与 auth-service 指向**同一个 Nacos**
- [ ] 启动类加了 `@EnableDubbo`
- [ ] 所有 `@DubboReference` 带 `version = "1.0.0"`
- [ ] 每次调用前设置 attachment `rpc-service-token`（或已确认服务端未启用鉴权）
- [ ] 租户参数一律传 `tenantUid` 随机串；其余 ID 字段传数字字符串
- [ ] 业务失败按响应字段判断（`success` / `id==0` / `valid` / 空串），仅捕获 `RpcException`
- [ ] 写操作 `retries = 0`
- [ ] `updateUser` 带字段掩码
- [ ] `refreshToken` 成功后已替换存储的新令牌对
