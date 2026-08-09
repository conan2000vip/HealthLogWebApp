-- ============================================
-- create_table.sql
-- HealthLogWebApp データベース構築用SQLファイル
-- ============================================

CREATE DATABASE IF NOT EXISTS healthlog;
USE healthlog;

-- --------------------------------------------
-- Table: users
-- --------------------------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `email_verified_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------------------------------
-- Table: auth_tokens
-- --------------------------------------------
DROP TABLE IF EXISTS `auth_tokens`;
CREATE TABLE `auth_tokens` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `token_type` varchar(30) NOT NULL COMMENT 'email_verification / password_reset',
  `token` varchar(255) NOT NULL,
  `expires_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `used_flg` tinyint(1) NOT NULL DEFAULT '0' COMMENT '0=未使用, 1=使用済み',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_auth_tokens_token` (`token`),
  KEY `idx_auth_tokens_user_type` (`user_id`,`token_type`),
  KEY `idx_auth_tokens_expires` (`expires_at`),
  CONSTRAINT `fk_auth_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------------------------------
-- Table: profiles
-- --------------------------------------------
DROP TABLE IF EXISTS `profiles`;
CREATE TABLE `profiles` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `name` varchar(100) NOT NULL,
  `birth_date` date DEFAULT NULL,
  `relationship` varchar(20) NOT NULL,
  `gender` varchar(10) DEFAULT NULL,
  `height` decimal(5,1) DEFAULT NULL,
  `target_weight` decimal(5,1) DEFAULT NULL,
  `water_goal_ml` int NOT NULL DEFAULT '1500',
  `step_goal` int NOT NULL DEFAULT '8000',
  `daily_sleep_goal` int NOT NULL DEFAULT '480',
  `profile_color` varchar(20) DEFAULT NULL,
  `is_primary` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_profiles_user_id` (`user_id`),
  KEY `idx_profiles_user_primary` (`user_id`,`is_primary`),
  CONSTRAINT `fk_profiles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------------------------------
-- Table: memo_logs
-- --------------------------------------------
DROP TABLE IF EXISTS `memo_logs`;
CREATE TABLE `memo_logs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `profile_id` bigint unsigned NOT NULL,
  `recorded_date` date NOT NULL,
  `title` varchar(100) DEFAULT NULL,
  `content` text NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_memo_profile_date` (`profile_id`, `recorded_date`),
  CONSTRAINT `fk_memo_logs_profile` FOREIGN KEY (`profile_id`) REFERENCES `profiles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------------------------------
-- Table: sleep_logs
-- --------------------------------------------
DROP TABLE IF EXISTS `sleep_logs`;
CREATE TABLE `sleep_logs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `profile_id` bigint unsigned NOT NULL,
  `recorded_date` date NOT NULL,
  `start_time` time DEFAULT NULL,
  `end_time` time DEFAULT NULL,
  `sleep_type` varchar(20) NOT NULL DEFAULT 'NIGHT',
  `sleep_minutes` int DEFAULT NULL,
  `memo` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sleep_profile_date` (`profile_id`,`recorded_date`),
  CONSTRAINT `fk_sleep_logs_profile` FOREIGN KEY (`profile_id`) REFERENCES `profiles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------------------------------
-- Table: step_logs
-- --------------------------------------------
DROP TABLE IF EXISTS `step_logs`;
CREATE TABLE `step_logs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `profile_id` bigint unsigned NOT NULL,
  `recorded_date` date NOT NULL,
  `steps` int NOT NULL,
  `memo` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_step_profile_date` (`profile_id`,`recorded_date`),
  KEY `idx_step_profile_date` (`profile_id`,`recorded_date`),
  CONSTRAINT `fk_step_logs_profile` FOREIGN KEY (`profile_id`) REFERENCES `profiles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------------------------------
-- Table: water_logs
-- --------------------------------------------
DROP TABLE IF EXISTS `water_logs`;
CREATE TABLE `water_logs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `profile_id` bigint unsigned NOT NULL,
  `recorded_date` date NOT NULL,
  `recorded_time` time DEFAULT NULL,
  `drink_type` varchar(30) DEFAULT NULL,
  `amount_ml` int NOT NULL,
  `memo` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_water_profile_date` (`profile_id`,`recorded_date`),
  CONSTRAINT `fk_water_logs_profile` FOREIGN KEY (`profile_id`) REFERENCES `profiles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------------------------------
-- Table: weight_logs
-- --------------------------------------------
DROP TABLE IF EXISTS `weight_logs`;
CREATE TABLE `weight_logs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `profile_id` bigint unsigned NOT NULL,
  `recorded_date` date NOT NULL,
  `weight` decimal(5,1) NOT NULL,
  `measured_at` datetime NOT NULL,
  `height` decimal(5,1) DEFAULT NULL,
  `memo` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_weight_profile_date` (`profile_id`,`recorded_date`),
  CONSTRAINT `fk_weight_logs_profile` FOREIGN KEY (`profile_id`) REFERENCES `profiles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;