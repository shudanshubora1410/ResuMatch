-- =====================================================================
-- ResuMatch - MySQL schema (single source of truth)
--
-- Run this ONCE against the target database before starting the app:
--
--   mysql -h HOST -P PORT -u USER -p < schema-mysql.sql
--
-- ---------------------------------------------------------------------
-- Why this file exists
-- ---------------------------------------------------------------------
-- 1) THE DATABASE. Hibernate's `ddl-auto=update` creates TABLES but never
--    creates the DATABASE. If `resumatch` does not exist, boot fails with
--    "Unknown database 'resumatch'".
--
-- 2) DDL AT BOOT IS UNRELIABLE ON MANAGED MySQL. With `ddl-auto=update`,
--    Hibernate issues ~31 CREATE/ALTER statements plus ~20 metadata
--    SELECTs on every boot. On a remote MySQL (Aiven, ~200ms per
--    round-trip) that is 10+ seconds of DDL traffic on a single
--    connection - and Aiven's free tier drops that connection partway
--    through ("Communications link failure / Socket closed"). The app
--    then dies with a half-created schema.
--
--    So: run this script once, and the app runs with `ddl-auto=validate`,
--    which only CHECKS the schema. Boot becomes ~2 seconds and the
--    connection is never stressed.
--
-- 3) THE SEQUENCE TABLE. `skill_aliases` uses a pooled SEQUENCE generator
--    so Hibernate can batch the 110 alias inserts. With IDENTITY, every
--    INSERT is its own round-trip (~150-200ms each = 25 seconds), which
--    also exceeded the managed host's connection timeout.
--    Sequence tables have no primary key by default, and managed MySQL
--    hosts set `sql_require_primary_key=ON`, which blocks Hibernate from
--    creating it. It is created here with a primary key instead.
--
-- Everything below was generated from the live schema Hibernate produced,
-- so it matches the JPA entities exactly.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS resumatch
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE resumatch;

-- Sequence for skill_aliases.id (pooled, 50 ids per DB round-trip).
CREATE TABLE IF NOT EXISTS `skill_alias_seq` (
  `next_val` bigint NOT NULL,
  PRIMARY KEY (`next_val`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `skill_alias_seq` (`next_val`) VALUES (0)
  ON DUPLICATE KEY UPDATE `next_val` = `next_val`;

CREATE TABLE IF NOT EXISTS `application_status_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `application_id` bigint NOT NULL,
  `changed_at` datetime(6) NOT NULL,
  `changed_by_user_id` bigint NOT NULL,
  `new_status` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notes` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `old_status` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `applications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `applied_at` datetime(6) NOT NULL,
  `job_id` bigint NOT NULL,
  `resume_version_id` bigint NOT NULL,
  `seeker_id` bigint NOT NULL,
  `status` enum('APPLIED','UNDER_REVIEW','SHORTLISTED','REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status_updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKd2lon5cqi48w2l8590hh7ksvx` (`job_id`,`seeker_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `companies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_description` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `is_active` bit(1) NOT NULL,
  `location` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `website` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `company_scores` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `application_id` bigint NOT NULL,
  `computed_at` datetime(6) NOT NULL,
  `education_score` double NOT NULL,
  `experience_score` double NOT NULL,
  `explanation_json` longtext COLLATE utf8mb4_unicode_ci,
  `formatting_score` double NOT NULL,
  `general_score_id` bigint DEFAULT NULL,
  `matched_skills_json` longtext COLLATE utf8mb4_unicode_ci,
  `missing_skills_json` longtext COLLATE utf8mb4_unicode_ci,
  `must_have_score` double NOT NULL,
  `nice_to_have_score` double NOT NULL,
  `recommendations_json` longtext COLLATE utf8mb4_unicode_ci,
  `total_score` double NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `email_otps` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `consumed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `otp_hash` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `purpose` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `general_scores` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `completeness_score` double NOT NULL,
  `experience_score` double NOT NULL,
  `explanation_json` longtext COLLATE utf8mb4_unicode_ci,
  `formatting_score` double NOT NULL,
  `resume_version_id` bigint NOT NULL,
  `scored_at` datetime(6) NOT NULL,
  `skills_score` double NOT NULL,
  `structure_score` double NOT NULL,
  `total_score` double NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `job_postings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `employment_type` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `max_experience_years` int DEFAULT NULL,
  `min_experience_years` int DEFAULT NULL,
  `recruiter_id` bigint NOT NULL,
  `status` enum('DRAFT','OPEN','CLOSED','FILLED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `job_reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `job_id` bigint NOT NULL,
  `other_reason_text` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reason` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `report_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reported_by_user_id` bigint NOT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reviewed_by_admin_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `job_skills` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `importance` enum('MUST_HAVE','NICE_TO_HAVE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `job_id` bigint NOT NULL,
  `normalized_skill_text` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `raw_skill_text` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `skill_id` bigint DEFAULT NULL,
  `weight` decimal(5,2) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `login_attempts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempted_at` datetime(6) NOT NULL,
  `identifier_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `identifier_value` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ip_address` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `success` bit(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `body` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `channel` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `is_read` bit(1) NOT NULL,
  `send_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `subject` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `password_reset_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `token_hash` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `recruiter_profiles` (
  `user_id` bigint NOT NULL,
  `company_id` bigint DEFAULT NULL,
  `designation` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `profile_completion_pct` int NOT NULL,
  `recruiter_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `resume_extractions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `experience_years` double DEFAULT NULL,
  `extracted_email` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `extracted_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `extracted_phone` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `extracted_text` longtext COLLATE utf8mb4_unicode_ci,
  `parsing_quality_score` double DEFAULT NULL,
  `resume_version_id` bigint NOT NULL,
  `sections_json` longtext COLLATE utf8mb4_unicode_ci,
  `skills_json` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `resume_format_flags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `flag_type` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resume_version_id` bigint NOT NULL,
  `section_name` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `severity` enum('LOW','MEDIUM','HIGH') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `resume_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `file_size` bigint NOT NULL,
  `mime_type` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `original_file_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `resume_id` bigint NOT NULL,
  `storage_path` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `uploaded_at` datetime(6) NOT NULL,
  `version_label` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version_number` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `resumes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active_version_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `seeker_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `seeker_profiles` (
  `user_id` bigint NOT NULL,
  `bio` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `headline` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `profile_completion_pct` int NOT NULL,
  `targeted_skills_text` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `skill_alias_seq` (
  `next_val` bigint NOT NULL,
  PRIMARY KEY (`next_val`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `skill_aliases` (
  `id` bigint NOT NULL,
  `alias_text` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `normalized_alias_text` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `skill_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK1dh57ajot6ynmkeyxdl409cdg` (`normalized_alias_text`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `skills` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `canonical_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK38h4kt5efabexmhskxpips9hi` (`canonical_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_status` enum('ACTIVE','DISABLED','PENDING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `display_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email_verified` bit(1) NOT NULL,
  `last_login_at` datetime(6) DEFAULT NULL,
  `password_hash` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` enum('SEEKER','RECRUITER','ADMIN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `two_factor_enabled` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UKdu5v5sr43g5bfnji4vb8hg5s3` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Optional: pre-populate the canonical skills and their aliases.
-- The application seeds these automatically on first boot, so this is
-- only a convenience. Safe to run repeatedly.
-- ---------------------------------------------------------------------
