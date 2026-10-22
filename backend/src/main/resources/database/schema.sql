-- ============================================================================
-- 專案名稱: SurveyFlow (企業級問卷調查與智慧數據分析系統)
-- 檔案名稱: schema.sql
-- 責任描述: MySQL 8.x 資料庫全表 DDL 結構腳本，嚴格遵循第三正規化 (3NF) 與高效聚合索引
-- 專案作者: 許哲誠 (HSU, CHE-CHENG)
-- 字元編碼: utf8mb4 / utf8mb4_unicode_ci
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `survey_flow`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `survey_flow`;

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `submission_answers`;
DROP TABLE IF EXISTS `submission_basic_infos`;
DROP TABLE IF EXISTS `submissions`;
DROP TABLE IF EXISTS `survey_options`;
DROP TABLE IF EXISTS `survey_questions`;
DROP TABLE IF EXISTS `survey_basic_info_configs`;
DROP TABLE IF EXISTS `surveys`;
DROP TABLE IF EXISTS `refresh_tokens`; -- 清理歷史遺留之雙 Token 旋轉控制表
DROP TABLE IF EXISTS `verification_tokens`;
DROP TABLE IF EXISTS `users`;
SET FOREIGN_KEY_CHECKS = 1;

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
    `role` VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER' COMMENT '系統角色: ROLE_GUEST, ROLE_USER, ROLE_ADMIN',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '帳號狀態: ACTIVE, SUSPENDED, DELETED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '帳號建立時間',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最後更新時間',
    `deleted_at` DATETIME DEFAULT NULL COMMENT '軟刪除時間標記 (NULL 代表有效)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_email` (`email`),
    UNIQUE KEY `uk_users_username` (`username`),
    KEY `idx_users_role_status` (`role`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='使用者與會員主表';

-- ----------------------------------------------------------------------------
-- 2. 問卷主表 (surveys)
-- 說明: 儲存問卷主題、前言、狀態、生效時間與建立者關聯
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `surveys` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '問卷唯一識別碼',
    `user_id` BIGINT NOT NULL COMMENT '建立者使用者 ID',
    `title` VARCHAR(150) NOT NULL COMMENT '問卷主題名稱',
    `description` TEXT DEFAULT NULL COMMENT '問卷前言與填答指引說明',
    `cover_image_url` LONGTEXT DEFAULT NULL COMMENT '問卷主視覺封面或前言說明圖片 (支援 Unsplash URL 或 Base64)',
    `category` VARCHAR(50) NOT NULL DEFAULT '綜合調查' COMMENT '問卷分類標籤',
    `is_public_to_lobby` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否公開展示至大廳 (0: 不公開僅限專屬連結, 1: 公開於大廳)',
    `is_open` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '私人連結是否開放填寫 (0: 否已截止關閉, 1: 是開放填寫)',
    `start_date` DATETIME DEFAULT NULL COMMENT '有效起始時間 (NULL 代表常態開放無時限)',
    `end_date` DATETIME DEFAULT NULL COMMENT '有效截止時間 (NULL 代表常態開放無時限)',
    `view_count` INT NOT NULL DEFAULT 0 COMMENT '問卷瀏覽次數計數器',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '問卷建立時間',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最後修改時間',
    `deleted_at` DATETIME DEFAULT NULL COMMENT '軟刪除時間戳記',
    PRIMARY KEY (`id`),
    KEY `idx_surveys_user` (`user_id`),
    KEY `idx_surveys_open_dates` (`is_open`, `is_public_to_lobby`, `start_date`, `end_date`),
    CONSTRAINT `fk_surveys_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='問卷主題主表';

-- ----------------------------------------------------------------------------
-- 3. 問卷受訪者基本資料收集設定表 (survey_basic_info_configs)
-- 說明: 控制受訪者進入問卷時，需填寫哪些個人輪廓欄位 (1:1 對應問卷)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `survey_basic_info_configs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '設定檔主鍵',
    `survey_id` BIGINT NOT NULL COMMENT '對應問卷 ID',
    `collect_basic_info` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否啟用受訪者基本資料蒐集 (0=免填/一個不用)',
    `require_name` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否收集受訪者姓名',
    `require_gender` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否收集性別',
    `require_age` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否收集年齡區間',
    `require_birthday` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集生日',
    `require_national_id` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集身分證字號',
    `require_phone` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集聯絡手機',
    `require_home_phone` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集住家市話',
    `require_email` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集電子郵件',
    `require_industry` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集所屬產業',
    `require_job_title` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集職稱',
    `require_department` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集所屬單位／部門',
    `require_employee_id` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集員工編號',
    `require_academic_major` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集就讀科系',
    `require_class_name` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集班級名稱',
    `require_student_id` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集學號',
    `require_height` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集身高',
    `require_weight` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集體重',
    `require_residence` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集居住地區',
    `require_registered_address` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集戶籍地址',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_survey_basic_config` (`survey_id`),
    CONSTRAINT `fk_basic_config_survey` FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='問卷受訪者前置基本資訊收集設定表';

-- ----------------------------------------------------------------------------
-- 4. 問卷題目主表 (survey_questions)
-- 說明: 支援單選、多選、文字簡答、評分量表、時間、日期與是非題型
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `survey_questions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '題目唯一識別碼',
    `survey_id` BIGINT NOT NULL COMMENT '所屬問卷 ID',
    `question_code` VARCHAR(50) DEFAULT NULL COMMENT '前端題目 UID (例如 Q1, Q2, 供跳題邏輯對應)',
    `type` VARCHAR(20) NOT NULL COMMENT '題型代碼: BOOLEAN, SINGLE, MULTI, TEXT, RATING, TIME, DATE',
    `question_text` VARCHAR(500) NOT NULL COMMENT '題目標題文字',
    `image_url` LONGTEXT DEFAULT NULL COMMENT '題目參考插圖或示意圖 (支援 Unsplash URL 或 Base64)',
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
-- 5. 題目選項表 (survey_options)
-- 說明: 單選與多選題之選項清單 (文字題與純評分題無選項)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `survey_options` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '選項唯一識別碼',
    `question_id` BIGINT NOT NULL COMMENT '所屬題目 ID',
    `option_text` VARCHAR(255) NOT NULL COMMENT '選項文字描述',
    `sort_order` INT NOT NULL DEFAULT 1 COMMENT '選項排序順序',
    `trigger_question_uid` VARCHAR(50) DEFAULT NULL COMMENT '勾選此選項時觸發解鎖顯示之題目代碼 (條件跳題)',
    PRIMARY KEY (`id`),
    KEY `idx_options_question_order` (`question_id`, `sort_order`),
    CONSTRAINT `fk_options_question` FOREIGN KEY (`question_id`) REFERENCES `survey_questions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='問卷題目選項明細表';

-- ----------------------------------------------------------------------------
-- 6. 填答紀錄主表 (submissions)
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
-- 7. 填答者基本輪廓資料明細表 (submission_basic_infos)
-- 說明: 儲存受訪者於該次填答提供之個人輪廓 (1:1 對應 submissions)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `submission_basic_infos` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主鍵識別碼',
    `submission_id` BIGINT NOT NULL COMMENT '所屬填答紀錄 ID',
    `name` VARCHAR(50) DEFAULT NULL COMMENT '填答者姓名',
    `gender` VARCHAR(10) DEFAULT NULL COMMENT '性別 (男, 女, 其他)',
    `age_group` VARCHAR(30) DEFAULT NULL COMMENT '年齡區間 (例如: 18-24歲, 25-34歲)',
    `birthday` VARCHAR(20) DEFAULT NULL COMMENT '生日 (YYYY-MM-DD)',
    `national_id` VARCHAR(20) DEFAULT NULL COMMENT '身分證字號',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '聯絡手機',
    `home_phone` VARCHAR(30) DEFAULT NULL COMMENT '住家市話',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '電子信箱',
    `industry` VARCHAR(50) DEFAULT NULL COMMENT '任職產業別',
    `job_title` VARCHAR(50) DEFAULT NULL COMMENT '職稱',
    `department` VARCHAR(50) DEFAULT NULL COMMENT '所屬單位／部門',
    `employee_id` VARCHAR(50) DEFAULT NULL COMMENT '員工編號',
    `academic_major` VARCHAR(50) DEFAULT NULL COMMENT '就讀科系',
    `class_name` VARCHAR(50) DEFAULT NULL COMMENT '班級名稱',
    `student_id` VARCHAR(50) DEFAULT NULL COMMENT '學號',
    `height` DOUBLE DEFAULT NULL COMMENT '身高 (cm)',
    `weight` DOUBLE DEFAULT NULL COMMENT '體重 (kg)',
    `residence` VARCHAR(50) DEFAULT NULL COMMENT '居住縣市地區',
    `registered_address` VARCHAR(200) DEFAULT NULL COMMENT '戶籍地址',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_basic_info_submission` (`submission_id`),
    CONSTRAINT `fk_basic_info_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='受訪者基本輪廓明細表';

-- ----------------------------------------------------------------------------
-- 8. 題目作答結果明細表 (submission_answers)
-- 說明: 儲存每一題的作答結果 (單選/多選/文字/量表均正規化存儲，方便高併發聚合查詢)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `submission_answers` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '作答明細識別碼',
    `submission_id` BIGINT NOT NULL COMMENT '所屬填答主表 ID',
    `question_id` BIGINT NOT NULL COMMENT '所屬題目 ID',
    `option_id` BIGINT DEFAULT NULL COMMENT '所選選項 ID (單選或多選時填入，多選題一選項一筆紀錄)',
    `text_answer` TEXT DEFAULT NULL COMMENT '開放式填答文字 (僅 TEXT/TIME/DATE 適用)',
    `rating_value` INT DEFAULT NULL COMMENT '量表給定數值 (僅 RATING 適用)',
    PRIMARY KEY (`id`),
    KEY `idx_answers_submission` (`submission_id`),
    KEY `idx_answers_question_option` (`question_id`, `option_id`),
    CONSTRAINT `fk_answers_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_answers_question` FOREIGN KEY (`question_id`) REFERENCES `survey_questions` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_answers_option` FOREIGN KEY (`option_id`) REFERENCES `survey_options` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='題目回答明細表 (支援高效報表聚合)';

-- ----------------------------------------------------------------------------
-- 9. 電子郵件安全驗證碼表 (verification_tokens)
-- 說明: 儲存會員註冊啟用與忘記密碼之時效性 6 碼驗證碼 (OTP)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `verification_tokens` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主鍵識別碼',
    `email` VARCHAR(100) NOT NULL COMMENT '目標電子信箱',
    `code` VARCHAR(10) NOT NULL COMMENT '6 碼數字安全驗證碼',
    `token_type` VARCHAR(30) NOT NULL COMMENT '驗證碼用途類型 (FORGOT_PASSWORD / REGISTRATION)',
    `expires_at` DATETIME NOT NULL COMMENT '驗證碼過期時間戳',
    `is_used` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已使用核銷 (0: 否, 1: 是)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    PRIMARY KEY (`id`),
    INDEX `idx_vt_email_type` (`email`, `token_type`),
    INDEX `idx_vt_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='電子郵件安全驗證碼表';


