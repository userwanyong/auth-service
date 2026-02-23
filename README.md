# Auth Service

> 基于 Spring Boot 3.x 的认证授权微服务，支持 REST API 和 Dubbo RPC

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.13-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Apache Dubbo](https://img.shields.io/badge/Dubbo-3.3.2-blue.svg)](https://dubbo.apache.org/)
[![Nacos](https://img.shields.io/badge/Nacos-3.1.1-green.svg)](https://nacos.io/)
[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 特性

- **Web 管理界面**：内置完整的前端管理界面，支持用户/角色/权限/租户管理
- **双协议支持**：REST API (HTTP) + Dubbo RPC (Triple 协议)
- **服务注册发现**：集成 Nacos 注册中心
- **JWT 双令牌机制**：Access Token + Refresh Token
- **RBAC 权限模型**：用户-角色-权限三层模型，支持完整的 CRUD 权限
- **多租户支持**：基于 tenant_id 的数据隔离，创建租户自动初始化管理员
- **平台级权限**：租户管理与平台管理员角色
- **令牌黑名单**：支持主动注销与令牌撤销
- **Redis 缓存**：令牌存储与会话管理
- **Protobuf 序列化**：高性能 RPC 通信
- **统一响应格式**：标准化的 API 响应

## 架构设计

```
                              ┌───────────────┐
                              │    Nacos      │
                              │   :8848       │
                              └───────┬───────┘
                                      │
                  ┌───────────────────┴──────────────────┐
                  │                                      │
          ┌───────▼────────┐                    ┌────────▼───────┐
          │  REST API      │                    │  Dubbo RPC     │
          │  :8123         │                    │  :20880        │
          │                │                    │  (Triple)      │
          │ ┌────────────┐ │                    │ ┌────────────┐ │
          │ │   Auth     │ │                    │ │   Auth     │ │
          │ │Controller  │ │                    │ │RpcService  │ │
          │ ├────────────┤ │                    │ ├────────────┤ │
          │ │   User     │ │                    │ │   Token    │ │
          │ │Controller  │ │                    │ │RpcService  │ │
          │ ├────────────┤ │                    │ │            │ │
          │ │   Role     │ │                    │ └────────────┘ │
          │ │Controller  │ │                    └────────┬───────┘
          │ ├────────────┤ │                             │
          │ │Permission  │ │                             │
          │ │Controller  │ │                             │
          │ ├────────────┤ │                             │
          │ │  Tenant    │ │                             │
          │ │Controller  │ │                             │
          │ └────────────┘ │                             │
          └────────┬───────┘                             │
                   │                                     │
                   └──────────┬──────────────────────────┘
                              │
                  ┌───────────▼──────────────────────┐
                  │        Service Layer             │
                  │ AuthService │ UserService │ ...  │
                  └───────────┬──────────────────────┘
                              │
                  ┌───────────▼──────────────────────┐
                  │        Data Layer                │
                  │     MySQL + Redis                │
                  └──────────────────────────────────┘
```

## 环境要求

| 组件 | 版本要求 | 说明 |
|------|------|------|
| JDK | 17+  | 编程语言 |
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 6.0+ | 缓存存储 |
| Nacos | 3.1+ | 服务注册中心 |
| Maven | 3.6+ | 构建工具 |

## 部署方式

前置准备：mysql、redis、nacos、并确保已执行数据库初始化脚本

### 方式一：Docker Compose 部署脚本（推荐）

使用 Docker Compose 部署应用服务。

```yml
version: '3.8'

services:
  auth-service:
    image: registry.cn-wulanchabu.aliyuncs.com/wanyj/auth-service:2.0
    container_name: auth-service-app
    restart: unless-stopped
    environment:
      TZ: Asia/Shanghai
      # Database Configuration
      SPRING_DATASOURCE_URL: jdbc:mysql://127.0.0.1:3306/auth_service?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: 12123
      # Redis Configuration
      SPRING_DATA_REDIS_HOST: 127.0.0.1
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_DATA_REDIS_PASSWORD: 12123
      # Nacos Configuration
      dubbo.registry.address: nacos://127.0.0.1:8848
      dubbo.registry.username: nacos
      dubbo.registry.password: nacos
      dubbo.metadata-report.address: nacos://127.0.0.1:8848
      dubbo.metadata-report.username: nacos
      dubbo.metadata-report.password: nacos
      # JVM Configuration
      JAVA_OPTS: >-
        -XX:+UseContainerSupport
        -XX:MaxRAMPercentage=75.0
        -XX:+PrintGCDetails
        -XX:+PrintGCTimeStamps
        -Xlog:gc*:file=/logs/gc.log:time,tags:filecount=10,filesize=100M
    ports:
      - "8123:8123"   # REST API
      - "20880:20880" # Dubbo RPC
    volumes:
      - app-logs:/app/logs
    networks:
      - auth-network

# Named volumes for data persistence
volumes:
  app-logs:
    driver: local

# Network for service communication
networks:
  auth-network:
    driver: bridge
```

**服务说明：**

| 服务 | 端口映射 | 说明 |
|------|----------|------|
| auth-service | 8123 | 应用服务（REST API） |
| mysql | 3306 | 数据库 |
| redis | 6379 | 缓存服务 |
| nacos | 8848 | 注册中心 |

**访问地址：**
- Web 管理界面：http://localhost:8123

### 方式二：开发环境运行

**IDE 配置（IDEA / Eclipse）：**

1. 导入项目为 Maven 项目
2. 等待依赖下载完成
3. 运行 `AuthServiceApplication` 主类

**或使用 Maven 命令：**
```bash
mvn spring-boot:run -pl auth-service-core
```

## 快速开始

### 1. 启动服务

按照上述"部署方式"中的任一方式启动服务。

### 2. 访问管理界面

打开浏览器访问：http://localhost:8123

### 3. 登录系统

使用默认管理员账户登录：

**平台租户登录：**
- 用户名：`admin`
- 密码：`123456`
- 租户：`平台租户`

**演示租户登录：**
- 用户名：`admin`
- 密码：`123456`
- 租户：`演示租户`

### 4. 开始使用

登录后即可进行用户、角色、权限、租户管理。

## API 接口

### REST API

#### 认证接口 (AuthController)

| 端点 | 方法 | 描述 | 公开 |
|------|------|------|------|
| `/api/auth/register` | POST | 用户注册（自动登录） | 是 |
| `/api/auth/login` | POST | 用户登录 | 是 |
| `/api/auth/refresh` | POST | 刷新访问令牌 | 是 |
| `/api/auth/logout` | POST | 用户登出 | 是 |
| `/api/auth/me` | GET | 获取当前用户信息 | 否 |
| `/api/auth/password` | PUT | 修改密码 | 否 |

#### 用户管理 (UserController)

| 端点 | 方法 | 描述 | 权限 |
|------|------|------|------|
| `/api/users/{id}` | GET | 根据ID获取用户 | ADMIN |
| `/api/users` | GET | 搜索用户（分页） | ADMIN |
| `/api/users/{id}/roles` | POST | 为用户分配角色 | ADMIN |
| `/api/users/{id}/status` | PUT | 更新用户状态 | ADMIN |
| `/api/users/{id}` | DELETE | 删除用户 | ADMIN |

#### 角色管理 (RoleController)

| 端点 | 方法 | 描述 | 权限 |
|------|------|------|------|
| `/api/roles` | GET | 获取所有角色 | ADMIN |
| `/api/roles/{id}` | GET | 根据ID获取角色 | ADMIN |
| `/api/roles/code/{code}` | GET | 根据编码获取角色 | ADMIN |
| `/api/roles` | POST | 创建角色 | ADMIN |
| `/api/roles/{id}` | PUT | 更新角色 | ADMIN |
| `/api/roles/{id}/permissions` | POST | 为角色分配权限 | ADMIN |
| `/api/roles/{id}` | DELETE | 删除角色 | ADMIN |

#### 权限管理 (PermissionController)

| 端点 | 方法 | 描述 | 权限 |
|------|------|------|------|
| `/api/permissions` | GET | 获取所有权限 | ADMIN |
| `/api/permissions/{id}` | GET | 根据ID获取权限 | ADMIN |
| `/api/permissions` | POST | 创建权限 | ADMIN |
| `/api/permissions/{id}` | DELETE | 删除权限 | ADMIN |

#### 租户管理 (TenantController)

| 端点 | 方法 | 描述 | 权限 |
|------|------|------|------|
| `/api/tenant` | POST | 创建租户 | 平台管理员 |
| `/api/tenant/{id}` | PUT | 更新租户 | 平台管理员 |
| `/api/tenant/{id}` | GET | 获取租户详情 | 平台管理员 |
| `/api/tenant` | GET | 获取所有租户 | 平台管理员 |
| `/api/tenant/{id}` | DELETE | 删除租户 | 平台管理员 |
| `/api/tenant/check-code` | GET | 检查租户编码是否可用 | 公开 |

### Dubbo RPC (Protobuf IDL)

#### 引入依赖

```xml
<dependency>
    <groupId>cn.wanyj.auth</groupId>
    <artifactId>auth-service-api</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

#### AuthRpcServiceProtobuf

| RPC 方法 | 描述 | 请求 | 响应 |
|----------|------|------|------|
| `authenticate` | 验证用户凭证 | `LoginRpcRequest` | `AuthResult` |
| `getUserById` | 根据ID获取用户 | `UserByIdRequest` | `UserRpcResponse` |
| `getUserByUsername` | 根据用户名获取用户 | `UserByUsernameRequest` | `UserRpcResponse` |
| `hasPermission` | 检查用户权限 | `PermissionCheckRequest` | `BoolValue` |
| `hasRole` | 检查用户角色 | `RoleCheckRequest` | `BoolValue` |
| `getUserPermissions` | 获取用户权限列表 | `UserPermissionsRequest` | `StringListResponse` |
| `getUserRoles` | 获取用户角色列表 | `UserRolesRequest` | `StringListResponse` |

#### TokenRpcServiceProtobuf

| RPC 方法 | 描述 | 请求 | 响应 |
|----------|------|------|------|
| `generateToken` | 为用户生成令牌 | `TokenGenerationRequest` | `TokenRpcResponse` |
| `parseToken` | 解析令牌获取用户信息 | `StringValue` | `TokenValidationResult` |
| `revokeAllTokens` | 撤销用户所有令牌 | `Int64Value` | `Empty` |

#### 调用示例

```java
@DubboReference(version = "1.0.0")
private AuthRpcServiceProtobuf authRpcService;

// 获取用户信息（多租户）
UserByIdRequest request = UserByIdRequest.newBuilder()
    .setUserId(userId)
    .setTenantId(tenantId)
    .build();

UserRpcResponse user = authRpcService.getUserById(request);

// 检查权限
PermissionCheckRequest permRequest = PermissionCheckRequest.newBuilder()
    .setUserId(userId)
    .setPermission("user:delete")
    .setTenantId(tenantId)
    .build();

BoolValue result = authRpcService.hasPermission(permRequest);
```

## 项目结构

```
auth-service/
├── auth-service-api/              # RPC 接口定义模块
│   └── src/main/
│       ├── java/                  # Java 源码
│       └── proto/                 # Protobuf IDL 定义
│           └── auth/
│               └── auth_service.proto
│
├── auth-service-core/             # 核心服务实现模块
│   └── src/main/
│       ├── java/cn/wanyj/auth/
│       │   ├── config/            # 配置类
│       │   ├── controller/        # REST API 控制器
│       │   ├── dto/               # REST DTO
│       │   ├── entity/            # 实体类
│       │   ├── exception/         # 异常处理
│       │   ├── mapper/            # MyBatis Mapper
│       │   ├── rpc/               # Dubbo RPC 实现
│       │   ├── security/          # 安全模块
│       │   └── service/           # 业务逻辑层
│       └── resources/
│           ├── mapper/            # MyBatis XML 映射
│           └── application.yaml   # 配置文件
│
├── docs/                          # 文档
│   ├── init-schema.sql            # 数据库初始化脚本
│   ├── mvp-design.md              # MVP 设计文档
│   ├── dubbo-client-example.md    # Dubbo 客户端调用示例
│   └── ...
│
└── pom.xml                        # 父 POM
```

## 数据库设计

### 表结构

#### tenant (租户表)

| 字段 | 类型 | 描述 |
|------|------|------|
| id | BIGINT | 主键ID |
| tenant_code | VARCHAR(50) | 租户编码（唯一） |
| tenant_name | VARCHAR(100) | 租户名称 |
| status | TINYINT | 状态：0-禁用，1-正常 |
| expired_at | DATETIME | 过期时间（NULL=永不过期） |
| max_users | INT | 最大用户数限制 |
| is_platform | TINYINT | 是否为平台租户 |

**特殊租户：**
- `id=0`：平台租户（is_platform=1）
- `id=1`：默认演示租户

#### user (用户表)

| 字段 | 类型 | 描述 |
|------|------|------|
| id | BIGINT | 主键ID |
| tenant_id | BIGINT | 租户ID（多租户隔离） |
| username | VARCHAR(50) | 用户名（租户内唯一） |
| password | VARCHAR(255) | 密码（BCrypt加密） |
| email | VARCHAR(100) | 邮箱（租户内唯一） |
| phone | VARCHAR(20) | 手机号 |
| nickname | VARCHAR(50) | 昵称 |
| avatar | VARCHAR(255) | 头像URL |
| status | TINYINT | 状态：0-禁用，1-正常 |
| email_verified | BOOLEAN | 邮箱是否验证 |
| last_login_at | DATETIME | 最后登录时间 |

#### role (角色表)

| 字段 | 类型 | 描述 |
|------|------|------|
| id | BIGINT | 主键ID |
| tenant_id | BIGINT | 租户ID（多租户隔离） |
| code | VARCHAR(50) | 角色编码（租户内唯一） |
| name | VARCHAR(50) | 角色名称 |
| description | VARCHAR(200) | 角色描述 |
| status | TINYINT | 状态：0-禁用，1-正常 |

**默认角色：**
- `ROLE_PLATFORM_ADMIN`：平台管理员（tenant_id=0）
- `ROLE_ADMIN`：系统管理员
- `ROLE_USER`：普通用户

#### permission (权限表)

| 字段 | 类型 | 描述 |
|------|------|------|
| id | BIGINT | 主键ID |
| tenant_id | BIGINT | 租户ID（多租户隔离） |
| code | VARCHAR(100) | 权限编码（租户内唯一） |
| name | VARCHAR(50) | 权限名称 |
| resource | VARCHAR(100) | 资源标识 |
| action | VARCHAR(50) | 操作类型 |
| description | VARCHAR(200) | 权限描述 |

#### user_role (用户角色关联表)

| 字段 | 类型 | 描述 |
|------|------|------|
| id | BIGINT | 主键ID |
| tenant_id | BIGINT | 租户ID |
| user_id | BIGINT | 用户ID |
| role_id | BIGINT | 角色ID |

唯一约束：(user_id, role_id, tenant_id)

#### role_permission (角色权限关联表)

| 字段 | 类型 | 描述 |
|------|------|------|
| id | BIGINT | 主键ID |
| tenant_id | BIGINT | 租户ID |
| role_id | BIGINT | 角色ID |
| permission_id | BIGINT | 权限ID |

唯一约束：(role_id, permission_id, tenant_id)

### 默认数据

| 类型 | 编码 | 名称 | 说明 |
|------|------|------|------|
| 租户 | platform | 平台租户 | 用于平台管理 |
| 租户 | demo | 演示租户 | 默认演示租户 |

**默认管理员账户：**

| 租户 | 用户名 | 密码 | 角色 | 权限 |
|------|--------|------|------|------|
| 平台租户 (id=0) | admin | 123456 | ROLE_PLATFORM_ADMIN | 租户管理权限 |
| 演示租户 (id=1) | admin | 123456 | ROLE_ADMIN | 所有用户/角色/权限管理权限 |

**默认角色：**
- `ROLE_PLATFORM_ADMIN`：平台管理员（tenant_id=0）
- `ROLE_ADMIN`：系统管理员（拥有所有 CRUD 权限）
- `ROLE_USER`：普通用户（仅查看权限）

## 技术栈

| 技术 | 版本     | 说明 |
|------|--------|------|
| Spring Boot | 3.4.13 | 基础框架 |
| Apache Dubbo | 3.3.2  | RPC 框架（Triple协议） |
| Nacos | 3.1.1  | 注册中心 |
| Spring Security | 6.4.2  | 安全框架 |
| JWT (jjwt) | 0.12.6 | 令牌生成与验证 |
| Protobuf | 3.25.2 | RPC 序列化 |
| MyBatis | 3.0.4  | ORM 框架 |
| MySQL | 8.0+   | 关系型数据库 |
| Redis | 6.0+   | 缓存存储 |
| Java | 17     | 编程语言 |

## 核心功能

### 多租户支持

**隔离策略**：共享数据库，通过 `tenant_id` 字段隔离

| 实体 | tenant_id | 行为 |
|------|-----------|------|
| User | 是 | 用户名/邮箱租户内唯一 |
| Role | 是 | 角色编码租户内唯一 |
| Permission | 是 | 权限编码租户内唯一 |
| Tenant | 否 | 全局租户注册表 |

**租户识别方式：**
- **注册/登录**：请求参数中的 `tenantId`（默认为1）
- **其他请求**：从JWT令牌中提取（服务端签名，不可伪造）

**Redis Key 模式（多租户隔离）：**
```
refresh_token:{tenant_id}:{user_id}
blacklist:{tenant_id}:{token}
```

### 平台级权限系统

**注解**：`@PreAuthorizePlatformAdmin`

**平台管理员条件**：
1. `tenantId = 0`（平台租户）
2. 拥有 `ROLE_PLATFORM_ADMIN` 角色

**受保护的端点**：
- 所有 `/api/tenant/**` 端点
- 租户CRUD操作

**使用示例**：
```java
@PostMapping
@PreAuthorizePlatformAdmin
public ResponseEntity<ApiResponse<TenantResponse>> createTenant(
    @Valid @RequestBody TenantRequest request) {
    // 仅平台管理员可访问
}
```

### JWT 双令牌机制

| 令牌类型 | 有效期 | 用途 |
|----------|--------|------|
| Access Token | 1小时 | API认证 |
| Refresh Token | 7天 | 令牌续期 |

**JWT Claims**：
```json
{
  "sub": "用户ID",
  "username": "用户名",
  "tenant_id": "租户ID",
  "roles": ["角色列表"],
  "permissions": ["权限列表"]
}
```

### RBAC 权限模型

```
用户 (User)
  └─> 角色 (Role) [多对多]
       └─> 权限 (Permission) [多对多]
```

**示例**：
```
用户 alice (tenant_id=1)
  └─> ROLE_ADMIN
       └─> user:read, user:write, user:delete
       └─> role:read, role:write
```

### 令牌黑名单

- 存储位置：Redis
- Key格式：`blacklist:{tenant_id}:{token}`
- TTL：匹配令牌剩余有效期
- 检查位置：`JwtAuthenticationFilter` 每次请求时验证

## 配置说明

### 数据库配置

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/auth_service?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
```

### Redis 配置

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: your_redis_password
      database: 0
```

### JWT 配置

```yaml
jwt:
  # 密钥（Base64编码，256位），生产环境请务必修改
  secret: Yo3bOIzQhkFc+lRvAEj90Hvx89IzgEC5FduXDPCTiB0=
  # 访问令牌有效期（毫秒）：1小时
  access-token-expiration: 3600000
  # 刷新令牌有效期（毫秒）：7天
  refresh-token-expiration: 604800000
```

### Dubbo RPC 配置

```yaml
dubbo:
  application:
    name: auth-service
    version: 1.0.0
  protocol:
    name: tri              # Triple 协议（基于 HTTP/2）
    port: 20880
    serialization: protobuf # Protobuf 序列化
  registry:
    address: nacos://localhost:8848
    username: nacos
    password: nacos
  scan:
    base-packages: cn.wanyj.auth.rpc
```

### MyBatis 配置

```yaml
mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: cn.wanyj.auth.entity
  configuration:
    map-underscore-to-camel-case: true  # 驼峰命名转换
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
```

## 错误码

| 错误码 | 说明 |
|--------|------|
| 1001 | 无效的用户名或密码 |
| 1002 | 用户不存在 |
| 1003 | 用户已被禁用 |
| 1004 | 用户名已存在 |
| 1005 | 邮箱已存在 |
| 1009 | 令牌已过期 |
| 1010 | 令牌已被撤销（黑名单） |
| 1015 | 无效或不存在的租户 |
| 1016 | 租户用户数量已达上限 |
| 1018 | 租户不存在 |

## 使用示例

### 用户注册

```bash
curl -X POST http://localhost:8123/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "password123",
    "email": "alice@example.com",
    "tenantId": 1
  }'
```

### 用户登录

```bash
curl -X POST http://localhost:8123/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "password123",
    "tenantId": 1
  }'
```

### 获取当前用户信息

```bash
curl -X GET http://localhost:8123/api/auth/me \
  -H "Authorization: Bearer <access_token>"
```

### 创建租户（平台管理员）

```bash
curl -X POST http://localhost:8123/api/tenant \
  -H "Authorization: Bearer <platform_admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantCode": "new-tenant",
    "tenantName": "新租户",
    "maxUsers": 100
  }'
```

## 安全说明

### 密码加密

- 算法：BCrypt
- 强度：默认 10 轮

### JWT 签名

- 密钥长度：256位（Base64编码）
- 算法：HS256（HMAC-SHA256）

### 安全建议

1. 生产环境必须修改 JWT 密钥
2. 使用 HTTPS 保护 API 通信
3. 定期轮换 Redis 密码
4. 限制数据库访问权限
5. 启用 Nacos 认证

## 服务端口

| 服务 | 端口 | 协议 |
|------|------|------|
| REST API | 8123 | HTTP |
| Dubbo RPC | 20880 | Triple (HTTP/2) |
| Nacos Registry | 8848 | - |

## 常见问题

### 1. 默认管理员账户是什么？

系统初始化后会创建两个管理员账户：

| 租户 | 用户名 | 密码 | 功能 |
|------|--------|------|------|
| 平台租户 (id=0) | admin | 123456 | 租户管理，可创建新租户 |
| 演示租户 (id=1) | admin | 123456 | 用户/角色/权限管理 |

**安全提示**：生产环境请务必修改默认密码！

### 2. 如何访问 Web 管理界面？

服务启动后访问：http://localhost:8123

根据登录的租户不同，管理界面会显示不同的功能：
- **平台租户**：只能看到租户管理
- **普通租户**：可以看到用户、角色、权限管理

### 3. 如何创建新租户？

1. 使用平台租户账户登录
2. 进入"租户管理"页面
3. 点击"添加租户"
4. 填写租户信息并保存

**注意**：创建新租户时会自动：
- 初始化 12 个 CRUD 权限（user:read/write/create/delete, role:read/write/create/delete, permission:read/write/create/delete）
- 创建 ROLE_ADMIN 和 ROLE_USER 角色
- 创建管理员用户（admin/123456）

### 4. 权限系统是如何工作的？

系统采用 RBAC（基于角色的访问控制）模型：

```
用户 ←→ 角色 ←→ 权限
```

**CRUD 权限说明**：
- `create`: 创建资源
- `read`: 查看资源
- `write`: 编辑资源
- `delete`: 删除资源

**默认权限列表**：
- 用户管理：user:read, user:create, user:write, user:delete
- 角色管理：role:read, role:create, role:write, role:delete
- 权限管理：permission:read, permission:create, permission:write, permission:delete

### 5. 如何为业务服务集成认证？

**步骤1：添加 Maven 依赖**

```xml
<dependency>
    <groupId>cn.wanyj.auth</groupId>
    <artifactId>auth-service-api</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

**步骤2：引用 RPC 服务并调用**

```java
@DubboReference(version = "1.0.0")
private AuthRpcServiceProtobuf authRpcService;

// 解析令牌获取用户信息
TokenValidationResult result = authRpcService.parseToken(
    StringValue.newBuilder().setValue(token).build()
);
```

### 6. 如何切换租户？

登录时在登录页面选择对应的租户即可。JWT 令牌中包含 tenant_id 信息，后续请求会自动识别租户。

## 许可证

[MIT](LICENSE)

---

**注意**：本项目为开源项目，可用于学习和生产环境。生产部署前请修改所有默认密码和密钥。
