import os

schema_sql = """-- ============================================================================
-- 專案名稱: SurveyFlow (企業級問卷調查與智慧數據分析系統)
-- 檔案名稱: 01_schema.sql
-- 責任描述: MySQL 8.x 資料庫全表 DDL 腳本，嚴格遵循第三正規化 (3NF) 與高效聚合索引
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
    `cover_image_url` VARCHAR(500) DEFAULT NULL COMMENT '問卷主視覺封面圖片網址 (Unsplash)',
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
    `image_url` VARCHAR(500) DEFAULT NULL COMMENT '題目參考插圖網址 (Unsplash)',
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
"""

# 預設資料生成
seed_sql_header = """-- ============================================================================
-- 專案名稱: SurveyFlow
-- 檔案名稱: 02_seed_data.sql
-- 責任描述: 注入極具商業價值、學術研究與生活日常統計意義之完整問卷與真實受訪者數據
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
"""

# 定義 4 份問卷各自的 10 題與 Unsplash 插圖
surveys_questions_config = [
    # Survey 1 (商業調研)
    [
        (1, "Q1", "BOOLEAN", "貴公司目前是否已將核心業務系統遷移至雲端微服務架構？", "https://images.unsplash.com/photo-1587560699334-cc4ff634909a?auto=format&fit=crop&w=800&q=80", True, 1, None, None, [
            ("是，已全面雲原生化 (Cloud-Native)", 1, None),
            ("否，仍維持自建機房地端部署 (On-Premise)", 2, None)
        ]),
        (2, "Q2", "SINGLE", "在採購企業級 SaaS 服務時，您是否曾遭遇單一簽入 (SSO) 或跨國法規合規阻礙？", None, True, 2, None, None, [
            ("經常遇到，合規審查週期平均超過三個月", 1, "Q3"),
            ("偶爾遇到，原廠資安白皮書尚可滿足要求", 2, None),
            ("從未遇到，現有採購流程極為敏捷", 3, None)
        ]),
        (3, "Q3", "MULTI", "【解鎖題】針對海外 SaaS 採購，團隊最重視下列哪些資安合規認證？（複選）", None, False, 3, None, None, [
            ("SOC 2 Type II 獨立第三方稽核認證", 1, None),
            ("ISO/IEC 27001 資訊安全管理體系", 2, None),
            ("GDPR 歐盟一般資料保護規則遵循", 3, None),
            ("HIPAA / 金融級法規相容資料加密", 4, None)
        ]),
        (4, "Q4", "SINGLE", "貴公司預計投入於數位協作工具之年度預算成長幅度為何？", "https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?auto=format&fit=crop&w=800&q=80", True, 4, None, None, [
            ("預算持平或小幅縮減 (0% 以下)", 1, None),
            ("溫和成長 (1% ~ 10%)", 2, None),
            ("大幅擴張 (11% ~ 25%)", 3, None),
            ("戰略級投入 (超過 25%)", 4, None)
        ]),
        (5, "Q5", "MULTI", "在團隊日常非同步協作中，下列哪些自動化機制為您節省最多時間？（複選）", None, True, 5, None, None, [
            ("整合 Slack / Teams 之自動化審批通知機器人", 1, None),
            ("非同步錄影螢幕解說工具 (Loom / ScreenREC)", 2, None),
            ("跨專案看板即時進度儀表板 (Jira / Linear)", 3, None),
            ("雲端白板腦力激盪空間 (Miro / FigJam)", 4, None)
        ]),
        (6, "Q6", "RATING", "整體而言，您對目前市面上企業協作軟體的「資安透明度」滿意度（1最低，5最高）", None, True, 6, 1, 5, []),
        (7, "Q7", "RATING", "淨推薦值 (NPS)：您向同業主管推薦採用現代雲端 SaaS 架構的意願（0-10分）", None, True, 7, 0, 10, []),
        (8, "Q8", "TIME", "您每日處理非同步跨國訊息與郵件效率最高的時間點是？", None, True, 8, None, None, []),
        (9, "Q9", "DATE", "貴部門下次預計啟動年度資訊架構審查 (IT Audit) 的具體日期是？", None, True, 9, None, None, []),
        (10, "Q10", "TEXT", "請分享您認為企業級軟體在「跨時區協作」與「權限控管」上最急需突破的瓶頸是什麼？（簡答題）", None, False, 10, None, None, [])
    ],
    # Survey 2 (商業消費)
    [
        (1, "Q1", "BOOLEAN", "您過去三個月內是否曾在行動端 App 或品牌官網完成購物下單？", None, True, 1, None, None, [
            ("是，每月平均線上下單 2 次以上", 1, None),
            ("否，多數消費仍偏好實體門市體驗", 2, None)
        ]),
        (2, "Q2", "SINGLE", "在購物結帳的關鍵最後一步，您是否曾因支付介面跳轉繁瑣而放棄購物車？", "https://images.unsplash.com/photo-1556742049-0a67c5574f73?auto=format&fit=crop&w=800&q=80", True, 2, None, None, [
            ("是，曾因 OTP 簡訊延遲或多次驗證失敗放棄結帳", 1, "Q3"),
            ("否，每次結帳皆能順暢在 1 分鐘內完成扣款", 2, None)
        ]),
        (3, "Q3", "MULTI", "【解鎖題】遭遇結帳阻礙時，平台提供哪些快捷支付能吸引您完成購買？（複選）", None, False, 3, None, None, [
            ("Apple Pay / Google Pay 一鍵生物指紋辨識", 1, None),
            ("LINE Pay 快速自動跳轉授權折抵", 2, None),
            ("超商取貨付款安心代收服務", 3, None),
            ("街口支付即時點數現金回饋折抵", 4, None)
        ]),
        (4, "Q4", "SINGLE", "您在決定是否回購該電商平台時，最核心的考量要素是？", None, True, 4, None, None, [
            ("價格優惠與會員常態全館免運門檻", 1, None),
            ("當日或 24 小時極速到貨配送服務", 2, None),
            ("商品退換貨流程是否完全免手續費且不刁難", 3, None),
            ("商品正品保證與原廠完整售後維修", 4, None)
        ]),
        (5, "Q5", "MULTI", "您平時在線上平台最經常選購的商品類別有哪些？（複選）", "https://images.unsplash.com/photo-1441986300917-64674bd600d8?auto=format&fit=crop&w=800&q=80", True, 5, None, None, [
            ("3C 數碼配件與電腦周邊硬體", 1, None),
            ("生活日用品與家庭消耗快消品", 2, None),
            ("潮流服飾、休閒鞋包與戶外裝備", 3, None),
            ("低溫生鮮食材與各地名特產美食", 4, None)
        ]),
        (6, "Q6", "RATING", "您對本電商平台「智慧搜尋推薦與篩選精準度」滿意度（1最低，5最高）", None, True, 6, 1, 5, []),
        (7, "Q7", "RATING", "淨推薦值 (NPS)：您向親友推薦在本商城購物的意願（0-10分）", None, True, 7, 0, 10, []),
        (8, "Q8", "TIME", "您最習慣瀏覽線上促銷特賣或領取優惠券的黃金時段通常在？", None, True, 8, None, None, []),
        (9, "Q9", "DATE", "您最近一次在線上完成購物的具體下單日期是？", None, True, 9, None, None, []),
        (10, "Q10", "TEXT", "針對我們在包裹環保包裝、出貨追蹤動態或客服回覆速度，您有何寶貴建議？（簡答題）", None, False, 10, None, None, [])
    ],
    # Survey 3 (學術研究)
    [
        (1, "Q1", "BOOLEAN", "您在進行文獻檢索、數據統計或論文草稿撰寫時，是否已常態使用大型語言模型？", "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?auto=format&fit=crop&w=800&q=80", True, 1, None, None, [
            ("是，已深度融入日常研究與教學管線", 1, None),
            ("否，因學術誠信疑慮或個人習慣未廣泛使用", 2, None)
        ]),
        (2, "Q2", "SINGLE", "您所屬的學術機構或系所，目前是否已正式發布「生成式 AI 使用指引與揭露規範」？", None, True, 2, None, None, [
            ("是，已有明確規範，且要求投稿前必須於致謝或方法段揭露", 1, "Q3"),
            ("否，目前尚無正式指引，全憑研究者自主自律", 2, None)
        ]),
        (3, "Q3", "MULTI", "【解鎖題】在提交論文審查時，您採取了哪些機制驗證 AI 輔助內容的真實性？（複選）", None, False, 3, None, None, [
            ("逐篇核對 DOI 原始文獻出處，杜絕 AI 幻覺虛構引用", 1, None),
            ("使用 Turnitin / iThenticate 進行嚴格學術抄襲率檢驗", 2, None),
            ("重跑所有 Python / R 實驗統計代碼驗證復現性", 3, None),
            ("邀請同行專家進行匿名 Double-Blind Peer Review", 4, None)
        ]),
        (4, "Q4", "SINGLE", "您認為目前 AI 工具對您研發產能節省的工時比例約為？", "https://images.unsplash.com/photo-1517694712202-14dd9538aa97?auto=format&fit=crop&w=800&q=80", True, 4, None, None, [
            ("節省少於 10% 工時，仍需投入大量校對時間", 1, None),
            ("節省 11% ~ 30% 工時，在基礎語法與格式排版助益顯著", 2, None),
            ("節省 31% ~ 50% 工時，大幅加速文獻閱讀與實驗腳本建置", 3, None),
            ("節省超過 50% 工時，研發產能呈現倍數成長", 4, None)
        ]),
        (5, "Q5", "MULTI", "AI 工具在下列哪些研究環節為您帶來最具突破性的實質幫助？（複選）", None, True, 5, None, None, [
            ("快速閱讀跨領域數十篇文獻並提煉核心方法論", 1, None),
            ("輔助編寫複雜演算法、數據清理腳本與正則表達式", 2, None),
            ("潤飾英文學術論文之句構流暢度與專業詞彙", 3, None),
            ("設計實驗假設與多維度研究問卷題項構面", 4, None)
        ]),
        (6, "Q6", "RATING", "您對當前主流學術 AI 工具「理論推導邏輯嚴謹度」的評分（1最低，5最高）", None, True, 6, 1, 5, []),
        (7, "Q7", "RATING", "淨推薦值 (NPS)：您向指導學生或同儕推薦系統化運用 AI 工具的意願（0-10分）", None, True, 7, 0, 10, []),
        (8, "Q8", "TIME", "您每日進行深度思考、撰寫關鍵研究核心章節的黃金時段通常在？", None, True, 8, None, None, []),
        (9, "Q9", "DATE", "您預計將本年度研究成果正式投稿至研討會或期刊的預定日期是？", None, True, 9, None, None, []),
        (10, "Q10", "TEXT", "對於建構平衡「創新研發產能」與「學術誠信底線」之治理機制，您有哪些前瞻建言？（簡答題）", None, False, 10, None, None, [])
    ],
    # Survey 4 (日常統計與內部封測)
    [
        (1, "Q1", "BOOLEAN", "您在本次封測期間，是否已實際透過專屬分享連結在行動裝置完成過填答？", None, True, 1, None, None, [
            ("是，在手機與平板端皆有進行完整流程實測", 1, None),
            ("否，僅在桌機筆電端以大螢幕進行功能測試", 2, None)
        ]),
        (2, "Q2", "SINGLE", "在操作次世代問卷設計器與圖表儀表板時，您是否曾遭遇畫面破版或繪圖卡頓？", "https://images.unsplash.com/photo-1551288049-bebda4e38f71?auto=format&fit=crop&w=800&q=80", True, 2, None, None, [
            ("是，曾遇到特定操作導致視窗震盪或元件破版", 1, "Q3"),
            ("否，全程操作流暢高幀率，動畫過渡自然舒服", 2, None)
        ]),
        (3, "Q3", "MULTI", "【解鎖題】發生介面異常時，主要集中在下列哪個情境？（複選）", None, False, 3, None, None, [
            ("深黑主題與純白 SaaS 樣式切換瞬間的過渡色調", 1, None),
            ("條件跳題即時動態計算觸發處之題目展開動效", 2, None),
            ("包含大量回答明細的數據匯出與圖表渲染區域", 3, None),
            ("密碼與電話輸入框自動填入時的邊界高亮光環", 4, None)
        ]),
        (4, "Q4", "SINGLE", "您對全面還原之「現代清新白色 SaaS 風格」視覺層級觀感評估為？", "https://images.unsplash.com/photo-1507238691740-187a5b1d37b8?auto=format&fit=crop&w=800&q=80", True, 4, None, None, [
            ("極具國際水準，文字對比清晰、資訊層次井然有序", 1, None),
            ("簡約現代，完全契合專業企業級軟體操作直覺", 2, None),
            ("整體尚可，但部分陰影與圓角微動態可再細膩收斂", 3, None),
            ("較不習慣，個人偏好深色高對比 Cyberpunk 風格", 4, None)
        ]),
        (5, "Q5", "MULTI", "在日常辦公福利與公設環境方面，您最常利用的空間有哪些？（複選）", None, True, 5, None, None, [
            ("專注膠囊工作間 (Focus Capsule Pod)", 1, None),
            ("手沖咖啡吧台與多功能休閒交流區", 2, None),
            ("人體工學升降桌與雙螢幕配置工位", 3, None),
            ("內部專屬健身房與淋浴放鬆設施", 4, None)
        ]),
        (6, "Q6", "RATING", "您對本次封閉測試版本的系統響應速度與穩定性整體評分（1最低，5最高）", None, True, 6, 1, 5, []),
        (7, "Q7", "RATING", "淨推薦值 (NPS)：您向其他研發部門推薦導入此套問卷系統的意願（0-10分）", None, True, 7, 0, 10, []),
        (8, "Q8", "TIME", "您認為每日系統發送「填答數據即時快訊推播」的最佳時間點是？", None, True, 8, None, None, []),
        (9, "Q9", "DATE", "您期望本次內部封測專案正式對外公開發布 (GA) 的理想日期是？", None, True, 9, None, None, []),
        (10, "Q10", "TEXT", "請留下您對此產品任何未竟之處的真心話或任何想許願的進階功能！（簡答題）", None, False, 10, None, None, [])
    ]
]

