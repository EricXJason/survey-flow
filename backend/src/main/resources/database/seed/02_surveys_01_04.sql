-- ============================================================================
-- 檔案名稱: 02_surveys_01_04.sql
-- 責任描述: 示範種子資料 02 / 05：testuser 擁有之問卷 1~4：題目、選項、基本資料收集設定與 40 份回收、逐題作答
-- 執行順序: schema.sql → seed/01 → 02 → 03 → 04 → 05 (完整重置)；
--           02 ~ 05 開頭會先刪除本檔負責之 ID 範圍，中途失敗時只需重跑該檔；本檔依賴 01 (會員)
-- 執行方式: Spring Boot (spring.sql.init.data-locations 依序列出)、整合測試、E2E，或於 TiDB / MySQL 依序貼上執行
-- 邊界:     只用於初始化或重建示範環境；全部為單筆 INSERT，網頁版 SQL Editor 也能完整執行
-- 字元編碼: utf8mb4 / utf8mb4_unicode_ci
-- ============================================================================

USE `survey_flow`;

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
-- [Why]: 各檔之間有外鍵參照 (例如 04 的作答參照 03 的選項)，暫停外鍵檢查讓單一檔案可獨立重跑；檔尾恢復
SET FOREIGN_KEY_CHECKS = 0;

-- 先刪除本檔負責之資料範圍 (子表在前)，因此可單獨重跑而不產生重複
DELETE FROM `submission_answers` WHERE `id` BETWEEN 1 AND 410;
DELETE FROM `submission_basic_infos` WHERE `id` BETWEEN 1 AND 30;
DELETE FROM `submissions` WHERE `id` BETWEEN 1 AND 40;
DELETE FROM `survey_options` WHERE `id` BETWEEN 1 AND 65;
DELETE FROM `survey_questions` WHERE `id` BETWEEN 1 AND 40;
DELETE FROM `survey_basic_info_configs` WHERE `survey_id` BETWEEN 1 AND 4;
DELETE FROM `surveys` WHERE `id` BETWEEN 1 AND 4;

-- -------------------------------------------------------------
-- 建立 4 筆問卷專案 (testuser 擁有)
-- -------------------------------------------------------------
INSERT INTO `surveys` (`id`, `user_id`, `title`, `description`, `cover_image_url`, `category`, `is_public_to_lobby`, `is_open`, `start_date`, `end_date`, `share_token`, `view_count`, `created_at`, `updated_at`) VALUES (1, 2, '2026 企業級 SaaS 雲端平臺使用者體驗與採購決策調研', '【商業統計】本調研旨在深入探討跨國企業在導入現代化雲端軟體時之資安合規、非同步協作效能與年費定價決策考量，為產品路線圖提供實質商業依據。', 'https://images.unsplash.com/photo-1522071820081-009f0129c71c?auto=format&fit=crop&w=1200&q=80', '產品體驗研究', 1, 1, '2026-01-01 00:00:00', NULL, 'kPL6FRh2idiOuIO-tF3naA', 168, NOW(), NOW());
INSERT INTO `surveys` (`id`, `user_id`, `title`, `description`, `cover_image_url`, `category`, `is_public_to_lobby`, `is_open`, `start_date`, `end_date`, `share_token`, `view_count`, `created_at`, `updated_at`) VALUES (2, 2, '2026 智慧零售與跨境電商消費者回購滿意度調查', '【商業消費】全面分析線上購物者對於多元行動支付（Apple Pay/LINE Pay）、物流配送時效、智慧客服滿意度與品牌忠誠度之關鍵指標。', 'https://images.unsplash.com/photo-1472851294608-062f824d29cc?auto=format&fit=crop&w=1200&q=80', '顧客滿意度', 1, 1, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'DlkqIUt1kXHKox38PgmlHw', 245, NOW(), NOW());
INSERT INTO `surveys` (`id`, `user_id`, `title`, `description`, `cover_image_url`, `category`, `is_public_to_lobby`, `is_open`, `start_date`, `end_date`, `share_token`, `view_count`, `created_at`, `updated_at`) VALUES (3, 2, '2026 生成式 AI 對大專院校研發產能與學術誠信影響評測', '【學術研究】針對全臺大專院校教師、研究生與技術研發人員，探討 LLM 輔助寫碼與論文閱讀對學術創新產能之推升程度及倫理審查現況。', 'https://images.unsplash.com/photo-1555066931-4365d14bab8c?auto=format&fit=crop&w=1200&q=80', '市場商業調研', 1, 1, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'EXV9Cdp6dDYhBrzAFjIgKA', 312, NOW(), NOW());
INSERT INTO `surveys` (`id`, `user_id`, `title`, `description`, `cover_image_url`, `category`, `is_public_to_lobby`, `is_open`, `start_date`, `end_date`, `share_token`, `view_count`, `created_at`, `updated_at`) VALUES (4, 2, '【封測專案／非公開】次世代 SurveyFlow 產品體驗與公設福利統計', '【日常統計】本問卷為封閉測試問卷（未發布至大廳，持專屬直達連結可填答），蒐集團隊對辦公室環境、健身公設利用率與全新儀表板之第一手體驗反饋。', 'https://images.unsplash.com/photo-1460925895917-afdab827c52f?auto=format&fit=crop&w=1200&q=80', '綜合調查', 0, 0, '2026-01-01 00:00:00', NULL, 'F13AaTxNStY76jtM2sUK5w', 58, NOW(), NOW());

