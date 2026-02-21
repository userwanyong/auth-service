# Auth Service

> 基于 Spring Boot 3.x 的认证授权微服务，支持 REST API 和 Dubbo RPC

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.13-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Apache Dubbo](https://img.shields.io/badge/Dubbo-3.3.2-blue.svg)](https://dubbo.apache.org/)
[![Nacos](https://img.shields.io/badge/Nacos-2.4.2-green.svg)](https://nacos.io/)
[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 特性

- **双协议支持**：REST API (HTTP) + Dubbo RPC (Triple)
- **服务注册发现**：集成 Nacos 注册中心
- **JWT 双令牌机制**：Access Token + Refresh Token
- **RBAC 权限模型**：用户-角色-权限
- **令牌黑名单**：支持主动注销
- **Redis 缓存**：令牌存储与会话管理
- **统一响应格式**：标准化的 API 响应

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                      auth-service                            │
│  ┌─────────────────────┐    ┌─────────────────────────┐    │
│  │    REST API Layer   │    │      RPC Layer          │    │
│  │   (端口: 8123)       │    │   (端口: 20880)          │    │
│  │                     │    │                         │    │
│  │  AuthController     │    │  AuthRpcServiceImpl     │    │
│  │  UserController     │    │  TokenRpcServiceImpl    │    │
│  └──────────┬──────────┘    └──────────┬──────────────┘    │
│             │                          │                     │
│             └──────────┬───────────────┘                     │
│                        │                                     │
│  ┌─────────────────────▼─────────────────────────────────┐  │
│  │                   Service Layer                        │  │
│  │  AuthService │ UserService │ TokenService │ ...      │  │
│  └─────────────────────┬─────────────────────────────────┘  │
│                        │                                     │
│  ┌─────────────────────▼─────────────────────────────────┐  │
│  │              Data Layer (MySQL + Redis)                │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
                  ┌───────────────┐
                  │    Nacos      │
                  │   :8848       │
                  └───────────────┘
```

## 环境要求

- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- Nacos 2.3+ (服务注册中心)
- Maven 3.6+

## 快速开始

### 1. 启动 Nacos

```bash
# 下载 Nacos
wget https://github.com/alibaba/nacos/releases/download/2.3.2/nacos-server-2.3.2.zip
unzip nacos-server-2.3.2.zip
cd nacos/bin

# 启动单机模式
sh startup.sh -m standalone
```

### 2. 初始化数据库

```sql
CREATE DATABASE auth_service CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

执行 `docs/schema.sql` 初始化表结构。

### 3. 配置文件

修改 `auth-service-core/src/main/resources/application.yaml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/auth_service
    username: root
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379
      password: your_redis_password

# Nacos 配置
nacos:
  address: localhost:8848
```

### 4. 编译项目

```bash
cd auth-service
mvn clean install
```

### 5. 启动服务

```bash
cd auth-service-core
mvn spring-boot:run
```

服务将在以下端口启动：
- REST API: `http://localhost:8123`
- Dubbo RPC: `localhost:20880`

## API 接口

### REST API (前端调用)

详见 [docs/mvp-design.md](docs/mvp-design.md)

### Dubbo RPC (微服务间调用)

详见 [docs/dubbo-client-example.md](docs/dubbo-client-example.md)

#### 引入依赖

```xml
<dependency>
    <groupId>cn.wanyj.auth</groupId>
    <artifactId>auth-service-api</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

#### 调用示例

```java
@DubboReference(version = "1.0.0")
private AuthRpcService authRpcService;

// 验证令牌
TokenValidationResult result = authRpcService.validateToken(token);

// 获取用户信息
UserRpcResponse user = authRpcService.getUserById(userId);

// 检查权限
boolean hasPermission = authRpcService.hasPermission(userId, "order:create");
```

## 项目结构

```
auth-service/
├── auth-service-api/              # RPC 接口定义模块
│   └── src/main/java/
│       └── cn/wanyj/auth/api/
│           ├── auth/              # AuthRpcService
│           ├── token/             # TokenRpcService
│           └── model/             # RPC DTO
│
├── auth-service-core/             # 核心服务实现模块
│   └── src/main/java/cn/wanyj/auth/
│       ├── config/                # 配置类
│       ├── controller/            # REST API 控制器
│       ├── dto/                   # REST DTO
│       ├── entity/                # 实体类
│       ├── exception/             # 异常处理
│       ├── mapper/                # MyBatis Mapper
│       ├── rpc/                   # Dubbo RPC 实现
│       ├── security/              # 安全模块
│       └── service/               # 业务逻辑层
│
├── docs/                          # 文档
│   ├── mvp-design.md              # MVP 设计文档
│   ├── microservice-rpc-design.md # 微服务化设计文档
│   ├── dubbo-client-example.md    # Dubbo 客户端调用示例
│   └── schema.sql                 # 数据库初始化脚本
│
└── pom.xml                        # 父 POM
```

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.4.13 | 基础框架 |
| Apache Dubbo | 3.3.2 | RPC 框架 |
| Nacos | 2.4.2 | 注册中心 |
| Spring Security | - | 安全框架 |
| JWT | 0.12.6 | 令牌生成与验证 |
| MyBatis | 3.0.4 | ORM 框架 |
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 6.0+ | 缓存存储 |

## 配置说明

### Dubbo 配置

```yaml
dubbo:
  protocol:
    name: tri              # Triple 协议 (基于 HTTP/2)
    port: 20880
  registry:
    address: nacos://localhost:8848
```

### Nacos 配置

```yaml
nacos:
  discovery:
    server-addr: localhost:8848
    username: nacos
    password: nacos
```

## 文档

- [MVP 核心设计](docs/mvp-design.md)
- [微服务化改造设计](docs/microservice-rpc-design.md)
- [Dubbo 客户端调用示例](docs/dubbo-client-example.md)

## 许可证

[MIT](LICENSE)