# 10 位受訪者真實輪廓資料池 (前 6 位有豐富深度建議，後 4 位文字簡答留空)
respondents_pool = [
    ("陳建宇", "男", "25-34歲", "0911222333", "jy.chen@example.com", "資訊科技業", "臺北市信義區", 145, "2026-10-10 14:20:00", True, "系統整體操作非常流暢，條件跳題在前端反應十分迅速，體驗超出預期！"),
    ("林雅婷", "女", "25-34歲", "0922333444", "yt.lin@example.com", "金融保險業", "新北市板橋區", 180, "2026-10-10 16:45:00", True, "白色 SaaS 界面乾淨俐落，字體排版極佳，看起來非常舒服且專業。"),
    ("黃志偉", "男", "35-44歲", "0933444555", "cw.huang@example.com", "電子科技業", "臺中市西屯區", 95, "2026-10-11 10:15:00", True, "希望能支援更多匯出格式，目前 JSON 導出結構非常完整且符合標準。"),
    ("張淑芬", "女", "35-44歲", "0944555666", "sf.chang@example.com", "製造業", "桃園市中壢區", 210, "2026-10-11 11:05:00", True, "後台數據分析報表很清楚，特別是 NPS 淨推薦值與受訪者人口輪廓，對決策非常有幫助。"),
    ("王俊賢", "男", "25-34歲", "0955666777", "jx.wang@example.com", "專業服務業", "新竹市東區", 130, "2026-10-12 09:30:00", True, "題型完整度很高，是非、單選、量表與時間日期一應俱全，真正符合企業級調研需求。"),
    ("李佩玲", "女", "25-34歲", "0966777888", "pl.lee@example.com", "醫療保健業", "高雄市左營區", 160, "2026-10-12 15:50:00", True, "視覺設計清新專業，問卷大廳的分類選單篩選極為順手，整體完成度極高！"),
    ("郭庭安", "女", "18-24歲", "0977888999", "ta.kuo@example.com", "教育學術界", "臺北市大安區", 85, "2026-10-13 13:10:00", False, ""),
    ("趙家豪", "男", "35-44歲", "0988999000", "jh.chao@example.com", "交通物流業", "臺南市東區", 115, "2026-10-13 16:22:00", False, ""),
    ("周雅婷", "女", "35-44歲", "0911000111", "yt.chou@example.com", "批發零售業", "新北市中和區", 140, "2026-10-13 18:05:00", False, ""),
    ("劉建銘", "男", "45-54歲", "0922111222", "cm.liu@example.com", "電子科技業", "新竹縣竹北市", 125, "2026-10-13 20:45:00", False, ""),
]

