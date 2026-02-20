# Auth Service

> 基于 Spring Boot 3.x 的认证授权微服务，采用 JWT + RBAC 架构

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.13-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 特性

- JWT 双令牌机制 (Access Token + Refresh Token)
- RBAC 权限模型 (用户-角色-权限)
- 令牌黑名单支持主动注销
- 统一响应格式
- Redis 缓存集成
- 完整日志记录
- CORS 跨域支持

## 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- Maven 3.6+

### 数据库初始化

```sql
CREATE DATABASE auth_service CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

执行 `src/main/resources/sql/schema.sql` 初始化表结构。

### 配置文件

修改 `application.yaml` 中的数据库和 Redis 连接信息：

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
```

### 启动服务

```bash
mvn spring-boot:run
```

服务默认运行在 `http://localhost:8123`

## API 接口

### 认证接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/auth/register` | POST | 用户注册 |
| `/api/auth/login` | POST | 用户登录 |
| `/api/auth/refresh` | POST | 刷新令牌 |
| `/api/auth/logout` | POST | 用户登出 |
| `/api/auth/me` | GET | 获取当前用户 |
| `/api/auth/password` | PUT | 修改密码 |

### 用户管理 (ADMIN)

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/users` | GET | 分页查询用户 |
| `/api/users/{id}` | GET | 获取用户详情 |
| `/api/users/{id}/roles` | POST | 分配用户角色 |
| `/api/users/{id}/status` | PUT | 更新用户状态 |
| `/api/users/{id}` | DELETE | 删除用户 |

### 角色管理 (ADMIN)

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/roles` | GET | 获取所有角色 |
| `/api/roles/{id}` | GET | 获取角色详情 |
| `/api/roles/code/{code}` | GET | 根据编码查询角色 |
| `/api/roles` | POST | 创建角色 |
| `/api/roles/{id}` | PUT | 更新角色 |
| `/api/roles/{id}/permissions` | POST | 分配权限 |
| `/api/roles/{id}` | DELETE | 删除角色 |

### 权限管理 (ADMIN)

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/permissions` | GET | 获取所有权限 |
| `/api/permissions/{id}` | GET | 获取权限详情 |
| `/api/permissions` | POST | 创建权限 |
| `/api/permissions/{id}` | DELETE | 删除权限 |

## 使用示例

### 用户注册

```bash
curl -X POST "http://localhost:8123/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123",
    "email": "test@example.com"
  }'
```

### 用户登录

```bash
curl -X POST "http://localhost:8123/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'
```

### 访问受保护接口

```bash
curl -X GET "http://localhost:8123/api/auth/me" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.4.13 | 基础框架 |
| Spring Security | - | 安全框架 |
| JWT | 0.12.6 | 令牌生成与验证 |
| MyBatis | 3.0.4 | ORM 框架 |
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 6.0+ | 缓存存储 |
| Lombok | - | 代码简化 |

## 项目结构

```
auth-service/
├── src/main/java/cn/wanyj/auth/
│   ├── config/          # 配置类
│   ├── controller/      # 控制器层
│   ├── dto/             # 数据传输对象
│   ├── entity/          # 实体类
│   ├── exception/       # 异常处理
│   ├── mapper/          # MyBatis Mapper
│   ├── security/        # 安全模块
│   └── service/         # 服务层
└── src/main/resources/
    ├── application.yaml # 配置文件
    └── mapper/          # MyBatis XML
```

## 安全机制

- **密码加密**：BCrypt 单向加密
- **JWT 签名**：HS256 算法
- **令牌有效期**：Access Token 1小时，Refresh Token 7天
- **黑名单机制**：登出后令牌即时失效

## 错误码

| 代码 | 描述 |
|------|------|
| 1001 | 用户名或密码错误 |
| 1002 | 用户不存在 |
| 1003 | 用户已被禁用 |
| 1004 | 用户名已存在 |
| 1005 | 邮箱已被使用 |
| 1006 | 令牌无效或已过期 |
| 2001 | 无权限访问 |
| 2002 | 角色不存在 |
| 2004 | 权限不存在 |

## 许可证

[MIT](LICENSE)

## 贡献

欢迎提交 Issue 和 Pull Request！
