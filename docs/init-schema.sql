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
  `tenant_name` VARCHAR(100) NOT NULL COMMENT '租户名称',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-正常',
  `expired_at` DATETIME DEFAULT NULL COMMENT '过期时间（NULL表示永不过期）',
  `max_users` INT DEFAULT 100 COMMENT '最大用户数限制',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_code`),
  KEY `idx_status` (`status`),
  KEY `idx_expired_at` (`expired_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';

-- ============================================
-- Table: user (用户表)
-- ============================================
CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '密码（bcrypt加密）',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-正常',
  `email_verified` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '邮箱是否验证',
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
-- Initial Data (初始数据)
-- ============================================

-- Insert example tenant
INSERT INTO `tenant` (`id`, `tenant_code`, `tenant_name`, `status`, `max_users`) VALUES
(1, 'demo', '演示租户', 1, 100)
ON DUPLICATE KEY UPDATE `tenant_name` = VALUES(`tenant_name`);

-- Insert default roles for demo tenant
INSERT INTO `role` (`id`, `tenant_id`, `code`, `name`, `description`) VALUES
(1, 1, 'ROLE_ADMIN', '系统管理员', '拥有所有权限'),
(2, 1, 'ROLE_USER', '普通用户', '基础用户权限')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- Insert default permissions for demo tenant
INSERT INTO `permission` (`tenant_id`, `code`, `name`, `resource`, `action`, `description`) VALUES
(1, 'user:read', '查看用户', 'user', 'read', '查看用户信息'),
(1, 'user:write', '编辑用户', 'user', 'write', '编辑用户信息'),
(1, 'user:delete', '删除用户', 'user', 'delete', '删除用户'),
(1, 'role:read', '查看角色', 'role', 'read', '查看角色信息'),
(1, 'role:write', '编辑角色', 'role', 'write', '编辑角色信息'),
(1, 'permission:read', '查看权限', 'permission', 'read', '查看权限信息'),
(1, 'tenant:read', '查看租户', 'tenant', 'read', '查看租户信息'),
(1, 'tenant:write', '编辑租户', 'tenant', 'write', '编辑租户信息')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- Assign all permissions to admin role
INSERT IGNORE INTO `role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT 1, 1, `id` FROM `permission` WHERE `tenant_id` = 1;

-- Assign user:read permission to user role
INSERT IGNORE INTO `role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT 1, 2, `id` FROM `permission` WHERE `tenant_id` = 1 AND `code` = 'user:read';

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
