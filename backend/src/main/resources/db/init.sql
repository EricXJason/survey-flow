-- ============================================================================
-- 專案名稱: SurveyFlow (企業級問卷調查與智慧數據分析系統)
-- 檔案名稱: init.sql
-- 責任描述: 【單一完整資料庫初始化與一鍵重置腳本】
-- 專案作者: 許哲誠 (HSU, CHE-CHENG)
-- 字元編碼: utf8mb4 / utf8mb4_unicode_ci
--
-- 💡 使用方式：
--    可以直接將本檔案內容全部複製，貼入 MySQL Workbench、DBeaver、Navicat 或終端機
--    直接執行即可！一次性自動建立資料庫、資料表結構與 40 筆預設真實測試數據。
-- ============================================================================

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
DROP TABLE IF EXISTS `refresh_tokens`;
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
    `cover_image_url` LONGTEXT DEFAULT NULL COMMENT '問卷主視覺封面或前言說明圖片 (支援 Unsplash URL 或 Base64)',
    `category` VARCHAR(50) NOT NULL DEFAULT '綜合調查' COMMENT '問卷分類標籤',
    `is_public_to_lobby` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否公開展示至大廳 (0: 不公開僅限專屬連結, 1: 公開於大廳)',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED' COMMENT '發布狀態: PUBLISHED (公開至大廳), UNPUBLISHED (不公開至大廳)',
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
-- 6. 題目選項表 (survey_options)
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
    `text_answer` TEXT DEFAULT NULL COMMENT '開放式填答文字 (僅 TEXT/TIME/DATE 適用)',
    `rating_value` INT DEFAULT NULL COMMENT '量表給定數值 (僅 RATING 適用)',
    PRIMARY KEY (`id`),
    KEY `idx_answers_submission` (`submission_id`),
    KEY `idx_answers_question_option` (`question_id`, `option_id`),
    CONSTRAINT `fk_answers_submission` FOREIGN KEY (`submission_id`) REFERENCES `submissions` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_answers_question` FOREIGN KEY (`question_id`) REFERENCES `survey_questions` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_answers_option` FOREIGN KEY (`option_id`) REFERENCES `survey_options` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='題目回答明細表 (支援高效報表聚合)';


-- ============================================================================
-- 專案名稱: SurveyFlow (企業級問卷調查與智慧數據分析系統)
-- 檔案名稱: data.sql
-- 責任描述: 注入極具商業價值、學術研究與生活日常統計意義之完整問卷與真實受訪者數據
-- 專案作者: 許哲誠 (HSU, CHE-CHENG)
-- 字元編碼: utf8mb4 / utf8mb4_unicode_ci
-- ============================================================================

USE `survey_flow`;

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE `submission_answers`;
TRUNCATE TABLE `submission_basic_infos`;
TRUNCATE TABLE `submissions`;
TRUNCATE TABLE `survey_options`;
TRUNCATE TABLE `survey_questions`;
TRUNCATE TABLE `survey_basic_info_configs`;
TRUNCATE TABLE `surveys`;
TRUNCATE TABLE `refresh_tokens`;
DELETE FROM `users` WHERE `id` > 2;
SET FOREIGN_KEY_CHECKS = 1;