-- 基本資料收集設定 (針對 4 份問卷規劃多元情境: 職場/通訊完整、只用一個、教育/學校背景、一個不用)
-- 問卷 1 (企業級 SaaS 雲端調研): 職場組織與通訊導向 (不收集居住地區)
INSERT INTO `survey_basic_info_configs` (`survey_id`, `collect_basic_info`, `require_name`, `require_gender`, `require_age`, `require_birthday`, `require_national_id`, `require_phone`, `require_home_phone`, `require_email`, `require_industry`, `require_job_title`, `require_department`, `require_employee_id`, `require_academic_major`, `require_class_name`, `require_student_id`, `require_height`, `require_weight`, `require_residence`, `require_registered_address`) VALUES (1, 1, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0);
-- 問卷 2 (智慧零售消費者回購): 「只用一個」極簡模式 (只收集姓名，其餘免填)
INSERT INTO `survey_basic_info_configs` (`survey_id`, `collect_basic_info`, `require_name`, `require_gender`, `require_age`, `require_birthday`, `require_national_id`, `require_phone`, `require_home_phone`, `require_email`, `require_industry`, `require_job_title`, `require_department`, `require_employee_id`, `require_academic_major`, `require_class_name`, `require_student_id`, `require_height`, `require_weight`, `require_residence`, `require_registered_address`) VALUES (2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
-- 問卷 3 (大專院校學術誠信評測): 學校與教育背景導向 (姓名、性別、信箱、科系、班級、學號)
INSERT INTO `survey_basic_info_configs` (`survey_id`, `collect_basic_info`, `require_name`, `require_gender`, `require_age`, `require_birthday`, `require_national_id`, `require_phone`, `require_home_phone`, `require_email`, `require_industry`, `require_job_title`, `require_department`, `require_employee_id`, `require_academic_major`, `require_class_name`, `require_student_id`, `require_height`, `require_weight`, `require_residence`, `require_registered_address`) VALUES (3, 1, 1, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0);
-- 問卷 4 (次世代產品封測福利統計): 「一個不用」完全匿名模式 (collect_basic_info=0，受訪者完全免填)
INSERT INTO `survey_basic_info_configs` (`survey_id`, `collect_basic_info`, `require_name`, `require_gender`, `require_age`, `require_birthday`, `require_national_id`, `require_phone`, `require_home_phone`, `require_email`, `require_industry`, `require_job_title`, `require_department`, `require_employee_id`, `require_academic_major`, `require_class_name`, `require_student_id`, `require_height`, `require_weight`, `require_residence`, `require_registered_address`) VALUES (4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);


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
VALUES (1, 1, '陳建宇', 1,  '25-34歲', '0911222333', 'jy.chen@example.com', '資訊科技業', NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (1, 1, 1, 1, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (2, 1, 2, 3, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (3, 1, 3, 6, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (4, 1, 3, 7, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (5, 1, 4, 12, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (6, 1, 5, 14, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (7, 1, 6, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (8, 1, 7, NULL, NULL, 9);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (9, 1, 8, NULL, '10:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (10, 1, 9, NULL, '2026-11-20', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (11, 1, 10, NULL, '希望 SSO 能支援 SAML 2.0 與 OIDC 動態群組映射，海外跨區連線延遲仍可再優化。', NULL);

-- 提交 2: 秒殺亂填者 (duration 僅 5 秒，量表全給 1 分差評，簡答亂填問號)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (2, 1, '192.168.1.11', 5, '2026-10-10 16:45:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (2, 2, '訪客', 3, '18-24歲', '0900000000', 'test@test.com', '學生', NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (12, 2, 1, 2, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (13, 2, 2, 5, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (14, 2, 4, 10, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (15, 2, 5, 14, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (17, 2, 6, NULL, NULL, 1);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (18, 2, 7, NULL, NULL, 0);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (19, 2, 8, NULL, '00:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (20, 2, 9, NULL, '2026-10-01', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (21, 2, 10, NULL, '？？？', NULL);

-- 提交 3: 快速抽獎刷題者 (duration 8 秒，量表全滿分 5 分，簡答填快點送出抽獎)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (3, 1, '192.168.1.12', 8, '2026-10-11 10:15:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (3, 3, '路人甲', 1,  '25-34歲', '0987654321', NULL, '自由業', NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (22, 3, 1, 1, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (23, 3, 2, 4, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (26, 3, 4, 13, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (27, 3, 5, 14, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (28, 3, 6, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (29, 3, 7, NULL, NULL, 10);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (30, 3, 8, NULL, '12:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (31, 3, 9, NULL, '2026-10-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (32, 3, 10, NULL, '快點送出抽獎，字太多了', NULL);

-- 提交 4: 正常主管填答
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (4, 1, '192.168.1.13', 190, '2026-10-11 11:05:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (4, 4, '林雅婷', 2,  '35-44歲', '0922333444', 'yt.lin@example.com', '金融保險業', NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (33, 4, 1, 1, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (34, 4, 2, 3, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (35, 4, 3, 7, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (36, 4, 4, 11, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (37, 4, 5, 16, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (38, 4, 6, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (39, 4, 7, NULL, NULL, 8);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (40, 4, 8, NULL, '14:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (41, 4, 9, NULL, '2026-12-01', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (42, 4, 10, NULL, '金融產業對資料落地與加密傳輸要求極高，審查流程通常需跨部門會簽。', NULL);

-- 提交 5: 隨便填的受訪者 (簡答填不知道要寫什麼)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (5, 1, '192.168.1.14', 35, '2026-10-12 09:30:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (5, 5, '阿明', 1,  '25-34歲', '0912345678', 'none@example.com', '餐飲業', NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (43, 5, 1, 2, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (44, 5, 2, 5, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (45, 5, 4, 10, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (46, 5, 5, 14, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (47, 5, 6, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (48, 5, 7, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (49, 5, 8, NULL, '15:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (50, 5, 9, NULL, '2026-10-20', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (51, 5, 10, NULL, '不知道要寫什麼', NULL);

-- 提交 6: 匿名跳過基本資料者 (個資多數為 NULL，簡答填無)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (6, 1, '192.168.1.15', 40, '2026-10-12 15:50:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (6, 6, NULL, 0, '25-34歲', NULL, NULL, '科技業', NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (52, 6, 1, 1, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (53, 6, 2, 4, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (54, 6, 4, 11, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (55, 6, 5, 16, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (56, 6, 6, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (57, 6, 7, NULL, NULL, 6);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (58, 6, 8, NULL, '09:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (59, 6, 9, NULL, '2026-11-05', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (60, 6, 10, NULL, '無', NULL);

-- 提交 7: 亂填鍵盤者 (姓名 asdf, 電話 0911111111, 地區火星, 簡答 111111)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (7, 1, '192.168.1.16', 6, '2026-10-13 13:10:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (7, 7, 'asdf', 3, '18-24歲', '0911111111', 'fake@mail.com', '待業中', NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (61, 7, 1, 2, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (62, 7, 2, 5, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (63, 7, 4, 10, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (64, 7, 5, 14, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (65, 7, 6, NULL, NULL, 1);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (66, 7, 7, NULL, NULL, 1);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (67, 7, 8, NULL, '01:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (68, 7, 9, NULL, '2026-10-01', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (69, 7, 10, NULL, '111111', NULL);

-- 提交 8: 毒舌差評者 (批評權限太難用)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (8, 1, '192.168.1.17', 75, '2026-10-13 16:22:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (8, 8, '使用者', 2,  '35-44歲', '0933999888', 'user@gmail.com', '專業服務業', NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (70, 8, 1, 1, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (71, 8, 2, 3, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (72, 8, 3, 8, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (73, 8, 4, 11, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (74, 8, 5, 14, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (75, 8, 6, NULL, NULL, 2);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (76, 8, 7, NULL, NULL, 2);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (77, 8, 8, NULL, '16:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (78, 8, 9, NULL, '2026-11-10', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (79, 8, 10, NULL, '系統權限設定太反人類，找個設定要點半天，介面字體還很小！', NULL);

-- 提交 9: 短評讚字者
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (9, 1, '192.168.1.18', 60, '2026-10-13 18:05:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (9, 9, '小王', 1,  '25-34歲', '0944555666', NULL, '電子科技業', NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (80, 9, 1, 1, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (81, 9, 2, 4, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (84, 9, 4, 12, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (85, 9, 5, 14, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (88, 9, 6, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (89, 9, 7, NULL, NULL, 8);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (90, 9, 8, NULL, '11:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (91, 9, 9, NULL, '2026-11-25', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (92, 9, 10, NULL, '讚', NULL);

-- 提交 10: 沒填文字反饋者 (簡答題 NULL)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (10, 1, '192.168.1.19', 110, '2026-10-13 20:45:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (10, 10, '張小姐', 2,  '45歲以上', '0955666777', 'ms.chang@example.com', '製造業', NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (96, 10, 1, 2, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (97, 10, 2, 4, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (98, 10, 4, 11, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (99, 10, 5, 15, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (101, 10, 6, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (102, 10, 7, NULL, NULL, 7);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (103, 10, 8, NULL, '10:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (104, 10, 9, NULL, '2026-12-05', NULL);
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
VALUES (13, 2, 'Q3', 'MULTI', '【解鎖題】遭遇結帳阻礙時，平臺提供哪些快捷支付能吸引您完成購買？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (22, 13, 'Apple Pay / Google Pay 一鍵生物指紋辨識', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (23, 13, 'LINE Pay 快速自動跳轉授權折抵', 2, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (24, 13, '超商取貨付款安心代收服務', 3, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (25, 13, '街口支付即時點數現金回饋折抵', 4, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (14, 2, 'Q4', 'SINGLE', '您在決定是否回購該電商平臺時，最核心的考量要素是？', NULL, 1, 4, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (26, 14, '價格優惠與會員常態全館免運門檻', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (27, 14, '當日或 24 小時極速到貨配送服務', 2, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (28, 14, '商品退換貨流程是否完全免手續費且不刁難', 3, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (29, 14, '商品正品保證與原廠完整售後維修', 4, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (15, 2, 'Q5', 'MULTI', '您平時在線上平臺最經常選購的商品類別有哪些？（複選）', 'https://images.unsplash.com/photo-1441986300917-64674bd600d8?auto=format&fit=crop&w=800&q=80', 1, 5, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (30, 15, '3C 數碼配件與電腦周邊硬體', 1, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (31, 15, '生活日用品與家庭消耗快消品', 2, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (32, 15, '潮流服飾、休閒鞋包與戶外裝備', 3, NULL);


INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`)
VALUES (33, 15, '低溫生鮮食材與各地名特產美食', 4, NULL);


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (16, 2, 'Q6', 'RATING', '您對本電商平臺「智慧搜尋推薦與篩選精準度」滿意度（1最低，5最高）', NULL, 1, 6, 1, 5, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (17, 2, 'Q7', 'RATING', '淨推薦值 (NPS)：您向親友推薦在本商城購物的意願（0-10分）', NULL, 1, 7, 0, 10, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (18, 2, 'Q8', 'TIME', '您最習慣瀏覽線上促銷特賣或領取優惠券的黃金時段通常在？', NULL, 1, 8, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (19, 2, 'Q9', 'DATE', '您最近一次在線上完成購物的具體下單日期是？', NULL, 1, 9, NULL, NULL, NOW(), NOW());


INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`)
VALUES (20, 2, 'Q10', 'TEXT', '針對我們在包裹環保包裝、出貨追蹤動態或客服回覆速度，您有何寶貴建議？（簡答題）', NULL, 0, 10, NULL, NULL, NOW(), NOW());

-- =============================================================
-- 問卷 2 (智慧零售消費者回購): 10 筆多樣化真實模擬填答 (包含秒殺亂填、退貨差評、敷衍、認真回饋等真實情境)
-- 基本資料設定: collect_basic_info = 1, require_name = 1 (極簡模式: 僅姓名有值，其餘 gender, age, residence, industry 等全為 NULL)
-- =============================================================

-- 填答紀錄 11: 秒殺亂填搶折價券型 (4 秒秒殺、亂選差評發洩、簡答催券)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (11, 2, '192.168.1.10', 4, '2026-10-10 14:20:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (11, 11, '路人甲', 0, NULL, NULL, NULL, NULL, NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (106, 11, 11, 18, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (107, 11, 12, 21, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (108, 11, 14, 26, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (109, 11, 15, 30, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (110, 11, 16, NULL, NULL, 1);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (111, 11, 17, NULL, NULL, 0);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (112, 11, 18, NULL, '00:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (113, 11, 19, NULL, '2026-10-01', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (114, 11, 20, NULL, '快點給折價券啦別廢話', NULL);

-- 填答紀錄 12: 憤怒退貨投訴型 (35 秒、觸發 Q3、退換貨差評 1 星、簡答大罵客服)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (12, 2, '192.168.1.11', 35, '2026-10-10 16:45:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (12, 12, '受害者', 0, NULL, NULL, NULL, NULL, NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (115, 12, 11, 18, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (116, 12, 12, 20, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (117, 12, 13, 24, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (118, 12, 14, 28, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (119, 12, 15, 31, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (120, 12, 16, NULL, NULL, 1);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (121, 12, 17, NULL, NULL, 0);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (122, 12, 18, NULL, '21:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (123, 12, 19, NULL, '2026-10-08', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (124, 12, 20, NULL, '退貨刁難一個禮拜、客服電話永遠打不通，爛死了再也不買！', NULL);

-- 填答紀錄 13: 鍵盤亂敲敷衍型 (7 秒、姓名亂打 asdf、簡答打無)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (13, 2, '192.168.1.12', 7, '2026-10-11 10:15:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (13, 13, 'asdf', 0, NULL, NULL, NULL, NULL, NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (125, 13, 11, 18, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (126, 13, 12, 21, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (127, 13, 14, 26, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (128, 13, 15, 30, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (129, 13, 16, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (130, 13, 17, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (131, 13, 18, NULL, '12:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (132, 13, 19, NULL, '2026-10-09', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (133, 13, 20, NULL, '無', NULL);

-- 填答紀錄 14: 數字亂打型 (9 秒、姓名 111、簡答 ？？？)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (14, 2, '192.168.1.13', 9, '2026-10-11 11:05:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (14, 14, '111', 0, NULL, NULL, NULL, NULL, NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (134, 14, 11, 19, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (135, 14, 12, 21, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (136, 14, 14, 27, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (137, 14, 15, 32, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (138, 14, 16, NULL, NULL, 2);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (139, 14, 17, NULL, NULL, 2);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (140, 14, 18, NULL, '18:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (141, 14, 19, NULL, '2026-10-05', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (142, 14, 20, NULL, '？？？', NULL);

-- 填答紀錄 15: 略過簡答型 (45 秒、簡答留空 NULL)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (15, 2, '192.168.1.14', 45, '2026-10-12 09:30:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (15, 15, '陳小明', 0, NULL, NULL, NULL, NULL, NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (143, 15, 11, 18, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (144, 15, 12, 20, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (145, 15, 13, 23, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (146, 15, 14, 26, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (147, 15, 15, 30, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (148, 15, 15, 31, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (149, 15, 16, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (150, 15, 17, NULL, NULL, 8);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (151, 15, 18, NULL, '22:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (152, 15, 19, NULL, '2026-10-10', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (153, 15, 20, NULL, NULL, NULL);

-- 填答紀錄 16: 秒殺刷全最高分隨便型 (5 秒、姓名測試、全給滿分、簡答打隨便)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (16, 2, '192.168.1.15', 5, '2026-10-12 15:50:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (16, 16, '測試', 0, NULL, NULL, NULL, NULL, NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (154, 16, 11, 18, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (155, 16, 12, 21, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (156, 16, 14, 26, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (157, 16, 15, 31, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (158, 16, 16, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (159, 16, 17, NULL, NULL, 10);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (160, 16, 18, NULL, '08:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (161, 16, 19, NULL, '2026-10-02', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (162, 16, 20, NULL, '隨便', NULL);

-- 填答紀錄 17: 一般正常回饋型 (110 秒、環保包裝意見)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (17, 2, '192.168.1.16', 110, '2026-10-13 13:10:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (17, 17, '黃先生', 0, NULL, NULL, NULL, NULL, NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (163, 17, 11, 18, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (164, 17, 12, 20, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (165, 17, 13, 22, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (166, 17, 13, 23, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (167, 17, 14, 27, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (168, 17, 15, 30, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (169, 17, 15, 33, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (170, 17, 16, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (171, 17, 17, NULL, NULL, 7);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (172, 17, 18, NULL, '20:15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (173, 17, 19, NULL, '2026-10-11', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (174, 17, 20, NULL, '希望能多用環保紙箱減少塑膠氣泡袋，出貨速度蠻快的。', NULL);

-- 填答紀錄 18: 物流差評型 (68 秒、生鮮退冰抱怨)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (18, 2, '192.168.1.17', 68, '2026-10-13 17:25:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (18, 18, '林小姐', 0, NULL, NULL, NULL, NULL, NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (175, 18, 11, 18, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (176, 18, 12, 21, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (177, 18, 14, 27, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (178, 18, 15, 33, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (179, 18, 16, NULL, NULL, 2);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (180, 18, 17, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (181, 18, 18, NULL, '14:20', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (182, 18, 19, NULL, '2026-10-07', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (183, 18, 20, NULL, '生鮮宅配送來都退冰了，包裝保冷完整性需要加強。', NULL);

-- 填答紀錄 19: 極簡填答型 (18 秒、簡答尚可)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (19, 2, '192.168.1.18', 18, '2026-10-13 19:40:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (19, 19, '匿名', 0, NULL, NULL, NULL, NULL, NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (184, 19, 11, 19, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (185, 19, 12, 21, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (186, 19, 14, 29, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (187, 19, 15, 31, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (188, 19, 16, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (189, 19, 17, NULL, NULL, 6);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (190, 19, 18, NULL, '19:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (191, 19, 19, NULL, '2026-10-03', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (192, 19, 20, NULL, '尚可', NULL);

-- 填答紀錄 20: 忠實顧客認真回饋型 (160 秒、細緻建議)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (20, 2, '192.168.1.19', 160, '2026-10-13 20:45:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (20, 20, '張惠君', 0, NULL, NULL, NULL, NULL, NULL);

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (193, 20, 11, 18, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (194, 20, 12, 20, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (195, 20, 13, 22, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (196, 20, 13, 23, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (197, 20, 13, 25, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (198, 20, 14, 26, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (199, 20, 15, 30, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (200, 20, 15, 31, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (201, 20, 15, 32, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (202, 20, 16, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (203, 20, 17, NULL, NULL, 9);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (204, 20, 18, NULL, '23:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (205, 20, 19, NULL, '2026-10-12', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (206, 20, 20, NULL, 'LINE Pay 點數折抵很方便，希望雙十一全館免運門檻能再調降一點。', NULL);

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

-- =============================================================
-- 問卷 3 (大專院校學術誠信評測): 10 筆真實學術與學生填答 (包含通識應付亂填、隨意敷衍、資工研究生、醫學系嚴謹建言等)
-- 基本資料設定: 學校教育背景導向 (收集姓名、性別、信箱、就讀科系、班級、學號；年齡/電話/產業/居住地全為 NULL)
-- =============================================================

-- 填答紀錄 21: 應付通識作業秒殺亂填型 (6 秒、姓名小明、簡答不知道要寫什麼)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (21, 3, '192.168.1.10', 6, '2026-10-10 14:20:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`, `academic_major`, `class_name`, `student_id`)
VALUES (21, 21, '小明', 1,  NULL, NULL, 'test@gmail.com', NULL, NULL, '企業管理學系', '企二甲', 'B11001001');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (211, 21, 21, 34, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (212, 21, 22, 37, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (213, 21, 24, 42, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (214, 21, 25, 46, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (215, 21, 26, NULL, NULL, 1);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (216, 21, 27, NULL, NULL, 0);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (217, 21, 28, NULL, '01:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (218, 21, 29, NULL, '2026-12-31', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (219, 21, 30, NULL, '不知道要寫什麼', NULL);

-- 填答紀錄 22: 隨意敷衍打字型 (8 秒、學號隨便打、簡答打無)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (22, 3, '192.168.1.11', 8, '2026-10-10 16:45:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`, `academic_major`, `class_name`, `student_id`)
VALUES (22, 22, '路人', 3, NULL, NULL, 'no@mail.com', NULL, NULL, '中國文學系', '中三乙', '12345678');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (220, 22, 21, 35, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (221, 22, 22, 37, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (222, 22, 24, 42, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (223, 22, 25, 48, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (224, 22, 26, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (225, 22, 27, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (226, 22, 28, NULL, '12:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (227, 22, 29, NULL, '2026-11-01', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (228, 22, 30, NULL, '無', NULL);

-- 填答紀錄 23: 資工研究生認真反饋型 (180 秒、解鎖 Q3、深入探討 LLM 數學推導缺陷)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (23, 3, '192.168.1.12', 180, '2026-10-11 10:15:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`, `academic_major`, `class_name`, `student_id`)
VALUES (23, 23, '林宏達', 1,  NULL, NULL, 'hd.lin@cs.edu.tw', NULL, NULL, '資訊工程學系', '碩一', 'R11292045');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (229, 23, 21, 34, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (230, 23, 22, 36, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (231, 23, 23, 38, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (232, 23, 23, 40, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (233, 23, 24, 45, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (234, 23, 25, 47, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (235, 23, 26, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (236, 23, 27, NULL, NULL, 9);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (237, 23, 28, NULL, '02:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (238, 23, 29, NULL, '2026-11-30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (239, 23, 30, NULL, 'LLM 寫演算法草稿很強，但數學推導與證明經常一本正經胡說八道，必須自己重新推演。', NULL);

-- 填答紀錄 24: 亂按問號敷衍型 (5 秒、姓名 asdf、簡答 ？？？)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (24, 3, '192.168.1.13', 5, '2026-10-11 11:05:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`, `academic_major`, `class_name`, `student_id`)
VALUES (24, 24, 'asdf', 1,  NULL, NULL, 'asdf@asdf.com', NULL, NULL, '電機工程學系', '電四丙', 'B10901234');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (240, 24, 21, 34, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (241, 24, 22, 37, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (242, 24, 24, 43, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (243, 24, 25, 46, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (244, 24, 26, NULL, NULL, 2);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (245, 24, 27, NULL, NULL, 1);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (246, 24, 28, NULL, '09:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (247, 24, 29, NULL, '2026-10-30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (248, 24, 30, NULL, '？？？', NULL);

-- 填答紀錄 25: 醫學系研究生嚴謹建言型 (240 秒、解鎖 Q3、強調病患個資與地端沙盒)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (25, 3, '192.168.1.14', 240, '2026-10-12 09:30:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`, `academic_major`, `class_name`, `student_id`)
VALUES (25, 25, '陳靜儀', 2,  NULL, NULL, 'cy.chen@med.edu.tw', NULL, NULL, '臨床醫學研究所', '博二', 'D11142008');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (249, 25, 21, 34, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (250, 25, 22, 36, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (251, 25, 23, 38, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (252, 25, 23, 39, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (253, 25, 23, 41, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (254, 25, 24, 44, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (255, 25, 25, 46, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (256, 25, 25, 48, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (257, 25, 26, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (258, 25, 27, NULL, NULL, 8);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (259, 25, 28, NULL, '23:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (260, 25, 29, NULL, '2026-12-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (261, 25, 30, NULL, '臨床數據涉及病人隱私嚴禁丟入公開模型，院內應建置地端隔離的 LLM 沙盒。', NULL);

-- 填答紀錄 26: 差評抗議 AI 氾濫型 (40 秒、差評 0 分、大罵失去思考力)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (26, 3, '192.168.1.15', 40, '2026-10-12 15:50:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`, `academic_major`, `class_name`, `student_id`)
VALUES (26, 26, '反對AI者', 2,  NULL, NULL, 'anti.ai@edu.tw', NULL, NULL, '哲學系', '哲四甲', 'B10910055');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (262, 26, 21, 35, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (263, 26, 22, 37, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (264, 26, 24, 42, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (265, 26, 25, 48, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (266, 26, 26, NULL, NULL, 1);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (267, 26, 27, NULL, NULL, 0);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (268, 26, 28, NULL, '15:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (269, 26, 29, NULL, '2026-12-01', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (270, 26, 30, NULL, '根本不該鼓勵學生用 AI，導致大家都不思考、論文充滿抄襲味！', NULL);

-- 填答紀錄 27: 簡答留空型 (65 秒、解鎖 Q3、簡答留空 NULL)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (27, 3, '192.168.1.16', 65, '2026-10-13 13:10:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`, `academic_major`, `class_name`, `student_id`)
VALUES (27, 27, '張家豪', 1,  NULL, NULL, 'jh.chang@ntu.edu.tw', NULL, NULL, '機械工程學系', '機三甲', 'B11050012');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (271, 27, 21, 34, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (272, 27, 22, 36, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (273, 27, 23, 39, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (274, 27, 24, 44, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (275, 27, 25, 47, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (276, 27, 26, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (277, 27, 27, NULL, NULL, 7);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (278, 27, 28, NULL, '14:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (279, 27, 29, NULL, '2026-11-25', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (280, 27, 30, NULL, NULL, NULL);

-- 填答紀錄 28: 幽默調侃被誤判型 (25 秒、抱怨 AI 抓 AI 誤判)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (28, 3, '192.168.1.17', 25, '2026-10-13 17:25:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`, `academic_major`, `class_name`, `student_id`)
VALUES (28, 28, '教授饒了我', 1,  NULL, NULL, 'helpme@mail.com', NULL, NULL, '經濟學系', '經四乙', 'B10930077');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (281, 28, 21, 34, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (282, 28, 22, 37, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (283, 28, 24, 43, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (284, 28, 25, 46, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (285, 28, 25, 48, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (286, 28, 26, NULL, NULL, 2);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (287, 28, 27, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (288, 28, 28, NULL, '03:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (289, 28, 29, NULL, '2026-11-10', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (290, 28, 30, NULL, '希望教授不要再用 AI 抓 AI 了，誤判率超高被退件很冤枉。', NULL);

-- 填答紀錄 29: 快速打字敷衍型 (12 秒、簡答只打一個點)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (29, 3, '192.168.1.18', 12, '2026-10-13 19:40:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`, `academic_major`, `class_name`, `student_id`)
VALUES (29, 29, '王大明', 1,  NULL, NULL, 'dm.wang@test.com', NULL, NULL, '外國語文學系', '外二甲', 'B11110022');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (291, 29, 21, 34, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (292, 29, 22, 37, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (293, 29, 24, 43, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (294, 29, 25, 48, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (295, 29, 26, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (296, 29, 27, NULL, NULL, 6);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (297, 29, 28, NULL, '10:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (298, 29, 29, NULL, '2026-12-20', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (299, 29, 30, NULL, '.', NULL);

-- 填答紀錄 30: 認真外文助教建言型 (195 秒、解鎖 Q3、建議訂定 Prompt 引用標準)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (30, 3, '192.168.1.19', 195, '2026-10-13 20:45:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`, `academic_major`, `class_name`, `student_id`)
VALUES (30, 30, '黃雅芳', 2,  NULL, NULL, 'yf.huang@nccu.edu.tw', NULL, NULL, '語言學研究所', '碩二', 'R11214002');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (300, 30, 21, 34, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (301, 30, 22, 36, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (302, 30, 23, 38, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (303, 30, 23, 39, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (304, 30, 24, 44, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (305, 30, 25, 46, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (306, 30, 25, 48, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (307, 30, 25, 49, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (308, 30, 26, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (309, 30, 27, NULL, NULL, 8);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (310, 30, 28, NULL, '21:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (311, 30, 29, NULL, '2026-12-10', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (312, 30, 30, NULL, '建議教育部統一訂定 Prompt 揭露範本與引用標準，讓各系所有法可依。', NULL);

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
VALUES (63, 35, '手沖咖啡吧臺與多功能休閒交流區', 2, NULL);


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

-- =============================================================
-- 問卷 4 (次世代產品封測福利統計): 10 筆內部封測工程師真實反饋 (包含 QA 壓測秒殺、Safari 抖動 Bug 投訴、Excel CSV 亂碼抱怨、設計師與 PM 認真回饋等)
-- 基本資料設定: collect_basic_info = 0 (「一個不用」完全匿名模式，受訪者完全免填個資，不插入任何 submission_basic_infos 記錄)
-- =============================================================

-- 填答紀錄 31: 快速壓測 QA 工程師 (3 秒、驗收快速通關)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (31, 4, '192.168.1.10', 3, '2026-10-10 14:20:00');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (313, 31, 31, 50, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (314, 31, 32, 53, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (315, 31, 34, 58, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (316, 31, 35, 62, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (317, 31, 35, 64, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (318, 31, 36, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (319, 31, 37, NULL, NULL, 10);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (320, 31, 38, NULL, '18:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (321, 31, 39, NULL, '2026-11-01', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (322, 31, 40, NULL, 'QA 驗收 pass', NULL);

-- 填答紀錄 32: 遭遇動效 Bug 吐槽工程師 (45 秒、解鎖 Q3、回報 Safari 展開抖動問題)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (32, 4, '192.168.1.11', 45, '2026-10-10 16:45:00');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (323, 32, 31, 50, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (324, 32, 32, 52, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (325, 32, 33, 55, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (326, 32, 33, 56, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (327, 32, 34, 60, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (328, 32, 35, 63, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (329, 32, 36, NULL, NULL, 2);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (330, 32, 37, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (331, 32, 38, NULL, '12:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (332, 32, 39, NULL, '2026-11-30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (333, 32, 40, NULL, '手機 Safari 直向滑到第 3 題展開時會閃爍抖動，請修復！', NULL);

-- 填答紀錄 33: 秒殺只為領飲料福利 (4 秒、簡答打無)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (33, 4, '192.168.1.12', 4, '2026-10-11 10:15:00');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (334, 33, 31, 51, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (335, 33, 32, 53, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (336, 33, 34, 59, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (337, 33, 35, 63, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (338, 33, 36, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (339, 33, 37, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (340, 33, 38, NULL, '15:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (341, 33, 39, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (342, 33, 40, NULL, '無', NULL);

-- 填答紀錄 34: 亂按問號敷衍型 (6 秒、簡答 ？？？)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (34, 4, '192.168.1.13', 6, '2026-10-11 11:05:00');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (343, 34, 31, 50, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (344, 34, 32, 53, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (345, 34, 34, 58, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (346, 34, 35, 62, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (347, 34, 36, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (348, 34, 37, NULL, NULL, 7);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (349, 34, 38, NULL, '09:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (350, 34, 39, NULL, '2026-11-20', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (351, 34, 40, NULL, '？？？', NULL);

-- 填答紀錄 35: 略過簡答型 (30 秒、簡答留空 NULL)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (35, 4, '192.168.1.14', 30, '2026-10-12 09:30:00');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (352, 35, 31, 50, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (353, 35, 32, 53, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (354, 35, 34, 58, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (355, 35, 35, 64, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (356, 35, 36, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (357, 35, 37, NULL, NULL, 8);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (358, 35, 38, NULL, '17:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (359, 35, 39, NULL, '2026-11-10', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (360, 35, 40, NULL, NULL, NULL);

-- 填答紀錄 36: UI 設計師認真回饋型 (150 秒、解鎖 Q3、建議卡片微位移)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (36, 4, '192.168.1.15', 150, '2026-10-12 15:50:00');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (361, 36, 31, 50, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (362, 36, 32, 52, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (363, 36, 33, 54, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (364, 36, 34, 58, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (365, 36, 35, 63, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (366, 36, 35, 64, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (367, 36, 36, NULL, NULL, 4);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (368, 36, 37, NULL, NULL, 9);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (369, 36, 38, NULL, '14:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (370, 36, 39, NULL, '2026-11-25', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (371, 36, 40, NULL, '白色主題陰影細節很讚，建議卡片 hover 時加上 2px 微抬升位移效果。', NULL);

-- 填答紀錄 37: 敷衍型 (5 秒、簡答隨便)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (37, 4, '192.168.1.16', 5, '2026-10-13 13:10:00');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (372, 37, 31, 51, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (373, 37, 32, 53, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (374, 37, 34, 59, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (375, 37, 35, 65, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (376, 37, 36, NULL, NULL, 3);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (377, 37, 37, NULL, NULL, 6);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (378, 37, 38, NULL, '11:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (379, 37, 39, NULL, '2026-12-01', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (380, 37, 40, NULL, '隨便', NULL);

-- 填答紀錄 38: 差評抱怨型 (55 秒、解鎖 Q3、回報 Excel 開啟 CSV 亂碼)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (38, 4, '192.168.1.17', 55, '2026-10-13 17:25:00');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (381, 38, 31, 51, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (382, 38, 32, 52, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (383, 38, 33, 56, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (384, 38, 34, 61, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (385, 38, 35, 62, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (386, 38, 36, NULL, NULL, 1);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (387, 38, 37, NULL, NULL, 1);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (388, 38, 38, NULL, '20:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (389, 38, 39, NULL, '2026-12-31', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (390, 38, 40, NULL, '匯出 CSV 中文在 Excel 打開會亂碼，希望能預設加 UTF-8 BOM！', NULL);

-- 填答紀錄 39: 極速點擊型 (7 秒、簡答讚)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (39, 4, '192.168.1.18', 7, '2026-10-13 19:40:00');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (391, 39, 31, 50, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (392, 39, 32, 53, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (393, 39, 34, 58, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (394, 39, 35, 63, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (395, 39, 36, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (396, 39, 37, NULL, NULL, 8);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (397, 39, 38, NULL, '16:00', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (398, 39, 39, NULL, '2026-11-05', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (399, 39, 40, NULL, '讚', NULL);

-- 填答紀錄 40: 產品經理認真回饋型 (180 秒、許願 PDF 報告產生)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (40, 4, '192.168.1.19', 180, '2026-10-13 20:45:00');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (400, 40, 31, 50, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (401, 40, 32, 53, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (402, 40, 34, 58, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (403, 40, 35, 62, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (404, 40, 35, 63, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (405, 40, 35, 64, NULL, NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (406, 40, 36, NULL, NULL, 5);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (407, 40, 37, NULL, NULL, 10);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (408, 40, 38, NULL, '09:30', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (409, 40, 39, NULL, '2026-11-15', NULL);
INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES (410, 40, 40, NULL, '統計儀表板的百分比圓餅圖很直觀，希望能支援直接產生 PDF 匯報簡報。', NULL);

-- 恢復外鍵檢查並提交
SET FOREIGN_KEY_CHECKS = 1;
COMMIT;
