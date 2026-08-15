-- ============================================
-- Auth Service Database Schema (Multi-Tenant)
-- Auth Service 数据库表结构 (多租户版本)
-- ============================================
-- Version: 2.1
-- Date: 2026-02-22
-- Description: 完整的数据库初始化脚本，包含多租户支持
-- ============================================

-- Create database if not exists
CREATE DATABASE IF NOT EXISTS `auth_service`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `auth_service`;

-- ============================================
-- Table: tenant (租户表)
-- ============================================
CREATE TABLE IF NOT EXISTS `tenant` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户ID',
  `tenant_code` VARCHAR(50) NOT NULL COMMENT '租户编码（唯一标识）',
  `tenant_uid` VARCHAR(16) NOT NULL COMMENT '对外租户标识（随机串，防枚举）',
  `tenant_name` VARCHAR(100) NOT NULL COMMENT '租户名称',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-正常',
  `expired_at` DATETIME DEFAULT NULL COMMENT '过期时间（NULL表示永不过期）',
  `max_users` INT DEFAULT 100 COMMENT '最大用户数限制',
  `is_platform` TINYINT NOT NULL DEFAULT 0 COMMENT '是否为平台租户：0-否，1-是',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_code`),
  UNIQUE KEY `uk_tenant_uid` (`tenant_uid`),
  KEY `idx_status` (`status`),
  KEY `idx_expired_at` (`expired_at`),
  KEY `idx_is_platform` (`is_platform`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';

-- ============================================
-- Table: user (用户表)
-- ============================================
CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL COMMENT '用户ID（雪花ID）',
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '密码（bcrypt加密）',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-正常',
  `email_verified` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '邮箱是否验证',
  `phone_verified` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '手机号是否验证',
  `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
  `gender` TINYINT DEFAULT 0 COMMENT '性别：0-未知，1-男，2-女',
  `birthday` DATE DEFAULT NULL COMMENT '生日',
  `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username_tenant` (`username`, `tenant_id`),
  UNIQUE KEY `uk_email_tenant` (`email`, `tenant_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_phone` (`phone`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================
-- Table: role (角色表)
-- ============================================
CREATE TABLE IF NOT EXISTS `role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `code` VARCHAR(50) NOT NULL COMMENT '角色编码',
  `name` VARCHAR(50) NOT NULL COMMENT '角色名称',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '角色描述',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-正常',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_tenant` (`code`, `tenant_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- ============================================
-- Table: permission (权限表)
-- ============================================
CREATE TABLE IF NOT EXISTS `permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `code` VARCHAR(100) NOT NULL COMMENT '权限编码',
  `name` VARCHAR(50) NOT NULL COMMENT '权限名称',
  `resource` VARCHAR(100) NOT NULL COMMENT '资源标识',
  `action` VARCHAR(50) NOT NULL COMMENT '操作类型：read,write,delete等',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '权限描述',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_tenant` (`code`, `tenant_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_resource` (`resource`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- ============================================
-- Table: user_role (用户角色关联表)
-- ============================================
CREATE TABLE IF NOT EXISTS `user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role_tenant` (`user_id`, `role_id`, `tenant_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- ============================================
-- Table: role_permission (角色权限关联表)
-- ============================================
CREATE TABLE IF NOT EXISTS `role_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `permission_id` BIGINT NOT NULL COMMENT '权限ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission_tenant` (`role_id`, `permission_id`, `tenant_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- ============================================
-- Table: uid_generator_worker_id (UID Generator worker allocation)
-- ============================================
CREATE TABLE IF NOT EXISTS `uid_generator_worker_id` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='uid-generator worker id分配表';

-- ============================================
-- Table: audit_log (审计日志表)
-- ============================================
CREATE TABLE IF NOT EXISTS `audit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
  `user_id` BIGINT NULL COMMENT '操作用户ID',
  `username` VARCHAR(100) NULL COMMENT '操作用户名',
  `action` VARCHAR(50) NOT NULL COMMENT '操作动作（LOGIN/REGISTER/LOGOUT/CHANGE_PASSWORD）',
  `resource` VARCHAR(100) NULL COMMENT '资源类型（User/Token）',
  `detail` VARCHAR(500) NULL COMMENT '操作详情',
  `ip_address` VARCHAR(45) NULL COMMENT '客户端IP',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_tenant_action` (`tenant_id`, `action`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';

-- ============================================
-- Table: login_method_config (登录方式配置表 - 平台级+租户级二级开关)
-- tenant_id=0 为平台级默认开关与凭证；其他为租户级开关（可在平台允许范围内覆盖凭证）
-- ============================================
CREATE TABLE IF NOT EXISTS `login_method_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级默认配置）',
  `method` VARCHAR(32) NOT NULL COMMENT '登录方式：password/email:aliyun/email:smtp/sms:aliyun/oauth:gitee/oauth:github（同类 email 租户级互斥，平台级可并存）',
  `enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用：0-否，1-是',
  `use_platform_config` TINYINT NOT NULL DEFAULT 1 COMMENT '仅租户行有效：1=用平台默认凭证，0=用自身config_json',
  `config_json` TEXT COMMENT '该方式的凭证配置（整段AES加密密文，结构因method而异）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_method` (`tenant_id`, `method`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录方式配置表';

-- 初始数据：password 平台级恒开（平台不可关闭，保证不锁死）
INSERT INTO `login_method_config` (`tenant_id`, `method`, `enabled`, `use_platform_config`, `config_json`) VALUES
(0, 'password', 1, 1, NULL) ON DUPLICATE KEY UPDATE `enabled` = 1;

-- ============================================
-- Table: user_oauth (OAuth 账号绑定表 - 多租户隔离)
-- 按 (tenant_id, provider, provider_uid) 匹配本地用户；同一用户同一 provider 仅绑定一次
-- ============================================
CREATE TABLE IF NOT EXISTS `user_oauth` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `user_id` BIGINT NOT NULL COMMENT '本地用户ID',
  `provider` VARCHAR(32) NOT NULL COMMENT 'OAuth提供方：gitee/microsoft/github',
  `provider_uid` VARCHAR(64) NOT NULL COMMENT '提供方用户唯一ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_provider_uid` (`tenant_id`, `provider`, `provider_uid`),
  UNIQUE KEY `uk_tenant_user_provider` (`tenant_id`, `user_id`, `provider`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth账号绑定表';

-- ============================================
-- Initial Data (初始数据)
-- ============================================
-- 注意：如需重新初始化，请先手动清空相关表数据
-- TRUNCATE TABLE user_role;
-- TRUNCATE TABLE role_permission;
-- TRUNCATE TABLE user;
-- TRUNCATE TABLE role;
-- TRUNCATE TABLE permission;
-- TRUNCATE TABLE tenant;

-- Insert tenants (需要 NO_AUTO_VALUE_ON_ZERO 允许 id=0)
SET sql_mode='NO_AUTO_VALUE_ON_ZERO';
INSERT INTO `tenant` (`id`, `tenant_code`, `tenant_uid`, `tenant_name`, `status`, `max_users`, `is_platform`) VALUES
(0, 'platform', 'pk7q2m8e', '平台租户', 1, 1000, 1),
(1, 'demo', 'dm3a9x1f', '演示租户', 1, 100, 0)
ON DUPLICATE KEY UPDATE `tenant_name` = VALUES(`tenant_name`), `is_platform` = VALUES(`is_platform`), `tenant_uid` = VALUES(`tenant_uid`);
SET sql_mode=(SELECT REPLACE(@@sql_mode,'NO_AUTO_VALUE_ON_ZERO',''));

-- Insert roles
-- 注意：使用 SET sql_mode='NO_AUTO_VALUE_ON_ZERO' 允许插入 id=0
SET sql_mode='NO_AUTO_VALUE_ON_ZERO';
INSERT INTO `role` (`id`, `tenant_id`, `code`, `name`, `description`) VALUES
(0, 0, 'ROLE_PLATFORM_ADMIN', '平台管理员', '拥有租户管理权限'),
(1, 1, 'ROLE_ADMIN', '系统管理员', '拥有所有权限'),
(2, 1, 'ROLE_USER', '普通用户', '基础用户权限')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
SET sql_mode=(SELECT REPLACE(@@sql_mode,'NO_AUTO_VALUE_ON_ZERO',''));

-- Insert platform permissions
INSERT INTO `permission` (`tenant_id`, `code`, `name`, `resource`, `action`, `description`) VALUES
(0, 'platform:tenant:create', '创建租户', 'tenant', 'create', '创建租户'),
(0, 'platform:tenant:update', '更新租户', 'tenant', 'update', '更新租户'),
(0, 'platform:tenant:delete', '删除租户', 'tenant', 'delete', '删除租户'),
(0, 'platform:tenant:read', '查看租户', 'tenant', 'read', '查看租户')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- Insert default permissions for demo tenant
INSERT INTO `permission` (`tenant_id`, `code`, `name`, `resource`, `action`, `description`) VALUES
(1, 'user:read', '查看用户', 'user', 'read', '查看用户'),
(1, 'user:create', '创建用户', 'user', 'create', '创建用户'),
(1, 'user:write', '编辑用户', 'user', 'write', '编辑用户'),
(1, 'user:delete', '删除用户', 'user', 'delete', '删除用户'),
(1, 'role:read', '查看角色', 'role', 'read', '查看角色'),
(1, 'role:create', '创建角色', 'role', 'create', '创建角色'),
(1, 'role:write', '编辑角色', 'role', 'write', '编辑角色'),
(1, 'role:delete', '删除角色', 'role', 'delete', '删除角色'),
(1, 'permission:read', '查看权限', 'permission', 'read', '查看权限'),
(1, 'permission:create', '创建权限', 'permission', 'create', '创建权限'),
(1, 'permission:write', '编辑权限', 'permission', 'write', '编辑权限'),
(1, 'permission:delete', '删除权限', 'permission', 'delete', '删除权限')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- Assign all platform permissions to platform admin role (ROLE_PLATFORM_ADMIN)
INSERT IGNORE INTO `role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT 0, 0, `id` FROM `permission` WHERE `tenant_id` = 0;

-- Assign all permissions to demo tenant admin role (ROLE_ADMIN)
INSERT IGNORE INTO `role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT 1, 1, `id` FROM `permission` WHERE `tenant_id` = 1;

-- Assign user:read permission to demo tenant user role (ROLE_USER)
INSERT IGNORE INTO `role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT 1, 2, `id` FROM `permission` WHERE `tenant_id` = 1 AND `code` = 'user:read';

-- Insert default platform admin user (username: admin, password: 123456)
-- Password is bcrypt hash of '123456'
SET sql_mode='NO_AUTO_VALUE_ON_ZERO';
INSERT INTO `user` (`id`, `tenant_id`, `username`, `password`, `nickname`, `status`) VALUES
(0, 0, 'admin', '$2a$10$z/I75HJV6HhtTpT1fzgcZ.WMzOPvej2.0trqSqgleMPdHUvJUxGDC', '平台管理员', 1)
ON DUPLICATE KEY UPDATE `password` = VALUES(`password`);
SET sql_mode=(SELECT REPLACE(@@sql_mode,'NO_AUTO_VALUE_ON_ZERO',''));

-- Assign platform admin role to default platform admin user
INSERT IGNORE INTO `user_role` (`tenant_id`, `user_id`, `role_id`)
VALUES (0, 0, 0);

-- Insert default demo tenant admin user (username: admin, password: 123456)
-- Password is bcrypt hash of '123456'
SET sql_mode='NO_AUTO_VALUE_ON_ZERO';
INSERT INTO `user` (`id`, `tenant_id`, `username`, `password`, `nickname`, `status`) VALUES
(1, 1, 'admin', '$2a$10$z/I75HJV6HhtTpT1fzgcZ.WMzOPvej2.0trqSqgleMPdHUvJUxGDC', '演示管理员', 1)
ON DUPLICATE KEY UPDATE `password` = VALUES(`password`);
SET sql_mode=(SELECT REPLACE(@@sql_mode,'NO_AUTO_VALUE_ON_ZERO',''));

-- Assign admin role to demo tenant admin user
INSERT IGNORE INTO `user_role` (`tenant_id`, `user_id`, `role_id`)
VALUES (1, 1, 1);

-- ============================================
-- Schema Initialization Complete
-- ============================================

SELECT '========================================' AS '';
SELECT 'Database schema initialized successfully!' AS status;
SELECT 'Version: 2.1 (Multi-Tenant)' AS version;
SELECT '========================================' AS '';

-- Display summary
SELECT
    'Tenants' AS metric,
    COUNT(*) AS count
FROM `tenant`
UNION ALL
SELECT
    'Roles',
    COUNT(*)
FROM `role`
UNION ALL
SELECT
    'Permissions',
    COUNT(*)
FROM `permission`;