-- 重設使用者帳號與密碼 (admin / testuser)
INSERT INTO `users` (`id`, `username`, `password_hash`, `full_name`, `email`, `phone`, `identity_card`, `role`, `status`, `created_at`, `updated_at`)
VALUES 
(1, 'admin', '$argon2id$v=19$m=65536,t=3,p=1$4Qz9yO2hX0yK1vP8rS3uTw$1N6tV8eX4qZ9wM2yK7vP8rS3uTw1N6tV8eX4qZ9wM2y', '系統最高管理員', 'admin@surveyflow.com', '0912345678', 'A123456789', 'ROLE_ADMIN', 'ACTIVE', NOW(), NOW()),
(2, 'testuser', '$argon2id$v=19$m=65536,t=3,p=1$7X1wM2yK7vP8rS3uTw1N6t$4Qz9yO2hX0yK1vP8rS3uTw1N6tV8eX4qZ9wM2yK7vP8r', '測試會員小明', 'testuser@surveyflow.com', '0987654321', 'B234567890', 'ROLE_USER', 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    username = VALUES(username),
    full_name = VALUES(full_name),
    email = VALUES(email),
    status = 'ACTIVE';

-- -------------------------------------------------------------
-- 建立 4 筆高度專業且符合統計意義之問卷專案 (testuser 擁有)
-- -------------------------------------------------------------
INSERT INTO `surveys` (`id`, `user_id`, `title`, `description`, `cover_image_url`, `category`, `is_public_to_lobby`, `status`, `start_date`, `end_date`, `view_count`, `created_at`, `updated_at`)
VALUES
(1, 2, '2026 企業級 SaaS 雲端平台使用者體驗與採購決策調研', '【商業統計】本調研旨在深入探討跨國企業在導入現代化雲端軟體時之資安合規、非同步協作效能與年費定價決策考量，為產品路線圖提供實質商業依據。', 'https://images.unsplash.com/photo-1522071820081-009f0129c71c?auto=format&fit=crop&w=1200&q=80', '產品體驗研究', 1, 'PUBLISHED', '2026-01-01 00:00:00', '2026-12-31 23:59:59', 168, NOW(), NOW()),
(2, 2, '2026 智慧零售與跨境電商消費者回購滿意度調查', '【商業消費】全面分析線上購物者對於多元行動支付（Apple Pay/LINE Pay）、物流配送時效、智慧客服滿意度與品牌忠誠度之關鍵指標。', 'https://images.unsplash.com/photo-1472851294608-062f824d29cc?auto=format&fit=crop&w=1200&q=80', '顧客滿意度', 1, 'PUBLISHED', '2026-01-01 00:00:00', '2026-12-31 23:59:59', 245, NOW(), NOW()),
(3, 2, '2026 生成式 AI 對大專院校研發產能與學術誠信影響評測', '【學術研究】針對全臺大專院校教師、研究生與技術研發人員，探討 LLM 輔助寫碼與論文閱讀對學術創新產能之推升程度及倫理審查現況。', 'https://images.unsplash.com/photo-1555066931-4365d14bab8c?auto=format&fit=crop&w=1200&q=80', '市場商業調研', 1, 'PUBLISHED', '2026-01-01 00:00:00', '2026-12-31 23:59:59', 312, NOW(), NOW()),
(4, 2, '【封測專案／非公開】次世代 SurveyFlow 產品體驗與公設福利統計', '【日常統計】本問卷為封閉測試問卷（未發布至大廳，持專屬直達連結可填答），蒐集團隊對辦公室環境、健身公設利用率與全新儀表板之第一手體驗反饋。', 'https://images.unsplash.com/photo-1460925895917-afdab827c52f?auto=format&fit=crop&w=1200&q=80', '綜合調查', 0, 'UNPUBLISHED', '2026-01-01 00:00:00', '2026-12-31 23:59:59', 58, NOW(), NOW());

-- 基本資料收集設定 (4 份問卷皆啟用)
INSERT INTO `survey_basic_info_configs` (`survey_id`, `require_name`, `require_gender`, `require_age`, `require_phone`, `require_email`, `require_industry`, `require_residence`)
VALUES
(1, 1, 1, 1, 1, 1, 1, 1),
(2, 1, 1, 1, 1, 1, 1, 1),
(3, 1, 1, 1, 1, 1, 1, 1),
(4, 1, 1, 1, 1, 1, 1, 1);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (1, 1, 'Q1', 'BOOLEAN', '貴公司目前是否已將核心業務系統遷移至雲端微服務架構？', 'https://images.unsplash.com/photo-1587560699334-cc4ff634909a?auto=format&fit=crop&w=800&q=80', 1, 1, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (1, 1, '是，已全面雲原生化 (Cloud-Native)', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (2, 1, '否，仍維持自建機房地端部署 (On-Premise)', 2, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (2, 1, 'Q2', 'SINGLE', '在採購企業級 SaaS 服務時，您是否曾遭遇單一簽入 (SSO) 或跨國法規合規阻礙？', NULL, 1, 2, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (3, 2, '經常遇到，合規審查週期平均超過三個月', 1, 'Q3');


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (4, 2, '偶爾遇到，原廠資安白皮書尚可滿足要求', 2, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (5, 2, '從未遇到，現有採購流程極為敏捷', 3, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (3, 1, 'Q3', 'MULTI', '【解鎖題】針對海外 SaaS 採購，團隊最重視下列哪些資安合規認證？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (6, 3, 'SOC 2 Type II 獨立第三方稽核認證', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (7, 3, 'ISO/IEC 27001 資訊安全管理體系', 2, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (8, 3, 'GDPR 歐盟一般資料保護規則遵循', 3, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (9, 3, 'HIPAA / 金融級法規相容資料加密', 4, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (4, 1, 'Q4', 'SINGLE', '貴公司預計投入於數位協作工具之年度預算成長幅度為何？', 'https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?auto=format&fit=crop&w=800&q=80', 1, 4, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (10, 4, '預算持平或小幅縮減 (0% 以下)', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (11, 4, '溫和成長 (1% ~ 10%)', 2, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (12, 4, '大幅擴張 (11% ~ 25%)', 3, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (13, 4, '戰略級投入 (超過 25%)', 4, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (5, 1, 'Q5', 'MULTI', '在團隊日常非同步協作中，下列哪些自動化機制為您節省最多時間？（複選）', NULL, 1, 5, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (14, 5, '整合 Slack / Teams 之自動化審批通知機器人', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (15, 5, '非同步錄影螢幕解說工具 (Loom / ScreenREC)', 2, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (16, 5, '跨專案看板即時進度儀表板 (Jira / Linear)', 3, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (17, 5, '雲端白板腦力激盪空間 (Miro / FigJam)', 4, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (6, 1, 'Q6', 'RATING', '整體而言，您對目前市面上企業協作軟體的「資安透明度」滿意度（1最低，5最高）', NULL, 1, 6, 1, 5, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (7, 1, 'Q7', 'RATING', '淨推薦值 (NPS)：您向同業主管推薦採用現代雲端 SaaS 架構的意願（0-10分）', NULL, 1, 7, 0, 10, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (8, 1, 'Q8', 'TIME', '您每日處理非同步跨國訊息與郵件效率最高的時間點是？', NULL, 1, 8, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (9, 1, 'Q9', 'DATE', '貴部門下次預計啟動年度資訊架構審查 (IT Audit) 的具體日期是？', NULL, 1, 9, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (10, 1, 'Q10', 'TEXT', '請分享您認為企業級軟體在「跨時區協作」與「權限控管」上最急需突破的瓶頸是什麼？（簡答題）', NULL, 0, 10, NULL, NULL, NOW(), NOW());


INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (1, 1, '192.168.1.10', 145, '2026-10-10 14:20:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (1, 1, '陳建宇', '男', '25-34歲', '0911222333', 'jy.chen@example.com', '資訊科技業', '臺北市信義區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (1, 1, 1, 2, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (2, 1, 2, 3, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (3, 1, 3, 6, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (4, 1, 3, 7, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (5, 1, 4, 10, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (6, 1, 5, 14, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (7, 1, 6, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (8, 1, 7, NULL, NULL, 10);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (9, 1, 8, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (10, 1, 9, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (11, 1, 10, NULL, '系統整體操作非常流暢，條件跳題在前端反應十分迅速，體驗超出預期！', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (2, 1, '192.168.1.11', 180, '2026-10-10 16:45:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (2, 2, '林雅婷', '女', '25-34歲', '0922333444', 'yt.lin@example.com', '金融保險業', '新北市板橋區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (12, 2, 1, 1, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (13, 2, 2, 4, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (14, 2, 4, 11, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (15, 2, 5, 14, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (16, 2, 5, 15, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (17, 2, 6, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (18, 2, 7, NULL, NULL, 10);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (19, 2, 8, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (20, 2, 9, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (21, 2, 10, NULL, '白色 SaaS 界面乾淨俐落，字體排版極佳，看起來非常舒服且專業。', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (3, 1, '192.168.1.12', 95, '2026-10-11 10:15:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (3, 3, '黃志偉', '男', '35-44歲', '0933444555', 'cw.huang@example.com', '電子科技業', '臺中市西屯區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (22, 3, 1, 1, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (23, 3, 2, 3, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (24, 3, 3, 6, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (25, 3, 3, 7, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (26, 3, 4, 12, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (27, 3, 5, 14, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (28, 3, 6, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (29, 3, 7, NULL, NULL, 10);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (30, 3, 8, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (31, 3, 9, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (32, 3, 10, NULL, '希望能支援更多匯出格式，目前 JSON 導出結構非常完整且符合標準。', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (4, 1, '192.168.1.13', 210, '2026-10-11 11:05:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (4, 4, '張淑芬', '女', '35-44歲', '0944555666', 'sf.chang@example.com', '製造業', '桃園市中壢區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (33, 4, 1, 2, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (34, 4, 2, 4, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (35, 4, 4, 13, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (36, 4, 5, 14, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (37, 4, 5, 15, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (38, 4, 6, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (39, 4, 7, NULL, NULL, 9);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (40, 4, 8, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (41, 4, 9, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (42, 4, 10, NULL, '後台數據分析報表很清楚，特別是 NPS 淨推薦值與受訪者人口輪廓，對決策非常有幫助。', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (5, 1, '192.168.1.14', 130, '2026-10-12 09:30:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (5, 5, '王俊賢', '男', '25-34歲', '0955666777', 'jx.wang@example.com', '專業服務業', '新竹市東區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (43, 5, 1, 1, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (44, 5, 2, 3, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (45, 5, 3, 6, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (46, 5, 3, 7, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (47, 5, 4, 10, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (48, 5, 5, 14, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (49, 5, 6, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (50, 5, 7, NULL, NULL, 9);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (51, 5, 8, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (52, 5, 9, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (53, 5, 10, NULL, '題型完整度很高，是非、單選、量表與時間日期一應俱全，真正符合企業級調研需求。', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (6, 1, '192.168.1.15', 160, '2026-10-12 15:50:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (6, 6, '李佩玲', '女', '25-34歲', '0966777888', 'pl.lee@example.com', '醫療保健業', '高雄市左營區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (54, 6, 1, 1, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (55, 6, 2, 4, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (56, 6, 4, 11, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (57, 6, 5, 14, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (58, 6, 5, 15, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (59, 6, 6, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (60, 6, 7, NULL, NULL, 8);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (61, 6, 8, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (62, 6, 9, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (63, 6, 10, NULL, '視覺設計清新專業，問卷大廳的分類選單篩選極為順手，整體完成度極高！', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (7, 1, '192.168.1.16', 85, '2026-10-13 13:10:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (7, 7, '郭庭安', '女', '18-24歲', '0977888999', 'ta.kuo@example.com', '教育學術界', '臺北市大安區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (64, 7, 1, 2, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (65, 7, 2, 3, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (66, 7, 3, 6, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (67, 7, 3, 7, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (68, 7, 4, 12, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (69, 7, 5, 14, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (70, 7, 6, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (71, 7, 7, NULL, NULL, 8);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (72, 7, 8, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (73, 7, 9, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (74, 7, 10, NULL, NULL, NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (8, 1, '192.168.1.17', 115, '2026-10-13 16:22:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (8, 8, '趙家豪', '男', '35-44歲', '0988999000', 'jh.chao@example.com', '交通物流業', '臺南市東區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (75, 8, 1, 1, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (76, 8, 2, 4, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (77, 8, 4, 13, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (78, 8, 5, 14, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (79, 8, 5, 15, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (80, 8, 6, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (81, 8, 7, NULL, NULL, 6);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (82, 8, 8, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (83, 8, 9, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (84, 8, 10, NULL, NULL, NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (9, 1, '192.168.1.18', 140, '2026-10-13 18:05:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (9, 9, '周雅婷', '女', '35-44歲', '0911000111', 'yt.chou@example.com', '批發零售業', '新北市中和區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (85, 9, 1, 1, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (86, 9, 2, 3, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (87, 9, 3, 6, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (88, 9, 3, 7, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (89, 9, 4, 10, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (90, 9, 5, 14, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (91, 9, 6, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (92, 9, 7, NULL, NULL, 6);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (93, 9, 8, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (94, 9, 9, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (95, 9, 10, NULL, NULL, NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (10, 1, '192.168.1.19', 125, '2026-10-13 20:45:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (10, 10, '劉建銘', '男', '45-54歲', '0922111222', 'cm.liu@example.com', '電子科技業', '新竹縣竹北市');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (96, 10, 1, 2, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (97, 10, 2, 4, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (98, 10, 4, 11, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (99, 10, 5, 14, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (100, 10, 5, 15, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (101, 10, 6, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (102, 10, 7, NULL, NULL, 6);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (103, 10, 8, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (104, 10, 9, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (105, 10, 10, NULL, NULL, NULL);

INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (11, 2, 'Q1', 'BOOLEAN', '您過去三個月內是否曾在行動端 App 或品牌官網完成購物下單？', NULL, 1, 1, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (18, 11, '是，每月平均線上下單 2 次以上', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (19, 11, '否，多數消費仍偏好實體門市體驗', 2, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (12, 2, 'Q2', 'SINGLE', '在購物結帳的關鍵最後一步，您是否曾因支付介面跳轉繁瑣而放棄購物車？', 'https://images.unsplash.com/photo-1556742049-0a67c5574f73?auto=format&fit=crop&w=800&q=80', 1, 2, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (20, 12, '是，曾因 OTP 簡訊延遲或多次驗證失敗放棄結帳', 1, 'Q3');


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (21, 12, '否，每次結帳皆能順暢在 1 分鐘內完成扣款', 2, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (13, 2, 'Q3', 'MULTI', '【解鎖題】遭遇結帳阻礙時，平台提供哪些快捷支付能吸引您完成購買？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (22, 13, 'Apple Pay / Google Pay 一鍵生物指紋辨識', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (23, 13, 'LINE Pay 快速自動跳轉授權折抵', 2, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (24, 13, '超商取貨付款安心代收服務', 3, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (25, 13, '街口支付即時點數現金回饋折抵', 4, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (14, 2, 'Q4', 'SINGLE', '您在決定是否回購該電商平台時，最核心的考量要素是？', NULL, 1, 4, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (26, 14, '價格優惠與會員常態全館免運門檻', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (27, 14, '當日或 24 小時極速到貨配送服務', 2, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (28, 14, '商品退換貨流程是否完全免手續費且不刁難', 3, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (29, 14, '商品正品保證與原廠完整售後維修', 4, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (15, 2, 'Q5', 'MULTI', '您平時在線上平台最經常選購的商品類別有哪些？（複選）', 'https://images.unsplash.com/photo-1441986300917-64674bd600d8?auto=format&fit=crop&w=800&q=80', 1, 5, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (30, 15, '3C 數碼配件與電腦周邊硬體', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (31, 15, '生活日用品與家庭消耗快消品', 2, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (32, 15, '潮流服飾、休閒鞋包與戶外裝備', 3, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (33, 15, '低溫生鮮食材與各地名特產美食', 4, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (16, 2, 'Q6', 'RATING', '您對本電商平台「智慧搜尋推薦與篩選精準度」滿意度（1最低，5最高）', NULL, 1, 6, 1, 5, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (17, 2, 'Q7', 'RATING', '淨推薦值 (NPS)：您向親友推薦在本商城購物的意願（0-10分）', NULL, 1, 7, 0, 10, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (18, 2, 'Q8', 'TIME', '您最習慣瀏覽線上促銷特賣或領取優惠券的黃金時段通常在？', NULL, 1, 8, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (19, 2, 'Q9', 'DATE', '您最近一次在線上完成購物的具體下單日期是？', NULL, 1, 9, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (20, 2, 'Q10', 'TEXT', '針對我們在包裹環保包裝、出貨追蹤動態或客服回覆速度，您有何寶貴建議？（簡答題）', NULL, 0, 10, NULL, NULL, NOW(), NOW());


INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (11, 2, '192.168.1.10', 145, '2026-10-10 14:20:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (11, 11, '陳建宇', '男', '25-34歲', '0911222333', 'jy.chen@example.com', '資訊科技業', '臺北市信義區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (106, 11, 11, 19, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (107, 11, 12, 20, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (108, 11, 13, 22, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (109, 11, 13, 23, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (110, 11, 14, 26, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (111, 11, 15, 30, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (112, 11, 16, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (113, 11, 17, NULL, NULL, 10);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (114, 11, 18, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (115, 11, 19, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (116, 11, 20, NULL, '系統整體操作非常流暢，條件跳題在前端反應十分迅速，體驗超出預期！', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (12, 2, '192.168.1.11', 180, '2026-10-10 16:45:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (12, 12, '林雅婷', '女', '25-34歲', '0922333444', 'yt.lin@example.com', '金融保險業', '新北市板橋區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (117, 12, 11, 18, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (118, 12, 12, 21, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (119, 12, 14, 27, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (120, 12, 15, 30, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (121, 12, 15, 31, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (122, 12, 16, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (123, 12, 17, NULL, NULL, 10);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (124, 12, 18, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (125, 12, 19, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (126, 12, 20, NULL, '白色 SaaS 界面乾淨俐落，字體排版極佳，看起來非常舒服且專業。', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (13, 2, '192.168.1.12', 95, '2026-10-11 10:15:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (13, 13, '黃志偉', '男', '35-44歲', '0933444555', 'cw.huang@example.com', '電子科技業', '臺中市西屯區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (127, 13, 11, 18, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (128, 13, 12, 20, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (129, 13, 13, 22, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (130, 13, 13, 23, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (131, 13, 14, 28, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (132, 13, 15, 30, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (133, 13, 16, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (134, 13, 17, NULL, NULL, 10);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (135, 13, 18, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (136, 13, 19, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (137, 13, 20, NULL, '希望能支援更多匯出格式，目前 JSON 導出結構非常完整且符合標準。', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (14, 2, '192.168.1.13', 210, '2026-10-11 11:05:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (14, 14, '張淑芬', '女', '35-44歲', '0944555666', 'sf.chang@example.com', '製造業', '桃園市中壢區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (138, 14, 11, 19, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (139, 14, 12, 21, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (140, 14, 14, 29, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (141, 14, 15, 30, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (142, 14, 15, 31, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (143, 14, 16, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (144, 14, 17, NULL, NULL, 9);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (145, 14, 18, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (146, 14, 19, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (147, 14, 20, NULL, '後台數據分析報表很清楚，特別是 NPS 淨推薦值與受訪者人口輪廓，對決策非常有幫助。', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (15, 2, '192.168.1.14', 130, '2026-10-12 09:30:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (15, 15, '王俊賢', '男', '25-34歲', '0955666777', 'jx.wang@example.com', '專業服務業', '新竹市東區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (148, 15, 11, 18, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (149, 15, 12, 20, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (150, 15, 13, 22, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (151, 15, 13, 23, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (152, 15, 14, 26, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (153, 15, 15, 30, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (154, 15, 16, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (155, 15, 17, NULL, NULL, 9);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (156, 15, 18, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (157, 15, 19, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (158, 15, 20, NULL, '題型完整度很高，是非、單選、量表與時間日期一應俱全，真正符合企業級調研需求。', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (16, 2, '192.168.1.15', 160, '2026-10-12 15:50:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (16, 16, '李佩玲', '女', '25-34歲', '0966777888', 'pl.lee@example.com', '醫療保健業', '高雄市左營區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (159, 16, 11, 18, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (160, 16, 12, 21, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (161, 16, 14, 27, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (162, 16, 15, 30, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (163, 16, 15, 31, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (164, 16, 16, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (165, 16, 17, NULL, NULL, 8);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (166, 16, 18, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (167, 16, 19, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (168, 16, 20, NULL, '視覺設計清新專業，問卷大廳的分類選單篩選極為順手，整體完成度極高！', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (17, 2, '192.168.1.16', 85, '2026-10-13 13:10:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (17, 17, '郭庭安', '女', '18-24歲', '0977888999', 'ta.kuo@example.com', '教育學術界', '臺北市大安區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (169, 17, 11, 19, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (170, 17, 12, 20, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (171, 17, 13, 22, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (172, 17, 13, 23, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (173, 17, 14, 28, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (174, 17, 15, 30, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (175, 17, 16, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (176, 17, 17, NULL, NULL, 8);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (177, 17, 18, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (178, 17, 19, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (179, 17, 20, NULL, NULL, NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (18, 2, '192.168.1.17', 115, '2026-10-13 16:22:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (18, 18, '趙家豪', '男', '35-44歲', '0988999000', 'jh.chao@example.com', '交通物流業', '臺南市東區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (180, 18, 11, 18, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (181, 18, 12, 21, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (182, 18, 14, 29, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (183, 18, 15, 30, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (184, 18, 15, 31, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (185, 18, 16, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (186, 18, 17, NULL, NULL, 6);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (187, 18, 18, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (188, 18, 19, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (189, 18, 20, NULL, NULL, NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (19, 2, '192.168.1.18', 140, '2026-10-13 18:05:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (19, 19, '周雅婷', '女', '35-44歲', '0911000111', 'yt.chou@example.com', '批發零售業', '新北市中和區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (190, 19, 11, 18, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (191, 19, 12, 20, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (192, 19, 13, 22, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (193, 19, 13, 23, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (194, 19, 14, 26, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (195, 19, 15, 30, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (196, 19, 16, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (197, 19, 17, NULL, NULL, 6);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (198, 19, 18, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (199, 19, 19, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (200, 19, 20, NULL, NULL, NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (20, 2, '192.168.1.19', 125, '2026-10-13 20:45:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (20, 20, '劉建銘', '男', '45-54歲', '0922111222', 'cm.liu@example.com', '電子科技業', '新竹縣竹北市');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (201, 20, 11, 19, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (202, 20, 12, 21, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (203, 20, 14, 27, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (204, 20, 15, 30, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (205, 20, 15, 31, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (206, 20, 16, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (207, 20, 17, NULL, NULL, 6);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (208, 20, 18, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (209, 20, 19, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (210, 20, 20, NULL, NULL, NULL);

INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (21, 3, 'Q1', 'BOOLEAN', '您在進行文獻檢索、數據統計或論文草稿撰寫時，是否已常態使用大型語言模型？', 'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?auto=format&fit=crop&w=800&q=80', 1, 1, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (34, 21, '是，已深度融入日常研究與教學管線', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (35, 21, '否，因學術誠信疑慮或個人習慣未廣泛使用', 2, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (22, 3, 'Q2', 'SINGLE', '您所屬的學術機構或系所，目前是否已正式發布「生成式 AI 使用指引與揭露規範」？', NULL, 1, 2, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (36, 22, '是，已有明確規範，且要求投稿前必須於致謝或方法段揭露', 1, 'Q3');


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (37, 22, '否，目前尚無正式指引，全憑研究者自主自律', 2, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (23, 3, 'Q3', 'MULTI', '【解鎖題】在提交論文審查時，您採取了哪些機制驗證 AI 輔助內容的真實性？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (38, 23, '逐篇核對 DOI 原始文獻出處，杜絕 AI 幻覺虛構引用', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (39, 23, '使用 Turnitin / iThenticate 進行嚴格學術抄襲率檢驗', 2, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (40, 23, '重跑所有 Python / R 實驗統計代碼驗證復現性', 3, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (41, 23, '邀請同行專家進行匿名 Double-Blind Peer Review', 4, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (24, 3, 'Q4', 'SINGLE', '您認為目前 AI 工具對您研發產能節省的工時比例約為？', 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?auto=format&fit=crop&w=800&q=80', 1, 4, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (42, 24, '節省少於 10% 工時，仍需投入大量校對時間', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (43, 24, '節省 11% ~ 30% 工時，在基礎語法與格式排版助益顯著', 2, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (44, 24, '節省 31% ~ 50% 工時，大幅加速文獻閱讀與實驗腳本建置', 3, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (45, 24, '節省超過 50% 工時，研發產能呈現倍數成長', 4, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (25, 3, 'Q5', 'MULTI', 'AI 工具在下列哪些研究環節為您帶來最具突破性的實質幫助？（複選）', NULL, 1, 5, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (46, 25, '快速閱讀跨領域數十篇文獻並提煉核心方法論', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (47, 25, '輔助編寫複雜演算法、數據清理腳本與正則表達式', 2, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (48, 25, '潤飾英文學術論文之句構流暢度與專業詞彙', 3, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (49, 25, '設計實驗假設與多維度研究問卷題項構面', 4, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (26, 3, 'Q6', 'RATING', '您對當前主流學術 AI 工具「理論推導邏輯嚴謹度」的評分（1最低，5最高）', NULL, 1, 6, 1, 5, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (27, 3, 'Q7', 'RATING', '淨推薦值 (NPS)：您向指導學生或同儕推薦系統化運用 AI 工具的意願（0-10分）', NULL, 1, 7, 0, 10, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (28, 3, 'Q8', 'TIME', '您每日進行深度思考、撰寫關鍵研究核心章節的黃金時段通常在？', NULL, 1, 8, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (29, 3, 'Q9', 'DATE', '您預計將本年度研究成果正式投稿至研討會或期刊的預定日期是？', NULL, 1, 9, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (30, 3, 'Q10', 'TEXT', '對於建構平衡「創新研發產能」與「學術誠信底線」之治理機制，您有哪些前瞻建言？（簡答題）', NULL, 0, 10, NULL, NULL, NOW(), NOW());


INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (21, 3, '192.168.1.10', 145, '2026-10-10 14:20:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (21, 21, '陳建宇', '男', '25-34歲', '0911222333', 'jy.chen@example.com', '資訊科技業', '臺北市信義區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (211, 21, 21, 35, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (212, 21, 22, 36, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (213, 21, 23, 38, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (214, 21, 23, 39, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (215, 21, 24, 42, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (216, 21, 25, 46, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (217, 21, 26, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (218, 21, 27, NULL, NULL, 10);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (219, 21, 28, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (220, 21, 29, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (221, 21, 30, NULL, '系統整體操作非常流暢，條件跳題在前端反應十分迅速，體驗超出預期！', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (22, 3, '192.168.1.11', 180, '2026-10-10 16:45:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (22, 22, '林雅婷', '女', '25-34歲', '0922333444', 'yt.lin@example.com', '金融保險業', '新北市板橋區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (222, 22, 21, 34, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (223, 22, 22, 37, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (224, 22, 24, 43, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (225, 22, 25, 46, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (226, 22, 25, 47, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (227, 22, 26, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (228, 22, 27, NULL, NULL, 10);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (229, 22, 28, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (230, 22, 29, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (231, 22, 30, NULL, '白色 SaaS 界面乾淨俐落，字體排版極佳，看起來非常舒服且專業。', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (23, 3, '192.168.1.12', 95, '2026-10-11 10:15:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (23, 23, '黃志偉', '男', '35-44歲', '0933444555', 'cw.huang@example.com', '電子科技業', '臺中市西屯區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (232, 23, 21, 34, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (233, 23, 22, 36, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (234, 23, 23, 38, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (235, 23, 23, 39, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (236, 23, 24, 44, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (237, 23, 25, 46, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (238, 23, 26, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (239, 23, 27, NULL, NULL, 10);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (240, 23, 28, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (241, 23, 29, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (242, 23, 30, NULL, '希望能支援更多匯出格式，目前 JSON 導出結構非常完整且符合標準。', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (24, 3, '192.168.1.13', 210, '2026-10-11 11:05:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (24, 24, '張淑芬', '女', '35-44歲', '0944555666', 'sf.chang@example.com', '製造業', '桃園市中壢區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (243, 24, 21, 35, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (244, 24, 22, 37, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (245, 24, 24, 45, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (246, 24, 25, 46, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (247, 24, 25, 47, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (248, 24, 26, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (249, 24, 27, NULL, NULL, 9);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (250, 24, 28, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (251, 24, 29, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (252, 24, 30, NULL, '後台數據分析報表很清楚，特別是 NPS 淨推薦值與受訪者人口輪廓，對決策非常有幫助。', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (25, 3, '192.168.1.14', 130, '2026-10-12 09:30:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (25, 25, '王俊賢', '男', '25-34歲', '0955666777', 'jx.wang@example.com', '專業服務業', '新竹市東區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (253, 25, 21, 34, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (254, 25, 22, 36, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (255, 25, 23, 38, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (256, 25, 23, 39, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (257, 25, 24, 42, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (258, 25, 25, 46, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (259, 25, 26, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (260, 25, 27, NULL, NULL, 9);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (261, 25, 28, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (262, 25, 29, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (263, 25, 30, NULL, '題型完整度很高，是非、單選、量表與時間日期一應俱全，真正符合企業級調研需求。', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (26, 3, '192.168.1.15', 160, '2026-10-12 15:50:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (26, 26, '李佩玲', '女', '25-34歲', '0966777888', 'pl.lee@example.com', '醫療保健業', '高雄市左營區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (264, 26, 21, 34, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (265, 26, 22, 37, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (266, 26, 24, 43, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (267, 26, 25, 46, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (268, 26, 25, 47, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (269, 26, 26, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (270, 26, 27, NULL, NULL, 8);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (271, 26, 28, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (272, 26, 29, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (273, 26, 30, NULL, '視覺設計清新專業，問卷大廳的分類選單篩選極為順手，整體完成度極高！', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (27, 3, '192.168.1.16', 85, '2026-10-13 13:10:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (27, 27, '郭庭安', '女', '18-24歲', '0977888999', 'ta.kuo@example.com', '教育學術界', '臺北市大安區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (274, 27, 21, 35, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (275, 27, 22, 36, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (276, 27, 23, 38, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (277, 27, 23, 39, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (278, 27, 24, 44, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (279, 27, 25, 46, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (280, 27, 26, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (281, 27, 27, NULL, NULL, 8);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (282, 27, 28, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (283, 27, 29, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (284, 27, 30, NULL, NULL, NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (28, 3, '192.168.1.17', 115, '2026-10-13 16:22:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (28, 28, '趙家豪', '男', '35-44歲', '0988999000', 'jh.chao@example.com', '交通物流業', '臺南市東區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (285, 28, 21, 34, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (286, 28, 22, 37, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (287, 28, 24, 45, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (288, 28, 25, 46, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (289, 28, 25, 47, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (290, 28, 26, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (291, 28, 27, NULL, NULL, 6);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (292, 28, 28, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (293, 28, 29, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (294, 28, 30, NULL, NULL, NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (29, 3, '192.168.1.18', 140, '2026-10-13 18:05:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (29, 29, '周雅婷', '女', '35-44歲', '0911000111', 'yt.chou@example.com', '批發零售業', '新北市中和區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (295, 29, 21, 34, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (296, 29, 22, 36, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (297, 29, 23, 38, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (298, 29, 23, 39, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (299, 29, 24, 42, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (300, 29, 25, 46, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (301, 29, 26, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (302, 29, 27, NULL, NULL, 6);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (303, 29, 28, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (304, 29, 29, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (305, 29, 30, NULL, NULL, NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (30, 3, '192.168.1.19', 125, '2026-10-13 20:45:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (30, 30, '劉建銘', '男', '45-54歲', '0922111222', 'cm.liu@example.com', '電子科技業', '新竹縣竹北市');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (306, 30, 21, 35, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (307, 30, 22, 37, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (308, 30, 24, 43, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (309, 30, 25, 46, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (310, 30, 25, 47, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (311, 30, 26, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (312, 30, 27, NULL, NULL, 6);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (313, 30, 28, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (314, 30, 29, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (315, 30, 30, NULL, NULL, NULL);

INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (31, 4, 'Q1', 'BOOLEAN', '您在本次封測期間，是否已實際透過專屬分享連結在行動裝置完成過填答？', NULL, 1, 1, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (50, 31, '是，在手機與平板端皆有進行完整流程實測', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (51, 31, '否，僅在桌機筆電端以大螢幕進行功能測試', 2, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (32, 4, 'Q2', 'SINGLE', '在操作次世代問卷設計器與圖表儀表板時，您是否曾遭遇畫面破版或繪圖卡頓？', 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?auto=format&fit=crop&w=800&q=80', 1, 2, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (52, 32, '是，曾遇到特定操作導致視窗震盪或元件破版', 1, 'Q3');


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (53, 32, '否，全程操作流暢高幀率，動畫過渡自然舒服', 2, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (33, 4, 'Q3', 'MULTI', '【解鎖題】發生介面異常時，主要集中在下列哪個情境？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (54, 33, '深黑主題與純白 SaaS 樣式切換瞬間的過渡色調', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (55, 33, '條件跳題即時動態計算觸發處之題目展開動效', 2, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (56, 33, '包含大量回答明細的數據匯出與圖表渲染區域', 3, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (57, 33, '密碼與電話輸入框自動填入時的邊界高亮光環', 4, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (34, 4, 'Q4', 'SINGLE', '您對全面還原之「現代清新白色 SaaS 風格」視覺層級觀感評估為？', 'https://images.unsplash.com/photo-1507238691740-187a5b1d37b8?auto=format&fit=crop&w=800&q=80', 1, 4, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (58, 34, '極具國際水準，文字對比清晰、資訊層次井然有序', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (59, 34, '簡約現代，完全契合專業企業級軟體操作直覺', 2, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (60, 34, '整體尚可，但部分陰影與圓角微動態可再細膩收斂', 3, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (61, 34, '較不習慣，個人偏好深色高對比 Cyberpunk 風格', 4, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (35, 4, 'Q5', 'MULTI', '在日常辦公福利與公設環境方面，您最常利用的空間有哪些？（複選）', NULL, 1, 5, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (62, 35, '專注膠囊工作間 (Focus Capsule Pod)', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (63, 35, '手沖咖啡吧台與多功能休閒交流區', 2, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (64, 35, '人體工學升降桌與雙螢幕配置工位', 3, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (65, 35, '內部專屬健身房與淋浴放鬆設施', 4, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (36, 4, 'Q6', 'RATING', '您對本次封閉測試版本的系統響應速度與穩定性整體評分（1最低，5最高）', NULL, 1, 6, 1, 5, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (37, 4, 'Q7', 'RATING', '淨推薦值 (NPS)：您向其他研發部門推薦導入此套問卷系統的意願（0-10分）', NULL, 1, 7, 0, 10, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (38, 4, 'Q8', 'TIME', '您認為每日系統發送「填答數據即時快訊推播」的最佳時間點是？', NULL, 1, 8, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (39, 4, 'Q9', 'DATE', '您期望本次內部封測專案正式對外公開發布 (GA) 的理想日期是？', NULL, 1, 9, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (40, 4, 'Q10', 'TEXT', '請留下您對此產品任何未竟之處的真心話或任何想許願的進階功能！（簡答題）', NULL, 0, 10, NULL, NULL, NOW(), NOW());


INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (31, 4, '192.168.1.10', 145, '2026-10-10 14:20:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (31, 31, '陳建宇', '男', '25-34歲', '0911222333', 'jy.chen@example.com', '資訊科技業', '臺北市信義區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (316, 31, 31, 51, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (317, 31, 32, 52, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (318, 31, 33, 54, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (319, 31, 33, 55, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (320, 31, 34, 58, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (321, 31, 35, 62, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (322, 31, 36, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (323, 31, 37, NULL, NULL, 10);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (324, 31, 38, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (325, 31, 39, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (326, 31, 40, NULL, '系統整體操作非常流暢，條件跳題在前端反應十分迅速，體驗超出預期！', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (32, 4, '192.168.1.11', 180, '2026-10-10 16:45:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (32, 32, '林雅婷', '女', '25-34歲', '0922333444', 'yt.lin@example.com', '金融保險業', '新北市板橋區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (327, 32, 31, 50, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (328, 32, 32, 53, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (329, 32, 34, 59, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (330, 32, 35, 62, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (331, 32, 35, 63, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (332, 32, 36, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (333, 32, 37, NULL, NULL, 10);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (334, 32, 38, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (335, 32, 39, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (336, 32, 40, NULL, '白色 SaaS 界面乾淨俐落，字體排版極佳，看起來非常舒服且專業。', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (33, 4, '192.168.1.12', 95, '2026-10-11 10:15:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (33, 33, '黃志偉', '男', '35-44歲', '0933444555', 'cw.huang@example.com', '電子科技業', '臺中市西屯區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (337, 33, 31, 50, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (338, 33, 32, 52, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (339, 33, 33, 54, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (340, 33, 33, 55, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (341, 33, 34, 60, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (342, 33, 35, 62, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (343, 33, 36, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (344, 33, 37, NULL, NULL, 10);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (345, 33, 38, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (346, 33, 39, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (347, 33, 40, NULL, '希望能支援更多匯出格式，目前 JSON 導出結構非常完整且符合標準。', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (34, 4, '192.168.1.13', 210, '2026-10-11 11:05:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (34, 34, '張淑芬', '女', '35-44歲', '0944555666', 'sf.chang@example.com', '製造業', '桃園市中壢區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (348, 34, 31, 51, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (349, 34, 32, 53, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (350, 34, 34, 61, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (351, 34, 35, 62, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (352, 34, 35, 63, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (353, 34, 36, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (354, 34, 37, NULL, NULL, 9);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (355, 34, 38, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (356, 34, 39, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (357, 34, 40, NULL, '後台數據分析報表很清楚，特別是 NPS 淨推薦值與受訪者人口輪廓，對決策非常有幫助。', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (35, 4, '192.168.1.14', 130, '2026-10-12 09:30:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (35, 35, '王俊賢', '男', '25-34歲', '0955666777', 'jx.wang@example.com', '專業服務業', '新竹市東區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (358, 35, 31, 50, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (359, 35, 32, 52, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (360, 35, 33, 54, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (361, 35, 33, 55, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (362, 35, 34, 58, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (363, 35, 35, 62, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (364, 35, 36, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (365, 35, 37, NULL, NULL, 9);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (366, 35, 38, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (367, 35, 39, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (368, 35, 40, NULL, '題型完整度很高，是非、單選、量表與時間日期一應俱全，真正符合企業級調研需求。', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (36, 4, '192.168.1.15', 160, '2026-10-12 15:50:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (36, 36, '李佩玲', '女', '25-34歲', '0966777888', 'pl.lee@example.com', '醫療保健業', '高雄市左營區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (369, 36, 31, 50, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (370, 36, 32, 53, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (371, 36, 34, 59, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (372, 36, 35, 62, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (373, 36, 35, 63, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (374, 36, 36, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (375, 36, 37, NULL, NULL, 8);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (376, 36, 38, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (377, 36, 39, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (378, 36, 40, NULL, '視覺設計清新專業，問卷大廳的分類選單篩選極為順手，整體完成度極高！', NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (37, 4, '192.168.1.16', 85, '2026-10-13 13:10:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (37, 37, '郭庭安', '女', '18-24歲', '0977888999', 'ta.kuo@example.com', '教育學術界', '臺北市大安區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (379, 37, 31, 51, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (380, 37, 32, 52, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (381, 37, 33, 54, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (382, 37, 33, 55, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (383, 37, 34, 60, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (384, 37, 35, 62, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (385, 37, 36, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (386, 37, 37, NULL, NULL, 8);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (387, 37, 38, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (388, 37, 39, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (389, 37, 40, NULL, NULL, NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (38, 4, '192.168.1.17', 115, '2026-10-13 16:22:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (38, 38, '趙家豪', '男', '35-44歲', '0988999000', 'jh.chao@example.com', '交通物流業', '臺南市東區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (390, 38, 31, 50, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (391, 38, 32, 53, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (392, 38, 34, 61, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (393, 38, 35, 62, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (394, 38, 35, 63, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (395, 38, 36, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (396, 38, 37, NULL, NULL, 6);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (397, 38, 38, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (398, 38, 39, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (399, 38, 40, NULL, NULL, NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (39, 4, '192.168.1.18', 140, '2026-10-13 18:05:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (39, 39, '周雅婷', '女', '35-44歲', '0911000111', 'yt.chou@example.com', '批發零售業', '新北市中和區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (400, 39, 31, 50, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (401, 39, 32, 52, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (402, 39, 33, 54, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (403, 39, 33, 55, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (404, 39, 34, 58, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (405, 39, 35, 62, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (406, 39, 36, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (407, 39, 37, NULL, NULL, 6);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (408, 39, 38, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (409, 39, 39, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (410, 39, 40, NULL, NULL, NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (40, 4, '192.168.1.19', 125, '2026-10-13 20:45:00');


INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (40, 40, '劉建銘', '男', '45-54歲', '0922111222', 'cm.liu@example.com', '電子科技業', '新竹縣竹北市');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (411, 40, 31, 51, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (412, 40, 32, 53, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (413, 40, 34, 59, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (414, 40, 35, 62, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (415, 40, 35, 63, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (416, 40, 36, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (417, 40, 37, NULL, NULL, 6);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (418, 40, 38, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (419, 40, 39, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (420, 40, 40, NULL, NULL, NULL);