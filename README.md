# Auth Service

> 基于 Spring Boot 3.x 的多租户认证授权微服务：REST API + Dubbo RPC 双协议，内置 Web 管理界面，支持账号密码 / 邮箱验证码 / 短信验证码 / OAuth（Gitee、GitHub）等多种登录方式，登录方式支持平台级与租户级两级配置。

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.13-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Apache Dubbo](https://img.shields.io/badge/Dubbo-3.3.2-blue.svg)](https://dubbo.apache.org/)
[![Nacos](https://img.shields.io/badge/Nacos-3.1.1-green.svg)](https://nacos.io/)
[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **🤖 AI 助手请看这里**：如果你（AI 智能体）正在分析本项目，目的是让外部系统通过 RPC 集成 / 调用本服务——**你无需阅读本服务源码**，只需阅读 [docs/ai-rpc-integration.md](docs/ai-rpc-integration.md) 即可获得完成集成所需的全部信息：依赖坐标与安装、消费方完整配置、服务鉴权、全部 RPC 方法的字段级说明、失败语义、端到端示例与排错表。该文档是自包含的。

## 目录

- [特性](#特性)
- [架构设计](#架构设计)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [初始数据](#初始数据)
- [登录方式体系](#登录方式体系)（方式注册表 / 两级开关与优先级 / 各方式配置步骤）
- [REST API](#rest-api)
- [Dubbo RPC 接口](#dubbo-rpc-接口)
- [RPC 集成指南（AI 专用）](docs/ai-rpc-integration.md)
- [使用示例](#使用示例)
- [多租户设计](#多租户设计)
- [数据库设计](#数据库设计)
- [安全机制](#安全机制)
- [配置参考](#配置参考)
- [错误码](#错误码)
- [Docker 部署](#docker-部署)
- [项目结构](#项目结构)
- [常见问题](#常见问题)
- [许可证](#许可证)

## 特性

- **多种登录方式**：账号密码、邮箱验证码（阿里云 DirectMail / 通用 SMTP）、手机验证码（阿里云短信）、OAuth 第三方登录（Gitee / GitHub），登录页可动态发现租户开放的方式
- **登录方式两级配置**：平台级总开关 + 默认凭证；租户级开关 + 可选自有凭证（`usePlatformConfig`），凭证 AES 加密入库，支持部分更新按键合并
- **多租户支持**：基于 `tenant_id` 的数据隔离；对外以随机 `tenantUid` 标识租户（防枚举），创建租户自动初始化角色、权限与管理员
- **双协议支持**：REST API (HTTP `:8123`) + Dubbo RPC（Triple 协议 `:20880`，Protobuf 序列化），共 9 个 RPC 服务
- **Web 管理界面**：内置前端管理界面，支持用户 / 角色 / 权限 / 租户 / 登录方式 / 第三方绑定 / 账号资料管理
- **RBAC 权限模型**：用户-角色-权限三层模型，支持完整的 CRUD 权限
- **平台级权限**：平台租户（tenant_id=0）+ `ROLE_PLATFORM_ADMIN` 角色控制租户管理与平台级登录方式配置
- **JWT 双令牌机制**：Access Token（1 天）+ Refresh Token（7 天），基于 jti 的令牌黑名单
- **邮箱/手机绑定管理**：账号页凭验证码绑定 / 换绑 / 解绑邮箱与手机号，管理员代改视为已验证
- **安全防护**：登录滑动窗口限流、验证码发送限流、OAuth state 防 CSRF（一次性、10 分钟有效）、RPC 服务间令牌鉴权
- **审计日志**：异步记录用户操作（登录 / 注册 / 登出 / 改密 / 绑定等）
- **头像上传**：阿里云 OSS，按租户/用户路径隔离，扩展名白名单 + 2MB 限制
- **服务注册发现**：集成 Nacos 注册中心
- **敏感配置外部化**：JWT 密钥、AES 密钥、数据库 / Redis 密码等均通过环境变量注入

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
          │ ├────────────┤ │                    │ ├────────────┤ │
          │ │   Role     │ │                    │ │   OAuth    │ │
          │ │Controller  │ │                    │ │RpcService  │ │
          │ ├────────────┤ │                    │ ├────────────┤ │
          │ │Permission  │ │                    │ │LoginMethod │ │
          │ │Controller  │ │                    │ │RpcService  │ │
          │ ├────────────┤ │                    │ └────────────┘ │
          │ │  Tenant    │ │                    └────────┬───────┘
          │ │Controller  │ │                             │
          │ ├────────────┤ │                             │
          │ │LoginMethod │ │                             │
          │ │Config Ctrl │ │                             │
          │ ├────────────┤ │                             │
          │ │  Upload    │ │                             │
          │ └────────────┘ │                             │
          └────────┬───────┘                             │
                   │                                     │
                   └──────────┬──────────────────────────┘
                              │
                  ┌───────────▼──────────────────────┐
                  │        Service Layer             │
                  │ AuthService │ LoginMethodConfig  │
                  │ OAuthLoginService │ CodeService  │
                  │ UserService │ RoleService │ ...  │
                  └───────────┬──────────────────────┘
                              │
                  ┌───────────▼──────────────────────┐
                  │        Data Layer                │
                  │     MySQL + Redis + OSS          │
                  └──────────────────────────────────┘
```

## 环境要求

| 组件 | 版本要求 | 说明 |
|------|------|------|
| JDK | 17+  | 编程语言 |
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 6.0+ | 缓存 / 验证码 / 令牌存储 |
| Nacos | 3.1+ | 服务注册中心 |
| Maven | 3.6+ | 构建工具 |
| 阿里云 OSS | 可选 | 头像上传，不配置则上传接口不可用 |

## 快速开始

### 1. 初始化数据库

在 MySQL 中执行初始化脚本（自动建库 `auth_service`、建表并写入初始数据）：

```bash
mysql -u root -p < docs/init-schema.sql
```

### 2. 配置环境变量

复制 `.env.example` 为 `.env`，填写实际值：

```bash
cp .env.example .env
```

加载机制（[spring-dotenv](https://github.com/paulschwarz/spring-dotenv)）：

- **本地调试**：应用启动时自动加载项目根目录的 `.env`（从运行目录向上查找，IDEA 直接运行或 `mvn spring-boot:run` 均可生效），无需在 IDE 里手动配置环境变量。
- **部署打包后**：直接编辑 `docker-compose.yml` 中 `environment` 的配置值即可——容器内环境变量优先级高于 `application.yaml` 中的占位符默认值。`application.yaml` 与 `docker-compose.yml` 中均不含任何真实密钥，仓库里只有 `your_*` 占位样例。

| 变量 | 必填 | 说明 |
|------|------|------|
| `DB_USERNAME` / `DB_PASSWORD` | 是 | MySQL 账号密码 |
| `REDIS_PASSWORD` | 是 | Redis 密码 |
| `JWT_SECRET` | 是 | JWT 签名密钥（`openssl rand -base64 32` 生成） |
| `LOGIN_CONFIG_AES_KEY` | 是 | 登录方式凭证加密密钥（AES，`openssl rand -base64 32` 生成），用于加密 `login_method_config.config_json` |
| `RPC_SERVICE_TOKEN` | 建议 | Dubbo 服务间调用鉴权令牌，未配置时跳过 RPC 鉴权 |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | 视情况 | Nacos 开启鉴权时必填，未开启可留空 |
| `DB_URL` / `REDIS_HOST` / `REDIS_PORT` / `NACOS_ADDRESS` | 可选 | 默认本机地址（`localhost`） |
| `OSS_ENDPOINT` / `OSS_BUCKET` / `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | 可选 | 阿里云 OSS（头像上传） |
| `OSS_OBJECT_PREFIX` | 可选 | 对象前缀，默认 `avatar` |

> **警告**：`LOGIN_CONFIG_AES_KEY` 一旦用于加密凭证后不可更换，否则已存凭证将无法解密。

### 3. 启动服务

**方式一：Docker Compose（推荐生产部署）**

见 [Docker 部署](#docker-部署)。

**方式二：本地开发**

1. 启动 MySQL、Redis、Nacos
2. 准备根目录 `.env`（见[配置环境变量](#2-配置环境变量)，spring-dotenv 启动时自动加载）
3. 导入项目为 Maven 工程，等待依赖下载
4. 运行 `Application` 主类（IDEA / Eclipse），或：

```bash
mvn spring-boot:run -pl auth-service-core
```

### 4. 登录管理界面

浏览器访问：<http://localhost:8123>（自动跳转登录页）

| 租户 | 用户名 | 密码 | 租户标识 (tenantUid) | 用途 |
|------|--------|------|------|------|
| 平台租户 | `admin` | `123456` | `pk7q2m8e` | 平台管理：租户管理、平台级登录方式配置 |
| 演示租户 | `admin` | `123456` | `dm3a9x1f` | 租户管理：用户/角色/权限、租户级登录方式配置 |

登录后根据租户类型显示不同功能：平台租户只见租户管理与平台登录方式配置；普通租户可见用户、角色、权限、登录方式与账号资料管理。

> **安全提示**：生产环境务必修改所有默认密码与密钥！

## 初始数据

执行 `docs/init-schema.sql` 后系统包含以下初始数据：

### 租户

| id | tenant_code | tenant_uid | 名称 | 说明 |
|----|-------------|------------|------|------|
| 0 | platform | `pk7q2m8e` | 平台租户 | `is_platform=1`，系统管理租户 |
| 1 | demo | `dm3a9x1f` | 演示租户 | 默认业务租户 |

### 管理员账户（密码均为 `123456`，BCrypt 加密）

| 租户 | 用户 id | 用户名 | 角色 | 权限 |
|------|---------|--------|------|------|
| 平台租户 (id=0) | 0 | admin | ROLE_PLATFORM_ADMIN | 4 个 `platform:tenant:*` 权限 |
| 演示租户 (id=1) | 1 | admin | ROLE_ADMIN | 12 个 `user/role/permission:*` 全量权限 |

### 角色与权限

| 角色 | 归属租户 | 权限 |
|------|----------|------|
| `ROLE_PLATFORM_ADMIN` | 平台租户 (0) | platform:tenant:create / update / delete / read |
| `ROLE_ADMIN` | 演示租户 (1) | user / role / permission 全部 12 个 CRUD 权限 |
| `ROLE_USER` | 演示租户 (1) | 仅 `user:read` |

### 登录方式

`login_method_config` 表仅预置一条：**平台级 `password` 恒为启用**（平台不可关闭，避免锁死）。其余登录方式默认关闭，需平台管理员在「平台登录方式配置」中开启并配置凭证，见[登录方式体系](#登录方式体系)。

### 新建租户的自动初始化

通过 API / 管理界面创建租户时，系统自动完成：

1. 生成 8 位随机 `tenant_uid`（[a-z0-9]，防枚举，DB 唯一约束兜底）
2. 初始化 12 个默认权限（`user/role/permission` × read/create/write/delete）
3. 创建 `ROLE_ADMIN`（全量权限）与 `ROLE_USER`（仅 `user:read`）角色
4. 创建租户管理员 `admin / 123456`（雪花 ID）

## 登录方式体系

### 支持的登录方式

登录方式以 `method = category:vendor` 编码，注册于 `LoginMethod` 枚举。新增方式只需追加枚举项并实现对应 Sender / Provider：

| method | 类别 | 显示名 | 凭证配置方 | 说明 |
|--------|------|--------|-----------|------|
| `password` | password | 账号密码 | 无需凭证 | 内置恒开，支持用户名或邮箱 + 密码登录 |
| `email:aliyun` | email | 邮箱验证码（阿里云） | 平台或租户 | 阿里云 DirectMail 发送验证码 |
| `email:smtp` | email | 邮箱验证码（SMTP 自有邮箱） | 平台或租户 | 通用 SMTP（QQ / 163 / 企业邮箱等） |
| `sms:aliyun` | sms | 手机验证码（阿里云） | 平台或租户 | 阿里云短信服务 |
| `oauth:gitee` | oauth | Gitee 登录 | 平台或租户 | OAuth 2.0 授权码模式 |
| `oauth:github` | oauth | GitHub 登录 | 平台或租户 | OAuth 2.0 授权码模式 |

### 两级开关与优先级逻辑

每个登录方式受**平台级**（`login_method_config` 中 `tenant_id=0` 的行）与**租户级**（`tenant_id=<租户id>` 的行）两级配置控制。某租户某方式是否生效（`isEnabled`）按以下顺序判定：

```
1. method 不受支持                         → 不生效
2. 租户是平台租户(tenant_id=0) 且不是 password → 不生效   ← 平台租户仅允许账号密码登录
3. 平台级开关：
   - password 恒为开启（不可关闭，防锁死）
   - 其他方式需平台级行 enabled=1
   平台未开启                                → 不生效
4. 租户级行不存在：password 默认开，其他默认关
5. 租户级行存在：enabled=1 才生效
```

**凭证优先级**（`getEffectiveConfig`，运行时 AES 解密）：

```
租户级行存在 且 use_platform_config=0 且自身 config_json 非空
    → 使用租户自有凭证
否则
    → 使用平台默认凭证（平台级行 config_json）
    → 平台也未配置 → 该方式无凭证（发码/授权时报「凭证未配置」）
```

补充规则：

- **邮箱类互斥**：同一租户内 `email:aliyun` 与 `email:smtp` 只能启用其一（保证登录/绑定链路唯一）；切换时须先禁用旧方式。仅 email 类别受限，sms / oauth 不受影响
- **租户可配置范围**：租户配置页仅展示平台已开启的方式；平台关闭某方式后，租户侧立即失效
- **凭证部分更新**：保存 `configJson` 时按非空键合并覆盖旧值——只想改邮件模板而不动已保存的 AK 凭证时，只传要改的键即可；空字符串视为「未提供」，不覆盖
- **验证码有效期**：`codeTtlMinutes`（1~30 分钟，默认 5），保存时校验合法性；运行时超范围自动收敛到边界，避免误配导致发码不可用

### 配置步骤

#### 前置：生成凭证加密密钥

所有方式的凭证（`configJson`）均以 `LOGIN_CONFIG_AES_KEY` AES 加密后入库，页面/接口传入**明文 JSON**，由服务端加密。

#### 1. 平台管理员开启方式并配置默认凭证

```
GET  /api/platform/login-methods                      # 查看所有方式及开关/凭证状态
PUT  /api/platform/login-methods/{method}             # 保存开关与默认凭证
```

示例——开启 `email:smtp` 并配置平台默认凭证：

```bash
curl -X PUT http://localhost:8123/api/platform/login-methods/email:smtp \
  -H "Authorization: Bearer <平台管理员token>" \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": 1,
    "configJson": "{\"host\":\"smtp.qq.com\",\"port\":\"465\",\"username\":\"you@qq.com\",\"password\":\"你的授权码\"}"
  }'
```

#### 2. 租户管理员开启本租户方式（可选：使用自有凭证）

```
GET  /api/tenant/login-methods                        # 本租户可配置的方式（平台已开启的子集）
PUT  /api/tenant/login-methods/{method}               # 保存本租户开关与凭证来源
```

示例——演示租户开启 `email:smtp` 并改用自有凭证：

```bash
curl -X PUT http://localhost:8123/api/tenant/login-methods/email:smtp \
  -H "Authorization: Bearer <租户管理员token>" \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": 1,
    "usePlatformConfig": 0,
    "configJson": "{\"host\":\"smtp.163.com\",\"port\":\"465\",\"username\":\"biz@163.com\",\"password\":\"授权码\"}"
  }'
```

若 `usePlatformConfig=1`（默认）则直接使用平台默认凭证，无需传 `configJson`。

#### 3. 登录页动态发现可用方式（公开接口）

```bash
curl "http://localhost:8123/api/auth/login-methods?tenantUid=dm3a9x1f"
# → {"code":200,"data":["password","email:smtp"]}
```

### 各登录方式凭证（configJson）结构

#### `email:aliyun`（阿里云 DirectMail）

必填：`accessKeyId`、`accessKeySecret`、`accountName`（控制台发信地址）

```json
{
  "accessKeyId": "LTAI5t...",
  "accessKeySecret": "...",
  "accountName": "noreply@mail.yourdomain.com",
  "fromAlias": "Auth Service",
  "region": "cn-hangzhou",
  "codeTtlMinutes": "5",
  "subject": "登录验证码",
  "template": "<p>您的验证码是 <b>{code}</b>，{minutes} 分钟内有效</p>"
}
```

#### `email:smtp`（通用 SMTP：QQ / 163 / 企业邮箱等）

必填：`host`、`username`、`password`（邮箱授权码或登录密码）

```json
{
  "host": "smtp.qq.com",
  "port": "465",
  "username": "you@qq.com",
  "password": "SMTP授权码",
  "from": "you@qq.com",
  "fromAlias": "Auth Service",
  "encryption": "ssl",
  "codeTtlMinutes": "5",
  "subject": "登录验证码",
  "template": "<p>您的验证码是 <b>{code}</b>，{minutes} 分钟内有效</p>"
}
```

| 字段 | 说明 |
|------|------|
| `encryption` | `ssl`（默认，端口默认 465）/ `starttls`（默认 587）/ `none`（默认 25，仅限内网中继） |
| `port` | 未配置时按 encryption 取默认；非法值保存/发送时报错 |
| `from` / `fromAlias` | 发件人与别名，未配置默认取 `username` / `Auth Service` |

**邮件模板规则**（平台级与租户级均可自定义，按生效侧取值）：

- `template` 必须包含 `{code}` 占位符，否则回退默认文案（避免用户收不到验证码）
- `{code}` → 6 位验证码；`{minutes}` → 有效期分钟数
- `subject` 未配置时默认「登录验证码」
- 正文的默认文案：`您的验证码是：<strong>123456</strong>，5 分钟内有效。`

#### `sms:aliyun`（阿里云短信）

必填：`accessKeyId`、`accessKeySecret`、`signName`、`templateCode`。短信模板需包含 `${code}` 变量。

```json
{
  "accessKeyId": "LTAI5t...",
  "accessKeySecret": "...",
  "signName": "你的签名",
  "templateCode": "SMS_123456789",
  "region": "cn-hangzhou"
}
```

#### `oauth:gitee` / `oauth:github`

先在提供方创建 OAuth 应用，将回调地址配到本服务，再把凭证填入：

```json
{
  "clientId": "你的Client ID",
  "clientSecret": "你的Client Secret",
  "redirectUri": "https://你的域名/api/auth/oauth/gitee/callback"
}
```

| 提供方 | 创建应用 | redirectUri | 默认 scope |
|--------|----------|-------------|-----------|
| Gitee | <https://gitee.com/oauth/applications> | `https://你的域名/api/auth/oauth/gitee/callback` | `user_info` |
| GitHub | <https://github.com/settings/developers> | `https://你的域名/api/auth/oauth/github/callback` | `read:user` |

> GitHub `/user` 默认不返回私密邮箱（email 可能为空），不影响登录；如需邮箱可扩展 scope `user:email` 并调用 `/user/emails`。

### OAuth 登录 / 绑定流程

```
登录：浏览器 → GET /api/auth/oauth/{provider}/authorize?tenantUid=xxx
      302 → 提供方授权页（携带一次性 state，Redis 存 10 分钟）
      用户授权 → 提供方 302 → GET /api/auth/oauth/{provider}/callback?code&state
      服务端：校验 state → 换 access_token → 拉取用户信息
            → 已有绑定则直接登录；无绑定则自动建用户（随机密码、ROLE_USER、绑定关系）
            → 302 → /login.html#oauth=success&accessToken=...&refreshToken=...

绑定：已登录用户 → GET /api/auth/oauth/{provider}/bind（返回授权 URL，前端跳转）
      授权回调同上，state 中 mode=bind → 绑定到当前账号
            → 302 → /index.html#bindings&bind=success|failed
```

- 同一 `(tenant_id, provider, provider_uid)` 唯一：第三方账号已绑其他本地账号时拒绝重复绑定
- 同一用户同一 provider 仅可绑定一次
- OAuth 新建用户：`username = {provider}_{providerUid}`（截断至 50 字符），随机密码（不可密码登录），邮箱冲突时不落邮箱，`email_verified=false`（需自行走验证码流程验证）

### 验证码机制

| 项 | 规则 |
|----|------|
| 格式 | 6 位数字，SecureRandom 生成 |
| 存储 | Redis，key = `login:code:{tenantId}:{method}:{target}`（租户+方式+目标三级隔离） |
| 有效期 | 默认 5 分钟；可按方式配置 `codeTtlMinutes`（1~30） |
| 校验 | 匹配即删除（一次性），不匹配/过期返回失败 |
| 发送限流 | 同一目标（邮箱/手机号）60 秒内仅可发 1 次（`rate_limit:code:{target}`） |

**验证码登录的用户处理**：邮箱/手机号在租户内不存在时**自动注册并登录**（与 OAuth 新账号逻辑一致：随机密码、ROLE_USER、验证标记置真）；已存在则顺带置 `email_verified` / `phone_verified = true`。

**账号绑定**：已登录用户凭验证码绑定/换绑邮箱或手机号（`POST /api/auth/me/email`、`POST /api/auth/me/phone`）；验码先于唯一性检查，防止无验证码探测占用情况；解绑清空字段并重置验证标记。

## REST API

统一响应格式：

```json
{ "code": 200, "message": "成功", "data": { ... } }
```

### 认证接口（`/api/auth`，AuthController）

| 端点 | 方法 | 描述 | 认证 |
|------|------|------|------|
| `/api/auth/register` | POST | 用户注册（自动登录） | 公开 |
| `/api/auth/login` | POST | 账号密码登录（用户名或邮箱） | 公开 |
| `/api/auth/send-code` | POST | 发送验证码（邮箱/手机） | 公开 |
| `/api/auth/login-by-code` | POST | 验证码登录（不存在则自动注册） | 公开 |
| `/api/auth/oauth/{provider}/authorize` | GET | OAuth 登录入口，302 到提供方（`?tenantUid=`） | 公开 |
| `/api/auth/oauth/{provider}/callback` | GET | OAuth 回调（提供方重定向进入） | 公开 |
| `/api/auth/oauth/{provider}/bind` | GET | 发起绑定授权，返回授权 URL | 需登录 |
| `/api/auth/me/oauth` | GET | 我的第三方绑定列表 | 需登录 |
| `/api/auth/me/oauth/{provider}` | DELETE | 解绑第三方平台 | 需登录 |
| `/api/auth/me/email` | POST | 绑定/换绑邮箱（凭验证码） | 需登录 |
| `/api/auth/me/email` | DELETE | 解绑邮箱 | 需登录 |
| `/api/auth/me/phone` | POST | 绑定/换绑手机号（凭验证码） | 需登录 |
| `/api/auth/me/phone` | DELETE | 解绑手机号 | 需登录 |
| `/api/auth/refresh` | POST | 刷新令牌（`Authorization: Bearer {refreshToken}`） | 公开 |
| `/api/auth/logout` | POST | 登出（拉黑 access、删除 refresh） | 公开 |
| `/api/auth/me` | GET | 当前用户信息（含租户名/租户标识） | 需登录 |
| `/api/auth/password` | PUT | 修改密码 | 需登录 |

### 登录方式配置（LoginMethodConfigController）

| 端点 | 方法 | 描述 | 权限 |
|------|------|------|------|
| `/api/platform/login-methods` | GET | 所有方式 + 平台开关与默认凭证状态 | 平台管理员 |
| `/api/platform/login-methods/{method}` | PUT | 保存平台开关与默认凭证（password 不可关） | 平台管理员 |
| `/api/tenant/login-methods` | GET | 本租户可配置方式及开关/凭证来源 | ADMIN / PLATFORM_ADMIN |
| `/api/tenant/login-methods/{method}` | PUT | 保存本租户开关与凭证来源 | ADMIN / PLATFORM_ADMIN |
| `/api/auth/login-methods` | GET | 某租户开放的登录方式（`?tenantUid=`，仅返回 method 名） | 公开 |

### 用户管理（`/api/users`，UserController）

| 端点 | 方法 | 描述 | 权限 |
|------|------|------|------|
| `/api/users/{id}` | GET | 根据 ID 获取用户 | ADMIN |
| `/api/users` | GET | 搜索用户（`?page=1&size=10&keyword=`） | ADMIN |
| `/api/users/{id}` | PUT | 更新用户资料（管理员编辑；邮箱/手机视为已验证绑定） | ADMIN |
| `/api/users/{id}/roles` | POST | 为用户分配角色 | ADMIN |
| `/api/users/{id}/status` | PUT | 更新用户状态（`?status=0|1`） | ADMIN |
| `/api/users/{id}` | DELETE | 删除用户 | ADMIN |

### 角色管理（`/api/roles`，RoleController）

| 端点 | 方法 | 描述 | 权限 |
|------|------|------|------|
| `/api/roles` | GET | 获取所有角色 | ADMIN |
| `/api/roles/{id}` | GET | 根据 ID 获取角色 | ADMIN |
| `/api/roles/code/{code}` | GET | 根据编码获取角色 | ADMIN |
| `/api/roles` | POST | 创建角色 | ADMIN |
| `/api/roles/{id}` | PUT | 更新角色 | ADMIN |
| `/api/roles/{id}/permissions` | POST | 为角色分配权限 | ADMIN |
| `/api/roles/{id}` | DELETE | 删除角色 | ADMIN |

### 权限管理（`/api/permissions`，PermissionController）

| 端点 | 方法 | 描述 | 权限 |
|------|------|------|------|
| `/api/permissions` | GET | 获取所有权限 | ADMIN |
| `/api/permissions/{id}` | GET | 根据 ID 获取权限 | ADMIN |
| `/api/permissions` | POST | 创建权限 | ADMIN |
| `/api/permissions/{id}` | DELETE | 删除权限 | ADMIN |

### 租户管理（`/api/tenant`，TenantController）

> 路径参数为**租户对外标识 uid**（`tenantUid`），非数字 id。

| 端点 | 方法 | 描述 | 权限 |
|------|------|------|------|
| `/api/tenant` | POST | 创建租户（自动初始化角色/权限/管理员） | 平台管理员 |
| `/api/tenant` | GET | 获取所有租户（含用户数统计） | 平台管理员 |
| `/api/tenant/{uid}` | GET | 获取租户详情 | 平台管理员 |
| `/api/tenant/{uid}` | PUT | 更新租户 | 平台管理员 |
| `/api/tenant/{uid}` | DELETE | 删除租户（平台租户不可删） | 平台管理员 |
| `/api/tenant/check-code` | GET | 检查租户编码是否可用（`?code=`） | 公开 |
| `/api/tenant/available` | GET | 可用租户列表（登录页下拉，返回 code/uid/name） | 公开 |

### 文件上传（`/api/upload`，UploadController）

| 端点 | 方法 | 描述 | 权限 |
|------|------|------|------|
| `/api/upload/avatar` | POST | 上传头像（multipart `file`，可选 `targetUserId` 指定归属用户） | 需登录 |

限制：扩展名白名单 jpg / jpeg / png / gif / webp，最大 2MB；未配置 OSS 时返回「OSS 未配置」。

## Dubbo RPC 接口

> **🤖 AI 助手 / 快速集成者**：本章仅为概览。若你要在外部系统中集成调用本服务的 RPC，请**直接阅读自包含的 [docs/ai-rpc-integration.md](docs/ai-rpc-integration.md)（RPC 集成指南）**，其中包含消费方工程配置、服务鉴权、每个方法的字段级参数表、失败语义与完整示例，无需阅读本服务源码。

Triple 协议（HTTP/2）+ Protobuf 序列化，IDL 定义见 [`auth-service-api/src/main/proto/auth/auth_service.proto`](auth-service-api/src/main/proto/auth/auth_service.proto)。

### 引入依赖

```xml
<dependency>
    <groupId>cn.wanyj.auth</groupId>
    <artifactId>auth-service-api</artifactId>
    <version>1.1</version>
</dependency>
```

> **注意**：请求消息中的 ID 字段（`userId`、`roleId` 等）均用 `string` 类型，避免 JavaScript 等弱类型语言中雪花 ID 大整数精度丢失，调用时直接传字符串；租户统一传 `tenantUid`（8 位随机串）。

### 服务总览（9 个）

| 服务 | 能力域 |
|------|--------|
| `AuthRpcServiceProtobuf` | 注册 / 认证 / 用户与权限查询 / 令牌刷新登出 / 改密 / 验证码登录 |
| `TokenRpcServiceProtobuf` | 令牌生成 / 解析 / 撤销 |
| `UserRpcServiceProtobuf` | 用户更新 / 状态 / 分配角色 / 删除 |
| `RoleRpcServiceProtobuf` | 角色 CRUD / 分配权限 |
| `PermissionRpcServiceProtobuf` | 权限查询 / 创建 / 删除 |
| `OAuthRpcServiceProtobuf` | OAuth 登录与绑定编排（授权 URL / 回调 / 绑定列表 / 解绑） |
| `ContactBindingRpcServiceProtobuf` | 邮箱/手机绑定与解绑 |
| `LoginMethodRpcServiceProtobuf` | 登录方式配置（平台 / 租户 / 公开三层） |
| `OssRpcServiceProtobuf` | 头像上传 |

### AuthRpcServiceProtobuf

| RPC 方法 | 描述 | 请求 | 响应 |
|----------|------|------|------|
| `register` | 注册用户并自动登录 | `RegisterRpcRequest` | `RegisterRpcResult` |
| `authenticate` | 验证用户凭证 | `LoginRpcRequest` | `AuthResult` |
| `getUserById` | 根据 ID 获取用户 | `UserByIdRequest` | `UserRpcResponse` |
| `getUserByUsername` | 根据用户名获取用户 | `UserByUsernameRequest` | `UserRpcResponse` |
| `hasPermission` | 检查用户权限 | `PermissionCheckRequest` | `BoolValue` |
| `hasRole` | 检查用户角色 | `RoleCheckRequest` | `BoolValue` |
| `getUserPermissions` | 获取用户权限列表 | `UserPermissionsRequest` | `StringListResponse` |
| `getUserRoles` | 获取用户角色列表 | `UserRolesRequest` | `StringListResponse` |
| `searchUsers` | 分页查询用户 | `SearchUsersRequest` | `UserPageResponse` |
| `refreshToken` | 刷新访问令牌 | `RefreshTokenRpcRequest` | `TokenRpcResponse` |
| `logout` | 用户登出 | `LogoutRpcRequest` | `OperationResult` |
| `changePassword` | 修改密码 | `ChangePasswordRpcRequest` | `OperationResult` |
| `sendCode` | 发送验证码（邮箱/手机） | `SendCodeRpcRequest` | `OperationResult` |
| `loginByCode` | 验证码登录 | `LoginByCodeRpcRequest` | `LoginByCodeRpcResult` |

### TokenRpcServiceProtobuf

| RPC 方法 | 描述 | 请求 | 响应 |
|----------|------|------|------|
| `generateToken` | 为用户生成令牌 | `TokenGenerationRequest` | `TokenRpcResponse` |
| `parseToken` | 解析令牌获取用户信息 | `ParseTokenRpcRequest` | `TokenValidationResult` |
| `revokeAllTokens` | 撤销用户所有令牌 | `RevokeAllTokensRpcRequest` | `Empty` |

### UserRpcServiceProtobuf

| RPC 方法 | 描述 | 请求 | 响应 |
|----------|------|------|------|
| `updateUser` | 更新用户信息（字段掩码） | `UpdateUserRpcRequest` | `OperationResult` |
| `updateUserStatus` | 更新用户状态 | `UpdateUserStatusRpcRequest` | `OperationResult` |
| `assignRoles` | 为用户分配角色 | `AssignRolesRpcRequest` | `OperationResult` |
| `deleteUser` | 删除用户 | `DeleteUserRpcRequest` | `OperationResult` |

### RoleRpcServiceProtobuf

| RPC 方法 | 描述 | 请求 | 响应 |
|----------|------|------|------|
| `getAllRoles` | 获取所有角色 | `GetAllRolesRequest` | `RoleListResponse` |
| `getRoleByCode` | 根据编码获取角色 | `GetRoleByCodeRequest` | `RoleRpcResponse` |
| `getRoleById` | 根据 ID 获取角色 | `GetRoleByIdRequest` | `RoleRpcResponse` |
| `createRole` | 创建角色 | `CreateRoleRpcRequest` | `RoleRpcResponse` |
| `updateRole` | 更新角色 | `UpdateRoleRpcRequest` | `OperationResult` |
| `deleteRole` | 删除角色 | `DeleteRoleRpcRequest` | `OperationResult` |
| `assignPermissions` | 为角色分配权限 | `AssignPermissionsRpcRequest` | `OperationResult` |

### PermissionRpcServiceProtobuf

| RPC 方法 | 描述 | 请求 | 响应 |
|----------|------|------|------|
| `getAllPermissions` | 获取所有权限 | `GetAllPermissionsRequest` | `PermissionListResponse` |
| `getPermissionById` | 根据 ID 获取权限 | `GetPermissionByIdRequest` | `PermissionRpcResponse` |
| `createPermission` | 创建权限 | `CreatePermissionRpcRequest` | `PermissionRpcResponse` |
| `deletePermission` | 删除权限 | `DeletePermissionRpcRequest` | `OperationResult` |

### OAuthRpcServiceProtobuf

| RPC 方法 | 描述 | 请求 | 响应 |
|----------|------|------|------|
| `buildAuthorizeUrl` | 构建登录授权页 URL（state 已暂存） | `OAuthAuthorizeUrlRpcRequest` | `OAuthUrlRpcResponse` |
| `buildBindAuthorizeUrl` | 构建绑定授权页 URL（已登录用户） | `OAuthBindUrlRpcRequest` | `OAuthUrlRpcResponse` |
| `handleCallback` | 处理回调：校验 state + 换 token + 匹配/建用户 + 签发 token（或完成绑定） | `OAuthCallbackRpcRequest` | `OAuthCallbackRpcResult` |
| `listBindings` | 列出用户已绑定的第三方平台 | `OAuthBindingsRpcRequest` | `OAuthBindingListRpcResponse` |
| `unbind` | 解绑某第三方平台 | `OAuthUnbindRpcRequest` | `OperationResult` |

### ContactBindingRpcServiceProtobuf

| RPC 方法 | 描述 | 请求 | 响应 |
|----------|------|------|------|
| `bindEmail` | 绑定/换绑邮箱（验证码验证后覆盖旧值） | `BindContactRpcRequest` | `OperationResult` |
| `unbindEmail` | 解绑邮箱（清空并重置验证标记） | `ContactUnbindRpcRequest` | `OperationResult` |
| `bindPhone` | 绑定/换绑手机号 | `BindContactRpcRequest` | `OperationResult` |
| `unbindPhone` | 解绑手机号 | `ContactUnbindRpcRequest` | `OperationResult` |

### LoginMethodRpcServiceProtobuf

| RPC 方法 | 描述 | 请求 | 响应 |
|----------|------|------|------|
| `listPlatformConfigs` | 平台级：所有方式及开关与默认凭证状态 | `Empty` | `LoginMethodListRpcResponse` |
| `savePlatformConfig` | 平台级：保存开关与默认凭证 | `SavePlatformLoginMethodRpcRequest` | `OperationResult` |
| `listTenantConfigs` | 租户级：本租户可配置方式及开关/凭证来源 | `TenantLoginMethodRpcRequest` | `LoginMethodListRpcResponse` |
| `saveTenantConfig` | 租户级：保存开关与凭证来源 | `SaveTenantLoginMethodRpcRequest` | `OperationResult` |
| `listEnabledMethods` | 公开：按 tenantUid 返回开放方式列表 | `EnabledLoginMethodsRpcRequest` | `StringListResponse` |

### OssRpcServiceProtobuf

| RPC 方法 | 描述 | 请求 | 响应 |
|----------|------|------|------|
| `uploadAvatar` | 上传头像，返回可访问 URL | `UploadAvatarRpcRequest` | `UploadAvatarRpcResponse` |

### 多租户归属校验

为防止跨租户越权，所有「按 ID 操作资源」的 RPC 都需在请求中携带 `tenantUid`（对外租户标识），服务端解析为内部租户后校验资源归属：

- 用户：`getUserById` / `getUserByUsername` / `updateUser` / `updateUserStatus` / `assignRoles` / `deleteUser`
- 角色：`getRoleById` / `updateRole` / `deleteRole` / `assignPermissions`
- 权限：`getPermissionById` / `deletePermission`
- 令牌：`revokeAllTokens`

> 上述按 ID 操作的方法 `tenantUid` 允许留空：留空=跳过归属校验；非空时强制校验，资源不属于该租户则视为不存在（返回空/失败，不抛 Forbidden，避免泄露存在性）。其余方法的 `tenantUid` 必填且须为有效租户。

### updateUser 字段掩码

`UpdateUserRpcRequest` 通过 `fields_to_update` 声明本次要更新的字段，**仅出现在列表中的字段才会被更新**。这解决了 proto3 中 `status=0`（禁用）、空字符串（清空字段）等默认值无法表达的陷阱：

```java
UpdateUserRpcRequest.newBuilder()
    .setUserId("123456")
    .setTenantUid("dm3a9x1f")
    .setNickname("新昵称")        // 设置新值
    .setStatus(0)                // 0 = 禁用（因出现在掩码中，不会被当作「未提供」）
    .addFieldsToUpdate("nickname")
    .addFieldsToUpdate("status")
    .build();
// 结果：仅 nickname 和 status 被更新，其余字段不变
```

### 调用示例

```java
@DubboReference(version = "1.0.0")
private AuthRpcServiceProtobuf authRpcService;

// 获取用户信息（多租户归属校验）
UserByIdRequest request = UserByIdRequest.newBuilder()
    .setUserId(userId)
    .setTenantUid(tenantUid)
    .build();
UserRpcResponse user = authRpcService.getUserById(request);

// 检查权限
PermissionCheckRequest permRequest = PermissionCheckRequest.newBuilder()
    .setUserId(userId)
    .setPermission("user:delete")
    .setTenantUid(tenantUid)
    .build();
BoolValue result = authRpcService.hasPermission(permRequest);
```

更多示例见 [docs/ai-rpc-integration.md](docs/ai-rpc-integration.md)（含网关鉴权、验证码登录、令牌签发、OAuth、用户管理等端到端场景）。

### RPC 服务鉴权

- 机制：Dubbo SPI Filter（`RpcAuthFilter`）
- 调用方在 attachment 中携带 `rpc-service-token`，与服务端 `RPC_SERVICE_TOKEN` 比对
- 服务端未配置 `RPC_SERVICE_TOKEN` 时跳过鉴权（开发环境友好）

## 使用示例

以下示例中 `tenantUid=dm3a9x1f` 为演示租户的对外标识（见[初始数据](#初始数据)，也可以通过 `GET /api/tenant/available` 查询）。

### 账号密码登录（用户名或邮箱）

```bash
curl -X POST http://localhost:8123/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "123456",
    "tenantUid": "dm3a9x1f"
  }'
```

### 用户注册（自动登录，需租户数字 ID）

```bash
curl -X POST http://localhost:8123/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "password123",
    "email": "alice@example.com",
    "nickname": "Alice",
    "tenantId": 1
  }'
```

### 发送邮箱验证码并登录

```bash
# 1. 发送验证码到邮箱（须该租户已启用 email:smtp 或 email:aliyun）
curl -X POST http://localhost:8123/api/auth/send-code \
  -H "Content-Type: application/json" \
  -d '{"tenantUid": "dm3a9x1f", "method": "email:smtp", "target": "alice@example.com"}'

# 2. 凭验证码登录（邮箱在租户内不存在时自动注册）
curl -X POST http://localhost:8123/api/auth/login-by-code \
  -H "Content-Type: application/json" \
  -d '{"tenantUid": "dm3a9x1f", "method": "email:smtp", "target": "alice@example.com", "code": "123456"}'
```

### 携带令牌访问 / 刷新 / 登出

```bash
# 获取当前用户信息
curl http://localhost:8123/api/auth/me \
  -H "Authorization: Bearer <access_token>"

# 刷新令牌（Authorization 头传 refreshToken）
curl -X POST http://localhost:8123/api/auth/refresh \
  -H "Authorization: Bearer <refresh_token>"

# 登出（拉黑 access + 删除 refresh）
curl -X POST http://localhost:8123/api/auth/logout \
  -H "Authorization: Bearer <access_token>" \
  -H "X-Refresh-Token: <refresh_token>"
```

### 创建租户（平台管理员）

```bash
curl -X POST http://localhost:8123/api/tenant \
  -H "Authorization: Bearer <platform_admin_token>" \
  -H "Content-Type: application/json" \
  -d '{"tenantCode": "new-tenant", "tenantName": "新租户", "maxUsers": 100}'
# 返回含自动生成的 tenantUid 与初始化的管理员账户（admin/123456）
```

### 绑定邮箱（已登录用户）

```bash
# 1. 先向新邮箱发码（同 send-code），然后：
curl -X POST http://localhost:8123/api/auth/me/email \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{"method": "email:smtp", "target": "new@example.com", "code": "123456"}'
```

## 多租户设计

**隔离策略**：共享数据库，通过 `tenant_id` 字段隔离。

| 实体 | tenant_id | 行为 |
|------|-----------|------|
| User | 是 | 用户名/邮箱租户内唯一 |
| Role | 是 | 角色编码租户内唯一 |
| Permission | 是 | 权限编码租户内唯一 |
| UserOauth | 是 | 第三方绑定按 (tenant, provider, provider_uid) 隔离 |
| LoginMethodConfig | 是 | tenant_id=0 为平台级，其他为租户级 |
| Tenant | 否 | 全局租户注册表 |

**两种租户标识**：

| 标识 | 用途 | 使用场景 |
|------|------|----------|
| `tenantId`（数字） | 内部主键，JWT 中签发 | REST 注册请求、服务端内部（RPC 已不暴露） |
| `tenantUid`（8 位随机串） | 对外标识，防枚举自增 ID | 全部 RPC 租户参数、登录、发码、验证码登录、登录方式发现、租户管理路径 |

**租户识别方式**：

- 注册：请求参数 `tenantId`（必填）
- 登录/发码/验证码登录：请求参数 `tenantUid`（必填，服务端换算为内部 id）
- 其他请求：从 JWT 令牌提取（服务端签名，不可伪造）

**平台租户（tenant_id=0）特殊规则**：

- 仅允许账号密码登录（其他登录方式一律禁用）
- 拥有 `ROLE_PLATFORM_ADMIN` 角色的用户为平台管理员，可管理租户与平台级登录方式
- 平台租户不可删除

**Redis Key 模式**：

```
refresh_token:{tenant_id}:{user_id}          # 刷新令牌
blacklist:{tenant_id}:{jti}                  # 令牌黑名单
rate-limit:login:{tenant_id}:{username}      # 登录限流（15分钟5次）
login:code:{tenant_id}:{method}:{target}     # 验证码（默认5分钟）
rate_limit:code:{target}                     # 发码限流（60秒1次）
oauth:state:{state}                          # OAuth state（10分钟一次性）
```

## 数据库设计

### ID 策略

- `user.id`：应用侧 uid-generator 生成雪花 ID；`uid_generator_worker_id` 表用于多实例分配唯一 workerId，避免发号冲突
- `tenant / role / permission / user_role / role_permission / user_oauth / audit_log / login_method_config`：自增主键（tenant/role 初始数据使用 0/1/2 固定 id）

### 表结构概览

#### tenant（租户表）

| 字段 | 类型 | 描述 |
|------|------|------|
| id | BIGINT | 主键 ID |
| tenant_code | VARCHAR(50) | 租户编码（唯一） |
| tenant_uid | VARCHAR(16) | 对外租户标识（随机串，防枚举，唯一） |
| tenant_name | VARCHAR(100) | 租户名称 |
| status | TINYINT | 状态：0-禁用，1-正常 |
| expired_at | DATETIME | 过期时间（NULL=永不过期） |
| max_users | INT | 最大用户数限制（默认 100） |
| is_platform | TINYINT | 是否为平台租户 |

#### user（用户表）

| 字段 | 类型 | 描述 |
|------|------|------|
| id | BIGINT | 主键（雪花 ID） |
| tenant_id | BIGINT | 租户 ID |
| username | VARCHAR(50) | 用户名（租户内唯一） |
| password | VARCHAR(255) | 密码（BCrypt） |
| email / phone | VARCHAR | 邮箱（租户内唯一）/ 手机号 |
| nickname / avatar | VARCHAR | 昵称 / 头像 URL |
| status | TINYINT | 0-禁用，1-正常 |
| email_verified / phone_verified | BOOLEAN | 验证标记 |
| real_name / gender / birthday | - | 真实姓名 / 性别（0未知 1男 2女）/ 生日 |
| last_login_at | DATETIME | 最后登录时间 |

#### role / permission（角色表 / 权限表）

| 字段 | 类型 | 描述 |
|------|------|------|
| id | BIGINT | 主键 ID |
| tenant_id | BIGINT | 租户 ID（隔离） |
| code | VARCHAR | 编码（租户内唯一）：角色如 `ROLE_ADMIN`，权限如 `user:read` |
| name / description | VARCHAR | 名称 / 描述 |
| resource / action | VARCHAR | 仅权限表：资源标识 / 操作类型 |

#### user_role / role_permission（关联表）

| 表 | 唯一约束 |
|----|----------|
| user_role | (user_id, role_id, tenant_id) |
| role_permission | (role_id, permission_id, tenant_id) |

#### login_method_config（登录方式配置表）

平台/租户二级开关，凭证 AES 加密存储：

| 字段 | 类型 | 描述 |
|------|------|------|
| tenant_id | BIGINT | 0=平台级默认配置，其他=租户级 |
| method | VARCHAR(32) | 登录方式（同类 email 租户级互斥，平台级可并存） |
| enabled | TINYINT | 是否启用 |
| use_platform_config | TINYINT | 仅租户行有效：1=用平台默认凭证，0=用自身 config_json |
| config_json | TEXT | 凭证配置（整段 AES 加密密文，结构因 method 而异） |

唯一约束：(tenant_id, method)。初始数据：`(0, 'password', 1)` 恒开。

#### user_oauth（OAuth 绑定表）

| 字段 | 类型 | 描述 |
|------|------|------|
| tenant_id / user_id | BIGINT | 租户 / 本地用户 |
| provider | VARCHAR(32) | 提供方：gitee / github |
| provider_uid | VARCHAR(64) | 提供方用户唯一 ID |

唯一约束：(tenant_id, provider, provider_uid)、(tenant_id, user_id, provider)。

#### audit_log（审计日志表）

| 字段 | 类型 | 描述 |
|------|------|------|
| tenant_id / user_id | BIGINT | 归属 |
| username | VARCHAR(100) | 操作用户名 |
| action | VARCHAR(50) | LOGIN / REGISTER / LOGOUT / CHANGE_PASSWORD / BIND_EMAIL / UNBIND_EMAIL / BIND_PHONE / UNBIND_PHONE |
| resource | VARCHAR(100) | 资源类型（User / Token） |
| detail | VARCHAR(500) | 操作详情 |
| ip_address | VARCHAR(45) | 客户端 IP |

## 安全机制

### JWT 双令牌

| 令牌类型 | 有效期 | 用途 |
|----------|--------|------|
| Access Token | 1 天 | API 认证（`Authorization: Bearer <token>`） |
| Refresh Token | 7 天 | 令牌续期（Redis 单点存储，刷新即轮换） |

JWT Claims：

```json
{
  "sub": "用户ID",
  "jti": "令牌唯一标识（用于黑名单）",
  "username": "用户名",
  "tenant_id": "租户ID",
  "roles": ["角色列表"],
  "permissions": ["权限列表"]
}
```

### 令牌黑名单

登出时将 access token 的 jti 写入 Redis（`blacklist:{tenant_id}:{jti}`，TTL 为令牌剩余有效期），`JwtAuthenticationFilter` 每次请求校验。

### 限流

| 场景 | 策略 | Key |
|------|------|-----|
| 密码登录 | Redis ZSET 滑动窗口，15 分钟最多 5 次失败，成功后重置 | `rate-limit:login:{tenantId}:{username}` |
| 发送验证码 | 同一目标 60 秒 1 次（SETNX 占位） | `rate_limit:code:{target}` |

### OAuth state 防 CSRF

授权前生成随机 state 存 Redis（10 分钟），回调时取出即删（一次性），并校验 provider 匹配。

### 凭证加密

`login_method_config.config_json` 整段以 `LOGIN_CONFIG_AES_KEY` AES 加密后入库；读取时运行时解密，接口返回仅含 `hasConfig` 标志，永不回显明文。

### 审计日志

- 存储：MySQL `audit_log` 表，`@Auditable` 注解 + AOP 异步写入，不影响主流程
- 身份提取：优先 SecurityContext，RPC 场景通过参数名匹配和 JWT 解析

### RPC 鉴权

Dubbo SPI Filter（`RpcAuthFilter`）校验 attachment 中的 `rpc-service-token`，见[上文](#rpc-服务鉴权)。

## 配置参考

### 应用配置（application.yaml 关键项）

```yaml
server:
  port: 8123                       # REST API 端口

spring:
  datasource:                      # MySQL
    url: jdbc:mysql://localhost:3306/auth_service?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}
  data:
    redis:                         # Redis
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}

jwt:
  secret: ${JWT_SECRET:}                 # 未设置时启动失败
  access-token-expiration: 86400000      # 1 天
  refresh-token-expiration: 604800000    # 7 天

login-config:
  aes-key: ${LOGIN_CONFIG_AES_KEY:}      # 登录方式凭证加密密钥

auth:
  rate-limit:
    max-attempts: 5                # 登录失败次数上限
    window-seconds: 900            # 滑动窗口（15 分钟）

dubbo:
  application:
    name: auth-service
    version: 1.0.0
  protocol:
    name: tri                      # Triple 协议（HTTP/2）
    port: 20880
    serialization: protobuf
  registry:
    address: ${NACOS_ADDRESS:nacos://localhost:8848}
    username: ${NACOS_USERNAME:}          # Nacos 开启鉴权时配置
    password: ${NACOS_PASSWORD:}
  rpc:
    service-token: ${RPC_SERVICE_TOKEN:}  # RPC 服务间鉴权令牌
  scan:
    base-packages: cn.wanyj.auth.rpc

aliyun:
  oss:                             # 头像上传；不配置 endpoint 则上传接口不可用
    endpoint: ${OSS_ENDPOINT:}
    bucket: ${OSS_BUCKET:}
    access-key-id: ${OSS_ACCESS_KEY_ID:}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET:}
    object-prefix: ${OSS_OBJECT_PREFIX:avatar}

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: cn.wanyj.auth.entity
  configuration:
    map-underscore-to-camel-case: true
```

### 服务端口

| 服务 | 端口 | 协议 |
|------|------|------|
| REST API / Web 管理界面 | 8123 | HTTP |
| Dubbo RPC | 20880 | Triple (HTTP/2) |
| Nacos Registry | 8848 | - |

## 错误码

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 403 | 禁止访问 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 1001 | 用户名或密码错误 |
| 1002 | 用户不存在 |
| 1003 | 用户已被禁用 |
| 1004 | 用户名已存在 |
| 1005 | 邮箱已被使用 |
| 1006 | 令牌无效或已过期 |
| 1007 | 未提供认证令牌 |
| 1009 | 令牌已过期 |
| 1010 | 令牌已被注销 |
| 1011 | 刷新令牌无效 |
| 1012 | 旧密码错误 |
| 1013 | 邮箱格式不正确 |
| 1014 | 手机号格式不正确 |
| 1015 | 租户无效或不存在 |
| 1016 | 租户用户数量已达上限 |
| 1017 | 租户编码已存在 |
| 1018 | 租户不存在 |
| 1019 | 登录尝试过于频繁，请稍后再试 |
| 1020 | 手机号已被使用 |
| 2001 | 无权限访问 |
| 2002 | 角色不存在 |
| 2003 | 角色编码已存在 |
| 2004 | 权限不存在 |
| 2005 | 权限编码已存在 |
| 3001 | 该登录方式未启用 |
| 3002 | 登录方式配置不存在 |
| 3003 | 登录方式凭证配置无效 |
| 3004 | 不支持的登录方式 |
| 3005 | 同类登录方式已启用 |

## Docker 部署

前置准备：MySQL、Redis、Nacos 已就绪，并已执行 `docs/init-schema.sql`。

项目自带多阶段构建 [Dockerfile](Dockerfile)（Maven 构建 → JRE 运行，非 root 用户，含健康检查）。

**配置方式**：`docker-compose.yml` 中的 `environment` 列出了全部配置项，仓库中均为 `your_*` 占位样例（Nacos 账密默认 `nacos/nacos`，与 Nacos 默认安装一致）。部署时直接把占位值替换为真实配置即可，容器内环境变量会覆盖 `application.yaml` 中的占位符默认值。

`docker-compose.yml` 示例：

```yaml
version: '3.8'

services:
  auth-service:
    image: registry.cn-wulanchabu.aliyuncs.com/wanyj/auth-service:5.0
    container_name: auth-service-app
    restart: unless-stopped
    environment:
      TZ: Asia/Shanghai
      # Dubbo 注册到 Nacos 的地址：不设置时默认注册容器内网 IP，
      # 外部无法直连，这里强制注册宿主机公网 IP
      DUBBO_IP_TO_REGISTRY: 127.0.0.1
      DUBBO_PORT_TO_REGISTRY: 20880
      # Database
      SPRING_DATASOURCE_URL: jdbc:mysql://127.0.0.1:3306/auth_service?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: your_db_password
      # Redis
      SPRING_DATA_REDIS_HOST: 127.0.0.1
      SPRING_DATA_REDIS_PORT: 6379
      SPRING_DATA_REDIS_PASSWORD: your_redis_password
      # JWT Secret (generate with: openssl rand -base64 32)
      JWT_SECRET: your_jwt_secret
      # 登录方式凭证加密密钥（openssl rand -base64 32）
      LOGIN_CONFIG_AES_KEY: your_login_config_aes_key
      # RPC Service Token (for inter-service authentication)
      RPC_SERVICE_TOKEN: your_rpc_service_token
      # 阿里云 OSS（可选，头像上传；不配置则上传接口返回「OSS 未配置」）
      OSS_ENDPOINT: your_oss_endpoint
      OSS_BUCKET: your_oss_bucket
      OSS_ACCESS_KEY_ID: your_oss_access_key_id
      OSS_ACCESS_KEY_SECRET: your_oss_access_key_secret
      OSS_OBJECT_PREFIX: avatar
      # Nacos（同时作用于 dubbo registry 和 metadata-report；默认账密 nacos/nacos，开启鉴权后请修改）
      NACOS_ADDRESS: nacos://127.0.0.1:8848
      NACOS_USERNAME: nacos
      NACOS_PASSWORD: nacos
      # JVM
      JAVA_OPTS: >-
        -XX:+UseContainerSupport
        -XX:MaxRAMPercentage=75.0
    ports:
      - "8123:8123"    # REST API
      - "20880:20880"  # Dubbo RPC
    volumes:
      - app-logs:/app/logs
    networks:
      - auth-network

volumes:
  app-logs:
    driver: local

networks:
  auth-network:
    driver: bridge
```

启动：

```bash
docker compose up -d
```

健康检查：`GET http://localhost:8123/actuator/health`（容器内置 HEALTHCHECK）。

## 项目结构

```
auth-service/
├── auth-service-api/              # RPC 接口定义模块（供消费方引入）
│   └── src/main/
│       ├── java/                  # protobuf 生成代码 + 手写接口
│       └── proto/auth/
│           └── auth_service.proto # Protobuf IDL（全部 RPC 服务与消息定义）
│
├── auth-service-core/             # 核心服务实现模块
│   └── src/main/
│       ├── java/cn/wanyj/auth/
│       │   ├── annotation/        # @Auditable 审计注解
│       │   ├── aspect/            # 审计日志 AOP 切面
│       │   ├── config/            # Security / Dubbo / Redis / OSS / Jackson 配置
│       │   ├── controller/        # REST API 控制器
│       │   ├── dto/               # REST 请求/响应 DTO
│       │   ├── entity/            # 实体（含 LoginMethod 登录方式注册表枚举）
│       │   ├── exception/         # 统一异常 / 错误码 / 全局处理器
│       │   ├── filter/            # RpcAuthFilter（Dubbo 服务间鉴权）
│       │   ├── mapper/            # MyBatis Mapper
│       │   ├── rpc/               # 9 个 Dubbo RPC 服务实现 + protobuf 转换器
│       │   ├── security/          # JWT / 过滤器 / 平台管理员注解 / AES 工具
│       │   ├── service/           # 业务逻辑层
│       │   │   ├── impl/          # 实现
│       │   │   ├── oauth/         # OAuth 编排与 Gitee/GitHub Provider
│       │   │   └── sender/        # 验证码发送（阿里云邮件/SMTP/阿里云短信）
│       │   └── util/              # 字段校验工具
│       └── resources/
│           ├── mapper/            # MyBatis XML 映射
│           └── application.yaml   # 配置文件
│
├── docs/                          # 设计文档
│   ├── ai-rpc-integration.md      # RPC 集成指南（AI 专用，自包含，外部系统接入只读这份）
│   ├── init-schema.sql            # 数据库初始化脚本（含初始数据）
│   ├── mvp-design.md              # MVP 设计文档
│   ├── microservice-rpc-design.md # 微服务 RPC 设计
│   ├── multi-tenant-design.md     # 多租户设计
│   └── test-verification-guide.md # 测试验证指南
│
├── Dockerfile                     # 多阶段构建镜像
├── docker-compose.yml             # 编排示例
└── pom.xml                        # 父 POM
```

## 构建与测试

```bash
# 完整构建（跳过测试）
mvn clean package -DskipTests

# 运行单元测试
mvn test -pl auth-service-core

# 启动（本地依赖就绪后）
mvn spring-boot:run -pl auth-service-core
```

## 常见问题

### 1. 默认管理员账户是什么？

见[初始数据](#初始数据)。两个 `admin / 123456`：平台租户（tenantUid `pk7q2m8e`）管理租户与平台登录方式；演示租户（tenantUid `dm3a9x1f`）管理用户/角色/权限。新建租户也会自动创建 `admin / 123456`。**生产环境务必修改**。

### 2. 登录时 tenantUid 从哪里来？

登录页下拉列表来自公开接口 `GET /api/tenant/available`；也可由平台管理员在租户管理页查看每个租户的 `tenantUid`。tenantUid 是 8 位随机串，用于替代自增数字 ID 对外暴露，防止枚举。

### 3. 邮箱/短信验证码登录收不到验证码？

依次检查：

1. 该租户该方式是否已启用（`GET /api/auth/login-methods?tenantUid=...`）
2. 平台级是否开启且配置了凭证（或租户 `usePlatformConfig=0` 且自有凭证完整）
3. 是否触发发送限流（同一目标 60 秒 1 次）
4. 服务端日志 `logs/auth-service.log` 中 Sender 报错（凭证错误、模板缺 `{code}` 等）

### 4. 邮箱类登录方式为什么只能启用一个？

`email:aliyun` 与 `email:smtp` 同属 email 类别，同一租户同时只能启用一个（错误码 3005），保证登录/绑定/换绑链路的邮件通道唯一。切换时先禁用旧方式再启用新方式。

### 5. OAuth 回调 404 / state 无效？

- 回调地址必须与提供方应用中配置的 `redirectUri` 完全一致（默认 `https://你的域名/api/auth/oauth/{provider}/callback`）
- state 有效期 10 分钟且一次性，重复回调或超时会报「state 无效或已过期」
- 从授权到回调需在 10 分钟内完成

### 6. 平台租户为什么不能用邮箱/OAuth 登录？

平台租户（tenant_id=0）是系统管理租户，出于安全仅允许账号密码登录，代码层面强制禁用其他一切方式。

### 7. 头像上传报「OSS 未配置」？

`OSS_ENDPOINT` 等环境变量未配置。OSS 为可选依赖，未配置时上传接口（HTTP 与 RPC）均返回不可用。

### 8. 如何新增一种登录方式？

1. 在 `LoginMethod` 枚举追加一项（`method = category:vendor`）
2. 按 category 实现对应扩展点：email 类实现 `MailSender`、sms 类实现 `SmsSender`、oauth 类实现 `OAuthProvider`
3. 无需改配置表结构——`login_method_config` 按 method 字符串存储，平台/租户两级开关与凭证逻辑自动生效

## 许可证

[MIT](LICENSE)

---

**注意**：本项目可用于学习和生产环境。生产部署前请修改所有默认密码与密钥（管理员密码、JWT_SECRET、LOGIN_CONFIG_AES_KEY、RPC_SERVICE_TOKEN 等）。
