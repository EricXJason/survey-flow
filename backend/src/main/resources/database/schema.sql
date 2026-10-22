-- ============================================================================
-- 專案名稱: SurveyFlow (企業級問卷調查與智慧數據分析系統)
-- 檔案名稱: schema.sql
-- 存放路徑: backend/src/main/resources/database/schema.sql (全系統唯一真實 DDL 來源)
-- 責任描述: TiDB Cloud Serverless / MySQL 8.0+ 資料庫全表 DDL 結構腳本
--           嚴格落實第三正規化 (3NF)、CHAR(10) 定長唯一索引、TINYINT 狀態與布林、級聯約束
-- 專案作者: 許哲誠 (HSU, CHE-CHENG)
-- 字元編碼: utf8mb4 / utf8mb4_unicode_ci
-- ============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 【方案 A】全量乾淨重置 (Clean Drop & Recreate)
-- 直接執行本區塊將以最新 3NF 與定長規範完整重建 9 大實體資料表
-- ─────────────────────────────────────────────────────────────────────────────

CREATE DATABASE IF NOT EXISTS `survey_flow`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `survey_flow`;

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 暫時關閉外鍵檢查，確保安全反向清理舊結構
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `submission_answers`;
DROP TABLE IF EXISTS `submission_basic_infos`;
DROP TABLE IF EXISTS `submissions`;
DROP TABLE IF EXISTS `survey_options`;
DROP TABLE IF EXISTS `survey_questions`;
DROP TABLE IF EXISTS `survey_basic_info_configs`;
DROP TABLE IF EXISTS `surveys`;
DROP TABLE IF EXISTS `verification_tokens`;
DROP TABLE IF EXISTS `refresh_tokens`; -- 清理歷史遺留
DROP TABLE IF EXISTS `users`;

SET FOREIGN_KEY_CHECKS = 1;