lines = [seed_sql_header]

q_global_id = 1
opt_global_id = 1
sub_global_id = 1
basic_global_id = 1
ans_global_id = 1

q_opt_map = {}

for survey_id in range(1, 5):
    q_list = surveys_questions_config[survey_id - 1]
    survey_q_ids = []
    
    # 建立題目與選項
    for (q_idx, code, q_type, text, img_url, is_req, order, min_r, max_r, options) in q_list:
        img_val = f"'{img_url}'" if img_url else "NULL"
        lines.append(f"""
INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES ({q_global_id}, {survey_id}, '{code}', '{q_type}', '{text}', {img_val}, {1 if is_req else 0}, {order}, {min_r if min_r is not None else 'NULL'}, {max_r if max_r is not None else 'NULL'}, NOW(), NOW());
""")
        current_qid = q_global_id
        survey_q_ids.append(current_qid)
        q_opt_map[current_qid] = []
        q_global_id += 1
        
        for (opt_text, opt_order, trig_code) in options:
            trig_val = f"'{trig_code}'" if trig_code else "NULL"
            lines.append(f"""
INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES ({opt_global_id}, {current_qid}, '{opt_text}', {opt_order}, {trig_val});
""")
            q_opt_map[current_qid].append(opt_global_id)
            opt_global_id += 1

    # 建立 10 筆填答資料
    for resp_idx, resp in enumerate(respondents_pool):
        (name, gender, age_group, phone, email, industry, residence, dur, sub_time, has_text, text_feedback) = resp
        
        cur_sub_id = sub_global_id
        lines.append(f"""
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES ({cur_sub_id}, {survey_id}, '192.168.1.{10 + resp_idx}', {dur}, '{sub_time}');
""")
        sub_global_id += 1
        
        lines.append(f"""
INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES ({basic_global_id}, {cur_sub_id}, '{name}', '{gender}', '{age_group}', '{phone}', '{email}', '{industry}', '{residence}');
""")
        basic_global_id += 1
        
        # Q1: BOOLEAN
        q1_id = survey_q_ids[0]
        q1_opts = q_opt_map[q1_id]
        chosen_opt1 = q1_opts[0] if (resp_idx % 3 != 0) else q1_opts[1]
        lines.append(f"INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES ({ans_global_id}, {cur_sub_id}, {q1_id}, {chosen_opt1}, NULL, NULL);")
        ans_global_id += 1
        
        # Q2: SINGLE
        q2_id = survey_q_ids[1]
        q2_opts = q_opt_map[q2_id]
        chosen_opt2 = q2_opts[0] if (resp_idx % 2 == 0) else (q2_opts[1] if len(q2_opts) > 1 else q2_opts[0])
        lines.append(f"INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES ({ans_global_id}, {cur_sub_id}, {q2_id}, {chosen_opt2}, NULL, NULL);")
        ans_global_id += 1
        
        # Q3: MULTI (解鎖題)
        q3_id = survey_q_ids[2]
        q3_opts = q_opt_map[q3_id]
        if resp_idx % 2 == 0:
            lines.append(f"INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES ({ans_global_id}, {cur_sub_id}, {q3_id}, {q3_opts[0]}, NULL, NULL);")
            ans_global_id += 1
            if len(q3_opts) > 1:
                lines.append(f"INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES ({ans_global_id}, {cur_sub_id}, {q3_id}, {q3_opts[1]}, NULL, NULL);")
                ans_global_id += 1
        
        # Q4: SINGLE
        q4_id = survey_q_ids[3]
        q4_opts = q_opt_map[q4_id]
        chosen_opt4 = q4_opts[resp_idx % len(q4_opts)]
        lines.append(f"INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES ({ans_global_id}, {cur_sub_id}, {q4_id}, {chosen_opt4}, NULL, NULL);")
        ans_global_id += 1
        
        # Q5: MULTI
        q5_id = survey_q_ids[4]
        q5_opts = q_opt_map[q5_id]
        lines.append(f"INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES ({ans_global_id}, {cur_sub_id}, {q5_id}, {q5_opts[0]}, NULL, NULL);")
        ans_global_id += 1
        if len(q5_opts) > 1 and resp_idx % 2 != 0:
            lines.append(f"INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES ({ans_global_id}, {cur_sub_id}, {q5_id}, {q5_opts[1]}, NULL, NULL);")
            ans_global_id += 1
            
        # Q6: RATING (1-5)
        q6_id = survey_q_ids[5]
        r6 = 5 if resp_idx % 3 == 0 else (4 if resp_idx % 2 == 0 else 3)
        lines.append(f"INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES ({ans_global_id}, {cur_sub_id}, {q6_id}, NULL, NULL, {r6});")
        ans_global_id += 1
        
        # Q7: RATING (0-10 NPS)
        q7_id = survey_q_ids[6]
        r7 = 10 if resp_idx in [0, 1, 2] else (9 if resp_idx in [3, 4] else (8 if resp_idx in [5, 6] else 6))
        lines.append(f"INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES ({ans_global_id}, {cur_sub_id}, {q7_id}, NULL, NULL, {r7});")
        ans_global_id += 1
        
        # Q8: TIME
        q8_id = survey_q_ids[7]
        lines.append(f"INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES ({ans_global_id}, {cur_sub_id}, {q8_id}, NULL, '09:30', NULL);")
        ans_global_id += 1
        
        # Q9: DATE
        q9_id = survey_q_ids[8]
        lines.append(f"INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES ({ans_global_id}, {cur_sub_id}, {q9_id}, NULL, '2026-11-15', NULL);")
        ans_global_id += 1
        
        # Q10: TEXT (簡答題)
        q10_id = survey_q_ids[9]
        if has_text and text_feedback:
            lines.append(f"INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES ({ans_global_id}, {cur_sub_id}, {q10_id}, NULL, '{text_feedback}', NULL);")
        else:
            lines.append(f"INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES ({ans_global_id}, {cur_sub_id}, {q10_id}, NULL, NULL, NULL);")
        ans_global_id += 1

