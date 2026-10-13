-- ============================================================================
-- 專案名稱: SurveyFlow (企業級問卷調查與智慧數據分析系統)
-- 檔案名稱: schema.sql
-- 責任描述: MySQL 8.x 資料庫全表 DDL 腳本，供系統初始化與 MySQL Workbench 檢視
-- 專案作者: 許哲誠 (HSU, CHE-CHENG)
-- 字元編碼: utf8mb4 / utf8mb4_unicode_ci
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `survey_flow`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `survey_flow`;

-- ----------------------------------------------------------------------------
-- 1. 使用者主表 (users)
-- 說明: 儲存系統會員與管理者帳號資料，密碼採用 Argon2id 雜湊存儲
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '使用者主鍵識別碼',
    `username` VARCHAR(50) NOT NULL COMMENT '使用者登入帳號',
    `email` VARCHAR(100) NOT NULL COMMENT '電子信箱 (作為唯一通訊管道)',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'Argon2id 密碼雜湊值',
    `full_name` VARCHAR(50) NOT NULL COMMENT '真實中文姓名 (2-6字)',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '臺灣手機號碼 (09開頭)',
    `identity_card` VARCHAR(20) DEFAULT NULL COMMENT '身分證字號 (經雙向檢核加密或遮罩)',
    `role` VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER' COMMENT '系統角色: ROLE_USER, ROLE_ADMIN',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '帳號狀態: ACTIVE, SUSPENDED, PENDING',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '帳號建立時間',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最後更新時間',
    `deleted_at` DATETIME DEFAULT NULL COMMENT '軟刪除時間標記 (NULL 代表有效)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_email` (`email`),
    UNIQUE KEY `uk_users_username` (`username`),
    KEY `idx_users_role_status` (`role`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='使用者與會員主表';

-- ----------------------------------------------------------------------------
-- 2. JWT Refresh Token 旋轉管理表 (refresh_tokens)
-- 說明: 支援安全雙 Token 機制，有效防止 Replay Attack
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `refresh_tokens` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Token 紀錄編號',
    `user_id` BIGINT NOT NULL COMMENT '所屬使用者 ID',
    `token_hash` VARCHAR(255) NOT NULL COMMENT 'Refresh Token 雜湊值',
    `expires_at` DATETIME NOT NULL COMMENT '權杖過期時間 (預設 7 天)',
    `revoked` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已被撤銷或主動登出',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '簽發時間',
    PRIMARY KEY (`id`),
    KEY `idx_refresh_tokens_user` (`user_id`),
    KEY `idx_refresh_tokens_hash` (`token_hash`),
    CONSTRAINT `fk_refresh_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='JWT Refresh Token 旋轉控制表';

-- ----------------------------------------------------------------------------
-- 3. 問卷主表 (surveys)
-- 說明: 儲存問卷主題、前言、狀態、生效時間與建立者關聯
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `surveys` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '問卷唯一識別碼',
    `user_id` BIGINT NOT NULL COMMENT '建立者使用者 ID',
    `title` VARCHAR(150) NOT NULL COMMENT '問卷主題名稱',
    `description` TEXT DEFAULT NULL COMMENT '問卷前言與填答指引說明',
    `category` VARCHAR(50) NOT NULL DEFAULT '綜合調查' COMMENT '問卷分類標籤',
    `is_public_to_lobby` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否公開展示至大廳 (FALSE: 僅限分享連結填寫, TRUE: 公開於大廳)',
    `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '問卷狀態: DRAFT (草稿), PUBLISHED (開放填寫), CLOSED (已截止關閉)',
    `start_date` DATETIME NOT NULL COMMENT '有效起始時間',
    `end_date` DATETIME NOT NULL COMMENT '有效截止時間',
    `view_count` INT NOT NULL DEFAULT 0 COMMENT '問卷瀏覽次數計數器',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '問卷建立時間',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最後修改時間',
    `deleted_at` DATETIME DEFAULT NULL COMMENT '軟刪除時間戳記',
    PRIMARY KEY (`id`),
    KEY `idx_surveys_user` (`user_id`),
    KEY `idx_surveys_status_dates` (`status`, `start_date`, `end_date`),
    CONSTRAINT `fk_surveys_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='問卷主題主表';

-- ----------------------------------------------------------------------------
-- 4. 問卷受訪者基本資料收集設定表 (survey_basic_info_configs)
-- 說明: 控制受訪者進入問卷時，需填寫哪些個人輪廓欄位 (1:1 對應問卷)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `survey_basic_info_configs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '設定檔主鍵',
    `survey_id` BIGINT NOT NULL COMMENT '對應問卷 ID',
    `require_name` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否收集受訪者姓名',
    `require_gender` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否收集性別',
    `require_age` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否收集年齡區間',
    `require_phone` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集聯絡手機',
    `require_email` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集電子郵件',
    `require_industry` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集所屬產業',
    `require_residence` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集居住地區',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_survey_basic_config` (`survey_id`),
    CONSTRAINT `fk_basic_config_survey` FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='問卷受訪者前置基本資訊收集設定表';

-- ----------------------------------------------------------------------------
-- 5. 問卷題目主表 (survey_questions)
-- 說明: 支援單選、多選、文字簡答與評分量表題型
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `survey_questions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '題目唯一識別碼',
    `survey_id` BIGINT NOT NULL COMMENT '所屬問卷 ID',
    `question_code` VARCHAR(50) DEFAULT NULL COMMENT '前端題目 UID (例如 Q1, Q2, 供跳題邏輯對應)',
    `type` VARCHAR(20) NOT NULL COMMENT '題型代碼: BOOLEAN, SINGLE, MULTI, TEXT, RATING, TIME, DATE',
    `question_text` VARCHAR(500) NOT NULL COMMENT '題目標題文字',
    `is_required` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否為必答題',
    `sort_order` INT NOT NULL DEFAULT 1 COMMENT '題目顯示排序 (由小到大)',
    `min_rating` INT DEFAULT 1 COMMENT '量表最低分 (僅 RATING 適用)',
    `max_rating` INT DEFAULT 5 COMMENT '量表最高分 (僅 RATING 適用)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',
    PRIMARY KEY (`id`),
    KEY `idx_questions_survey_order` (`survey_id`, `sort_order`),
    CONSTRAINT `fk_questions_survey` FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='問卷題目主表';

-- ----------------------------------------------------------------------------
-- 6. 題目選項表 (survey_options)
-- 說明: 單選與多選題之選項清單 (文字題與純評分題無選項)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `survey_options` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '選項唯一識別碼',
    `question_id` BIGINT NOT NULL COMMENT '所屬題目 ID',
    `option_text` VARCHAR(255) NOT NULL COMMENT '選項文字描述',
    `sort_order` INT NOT NULL DEFAULT 1 COMMENT '選項排序順序',
    PRIMARY KEY (`id`),
    KEY `idx_options_question_order` (`question_id`, `sort_order`),
    CONSTRAINT `fk_options_question` FOREIGN KEY (`question_id`) REFERENCES `survey_questions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='問卷題目選項明細表';

-- ----------------------------------------------------------------------------
-- 7. 填答紀錄主表 (submissions)
-- 說明: 每一次受訪者成功提交產生的主紀錄
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `submissions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '填答紀錄唯一識別碼',
    `survey_id` BIGINT NOT NULL COMMENT '所屬問卷 ID',
    `ip_address` VARCHAR(45) DEFAULT NULL COMMENT '提交者 IPv4 / IPv6 網路位址 (用於防重複填答)',
    `duration_seconds` INT DEFAULT NULL COMMENT '受訪者作答所耗費秒數',
    `submitted_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '填答送出時間戳記',
    PRIMARY KEY (`id`),
    KEY `idx_submissions_survey_time` (`survey_id`, `submitted_at`),
    CONSTRAINT `fk_submissions_survey` FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='受訪者填答紀錄主表';

-- ----------------------------------------------------------------------------
-- 8. 填答者基本輪廓資料明細表 (submission_basic_infos)
-- 說明: 儲存受訪者於該次填答提供之個人輪廓 (1:1 對應 submissions)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `submission_basic_infos` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主鍵識別碼',
    `submission_id` BIGINT NOT NULL COMMENT '所屬填答紀錄 ID',
    `name` VARCHAR(50) DEFAULT NULL COMMENT '填答者姓名',
    `gender` VARCHAR(10) DEFAULT NULL COMMENT '性別 (男, 女, 其他)',
    `age_group` VARCHAR(30) DEFAULT NULL COMMENT '年齡區間 (例如: 18-24歲, 25-34歲)',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '聯絡電話',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '電子信箱',
    `industry` VARCHAR(50) DEFAULT NULL COMMENT '任職產業別',
    `residence` VARCHAR(50) DEFAULT NULL COMMENT '居住縣市地區',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_basic_info_submission` (`submission_id`),
    CONSTRAINT `fk_basic_info_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='受訪者基本輪廓明細表';

-- ----------------------------------------------------------------------------
-- 9. 題目作答結果明細表 (submission_answers)
-- 說明: 儲存每一題的作答結果 (單選/多選/文字/量表均正規化存儲，方便高併發聚合查詢)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `submission_answers` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '作答明細識別碼',
    `submission_id` BIGINT NOT NULL COMMENT '所屬填答主表 ID',
    `question_id` BIGINT NOT NULL COMMENT '所屬題目 ID',
    `option_id` BIGINT DEFAULT NULL COMMENT '所選選項 ID (單選或多選時填入，多選題一選項一筆紀錄)',
    `text_answer` TEXT DEFAULT NULL COMMENT '開放式填答文字 (僅 TEXT 適用)',
    `rating_value` INT DEFAULT NULL COMMENT '量表給定數值 (僅 RATING 適用)',
    PRIMARY KEY (`id`),
    KEY `idx_answers_submission` (`submission_id`),
    KEY `idx_answers_question_option` (`question_id`, `option_id`),
    CONSTRAINT `fk_answers_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_answers_question` FOREIGN KEY (`question_id`) REFERENCES `survey_questions` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_answers_option` FOREIGN KEY (`option_id`) REFERENCES `survey_options` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='題目回答明細表 (支援高效報表聚合)';