-- 1. 會員核心資料表 (users)
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '會員唯一流水號',
    `username` VARCHAR(50) NOT NULL COMMENT '登入帳號名稱',
    `email` VARCHAR(100) NOT NULL COMMENT '會員電子郵件',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'Argon2id 密碼雜湊值',
    `full_name` VARCHAR(50) NOT NULL COMMENT '真實姓名',
    `phone` CHAR(10) DEFAULT NULL COMMENT '定長聯絡手機 (10碼純數字)',
    `identity_card` CHAR(10) DEFAULT NULL COMMENT '定長身分證統一編號 (10碼)',
    `role` VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER' COMMENT '系統權限角色: ROLE_ADMIN, ROLE_USER, ROLE_GUEST',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '帳號狀態: ACTIVE, SUSPENDED, DELETED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最後異動時間',
    `deleted_at` DATETIME DEFAULT NULL COMMENT '軟刪除時間標記 (NULL 代表有效)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_email` (`email`),
    UNIQUE KEY `uk_users_username` (`username`),
    UNIQUE KEY `uk_users_phone` (`phone`),
    UNIQUE KEY `uk_users_identity_card` (`identity_card`),
    KEY `idx_users_role_status` (`role`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系統會員核心資料表';

-- 2. 帳號時效性驗證碼記錄表 (verification_tokens)
CREATE TABLE IF NOT EXISTS `verification_tokens` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '驗證碼流水號',
    `user_id` BIGINT DEFAULT NULL COMMENT '關聯會員流水號 (未註冊前為 NULL)',
    `email` VARCHAR(100) NOT NULL COMMENT '驗證目標電子郵件',
    `code` VARCHAR(10) NOT NULL COMMENT '6至10位時效驗證碼',
    `token_type` VARCHAR(30) NOT NULL COMMENT '權杖業務類型: REGISTER, FORGOT_PASSWORD',
    `expires_at` DATETIME NOT NULL COMMENT '過期時限戳記',
    `is_used` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已核銷使用 (0:未使用, 1:已使用)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成時間',
    PRIMARY KEY (`id`),
    KEY `idx_vt_email_type` (`email`, `token_type`),
    KEY `idx_vt_user` (`user_id`),
    CONSTRAINT `fk_vt_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='時效性 OTP 驗證碼記錄表';

-- 3. 問卷專案主檔表 (surveys)
CREATE TABLE IF NOT EXISTS `surveys` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '問卷唯一識別碼',
    `user_id` BIGINT NOT NULL COMMENT '發起人會員 ID (關聯 users.id)',
    `title` VARCHAR(150) NOT NULL COMMENT '問卷主題名稱',
    `description` TEXT DEFAULT NULL COMMENT '問卷前言介紹與作答說明',
    `category` VARCHAR(50) NOT NULL DEFAULT '綜合調查' COMMENT '問卷 12 大領域分類標籤',
    `is_public_to_lobby` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否公開呈現於大廳廣場 (0:私有, 1:公開)',
    `is_open` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '私人連結是否開放填寫 (0:關閉, 1:開放)',
    `cover_image_url` LONGTEXT DEFAULT NULL COMMENT '問卷主視覺封面 (支援 Unsplash URL 或 Base64)',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='問卷專案主檔資料表';

-- 4. 問卷受訪者個人輪廓收集欄位配置表 (survey_basic_info_configs)
CREATE TABLE IF NOT EXISTS `survey_basic_info_configs` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '配置記錄編號',
    `survey_id` BIGINT NOT NULL UNIQUE COMMENT '所屬問卷 ID (一對一強制唯一)',
    `collect_basic_info` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否啟用受訪者資料收集 (0:關閉, 1:啟用)',
    `require_name` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否收集受訪者姓名',
    `require_gender` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否收集性別',
    `require_age` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否收集年齡區間',
    `require_birthday` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收集生日',
    `require_national_id` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收集身分證字號',
    `require_phone` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收集聯絡手機',
    `require_home_phone` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收集住家市話',
    `require_email` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收集電子郵件',
    `require_industry` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收集所屬產業',
    `require_job_title` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收集職稱',
    `require_department` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收集所屬單位／部門',
    `require_employee_id` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收集員工編號',
    `require_academic_major` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收集就讀科系',
    `require_class_name` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收集班級名稱',
    `require_student_id` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收集學生學號',
    `require_height` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收集身高數據',
    `require_weight` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收集體重數據',
    `require_residence` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收集居住城市',
    `require_registered_address` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收集戶籍地址',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_basic_info_survey` FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='問卷受訪者個人輪廓收集欄位配置表';

-- 5. 問卷題目清單資料表 (survey_questions)
CREATE TABLE IF NOT EXISTS `survey_questions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '題目流水號',
    `survey_id` BIGINT NOT NULL COMMENT '所屬問卷 ID',
    `question_code` VARCHAR(50) DEFAULT NULL COMMENT '前端題號識別代碼 (如 Q1, Q2)',
    `type` VARCHAR(20) NOT NULL COMMENT '題目類型 (SINGLE, MULTI, BOOLEAN, RATING, TEXT, TIME, DATE)',
    `question_text` VARCHAR(500) NOT NULL COMMENT '題目主體敘述文字',
    `image_url` LONGTEXT DEFAULT NULL COMMENT '題目附加圖片',
    `is_required` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否為必填題目 (0:非必填, 1:必填)',
    `sort_order` INT NOT NULL DEFAULT 1 COMMENT '題目呈現順序',
    `min_rating` INT DEFAULT 1 COMMENT '評分題最小值 (預設 1 或 0)',
    `max_rating` INT DEFAULT 5 COMMENT '評分題最大值 (預設 5 或 10)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',
    PRIMARY KEY (`id`),
    KEY `idx_questions_survey_sort` (`survey_id`, `sort_order`),
    CONSTRAINT `fk_questions_survey` FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='問卷題目清單資料表';

-- 6. 單選/多選/是非題目選項資料表 (survey_options)
CREATE TABLE IF NOT EXISTS `survey_options` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '選項唯一流水號',
    `question_id` BIGINT NOT NULL COMMENT '所屬題目 ID',
    `option_text` VARCHAR(255) NOT NULL COMMENT '選項文字標籤',
    `sort_order` INT NOT NULL DEFAULT 1 COMMENT '選項排列順序',
    `trigger_question_uid` VARCHAR(50) DEFAULT NULL COMMENT '條件跳題解鎖之目標題目 UID',
    PRIMARY KEY (`id`),
    KEY `idx_options_question_sort` (`question_id`, `sort_order`),
    CONSTRAINT `fk_options_question` FOREIGN KEY (`question_id`) REFERENCES `survey_questions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='題目選項資料表';

-- 7. 問卷填答主檔紀錄表 (submissions)
CREATE TABLE IF NOT EXISTS `submissions` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '填答事件流水號',
    `survey_id` BIGINT NOT NULL COMMENT '作答之問卷 ID',
    `ip_address` VARCHAR(45) DEFAULT NULL COMMENT '受訪者 IPv4 / IPv6 位址',
    `duration_seconds` INT DEFAULT NULL COMMENT '填答作答總耗時 (秒)',
    `submitted_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交作答時間戳',
    PRIMARY KEY (`id`),
    KEY `idx_submissions_survey` (`survey_id`),
    KEY `idx_submissions_time` (`submitted_at`),
    CONSTRAINT `fk_submissions_survey` FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='問卷填答主檔紀錄表';

-- 8. 受訪者個資輪廓真實填報明細表 (submission_basic_infos)
CREATE TABLE IF NOT EXISTS `submission_basic_infos` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '受訪者基本資料流水號',
    `submission_id` BIGINT NOT NULL UNIQUE COMMENT '關聯之填答紀錄 ID (一對一唯一定位)',
    `name` VARCHAR(50) DEFAULT NULL COMMENT '填報姓名',
    `gender` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '生理性別 (0:未指定, 1:男性, 2:女性, 3:其他)',
    `age_group` VARCHAR(30) DEFAULT NULL COMMENT '年齡區間',
    `birthday` VARCHAR(20) DEFAULT NULL COMMENT '出生年月日',
    `national_id` CHAR(10) DEFAULT NULL COMMENT '定長身分證統一編號 (10碼)',
    `phone` CHAR(10) DEFAULT NULL COMMENT '定長聯絡電話 (10碼純數字)',
    `home_phone` VARCHAR(20) DEFAULT NULL COMMENT '住家市話',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '電子信箱',
    `industry` VARCHAR(50) DEFAULT NULL COMMENT '就業產業',
    `job_title` VARCHAR(50) DEFAULT NULL COMMENT '擔任職位',
    `department` VARCHAR(50) DEFAULT NULL COMMENT '所屬部門',
    `employee_id` VARCHAR(50) DEFAULT NULL COMMENT '公司工號',
    `academic_major` VARCHAR(50) DEFAULT NULL COMMENT '修讀系所',
    `class_name` VARCHAR(50) DEFAULT NULL COMMENT '所屬班級',
    `student_id` VARCHAR(50) DEFAULT NULL COMMENT '在校學號',
    `height` DOUBLE DEFAULT NULL COMMENT '身高數據 cm',
    `weight` DOUBLE DEFAULT NULL COMMENT '體重數據 kg',
    `residence` VARCHAR(50) DEFAULT NULL COMMENT '居住城市',
    `registered_address` VARCHAR(200) DEFAULT NULL COMMENT '戶籍完整地址',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_sub_info_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='受訪者個資輪廓真實填報明細表';

-- 9. 受訪者各題詳細作答答案表 (submission_answers)
CREATE TABLE IF NOT EXISTS `submission_answers` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '單題作答流水號',
    `submission_id` BIGINT NOT NULL COMMENT '所屬填答紀錄 ID',
    `question_id` BIGINT NOT NULL COMMENT '作答之題目 ID',
    `option_id` BIGINT DEFAULT NULL COMMENT '選取之選項 ID (單選、多選、是非題填入，文字與量表為 NULL)',
    `text_answer` TEXT DEFAULT NULL COMMENT '簡答題文字內容或日期/時間字串',
    `rating_value` INT DEFAULT NULL COMMENT '評分題數值 (1 至 10)',
    PRIMARY KEY (`id`),
    KEY `idx_answers_submission` (`submission_id`),
    KEY `idx_answers_question` (`question_id`),
    KEY `idx_answers_option` (`option_id`),
    CONSTRAINT `fk_answers_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_answers_question` FOREIGN KEY (`question_id`) REFERENCES `survey_questions` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_answers_option` FOREIGN KEY (`option_id`) REFERENCES `survey_options` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='受訪者各題詳細作答答案表';

-- ─────────────────────────────────────────────────────────────────────────────
-- 初始化系統最高管理員種子帳號 (預設帳號: admin / 密碼: Admin123!)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO `users` (`id`, `username`, `email`, `password_hash`, `full_name`, `phone`, `identity_card`, `role`, `status`, `created_at`, `updated_at`)
VALUES (
    1,
    'admin',
    'admin@surveyflow.com',
    '$argon2id$v=19$m=4096,t=3,p=1$4u8prALaBK1aC7DVJVg/+g$YoCtbfr0dHvdLvrlYsJ1BHw04HpeH0KTdb50iK3RAQA',
    '系統最高管理員',
    '0912345678',
    'A123456789',
    'ROLE_ADMIN',
    'ACTIVE',
    NOW(),
    NOW()
) ON DUPLICATE KEY UPDATE `updated_at` = CURRENT_TIMESTAMP;


-- ============================================================================
-- 【方案 B】增量更新專用區塊 (ALTER TABLE - 保留既有生產資料庫數據)
-- 若您不想刪除現有問卷與填答記錄，請僅執行以下語句：
-- ============================================================================
/*
-- 1. 會員表調整定長與 UNIQUE 約束
ALTER TABLE users 
    MODIFY COLUMN phone CHAR(10) DEFAULT NULL COMMENT '定長聯絡手機 (10碼純數字)',
    MODIFY COLUMN identity_card CHAR(10) DEFAULT NULL COMMENT '定長身分證統一編號 (10碼)';

-- 補齊唯一鍵索引 (若尚未建立)
ALTER TABLE users ADD UNIQUE INDEX uk_users_phone (phone);
ALTER TABLE users ADD UNIQUE INDEX uk_users_identity_card (identity_card);

-- 2. 驗證碼表記錄加入 user_id 外鍵關聯
ALTER TABLE verification_tokens 
    ADD COLUMN user_id BIGINT DEFAULT NULL COMMENT '關聯會員流水號' AFTER id,
    MODIFY COLUMN is_used TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已核銷使用 (0:未使用, 1:已使用)';
ALTER TABLE verification_tokens 
    ADD CONSTRAINT fk_vt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- 3. 受訪者輪廓表調整型別
ALTER TABLE submission_basic_infos 
    MODIFY COLUMN gender TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '生理性別 (0:未指定, 1:男, 2:女, 3:其他)',
    MODIFY COLUMN national_id CHAR(10) DEFAULT NULL COMMENT '定長身分證統一編號 (10碼)',
    MODIFY COLUMN phone CHAR(10) DEFAULT NULL COMMENT '定長聯絡電話 (10碼純數字)',
    MODIFY COLUMN home_phone VARCHAR(20) DEFAULT NULL COMMENT '住家市話';
*/