seed_sql_content = "\n".join(lines)

db_dir = "/Users/hsuche-cheng/Documents/project/survey-flow/backend/src/main/resources/db"
os.makedirs(db_dir, exist_ok=True)

# 1. 寫入 01_schema.sql
with open(os.path.join(db_dir, "01_schema.sql"), "w", encoding="utf-8") as f:
    f.write(schema_sql)

# 2. 寫入 02_seed_data.sql
with open(os.path.join(db_dir, "02_seed_data.sql"), "w", encoding="utf-8") as f:
    f.write(seed_sql_content)

# 3. 寫入 reset_database.sql (01 + 02 完整組合，單一檔案即可重置完整資料庫)
reset_sql = f"""-- ============================================================================
-- 專案名稱: SurveyFlow (企業級問卷調查與智慧數據分析系統)
-- 檔案名稱: reset_database.sql
-- 責任描述: 【單一完整資料庫重置檔案】執行此單一檔案即可一次性完全重置資料表結構與 40 筆預設數據
-- 使用方式: mysql -u root -p survey_flow < reset_database.sql
-- ============================================================================

{schema_sql}

{seed_sql_content}
"""

with open(os.path.join(db_dir, "reset_database.sql"), "w", encoding="utf-8") as f:
    f.write(reset_sql)

# 5. 清理舊有模糊與過時的過渡腳本檔案
old_files_to_remove = [
    os.path.join(db_dir, "seed_10q_database.sql"),
    os.path.join(db_dir, "seed_10q_generator.py"),
    os.path.join(db_dir, "seed_all_types.sql")
]
for old_path in old_files_to_remove:
    if os.path.exists(old_path):
        os.remove(old_path)
        print(f"Removed legacy file: {os.path.basename(old_path)}")

print("Database SQL files generated successfully in db/ directory!")
