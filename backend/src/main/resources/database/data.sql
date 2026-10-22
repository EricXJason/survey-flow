-- ============================================================================
-- 專案名稱: SurveyFlow (企業級問卷調查與智慧數據分析系統)
-- 檔案名稱: data.sql
-- 責任描述: 注入極具商業價值、學術研究與生活日常統計意義之完整問卷與真實受訪者數據
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
TRUNCATE TABLE `users`;

-- 重設系統預設使用者帳號與密碼 (admin: Admin123! / testuser: User1234!)
INSERT INTO `users` (`id`, `username`, `password_hash`, `full_name`, `email`, `phone`, `identity_card`, `role`, `status`, `created_at`, `updated_at`)
VALUES 
(1, 'admin', '$argon2id$v=19$m=4096,t=3,p=1$4u8prALaBK1aC7DVJVg/+g$YoCtbfr0dHvdLvrlYsJ1BHw04HpeH0KTdb50iK3RAQA', '系統最高管理員', 'admin@surveyflow.com', '0912345678', 'A123456789', 'ROLE_ADMIN', 'ACTIVE', NOW(), NOW()),
(2, 'testuser', '$argon2id$v=19$m=4096,t=3,p=1$+bDUHogksGfUTL5PLnxALw$umIOklN0jNKTEWF1kP9JGin4eBrQU69UTXHsk1tSFvc', '測試會員小明', 'testuser@surveyflow.com', '0987654321', 'B234567890', 'ROLE_USER', 'ACTIVE', NOW(), NOW()),
(3, 'academic_chen', '$argon2id$v=19$m=4096,t=3,p=1$+bDUHogksGfUTL5PLnxALw$umIOklN0jNKTEWF1kP9JGin4eBrQU69UTXHsk1tSFvc', '陳思翰副教授', 'sh.chen@surveyflow.com', '0911223344', 'C123456789', 'ROLE_USER', 'ACTIVE', NOW(), NOW()),
(4, 'product_lin', '$argon2id$v=19$m=4096,t=3,p=1$+bDUHogksGfUTL5PLnxALw$umIOklN0jNKTEWF1kP9JGin4eBrQU69UTXHsk1tSFvc', '林品妤產品長', 'py.lin@surveyflow.com', '0922334455', 'D234567890', 'ROLE_USER', 'ACTIVE', NOW(), NOW()),
(5, 'hr_huang', '$argon2id$v=19$m=4096,t=3,p=1$+bDUHogksGfUTL5PLnxALw$umIOklN0jNKTEWF1kP9JGin4eBrQU69UTXHsk1tSFvc', '黃彥碩人資主管', 'ys.huang@surveyflow.com', '0933445566', 'E123456789', 'ROLE_USER', 'ACTIVE', NOW(), NOW()),
(6, 'market_wu', '$argon2id$v=19$m=4096,t=3,p=1$+bDUHogksGfUTL5PLnxALw$umIOklN0jNKTEWF1kP9JGin4eBrQU69UTXHsk1tSFvc', '吳芷晴行銷總監', 'cq.wu@surveyflow.com', '0944556677', 'F234567890', 'ROLE_USER', 'ACTIVE', NOW(), NOW()),
(7, 'event_chang', '$argon2id$v=19$m=4096,t=3,p=1$+bDUHogksGfUTL5PLnxALw$umIOklN0jNKTEWF1kP9JGin4eBrQU69UTXHsk1tSFvc', '張銘宇活動策劃', 'my.chang@surveyflow.com', '0955667788', 'G123456789', 'ROLE_USER', 'ACTIVE', NOW(), NOW()),
(8, 'support_li', '$argon2id$v=19$m=4096,t=3,p=1$+bDUHogksGfUTL5PLnxALw$umIOklN0jNKTEWF1kP9JGin4eBrQU69UTXHsk1tSFvc', '李冠廷客服副總', 'kt.li@surveyflow.com', '0966778899', 'H234567890', 'ROLE_USER', 'ACTIVE', NOW(), NOW()),
(9, 'edu_wang', '$argon2id$v=19$m=4096,t=3,p=1$+bDUHogksGfUTL5PLnxALw$umIOklN0jNKTEWF1kP9JGin4eBrQU69UTXHsk1tSFvc', '王靜雯培訓講師', 'cw.wang@surveyflow.com', '0977889900', 'I123456789', 'ROLE_USER', 'ACTIVE', NOW(), NOW()),
(10, 'health_tsai', '$argon2id$v=19$m=4096,t=3,p=1$+bDUHogksGfUTL5PLnxALw$umIOklN0jNKTEWF1kP9JGin4eBrQU69UTXHsk1tSFvc', '蔡承翰健康顧問', 'ch.tsai@surveyflow.com', '0988990011', 'J234567890', 'ROLE_USER', 'ACTIVE', NOW(), NOW()),
(11, 'policy_yang', '$argon2id$v=19$m=4096,t=3,p=1$+bDUHogksGfUTL5PLnxALw$umIOklN0jNKTEWF1kP9JGin4eBrQU69UTXHsk1tSFvc', '楊心慈政策研究員', 'ct.yang@surveyflow.com', '0999001122', 'K123456789', 'ROLE_USER', 'ACTIVE', NOW(), NOW()),
(12, 'social_liao', '$argon2id$v=19$m=4096,t=3,p=1$+bDUHogksGfUTL5PLnxALw$umIOklN0jNKTEWF1kP9JGin4eBrQU69UTXHsk1tSFvc', '廖俊翔社群經理', 'jx.liao@surveyflow.com', '0900112233', 'L234567890', 'ROLE_USER', 'ACTIVE', NOW(), NOW());

-- -------------------------------------------------------------
-- 建立 4 筆高度專業且符合統計意義之問卷專案 (testuser 擁有)
-- -------------------------------------------------------------
INSERT INTO `surveys` (`id`, `user_id`, `title`, `description`, `cover_image_url`, `category`, `is_public_to_lobby`, `is_open`, `start_date`, `end_date`, `view_count`, `created_at`, `updated_at`)
VALUES
(1, 2, '2026 企業級 SaaS 雲端平台使用者體驗與採購決策調研', '【商業統計】本調研旨在深入探討跨國企業在導入現代化雲端軟體時之資安合規、非同步協作效能與年費定價決策考量，為產品路線圖提供實質商業依據。', 'https://images.unsplash.com/photo-1522071820081-009f0129c71c?auto=format&fit=crop&w=1200&q=80', '產品體驗研究', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 168, NOW(), NOW()),
(2, 2, '2026 智慧零售與跨境電商消費者回購滿意度調查', '【商業消費】全面分析線上購物者對於多元行動支付（Apple Pay/LINE Pay）、物流配送時效、智慧客服滿意度與品牌忠誠度之關鍵指標。', 'https://images.unsplash.com/photo-1472851294608-062f824d29cc?auto=format&fit=crop&w=1200&q=80', '顧客滿意度', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 245, NOW(), NOW()),
(3, 2, '2026 生成式 AI 對大專院校研發產能與學術誠信影響評測', '【學術研究】針對全臺大專院校教師、研究生與技術研發人員，探討 LLM 輔助寫碼與論文閱讀對學術創新產能之推升程度及倫理審查現況。', 'https://images.unsplash.com/photo-1555066931-4365d14bab8c?auto=format&fit=crop&w=1200&q=80', '市場商業調研', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 312, NOW(), NOW()),
(4, 2, '【封測專案／非公開】次世代 SurveyFlow 產品體驗與公設福利統計', '【日常統計】本問卷為封閉測試問卷（未發布至大廳，持專屬直達連結可填答），蒐集團隊對辦公室環境、健身公設利用率與全新儀表板之第一手體驗反饋。', 'https://images.unsplash.com/photo-1460925895917-afdab827c52f?auto=format&fit=crop&w=1200&q=80', '綜合調查', 0, 0, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 58, NOW(), NOW());

-- 基本資料收集設定 (針對 4 份問卷規劃多元情境: 職場/通訊完整、只用一個、教育/學校背景、一個不用)
INSERT INTO `survey_basic_info_configs` (
    `survey_id`, `collect_basic_info`,
    `require_name`, `require_gender`, `require_age`, `require_birthday`, `require_national_id`,
    `require_phone`, `require_home_phone`, `require_email`,
    `require_industry`, `require_job_title`, `require_department`, `require_employee_id`,
    `require_academic_major`, `require_class_name`, `require_student_id`,
    `require_height`, `require_weight`,
    `require_residence`, `require_registered_address`
) VALUES
-- 問卷 1 (企業級 SaaS 雲端調研): 職場組織與通訊導向 (不收集居住地區)
(1, 1, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0),
-- 問卷 2 (智慧零售消費者回購): 「只用一個」極簡模式 (只收集姓名，其餘免填)
(2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
-- 問卷 3 (大專院校學術誠信評測): 學校與教育背景導向 (姓名、性別、信箱、科系、班級、學號)
(3, 1, 1, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0),
-- 問卷 4 (次世代產品封測福利統計): 「一個不用」完全匿名模式 (collect_basic_info=0，受訪者完全免填)
(4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);


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
VALUES (2, 2, '訪客', '其他', '18-24歲', '0900000000', 'test@test.com', '學生', NULL);

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
VALUES (6, 6, NULL, NULL, '25-34歲', NULL, NULL, '科技業', NULL);

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
VALUES (7, 7, 'asdf', '其他', '18-24歲', '0911111111', 'fake@mail.com', '待業中', NULL);

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

-- =============================================================
-- 問卷 2 (智慧零售消費者回購): 10 筆多樣化真實模擬填答 (包含秒殺亂填、退貨差評、敷衍、認真回饋等真實情境)
-- 基本資料設定: collect_basic_info = 1, require_name = 1 (極簡模式: 僅姓名有值，其餘 gender, age, residence, industry 等全為 NULL)
-- =============================================================

-- 填答紀錄 11: 秒殺亂填搶折價券型 (4 秒秒殺、亂選差評發洩、簡答催券)
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`)
VALUES (11, 2, '192.168.1.10', 4, '2026-10-10 14:20:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`)
VALUES (11, 11, '路人甲', NULL, NULL, NULL, NULL, NULL, NULL);

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
VALUES (12, 12, '受害者', NULL, NULL, NULL, NULL, NULL, NULL);

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
VALUES (13, 13, 'asdf', NULL, NULL, NULL, NULL, NULL, NULL);

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
VALUES (14, 14, '111', NULL, NULL, NULL, NULL, NULL, NULL);

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
VALUES (15, 15, '陳小明', NULL, NULL, NULL, NULL, NULL, NULL);

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
VALUES (16, 16, '測試', NULL, NULL, NULL, NULL, NULL, NULL);

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
VALUES (17, 17, '黃先生', NULL, NULL, NULL, NULL, NULL, NULL);

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
VALUES (18, 18, '林小姐', NULL, NULL, NULL, NULL, NULL, NULL);

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
VALUES (19, 19, '匿名', NULL, NULL, NULL, NULL, NULL, NULL);

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
VALUES (20, 20, '張惠君', NULL, NULL, NULL, NULL, NULL, NULL);

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
VALUES (22, 22, '路人', '其他', NULL, NULL, 'no@mail.com', NULL, NULL, '中國文學系', '中三乙', '12345678');

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


-- =============================================================
-- 注入 10 位真實專業使用者所屬之 20 筆多元化高擬真問卷專案 (問卷 5 至 24)
-- 全面覆蓋大廳 12 大主題分類與 7 大題型 (BOOLEAN, SINGLE, MULTI, RATING, DATE, TIME, TEXT)
-- =============================================================

INSERT INTO `surveys` (`id`, `user_id`, `title`, `description`, `cover_image_url`, `category`, `is_public_to_lobby`, `is_open`, `start_date`, `end_date`, `view_count`, `created_at`, `updated_at`) VALUES
(5, 3, '2026 大專校院全英語授課 (EMI) 學習成效與文化適應調研', '【學術研究】本研究探討臺灣高教體系推動 EMI 課程之學習障礙、專業術語理解度與課堂互動成效。', 'https://images.unsplash.com/photo-1523240795612-9a054b0db644?auto=format&fit=crop&w=1200&q=80', '學術專題研究', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 125, NOW(), NOW()),
(6, 3, '2026 數位轉型背景下大專技職實習滿意度與就業銜接評鑑', '【教育培訓】追蹤技專校院學生於科技園區產業實習之專業技能匹配度、導師制度與留任意願。', 'https://images.unsplash.com/photo-1524178232363-1fb2b075b655?auto=format&fit=crop&w=1200&q=80', '教育與培訓評鑑', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 130, NOW(), NOW()),
(7, 4, '2026 跨平台 AI 智慧語音筆記軟體日常可用性評測', '【產品體驗】針對新世代音訊摘要、即時逐字稿轉譯與思維導圖生成功能的整體操作流暢度評鑑。', 'https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=1200&q=80', '產品體驗研究', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 135, NOW(), NOW()),
(8, 4, '2026 訂閱制設計軟體雲端協作版本相容性深度回饋', '【顧客滿意】蒐集資深 UI/UX 設計師與前端工程師對於 Design System 權杖同步與效能之真實評分。', 'https://images.unsplash.com/photo-1581291518857-4e27b48ff24e?auto=format&fit=crop&w=1200&q=80', '顧客滿意度', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 140, NOW(), NOW()),
(9, 5, '2026 跨國混合辦公 (Hybrid Work) 團隊信任與心智健康調研', '【職場文化】評估遠距與實體彈性工時制度對跨時區員工心理福祉、倦怠感及專案推進效率之影響。', 'https://images.unsplash.com/photo-1497215728101-856f4ea42174?auto=format&fit=crop&w=1200&q=80', '員工與職場文化', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 145, NOW(), NOW()),
(10, 5, '2026 現代白領工作者作息、運動頻率與睡眠品質綜合量表', '【健康生活】透過標準量表與日常作息時間填答，分析久坐辦公室族群之亞健康成因與自評狀態。', 'https://images.unsplash.com/photo-1517838277536-f5f99be501cd?auto=format&fit=crop&w=1200&q=80', '健康與生活型態', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 150, NOW(), NOW()),
(11, 6, '2026 臺灣新世代生成式搜尋引擎 (AI Search) 消費者行為變遷', '【商業調研】探究消費者在電商購物與資訊檢索時，從傳統關鍵字轉移至對話式 AI 推薦之信任度。', 'https://images.unsplash.com/photo-1460925895917-afdab827c52f?auto=format&fit=crop&w=1200&q=80', '市場商業調研', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 155, NOW(), NOW()),
(12, 6, '2026 品牌永續 ESG 承諾與綠色溢價支付意願意向評估', '【品牌行銷】分析年輕消費者對品牌減碳包裝、公平貿易認證與碳足跡標籤之品牌偏好與加價意願。', 'https://images.unsplash.com/photo-1542601906990-b4d3fb778b09?auto=format&fit=crop&w=1200&q=80', '品牌行銷與推廣', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 160, NOW(), NOW()),
(13, 7, '2026 國際開源技術年會 (TechConf) 現場體驗與議程滿意度', '【活動回饋】蒐集萬人開發者年會參與者對 Keynote 演講深度、技術工作坊、攤位互動之即時意見。', 'https://images.unsplash.com/photo-1511578314322-379afb476865?auto=format&fit=crop&w=1200&q=80', '活動與會後回饋', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 165, NOW(), NOW()),
(14, 7, '2026 虛擬社群平台沉浸式互動與創作者經濟生態參與', '【社群綜合】探討數位創作者在虛擬空間、影音社群之變現模式、社群黏著度與微打賞機制。', 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=1200&q=80', '社群互動與綜合', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 170, NOW(), NOW()),
(15, 8, '2026 智慧物流冷鏈宅配溫控準時性與售後客服評價', '【售後服務】評估生鮮電商配送過程中的溫控品質、司機聯絡禮儀及問題訂單退款處理時效。', 'https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=1200&q=80', '售後服務與支援', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 175, NOW(), NOW()),
(16, 8, '2026 新能源車車主充電生活圈與專屬 App 維修滿意度', '【顧客滿意】深度調查電動車主對原廠快充樁覆蓋率、智慧預約保養系統與道路救援滿意度。', 'https://images.unsplash.com/photo-1593941707882-a5bba14938c7?auto=format&fit=crop&w=1200&q=80', '顧客滿意度', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 180, NOW(), NOW()),
(17, 9, '2026 企業內部軟體架構微證照培訓學程評鑑調研', '【教育培訓】調查研發團隊在接受 DDD、微服務與雲原生 DevOps 系列認證課程後的實務轉化效益。', 'https://images.unsplash.com/photo-1531482615713-2afd69097998?auto=format&fit=crop&w=1200&q=80', '教育與培訓評鑑', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 185, NOW(), NOW()),
(18, 9, '2026 臺灣老齡化社會遠距智慧醫療接受度實證研究', '【學術研究】以科技接受模型 (TAM) 驗證長者及其照護家屬對穿戴式即時生理監控系統之採納意向。', 'https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?auto=format&fit=crop&w=1200&q=80', '學術專題研究', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 190, NOW(), NOW()),
(19, 10, '2026 都市上班族飲食營養標示閱讀習慣與輕食消費傾向', '【健康生活】解析現代都會人群於超商購買鮮食時對減糖、高蛋白及清真認證等營養標籤之關注度。', 'https://images.unsplash.com/photo-1498837167922-ddd27525d352?auto=format&fit=crop&w=1200&q=80', '健康與生活型態', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 195, NOW(), NOW()),
(20, 10, '2026 淨零排碳與綠色公共交通月票定價民意滿意度', '【公共政策】調查六都通勤族對各類大眾運輸轉乘優惠、共享單車站點密集度與碳權抵換之支持度。', 'https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=1200&q=80', '公共政策與民意', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 200, NOW(), NOW()),
(21, 11, '2026 數位身分認證 (FIDO2) 與個資自主權保護公民意向', '【公共政策】探討無密碼認證機制在公部門數位便民服務中之普及障礙、隱私保護疑慮與法制期待。', 'https://images.unsplash.com/photo-1563986768609-322da13575f3?auto=format&fit=crop&w=1200&q=80', '公共政策與民意', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 205, NOW(), NOW()),
(22, 11, '2026 現代家長對兒童數位螢幕接觸時長與課外讀物綜合統計', '【產品體驗】全方位剖析學齡前至國小階段學童之 3C 使用守則、親子共讀頻率與戶外探索時間。', 'https://images.unsplash.com/photo-1503676260728-1c00da094a0b?auto=format&fit=crop&w=1200&q=80', '產品體驗研究', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 210, NOW(), NOW()),
(23, 12, '2026 獨立遊戲開發者生態系資助管道與社群募資參與', '【社群綜合】針對臺灣獨立遊戲創作者，調研群眾募資平台抽成、Steam 上架推廣與社群回饋模式。', 'https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=1200&q=80', '社群互動與綜合', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 215, NOW(), NOW()),
(24, 12, '2026 咖啡愛好者自烘豆風味偏好與連鎖手搖飲替代行為', '【商業調研】調研精品咖啡品飲族群對淺焙微批次、厭氧處理法以及燕麥奶替代方案之消費決策。', 'https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?auto=format&fit=crop&w=1200&q=80', '市場商業調研', 1, 1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 220, NOW(), NOW());

INSERT INTO `survey_basic_info_configs` (`survey_id`, `collect_basic_info`, `require_name`, `require_gender`, `require_age`, `require_birthday`, `require_national_id`, `require_phone`, `require_home_phone`, `require_email`, `require_industry`, `require_job_title`, `require_department`, `require_employee_id`, `require_academic_major`, `require_class_name`, `require_student_id`, `require_height`, `require_weight`, `require_residence`, `require_registered_address`) VALUES
-- 5. EMI 英語授課: 學校與教育背景導向 (姓名、性別、信箱、科系、班級、學號)
(5, 1, 1, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0),
-- 6. 技職實習銜接: 學生實習與職場複合 (姓名、性別、年齡、電話、信箱、產業、部門、科系、學號)
(6, 1, 1, 1, 1, 0, 0, 1, 0, 1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 0, 0),
-- 7. AI 語音筆記軟體: 完全匿名無拘束填答 (受訪者完全免填基本資料)
(7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
-- 8. 訂閱制設計軟體: 專業從業者導向 (姓名、信箱、產業、職稱)
(8, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0),
-- 9. 混合辦公心智健康: 企業內部員工專屬 (姓名、性別、年齡、信箱、職稱、部門、工號)
(9, 1, 1, 1, 1, 0, 0, 0, 0, 1, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0),
-- 10. 白領作息健康量表: 免填姓名隱私模式 (僅收集性別、年齡、生日、產業、身高、體重、居住地)
(10, 1, 0, 1, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0),
-- 11. AI 搜尋消費者變遷: 大眾消費免個資 (免姓名、免通訊，僅性別、年齡、產業、居住地)
(11, 1, 0, 1, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0),
-- 12. 品牌 ESG 承諾意願: 綠色消費者人口特徵 (姓名、性別、年齡、信箱、居住地)
(12, 1, 1, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0),
-- 13. 國際開源技術年會: 專業大會簽到聯絡 (姓名、手機、信箱、所屬產業、職稱)
(13, 1, 1, 0, 0, 0, 0, 1, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0),
-- 14. 虛擬社群互動: 匿名極客社群 (collect_basic_info=0 完全免填)
(14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
-- 15. 智慧物流溫控配送: 電商宅配住址導向 (姓名、手機、市話、信箱、居住地、戶籍地址)
(15, 1, 1, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1),
-- 16. 新能源車充電生活圈: 車主完整輪廓 (姓名、性別、年齡、手機、信箱、產業、居住地)
(16, 1, 1, 1, 1, 0, 0, 1, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0),
-- 17. 企業架構培訓學程: 內部培訓追蹤 (姓名、信箱、職稱、部門、員工編號)
(17, 1, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0),
-- 18. 智慧醫療遠距照護: 醫療生理健康檔案 (姓名、性別、年齡、生日、身分證、手機、身高、體重、居住地)
(18, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0),
-- 19. 都市飲食營養標示: 匿名健康體質調研 (免填姓名，僅性別、年齡、身高、體重、居住地)
(19, 1, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0),
-- 20. 綠色公車通勤月票: 公民免實名意見徵集 (collect_basic_info=0 完全免填)
(20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
-- 21. 數位身分認證 FIDO2: 資安公民實名意願 (姓名、性別、年齡、身分證、手機、信箱、產業、居住地)
(21, 1, 1, 1, 1, 0, 1, 1, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0),
-- 22. 兒童螢幕接觸家長: 親子家庭背景 (姓名、性別、年齡、手機、信箱、居住地)
(22, 1, 1, 1, 1, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0),
-- 23. 獨立遊戲募資參與: 獨立開發者社群 (collect_basic_info=0 完全匿名)
(23, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
-- 24. 咖啡風味品飲偏好: 極簡品味 (免填姓名，僅填寫年齡層與居住縣市)
(24, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0);

INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `image_url`, `is_required`, `sort_order`, `min_rating`, `max_rating`, `created_at`, `updated_at`) VALUES
(41, 5, 'Q1', 'BOOLEAN', '您所屬系所本學期是否已開設至少兩門全英語授課專業必修或選修課程？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(42, 5, 'Q2', 'SINGLE', '在修習全英語專業課程時，您個人感受到的最大挑戰是？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(43, 5, 'Q3', 'MULTI', '您認為下列哪些輔助措施最能實質提升 EMI 學習效果？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(44, 5, 'Q4', 'RATING', '整體而言，您對目前所修習全英語課程之專業知能吸收滿意度評價（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(45, 5, 'Q5', 'DATE', '您預計完成本學期學術論文初稿或專案成果報告的目標日期為何？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(46, 5, 'Q6', 'TIME', '您平日通常在一天中的哪個時段進行全英語文獻閱讀與論文撰寫？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(47, 5, 'Q7', 'TEXT', '請留下您對校方未來推動 EMI 雙語高教政策與教學輔助資源的真心建議：', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(48, 6, 'Q1', 'BOOLEAN', '您在校所學之軟硬體核心技能是否能直接銜接目前實習企業之專案需求？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(49, 6, 'Q2', 'SINGLE', '實習期間企業所指派的職場導師 (Mentor) 對您專業成長的指導頻率為？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(50, 6, 'Q3', 'MULTI', '若畢業後考慮留任原實習企業，下列哪些福利是您的核心評估要素？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(51, 6, 'Q4', 'RATING', '您對目前實習單位所提供的培訓資源與整體工作環境給予幾分評價（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(52, 6, 'Q5', 'DATE', '您與實習企業約定之正式實習合約期滿或轉正考核日期為何？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(53, 6, 'Q6', 'TIME', '您在實習期間每日平均開始投入核心工作與專案開發的時間為？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(54, 6, 'Q7', 'TEXT', '請簡述您認為大專院校在學生進入產業實習前，應優先加強培訓的專業技能或軟實力：', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(55, 7, 'Q1', 'BOOLEAN', '您目前在跨國會議或日常訪談中，是否已將 AI 語音筆記列為主要會議記錄工具？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(56, 7, 'Q2', 'SINGLE', '在使用語音筆記生成摘要時，您最頻繁調用哪一類智慧功能？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(57, 7, 'Q3', 'MULTI', '在行動裝置跨平台同步使用時，您最重視下列哪些效能指標？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(58, 7, 'Q4', 'RATING', '請針對本軟體在多國語言混雜（中英夾雜）時之逐字稿辨識精準度給予評分（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(59, 7, 'Q5', 'DATE', '您首次下載並開始正式將本產品導入團隊日常工作流的日期為？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(60, 7, 'Q6', 'TIME', '您一天當中最常開啟語音筆記記錄會議或整理想法的高峰時段為？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(61, 7, 'Q7', 'TEXT', '若有機會為此軟體新增一項殺手級功能，您最希望我們優先實作什麼？', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(62, 8, 'Q1', 'BOOLEAN', '貴團隊設計系統 (Design Tokens) 是否已成功串接至自動化前端程式碼發布管線？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(63, 8, 'Q2', 'SINGLE', '當十人以上設計師在同一個巨大畫布進行協作時，您最常遇到的卡頓問題是？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(64, 8, 'Q3', 'MULTI', '在升級付費企業方案時，下列哪些管理功能對貴團隊最具吸引力？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(65, 8, 'Q4', 'RATING', '整體而言，您對目前雲端設計平台之版本穩定性與客戶支援滿意度評價（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(66, 8, 'Q5', 'DATE', '貴公司當前簽署之軟體企業授權預計於哪一天進行續約評估？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(67, 8, 'Q6', 'TIME', '您通常每週在團隊進行 Sprint Review 與設計走查 (Design Walkthrough) 的固定時間為？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(68, 8, 'Q7', 'TEXT', '對於即將發布的次世代向量排版引擎，您有任何期望或相容性擔憂嗎？', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(69, 9, 'Q1', 'BOOLEAN', '實施每週兩天遠距工作制度後，您是否感受到工作與個人生活的平衡度顯著改善？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(70, 9, 'Q2', 'SINGLE', '在非同步協作環境中，主管對團隊工作進度的追蹤模式最接近下列何者？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(71, 9, 'Q3', 'MULTI', '您認為公司目前最需要加強哪些混合辦公配套支援方案？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(72, 9, 'Q4', 'RATING', '請針對目前公司在推動混合辦公政策時的透明度與內部信任感評分（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(73, 9, 'Q5', 'DATE', '公司人資團隊預計於哪一天正式公布下半年度升遷與組織架構調整名單？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(74, 9, 'Q6', 'TIME', '您在居家辦公期間，每日最能全神貫注處理高難度深度工作 (Deep Work) 的時段是？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(75, 9, 'Q7', 'TEXT', '請留下您對公司內部跨部門溝通效率與組織文化的具體觀察與建言：', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(76, 10, 'Q1', 'BOOLEAN', '您每週是否能維持至少三次、每次三十分鐘以上的中高強度有氧或肌力運動？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(77, 10, 'Q2', 'SINGLE', '在工作日期間，您每日平均在辦公桌前連續久坐未起身走動的時間約為？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(78, 10, 'Q3', 'MULTI', '在改善日常體能與睡眠品質方面，您目前已主動採取哪些健康行動？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(79, 10, 'Q4', 'RATING', '請針對您最近一個月的整體清晨起床精力充沛程度與睡眠深層品質評分（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(80, 10, 'Q5', 'DATE', '您最近一次進行全身健康檢查或預計進行年度體檢的日期為何？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(81, 10, 'Q6', 'TIME', '在一般上班工作日，您通常晚上入睡的平均時間點是？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(82, 10, 'Q7', 'TEXT', '您目前最想改善的身體疲勞或亞健康問題是什麼？有嘗試過哪些有效的方法？', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(83, 11, 'Q1', 'BOOLEAN', '您在查詢商品評測與購買決策時，是否已優先使用對話式 AI 取代傳統 Google 關鍵字搜尋？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(84, 11, 'Q2', 'SINGLE', '當 AI 搜尋工具在答案中直接標注購買連結時，您的點擊購買意願主要受何者影響？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(85, 11, 'Q3', 'MULTI', '在日常資訊檢索時，下列哪些類型的主題您最信任對話式 AI 的整理能力？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(86, 11, 'Q4', 'RATING', '對於當前主流 AI 搜尋引擎產出答案之客觀性與抗幻覺能力，您的信任程度評分為（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(87, 11, 'Q5', 'DATE', '您預計在下一個雙十一或年中大促購物檔期的正式採購評估日期為？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(88, 11, 'Q6', 'TIME', '您平日最頻繁在社群平台被精準廣告推播打中並產生瀏覽行為的時間為？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(89, 11, 'Q7', 'TEXT', '對於品牌方在 AI 推薦生成結果中置入贊助廣告內容，您有哪些界線與期望？', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(90, 12, 'Q1', 'BOOLEAN', '在商品品質與功能相當的情況下，您是否會優先選購擁有清楚環保碳標籤認證之品牌？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(91, 12, 'Q2', 'SINGLE', '若某品牌承諾商品包裝全面採用 100% 海洋回收塑料，您最高願意支付多少綠色溢價？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(92, 12, 'Q3', 'MULTI', '下列哪些企業永續實踐作為，最能贏得您的品牌好感度並激發自發性社群推薦？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(93, 12, 'Q4', 'RATING', '您對臺灣現階段消費品牌在落實減碳宣傳時的真誠度（杜絕漂綠行銷）評分為（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(94, 12, 'Q5', 'DATE', '您預計參與今年度世界地球日或品牌永續淨灘宣導活動的預定日期為？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(95, 12, 'Q6', 'TIME', '您平日在通訊群組或社群動態中，最常關注環保生活與開箱討論的時段為？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(96, 12, 'Q7', 'TEXT', '請分享一件您近期對特定品牌在環境保護或企業社會責任方面深感驚艷的真實經驗：', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(97, 13, 'Q1', 'BOOLEAN', '本次技術年會全會場是否提供了足夠穩定順暢的高密度 Wi-Fi 連線與電源插座？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(98, 13, 'Q2', 'SINGLE', '在本次年會所有技術議程軌 (Track) 中，您個人收穫最豐碩的主題軌是？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(99, 13, 'Q3', 'MULTI', '若未來繼續參與主辦方舉辦的進階活動，您最渴望看到下列哪種互動形式？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(100, 13, 'Q4', 'RATING', '整體而言，您對本次國際開源技術年會籌備團隊之現場引導與整體滿意度評價（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(101, 13, 'Q5', 'DATE', '您預計在貴公司內部進行技術分享會 (Tech Talk) 轉移年會新知的日期為？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(102, 13, 'Q6', 'TIME', '在年會進行期間，您認為安排主題演講 (Keynote) 最能保持專注的精神高峰時段是？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(103, 13, 'Q7', 'TEXT', '請對年會策劃團隊留下您的真心話或給予明年度議程籌辦的具體改進建議：', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(104, 14, 'Q1', 'BOOLEAN', '您每週是否固定會觀看特定虛擬實況主 (VTuber) 或數位創作者之現場直播超過兩小時？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(105, 14, 'Q2', 'SINGLE', '在支持您喜愛的網路創作者時，您最頻繁採取的實質支持管道為何？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(106, 14, 'Q3', 'MULTI', '您認為數位創作者要長期維持社群忠誠度，最重要的關鍵特質是什麼？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(107, 14, 'Q4', 'RATING', '請針對當前主流創作者平台在防止惡意刷榜與維護友善留言氛圍之機制評分（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(108, 14, 'Q5', 'DATE', '您預計報名參加下一次創作者線下見面會或大型動漫展覽的預算規劃日期為？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(109, 14, 'Q6', 'TIME', '您通常在平日夜晚或週末放鬆時刻，開啟影音直播或社群平台的尖峰時段為？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(110, 14, 'Q7', 'TEXT', '在 AI 生成內容日益氾濫的時代，您對全人工真實創作者的未來生存空間有何看法？', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(111, 15, 'Q1', 'BOOLEAN', '您收件時之冷藏或冷凍食品外箱包裝，是否皆保持低溫硬挺無退冰融化跡象？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(112, 15, 'Q2', 'SINGLE', '當您因訂單缺件或外箱破損聯絡客服時，客服人員在多長時間內完成實質回覆？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(113, 15, 'Q3', 'MULTI', '在選擇生鮮網購平台時，下列哪些售後保障政策最能讓您安心下單？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(114, 15, 'Q4', 'RATING', '整體而言，您對本次物流配送司機之專業服務態度與外送禮儀滿意度評價（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(115, 15, 'Q5', 'DATE', '您下次預計在生鮮宅配平台進行全家每週食材批量採購的配送日期為？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(116, 15, 'Q6', 'TIME', '若物流宅配能自選一小時精準送達區間，您平日白天最便於在家親自簽收的時段是？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(117, 15, 'Q7', 'TEXT', '請留下您對冷鏈物流包裝耗材環保化或宅配配送體驗的任何改進建言：', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(118, 16, 'Q1', 'BOOLEAN', '在您日常主要通勤路徑與住家半徑五公里內，快充站點數量是否能滿足臨時補電需求？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(119, 16, 'Q2', 'SINGLE', '透過車廠專屬行動 App 進行智慧預約回廠定期保養時，最讓您感到便利的體驗是？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(120, 16, 'Q3', 'MULTI', '針對國道長途出遊之充電體驗，您最希望車廠優先推動哪些充電基礎設施改善？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(121, 16, 'Q4', 'RATING', '您對原廠服務中心接待專員與技師之專業診斷解說能力的整體滿意度評價（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(122, 16, 'Q5', 'DATE', '您預計為愛車預約下一次例行保養或原廠軟體大版本 OTA 更新檢測的日期為？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(123, 16, 'Q6', 'TIME', '您日常最常前往原廠或第三方超級充電站進行快速補電的偏好時段為？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(124, 16, 'Q7', 'TEXT', '請留下您對愛車車載作業系統或智慧聯網充電生活圈的未來優化期待：', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(125, 17, 'Q1', 'BOOLEAN', '完成軟體架構微證照課程後，您是否已在團隊的實體產品專案中實踐相關設計模式？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(126, 17, 'Q2', 'SINGLE', '在本次專業培訓學程中，您認為對實戰技能提升最關鍵的學習模組是？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(127, 17, 'Q3', 'MULTI', '為了使同仁能持續保持技術精進，您希望公司未來提供哪些內部技術成長機制？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(128, 17, 'Q4', 'RATING', '您對本次培訓講師之業界實務深度、講義完整性與學程規劃整體滿意度評價（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(129, 17, 'Q5', 'DATE', '您計畫參加下一梯次進階軟體架構認證實體考核的預定日期為？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(130, 17, 'Q6', 'TIME', '在繁忙的衝刺開發週期中，您認為每週安排技術培訓的最佳時段是？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(131, 17, 'Q7', 'TEXT', '請具體描述您在本次架構培訓中印象最深刻的技術收穫或對後續課程的期許：', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(132, 18, 'Q1', 'BOOLEAN', '您或家中長輩目前是否已使用智慧手環或手錶進行 24 小時心率與跌倒自動偵測？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(133, 18, 'Q2', 'SINGLE', '在推廣遠距醫療諮詢門診時，長輩通常在面對哪一項流程時感到最大挫折？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(134, 18, 'Q3', 'MULTI', '您認為提升高齡族群對遠距健康照護科技信賴度的首要功能為何？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(135, 18, 'Q4', 'RATING', '請針對目前臺灣遠距智慧醫療服務在整體便利性與隱私保障之表現給予評分（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(136, 18, 'Q5', 'DATE', '您預計陪伴長輩進行下一季慢性病遠距門診複診或健康諮詢的預定日期為？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(137, 18, 'Q6', 'TIME', '長輩每日在家中最配合量測血壓與常態健康數據紀錄的固定時間為？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(138, 18, 'Q7', 'TEXT', '請留下您對促進智慧醫療平民化、消除長者數位落差的實務觀察與寶貴建言：', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(139, 19, 'Q1', 'BOOLEAN', '您在便利商店或超市購買包裝食品時，是否具備先翻到背面查看營養標示的固定習慣？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(140, 19, 'Q2', 'SINGLE', '在檢視食品營養成分標籤時，下列哪一項數值最常直接左右您的購買意願？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(141, 19, 'Q3', 'MULTI', '在選購健康輕食與健身便當時，您最偏好下列哪些食材組合與認證？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(142, 19, 'Q4', 'RATING', '請針對目前市售健康輕食便當在性價比與飽足感平衡度之整體滿意度評分（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(143, 19, 'Q5', 'DATE', '您為自己設定下一階段飲食體態控制計畫的目標驗收日期為？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(144, 19, 'Q6', 'TIME', '您在工作日最容易產生飢餓感、想選購健康下午茶點心或無糖豆漿的時間為？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(145, 19, 'Q7', 'TEXT', '請分享您在日常忙碌外食生活中，維持營養均衡與抵禦高糖高熱量誘惑的實戰心得：', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(146, 20, 'Q1', 'BOOLEAN', '自公共運輸通勤月票 (TPASS) 實施以來，您個人搭乘大眾運具的頻率是否顯著增加？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(147, 20, 'Q2', 'SINGLE', '您認為目前公共運輸在最後一哩路 (Last Mile) 接駁上最亟需克服的痛點是？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(148, 20, 'Q3', 'MULTI', '若政府計畫擴大月票適用範圍，您最期待納入下列哪些綠色低碳運具？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(149, 20, 'Q4', 'RATING', '請針對目前中央與地方政府推動綠色公共交通政策之整體滿意度給予評分（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(150, 20, 'Q5', 'DATE', '您個人當前使用的公共運輸通勤定期票卡下次自動扣款展延的生效日期為？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(151, 20, 'Q6', 'TIME', '您在工作日出門搭乘第一段大眾運具（捷運、火車或公車）的平均出發時間為？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(152, 20, 'Q7', 'TEXT', '請為主管機關在改善通勤品質或提倡搭乘綠色運具方面提出一項最迫切的政策建言：', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(153, 21, 'Q1', 'BOOLEAN', '您在登入政府數位便民服務時，是否已優先使用行動自然人憑證或指紋生物辨識登入？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(154, 21, 'Q2', 'SINGLE', '在考慮全面採用數位身分認證時，公眾內心最大的資安疑慮通常來自何者？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(155, 21, 'Q3', 'MULTI', '在推動數位身分防偽認證方面，您認為哪些制度設計最能贏得公民信任？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(156, 21, 'Q4', 'RATING', '對於臺灣公部門目前在個人資料保護與資安防禦工程之信賴度，您的評分為（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(157, 21, 'Q5', 'DATE', '您預計至戶政事務所或線上展期自然人憑證與數位證明的到期日期為？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(158, 21, 'Q6', 'TIME', '您平日最常使用數位便民系統（如報稅、勞保退休金試算、健保快易通）的時段為？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(159, 21, 'Q7', 'TEXT', '請從公民權益與科技便民角度，對政府推行次世代數位身分架構留下具體建議：', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(160, 22, 'Q1', 'BOOLEAN', '在您的家庭規範中，是否明確限制學齡孩童平日使用平板或智慧手機的單日上限時長？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(161, 22, 'Q2', 'SINGLE', '孩童在接觸數位螢幕時，最主要投入的內容類型為何？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(162, 22, 'Q3', 'MULTI', '為了培養孩童獨立思考能力，您目前最常採用的課外輔助教養方式包括？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(163, 22, 'Q4', 'RATING', '請針對目前臺灣市面上兒童專用數位讀物與教育 App 之無廣告純淨度評分（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(164, 22, 'Q5', 'DATE', '您為孩子規劃下一次全家露營出遊或實體兒童劇團表演觀賞的預訂日期為？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(165, 22, 'Q6', 'TIME', '在平日就寢前，全家人固定放下 3C 產品、進行親子睡前床邊故事談心的時間點為？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(166, 22, 'Q7', 'TEXT', '請分享您在平衡兒童數位科技接觸與保護視力健康方面最受用的教養心法：', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(167, 23, 'Q1', 'BOOLEAN', '您在過去一年內是否曾在群眾募資平台（如嘖嘖、Kickstarter）贊助過臺灣原創獨立遊戲？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(168, 23, 'Q2', 'SINGLE', '在決定是否贊助一款尚在開發階段的獨立遊戲時，最吸引您的核心亮點為何？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(169, 23, 'Q3', 'MULTI', '您認為獨立遊戲團隊在發行推廣過程中，面臨最嚴峻的現實瓶頸有哪些？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(170, 23, 'Q4', 'RATING', '請針對近兩年臺灣獨立遊戲作品在美術原創性與音樂音效設計之整體表現評分（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(171, 23, 'Q5', 'DATE', '您近期最期待上架支援繁中版本的獨立遊戲大作在 Steam 預計公開的試玩日期為？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(172, 23, 'Q6', 'TIME', '您平日最常瀏覽 Steam 新品節或遊戲實況主播試玩新遊戲的高峰時段為？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(173, 23, 'Q7', 'TEXT', '請為臺灣正默默耕耘的獨立遊戲創作者留下您最真摯的應援留言或改善期望：', NULL, 0, 7, NULL, NULL, NOW(), NOW()),
(174, 24, 'Q1', 'BOOLEAN', '在您日常提神與品飲習慣中，您是否已將手沖單品咖啡取代商業連鎖手搖飲料？', NULL, 1, 1, NULL, NULL, NOW(), NOW()),
(175, 24, 'Q2', 'SINGLE', '在選購精品手沖咖啡豆時，您最偏好的烘焙程度與處理法為下列何者？', NULL, 1, 2, NULL, NULL, NOW(), NOW()),
(176, 24, 'Q3', 'MULTI', '在選擇常態光顧的自家烘焙獨立咖啡館時，下列哪些特質是您的必備條件？（複選）', NULL, 0, 3, NULL, NULL, NOW(), NOW()),
(177, 24, 'Q4', 'RATING', '請針對目前臺灣精品咖啡館在單品手沖定價與杯測風味描述一致性之滿意度評分（1-5分）', NULL, 1, 4, 1, 5, NOW(), NOW()),
(178, 24, 'Q5', 'DATE', '您預計參加下一場臺北國際咖啡展或獨立咖啡市集尋覓新豆的日期為？', NULL, 1, 5, NULL, NULL, NOW(), NOW()),
(179, 24, 'Q6', 'TIME', '在一天當中，您通常享受第一杯手沖香醇黑咖啡喚醒專注力的黃金時間為？', NULL, 1, 6, NULL, NULL, NOW(), NOW()),
(180, 24, 'Q7', 'TEXT', '請分享您在日常手沖沖煮咖啡時，最講究的儀式感細節或最鍾愛的一支私藏豆款：', NULL, 0, 7, NULL, NULL, NOW(), NOW());

INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`, `trigger_question_uid`) VALUES
(66, 41, '是，已有多門專業課程採全英語授課', 1, NULL),
(67, 41, '否，目前仍以雙語或全中文講授為主', 2, NULL),
(68, 42, '專業術語與學術寫作詞彙不足', 1, 'Q3'),
(69, 42, '課堂全英語即時口語發言焦慮', 2, NULL),
(70, 42, '授課教師發音與語速適應困難', 3, NULL),
(71, 42, '同儕分組報告討論效率降低', 4, NULL),
(72, 43, '提供課堂雙語專業術語對照表', 1, NULL),
(73, 43, '配置專業領域助教課後課業輔導', 2, NULL),
(74, 43, '課堂提供即時 AI 語音逐字稿字幕', 3, NULL),
(75, 43, '降低課堂即時發言口試佔分比重', 4, NULL),
(76, 48, '是，學校專業課程與產業需求接軌度極高', 1, NULL),
(77, 48, '否，多數專業工具需至企業重新培訓學習', 2, NULL),
(78, 49, '每週固定進行 1 對 1 程式碼審查與深度指導', 1, 'Q3'),
(79, 49, '每隔雙週進行一次專案進度總結檢視', 2, NULL),
(80, 49, '僅在遭遇技術瓶頸或重大阻礙時主動求助', 3, NULL),
(81, 49, '幾乎全靠自主摸索與官方技術文件研讀', 4, NULL),
(82, 50, '具競爭力之起薪與透明晉升考核機制', 1, NULL),
(83, 50, '彈性混合遠距辦公 (Hybrid Work) 制度', 2, NULL),
(84, 50, '年度教育訓練與國際技術研討會全額公費補助', 3, NULL),
(85, 50, '明確無毒之扁平團隊文化與心理安全感', 4, NULL),
(86, 55, '是，已成為日常工作與會議記錄之必備主力工具', 1, NULL),
(87, 55, '否，仍習慣手動筆記或事後重新聆聽錄音', 2, NULL),
(88, 56, '會議 Action Items 行動指派自動擷取', 1, 'Q3'),
(89, 56, '長篇音訊之五百字核心摘要生成', 2, NULL),
(90, 56, '即時演講之概念思維導圖結構化梳理', 3, NULL),
(91, 56, '跨國發言者身分自動辨識與分段標記', 4, NULL),
(92, 57, '端點間即時同步秒差小於一秒', 1, NULL),
(93, 57, '無網路環境下之離線錄音與本地推論暫存', 2, NULL),
(94, 57, '低電量耗損與後台長時間錄音不中斷', 3, NULL),
(95, 57, '高度跨平台版面自適應與深色模式支援', 4, NULL),
(96, 62, '是，已達成變更自動發布至 NPM / GitHub 倉庫', 1, NULL),
(97, 62, '否，目前仍需工程師手動核對數值並轉譯代碼', 2, NULL),
(98, 63, '畫布平移縮放時圖層重繪延遲 (FPS 下降)', 1, 'Q3'),
(99, 63, '多人游標位置跳動與元件權限衝突鎖定', 2, NULL),
(100, 63, '龐大變體組件庫切換時記憶體佔用爆增', 3, NULL),
(101, 63, '從未感覺卡頓，效能極度流暢穩定', 4, NULL),
(102, 64, '元件版本歷史分支 (Branching) 與衝突合併', 1, NULL),
(103, 64, '單一簽入 (SSO) 與團隊權限細粒度審計日誌', 2, NULL),
(104, 64, '無限量雲端專案備份與資安資料夾隔離', 3, NULL),
(105, 64, '專屬客戶成功經理與 24 小時快速技術支援', 4, NULL),
(106, 69, '是，工作效率提升且通勤壓力大幅降低', 1, NULL),
(107, 69, '否，工作與生活界線變得模糊且訊息焦慮增加', 2, NULL),
(108, 70, '完全以客觀交付成果 (OKRs/KPIs) 為驗收準據', 1, 'Q3'),
(109, 70, '每日要求填寫工作日誌或進行簡短晨會回報', 2, NULL),
(110, 70, '頻繁透過通訊軟體檢查在線狀態與回訊速度', 3, NULL),
(111, 70, '放手自治授權，僅在遭遇重大阻礙時介入', 4, NULL),
(112, 71, '提供人體工學遠距設備補助津貼', 1, NULL),
(113, 71, '全額補助專業線上心理諮商與舒壓課程', 2, NULL),
(114, 71, '建立清晰的非同步溝通不被打擾下班防守線', 3, NULL),
(115, 71, '定期舉辦實體無工作壓力的同仁凝聚聚會', 4, NULL),
(116, 76, '是，每週維持規律運動習慣', 1, NULL),
(117, 76, '否，平時極少運動或因忙碌而中斷', 2, NULL),
(118, 77, '連續坐超過四小時以上', 1, 'Q3'),
(119, 77, '兩至三小時走動喝水或洗手間', 2, NULL),
(120, 77, '每小時皆會起身活動伸展片刻', 3, NULL),
(121, 77, '使用升降桌交替站立工作', 4, NULL),
(122, 78, '戒除或嚴格限制睡前一小時滑手機習慣', 1, NULL),
(123, 78, '每日規律補充綜合維他命或魚油保健食品', 2, NULL),
(124, 78, '購買智慧穿戴裝置持續監控睡眠心率變化', 3, NULL),
(125, 78, '自備減醣減鹽健康便當取代外食高油高鈉', 4, NULL),
(126, 83, '是，已高度依賴對話式 AI 總結重點推薦', 1, NULL),
(127, 83, '否，仍習慣點擊傳統部落格與論壇開箱文', 2, NULL),
(128, 84, 'AI 摘要中是否清楚引述多方客觀權威來源', 1, 'Q3'),
(129, 84, '推薦商品之價格與主流電商平台相比是否實惠', 2, NULL),
(130, 84, 'AI 對產品缺點與限制是否坦承列出', 3, NULL),
(131, 84, '品牌本身之知名度與市場正面口碑', 4, NULL),
(132, 85, '程式碼編寫除錯與開源工具選型比較', 1, NULL),
(133, 85, '海外旅遊行程動線與機票住宿性價比規劃', 2, NULL),
(134, 85, '學術文獻深奧論文核心概念通俗白話解析', 3, NULL),
(135, 85, '複雜電子科技產品規格與橫向評測對比', 4, NULL),
(136, 90, '是，環保理念是極重要的品牌加分項', 1, NULL),
(137, 90, '否，價格與個人偏好仍為絕對決定性因素', 2, NULL),
(138, 91, '完全不願意支付額外費用 (0%)', 1, 'Q3'),
(139, 91, '願意支付 5% 以內之合理環保工藝加價', 2, NULL),
(140, 91, '願意支付 5% ~ 15% 支持綠色轉型', 3, NULL),
(141, 91, '願意支付 15% 以上支持地球永續專利', 4, NULL),
(142, 92, '透明公開產品原物料供應鏈與碳排數據報告', 1, NULL),
(143, 92, '提供舊機回收折抵購物金與循環租賃服務', 2, NULL),
(144, 92, '承諾固定提撥營業額一定比例投入造林復育', 3, NULL),
(145, 92, '徹底杜絕過度耗材包裝與禁用一次性塑膠泡棉', 4, NULL),
(146, 97, '是，會場網路與電力配置極為完善穩定', 1, NULL),
(147, 97, '否，部分演講廳頻繁遭遇斷線或插座不足', 2, NULL),
(148, 98, '雲原生微服務與大型分散式系統架構架構實踐', 1, 'Q3'),
(149, 98, '大語言模型 (LLM) 本地推論微調與智能體設計', 2, NULL),
(150, 98, '跨平台行動端前端效能極限優化與微前端', 3, NULL),
(151, 98, '開源社群營運、授權法規與資安供應鏈防禦', 4, NULL),
(152, 99, '閉門實作黑客松 (Hackathon) 與專家即時評審', 1, NULL),
(153, 99, '資深架構師一對一架構諮詢門診 (Office Hour)', 2, NULL),
(154, 99, '會前深水區實戰工作坊 (Pre-event Workshop)', 3, NULL),
(155, 99, '非正式講者夜間沙龍聚會與無限制輕鬆交流', 4, NULL),
(156, 104, '是，為特定創作者之忠實支持與常態觀眾', 1, NULL),
(157, 104, '否，多半僅在社群動態隨機刷到短影音精華', 2, NULL),
(158, 105, '加入創作者頻道專屬付費月度會員', 1, 'Q3'),
(159, 105, '直播過程中發送超級留言 (Super Chat) 打賞', 2, NULL),
(160, 105, '購買官方限量實體周邊商品與聯名紀念品', 3, NULL),
(161, 105, '純粹按讚分享留言衝刺社群演算法熱度', 4, NULL),
(162, 106, '穩定高頻且品質出眾的原創內容產出', 1, NULL),
(163, 106, '高度真誠且具親和力之即時雙向社群互動', 2, NULL),
(164, 106, '風格鮮明且不易被他人取代之個人獨特魅力', 3, NULL),
(165, 106, '具備高道德自律與健康正向的價值觀引領', 4, NULL),
(166, 111, '是，保冷溫控非常良好且無退冰情形', 1, NULL),
(167, 111, '否，收件時已有些微退冰軟化或外箱受潮', 2, NULL),
(168, 112, '十分鐘內由在線專人即時接聽並妥處', 1, 'Q3'),
(169, 112, '一小時內透過官方通訊軟體完成退款確認', 2, NULL),
(170, 112, '當日下班前收到信件正式致歉與補寄通知', 3, NULL),
(171, 112, '超過一天以上才收到制式罐頭訊息回應', 4, NULL),
(172, 113, '退冰或變質保證 100% 全額無條件退款退貨', 1, NULL),
(173, 113, '即時 GPS 物流車廂溫控數據即時查詢追蹤', 2, NULL),
(174, 113, '配送前司機以簡訊或電話精準預約送達時段', 3, NULL),
(175, 113, '提供智慧溫控自提櫃 24 小時隨時取件服務', 4, NULL),
(176, 118, '是，充電網絡密佈且極少排隊等待', 1, NULL),
(177, 118, '否，尖峰時段充電樁時常客滿或站點偏遠', 2, NULL),
(178, 119, '即時透明顯示所有保養工單項目與預估報價', 1, 'Q3'),
(179, 119, '即時查詢各服務廠當日剩餘可用預約時段', 2, NULL),
(180, 119, '施工進度即時推播包含現場師傅維修照片', 3, NULL),
(181, 119, 'App 整合數位電子錢包無感一鍵扣款', 4, NULL),
(182, 120, '國道休息站增設超高速 360kW 旗艦充電樁', 1, NULL),
(183, 120, '建立佔位超時罰款機制防止燃油車或充飽佔用', 2, NULL),
(184, 120, '跨品牌第三方充電站點漫遊互通一卡整合', 3, NULL),
(185, 120, '車載導航即時智慧規劃沿途最優補電站點', 4, NULL),
(186, 125, '是，已成功導入實務架構重構與模組劃分', 1, NULL),
(187, 125, '否，目前仍受限於既有遺留系統 (Legacy) 包袱', 2, NULL),
(188, 126, '領域驅動設計 (DDD) 戰略與戰術建模工作坊', 1, 'Q3'),
(189, 126, '分散式事務一致性 (Saga/Outbox) 實戰演練', 2, NULL),
(190, 126, 'Container 容器化與 Kubernetes 微服務集群編排', 3, NULL),
(191, 126, '全自動 CI/CD 測試部署管線與金絲雀發布實戰', 4, NULL),
(192, 127, '每月固定舉辦技術架構讀書會與沙盒實作', 1, NULL),
(193, 127, '資深工程師內部導師領航計畫 (Mentorship)', 2, NULL),
(194, 127, '提撥個人年度自由支配之技術書籍購置基金', 3, NULL),
(195, 127, '補助考取國際雲端大廠 (AWS/GCP/Azure) 專業認證', 4, NULL),
(196, 132, '是，已持續配戴並串聯家屬緊急聯絡通知', 1, NULL),
(197, 132, '否，長輩排斥穿戴電子設備或因操作過於複雜', 2, NULL),
(198, 133, '視訊連線操作與麥克風鏡頭授權設定障礙', 1, 'Q3'),
(199, 133, '數位健保卡與虛擬身分認證刷卡不易通過', 2, NULL),
(200, 133, '無法與熟悉的主治醫師面對面望聞問切不安感', 3, NULL),
(201, 133, '藥局宅配送藥時效與慢箋處方確認困惑', 4, NULL),
(202, 134, '大字體高對比與純語音對話操控極簡介面', 1, NULL),
(203, 134, '異常心律或血氧過低時自動通報 119 與家屬', 2, NULL),
(204, 134, '與地方區域醫院遠距門診掛號系統深度串接', 3, NULL),
(205, 134, '每週自動寄發以繁中白話文撰寫之健康評估報表', 4, NULL),
(206, 139, '是，習慣先查看蛋白質、熱量與糖分含量', 1, NULL),
(207, 139, '否，通常只看正面包裝名稱、外觀吸引力與價格', 2, NULL),
(208, 140, '總精製添加糖含量 (Sugar)', 1, 'Q3'),
(209, 140, '總熱量大卡 (Calories)', 2, NULL),
(210, 140, '每份蛋白質克數 (Protein)', 3, NULL),
(211, 140, '飽和脂肪與鈉含量 (Sodium)', 4, NULL),
(212, 141, '低溫舒肥雞胸肉配綜合水煮根莖時蔬', 1, NULL),
(213, 141, '高纖非基因改造藜麥毛豆複合全穀主食', 2, NULL),
(214, 141, '嚴格少油低鈉與無添加人工香料防腐劑', 3, NULL),
(215, 141, '具備潔淨標籤 (Clean Label) 雙認證標章', 4, NULL),
(216, 146, '是，已成為日常上下班與假日出遊之第一首選', 1, NULL),
(217, 146, '否，仍以自駕汽車或騎乘機車通勤為主', 2, NULL),
(218, 147, '尖峰時段捷運站周邊共享單車無車可借或無位可還', 1, 'Q3'),
(219, 147, '接駁公車班次間距過長且動態預估時間不準確', 2, NULL),
(220, 147, '轉乘步行通道動線缺乏完善遮雨防曬人行步道', 3, NULL),
(221, 147, '不同縣市運具間之電子票證整合仍有小瑕疵', 4, NULL),
(222, 148, '共享電動機車 (WeMo/GoShare/iRent) 每日免費分鐘數', 1, NULL),
(223, 148, '臺鐵自強號與高鐵全線有限度跨區搭乘優惠', 2, NULL),
(224, 148, '偏遠地區預約型小黃公車與偏鄉共享交通服務', 3, NULL),
(225, 148, '綠能觀光渡輪與低碳遊園電動巡迴接駁車', 4, NULL),
(226, 153, '是，已全面啟用免密碼生物辨識快速通關', 1, NULL),
(227, 153, '否，仍偏好使用傳統帳號密碼搭配簡訊驗證碼', 2, NULL),
(228, 154, '擔心中央資料庫遭受駭客滲透導致生物特徵外洩', 1, 'Q3'),
(229, 154, '遺失手機或硬體金鑰時之緊急身分找回程序繁瑣', 2, NULL),
(230, 154, '公私部門不同系統間個人行蹤軌跡遭到交叉比對', 3, NULL),
(231, 154, '長輩與數位弱勢族群因操作複雜面臨數位排擠', 4, NULL),
(232, 155, '個資調閱紀錄完全透明化，民眾可隨時查詢誰調閱過', 1, NULL),
(233, 155, '全面落實零信任架構 (Zero Trust) 最小權限授權', 2, NULL),
(234, 155, '建立跨部門獨立第三方個資隱私監督保護委員會', 3, NULL),
(235, 155, '立法明文保障民眾有權選擇退出並維持實體紙本替代途徑', 4, NULL),
(236, 160, '是，嚴格落實限時規定（每日 30 至 60 分鐘以內）', 1, NULL),
(237, 160, '否，多半彈性處置或僅於特定完成作業後開放', 2, NULL),
(238, 161, '互動式英語學習與程式啟蒙益智教育 App', 1, 'Q3'),
(239, 161, '短影音平台 (YouTube Shorts / 串流影音)', 2, NULL),
(240, 161, '多人在線開放沙盒手遊 (如 Roblox/Minecraft)', 3, NULL),
(241, 161, '學校指定之線上遠距教學平台與數位作業', 4, NULL),
(242, 162, '每週固定進行實體繪本或科普書籍親子共讀', 1, NULL),
(243, 162, '週末安排全日戶外大自然探索與體育運動', 2, NULL),
(244, 162, '帶領參與博物館、科學館沉浸式手作互動工作坊', 3, NULL),
(245, 162, '共同動手烹飪做家事並討論生活生活常識', 4, NULL),
(246, 167, '是，曾熱心參與早期群募搶先體驗支援', 1, NULL),
(247, 167, '否，多半等待作品正式於 Steam 發售後才評估購買', 2, NULL),
(248, 168, '極具原創性之世界觀美術風格與本土文化故事敘事', 1, 'Q3'),
(249, 168, '獨創具深度且富有挑戰性之核心遊玩機制 (Gameplay)', 2, NULL),
(250, 168, '可立即免費下載體驗之高品質試玩版 Demo', 3, NULL),
(251, 168, '開發團隊過往作品之良好口碑與透明定期日誌回報', 4, NULL),
(252, 169, '發行預算匱乏，難以在全球各大社群平台購買足夠曝光', 1, NULL),
(253, 169, '外語本地化 (Localization) 翻譯品質與跨文化行銷挑戰', 2, NULL),
(254, 169, '遊戲核心程式最佳化與跨主機平台 (Switch/PS5) 移植經驗不足', 3, NULL),
(255, 169, '社群酸民過度苛責批評與負面評價之心理調適壓力', 4, NULL),
(256, 174, '是，已成為日常不可或缺之精品咖啡愛好者', 1, NULL),
(257, 174, '否，仍以平價手搖茶飲或超商大眾美式咖啡為主', 2, NULL),
(258, 175, '淺焙花果香調 (衣索比亞水洗 / 耶加雪菲)', 1, 'Q3'),
(259, 175, '中深焙堅果黑巧克力厚實可可調 (哥倫比亞/曼特寧)', 2, NULL),
(260, 175, '特殊發酵處理法 (厭氧雙重發酵/百香果酒香蜜處理)', 3, NULL),
(261, 175, '義式配方深焙豆適合搭配燕麥奶製作拿鐵', 4, NULL),
(262, 176, '咖啡師具備專業 CQI Q Grader 國際杯測師認證', 1, NULL),
(263, 176, '店內環境安靜舒適、採光明亮且配置足夠筆電充電插座', 2, NULL),
(264, 176, '自備環保隨行杯提供實質五至十元折抵優惠', 3, NULL),
(265, 176, '定期舉辦咖啡杯測會 (Cupping) 與手沖技巧交流', 4, NULL);

INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`) VALUES
(41, 5, '192.168.1.81', 110, '2026-10-11 10:25:00'),
(42, 5, '192.168.1.82', 135, '2026-10-12 13:25:00'),
(43, 5, '192.168.1.83', 160, '2026-10-13 16:25:00'),
(44, 6, '192.168.1.84', 110, '2026-10-11 10:25:00'),
(45, 6, '192.168.1.85', 135, '2026-10-12 13:25:00'),
(46, 6, '192.168.1.86', 160, '2026-10-13 16:25:00'),
(47, 7, '192.168.1.87', 110, '2026-10-11 10:25:00'),
(48, 7, '192.168.1.88', 135, '2026-10-12 13:25:00'),
(49, 7, '192.168.1.89', 160, '2026-10-13 16:25:00'),
(50, 8, '192.168.1.90', 110, '2026-10-11 10:25:00'),
(51, 8, '192.168.1.91', 135, '2026-10-12 13:25:00'),
(52, 8, '192.168.1.92', 160, '2026-10-13 16:25:00'),
(53, 9, '192.168.1.93', 110, '2026-10-11 10:25:00'),
(54, 9, '192.168.1.94', 135, '2026-10-12 13:25:00'),
(55, 9, '192.168.1.95', 160, '2026-10-13 16:25:00'),
(56, 10, '192.168.1.96', 110, '2026-10-11 10:25:00'),
(57, 10, '192.168.1.97', 135, '2026-10-12 13:25:00'),
(58, 10, '192.168.1.98', 160, '2026-10-13 16:25:00'),
(59, 11, '192.168.1.99', 110, '2026-10-11 10:25:00'),
(60, 11, '192.168.1.100', 135, '2026-10-12 13:25:00'),
(61, 11, '192.168.1.101', 160, '2026-10-13 16:25:00'),
(62, 12, '192.168.1.102', 110, '2026-10-11 10:25:00'),
(63, 12, '192.168.1.103', 135, '2026-10-12 13:25:00'),
(64, 12, '192.168.1.104', 160, '2026-10-13 16:25:00'),
(65, 13, '192.168.1.105', 110, '2026-10-11 10:25:00'),
(66, 13, '192.168.1.106', 135, '2026-10-12 13:25:00'),
(67, 13, '192.168.1.107', 160, '2026-10-13 16:25:00'),
(68, 14, '192.168.1.108', 110, '2026-10-11 10:25:00'),
(69, 14, '192.168.1.109', 135, '2026-10-12 13:25:00'),
(70, 14, '192.168.1.110', 160, '2026-10-13 16:25:00'),
(71, 15, '192.168.1.111', 110, '2026-10-11 10:25:00'),
(72, 15, '192.168.1.112', 135, '2026-10-12 13:25:00'),
(73, 15, '192.168.1.113', 160, '2026-10-13 16:25:00'),
(74, 16, '192.168.1.114', 110, '2026-10-11 10:25:00'),
(75, 16, '192.168.1.115', 135, '2026-10-12 13:25:00'),
(76, 16, '192.168.1.116', 160, '2026-10-13 16:25:00'),
(77, 17, '192.168.1.117', 110, '2026-10-11 10:25:00'),
(78, 17, '192.168.1.118', 135, '2026-10-12 13:25:00'),
(79, 17, '192.168.1.119', 160, '2026-10-13 16:25:00'),
(80, 18, '192.168.1.120', 110, '2026-10-11 10:25:00'),
(81, 18, '192.168.1.121', 135, '2026-10-12 13:25:00'),
(82, 18, '192.168.1.122', 160, '2026-10-13 16:25:00'),
(83, 19, '192.168.1.123', 110, '2026-10-11 10:25:00'),
(84, 19, '192.168.1.124', 135, '2026-10-12 13:25:00'),
(85, 19, '192.168.1.125', 160, '2026-10-13 16:25:00'),
(86, 20, '192.168.1.126', 110, '2026-10-11 10:25:00'),
(87, 20, '192.168.1.127', 135, '2026-10-12 13:25:00'),
(88, 20, '192.168.1.128', 160, '2026-10-13 16:25:00'),
(89, 21, '192.168.1.129', 110, '2026-10-11 10:25:00'),
(90, 21, '192.168.1.130', 135, '2026-10-12 13:25:00'),
(91, 21, '192.168.1.131', 160, '2026-10-13 16:25:00'),
(92, 22, '192.168.1.132', 110, '2026-10-11 10:25:00'),
(93, 22, '192.168.1.133', 135, '2026-10-12 13:25:00'),
(94, 22, '192.168.1.134', 160, '2026-10-13 16:25:00'),
(95, 23, '192.168.1.135', 110, '2026-10-11 10:25:00'),
(96, 23, '192.168.1.136', 135, '2026-10-12 13:25:00'),
(97, 23, '192.168.1.137', 160, '2026-10-13 16:25:00'),
(98, 24, '192.168.1.138', 110, '2026-10-11 10:25:00'),
(99, 24, '192.168.1.139', 135, '2026-10-12 13:25:00'),
(100, 24, '192.168.1.140', 160, '2026-10-13 16:25:00');

INSERT INTO `submission_basic_infos` (`id`, `submission_id`, `name`, `gender`, `age_group`, `phone`, `email`, `industry`, `residence`) VALUES
(41, 41, '王彥鈞', 1,  '25-34歲', NULL, 'yj.wang@uni.edu.tw', '教育與研究', '臺北市大安區'),
(42, 42, '張書瑜', 2,  '18-24歲', NULL, 'sy.chang@ntu.edu.tw', '大專院校學生', '新北市中和區'),
(43, 43, '李威霆', 1,  '18-24歲', NULL, 'wt.lee@nccu.edu.tw', '大專院校學生', '桃園市中壢區'),
(44, 44, '蔡佩容', 2,  '20-24歲', '0943445566', 'pj.tsai@tech.edu.tw', '電子資訊業', '新竹市東區'),
(45, 45, '劉家豪', 1,  '20-24歲', '0954556677', 'jh.liu@semi.com.tw', '半導體製造業', '臺中市西屯區'),
(46, 46, '陳品妍', 2,  '20-24歲', '0965667788', 'py.chen@poly.edu.tw', '精密製造業', '高雄市前鎮區'),
(47, 50, '周子安', 1,  '30-39歲', NULL, 'za.chou@studio.design', '數位創意設計', '臺北市信義區'),
(48, 51, '趙若涵', 2,  '25-34歲', NULL, 'rh.chao@uxlab.io', '軟體網路業', '新北市新店區'),
(49, 52, '廖建宏', 1,  '35-44歲', NULL, 'jh.liao@agency.com', '整合行銷設計', '臺中市南屯區'),
(50, 53, '徐詠晴', 2,  '28-35歲', NULL, 'yc.hsu@global.corp', '跨國科技業', '臺北市中山區'),
(51, 54, '謝承恩', 1,  '35-45歲', NULL, 'ce.hsieh@cloud.org', '資訊服務業', '桃園市桃園區'),
(52, 55, '郭婷雅', 2,  '25-34歲', NULL, 'ty.kuo@fintech.tw', '數位金融業', '新北市三重區'),
(53, 56, NULL, 1,  '30-39歲', NULL, NULL, '金融保險業', '臺北市松山區'),
(54, 57, NULL, 2,  '25-34歲', NULL, NULL, '軟體工程師', '新北市板橋區'),
(55, 58, NULL, 1,  '40-49歲', NULL, NULL, '律師會計業', '臺中市西屯區'),
(56, 59, NULL, 2,  '20-29歲', NULL, NULL, '零售電商', '臺北市信義區'),
(57, 60, NULL, 1,  '30-39歲', NULL, NULL, '製造業', '臺南市東區'),
(58, 61, NULL, 2,  '40-49歲', NULL, NULL, '醫療生技', '高雄市新興區'),
(59, 62, '曾致遠', 1,  '25-34歲', NULL, 'cy.tseng@green.org', '綠能科技業', '臺南市東區'),
(60, 63, '潘郁璇', 2,  '18-24歲', NULL, 'yh.pan@esg.tw', '大專院校學生', '臺北市文山區'),
(61, 64, '邱柏翔', 1,  '35-44歲', NULL, 'ps.chiu@retail.com', '連鎖零售業', '高雄市左營區'),
(62, 65, '賴孟潔', 2,  '28-35歲', '0985667788', 'mj.lai@opensource.tw', '軟體開源生態', '臺北市松山區'),
(63, 66, '楊哲睿', 1,  '24-30歲', '0916778899', 'cr.yang@dev.io', '雲端架構開發', '新竹縣竹北市'),
(64, 67, '許雅婷', 2,  '30-38歲', '0927889900', 'yt.hsu@ai.tech', '演算法工程', '臺中市北區'),
(65, 71, '何冠宇', 1,  '30-45歲', '0938990011', 'ky.ho@logistics.tw', '物流供應鏈', '新北市汐止區'),
(66, 72, '柯怡安', 2,  '25-35歲', '0949001122', 'ya.ko@ecom.tw', '電子商務運營', '桃園市蘆竹區'),
(67, 73, '盧柏儒', 1,  '40-55歲', '0950112233', 'pr.lu@fresh.tw', '生鮮冷鏈批發', '彰化縣員林市'),
(68, 74, '葉語彤', 2,  '30-40歲', '0961223344', 'yt.yeh@ev.owner.tw', '電子製造研發', '臺中市南區'),
(69, 75, '方宇軒', 1,  '35-48歲', '0972334455', 'yh.fang@auto.tw', '綠色運輸顧問', '臺北市內湖區'),
(70, 76, '蕭雅筑', 2,  '28-38歲', '0983445566', 'yc.hsiao@energy.tw', '綠能電網工程', '嘉義市東區'),
(71, 77, '沈冠廷', 1,  '32-42歲', NULL, 'kt.shen@enterprise.tw', '企業架構核心組', '臺北市中正區'),
(72, 78, '胡若薇', 2,  '26-34歲', NULL, 'rw.hu@saas.tw', '分散式系統組', '新北市土城區'),
(73, 79, '余宏偉', 1,  '38-50歲', NULL, 'hw.yu@corp.tw', '系統整合技術處', '高雄市苓雅區'),
(74, 80, '鄧涵予', 2,  '55-64歲', '0947889900', NULL, '退休公職長輩', '屏東縣屏東市'),
(75, 81, '彭文瑞', 1,  '65歲以上', '0958990011', NULL, '高齡樂活照護', '苗栗縣頭份市'),
(76, 82, '薛詠真', 2,  '45-54歲', '0969001122', NULL, '家庭長照照護者', '宜蘭縣羅東鎮'),
(77, 83, NULL, 2,  '25-34歲', NULL, NULL, '廣告公關業', '臺北市大安區'),
(78, 84, NULL, 1,  '35-44歲', NULL, NULL, '金融投顧業', '新北市新莊區'),
(79, 85, NULL, 2,  '18-24歲', NULL, NULL, '大專院校學生', '臺中市南區'),
(80, 89, '蘇柏凱', 1,  '25-34歲', '0970112233', 'pk.su@cyber.gov.tw', '資安與公共治理', '臺北市大同區'),
(81, 90, '鐘孟容', 2,  '35-44歲', '0981223344', 'mj.chung@civic.tw', '公共行政法治', '新北市永和區'),
(82, 91, '簡宏宇', 1,  '20-29歲', '0912334455', 'hy.chien@crypto.tw', '密碼學與資安研發', '新竹市北區'),
(83, 92, '羅佳敏', 2,  '30-39歲', '0923445566', 'cm.lo@parent.tw', '全職自由接案家長', '桃園市八德區'),
(84, 93, '董家瑋', 1,  '35-45歲', '0934556677', 'cw.tung@edu.tw', '中等教育教師', '臺中市西區'),
(85, 94, '白芷涵', 2,  '32-40歲', '0945667788', 'ch.pai@kids.tw', '幼兒親職推廣', '臺南市永康區'),
(86, 98, NULL, NULL, '25-34歲', NULL, NULL, '自由業', '臺北市中正區'),
(87, 99, NULL, NULL, '35-44歲', NULL, NULL, '製造工程', '新竹市東區'),
(88, 100, NULL, NULL, '45-54歲', NULL, NULL, '商業貿易', '臺中市南屯區');

INSERT INTO `submission_answers` (`id`, `submission_id`, `question_id`, `option_id`, `text_answer`, `rating_value`) VALUES
(421, 41, 41, 66, NULL, NULL),
(422, 41, 42, 68, NULL, NULL),
(423, 41, 43, 72, NULL, NULL),
(424, 41, 43, 73, NULL, NULL),
(425, 41, 44, NULL, NULL, 5),
(426, 41, 45, NULL, '2026-06-15', NULL),
(427, 41, 46, NULL, '09:30', NULL),
(428, 41, 47, NULL, '教授語速適中但專業術語還是希望能有中英對照講義，整體吸收還行', NULL),
(429, 42, 41, 66, NULL, NULL),
(430, 42, 42, 69, NULL, NULL),
(431, 42, 43, 72, NULL, NULL),
(432, 42, 44, NULL, NULL, 4),
(433, 42, 45, NULL, '2026-08-20', NULL),
(434, 42, 46, NULL, '14:15', NULL),
(435, 42, 47, NULL, '希望能增加更多小組口說討論互動時間，不然大部分時間還是單向聽講', NULL),
(436, 43, 41, 67, NULL, NULL),
(437, 43, 42, 68, NULL, NULL),
(438, 43, 43, 72, NULL, NULL),
(439, 43, 43, 73, NULL, NULL),
(440, 43, 44, NULL, NULL, 5),
(441, 43, 45, NULL, '2026-10-05', NULL),
(442, 43, 46, NULL, '20:45', NULL),
(443, 43, 47, NULL, NULL, NULL),
(444, 44, 48, 76, NULL, NULL),
(445, 44, 49, 78, NULL, NULL),
(446, 44, 50, 82, NULL, NULL),
(447, 44, 50, 83, NULL, NULL),
(448, 44, 51, NULL, NULL, 5),
(449, 44, 52, NULL, '2026-06-15', NULL),
(450, 44, 53, NULL, '09:30', NULL),
(451, 44, 54, NULL, '希望能提前安排企業參訪與專題實作，比較不會到實習現場才手忙腳亂', NULL),
(452, 45, 48, 76, NULL, NULL),
(453, 45, 49, 79, NULL, NULL),
(454, 45, 50, 82, NULL, NULL),
(455, 45, 51, NULL, NULL, 4),
(456, 45, 52, NULL, '2026-08-20', NULL),
(457, 45, 53, NULL, '14:15', NULL),
(458, 45, 54, NULL, '無，實習津貼如果能再提高一點更好', NULL),
(459, 46, 48, 77, NULL, NULL),
(460, 46, 49, 78, NULL, NULL),
(461, 46, 50, 82, NULL, NULL),
(462, 46, 50, 83, NULL, NULL),
(463, 46, 51, NULL, NULL, 5),
(464, 46, 52, NULL, '2026-10-05', NULL),
(465, 46, 53, NULL, '20:45', NULL),
(466, 46, 54, NULL, '普通，主要看帶領主管的風格', NULL),
(467, 47, 55, 86, NULL, NULL),
(468, 47, 56, 88, NULL, NULL),
(469, 47, 57, 92, NULL, NULL),
(470, 47, 57, 93, NULL, NULL),
(471, 47, 58, NULL, NULL, 5),
(472, 47, 59, NULL, '2026-06-15', NULL),
(473, 47, 60, NULL, '09:30', NULL),
(474, 47, 61, NULL, '會議逐字稿辨識中英混雜時常出錯，希望語意斷句與專有名詞校正能更聰明', NULL),
(475, 48, 55, 86, NULL, NULL),
(476, 48, 56, 89, NULL, NULL),
(477, 48, 57, 92, NULL, NULL),
(478, 48, 58, NULL, NULL, 4),
(479, 48, 59, NULL, '2026-08-20', NULL),
(480, 48, 60, NULL, '14:15', NULL),
(481, 48, 61, NULL, '好用！希望能推出 Apple Watch 錄音一鍵同步與背景上傳', NULL),
(482, 49, 55, 87, NULL, NULL),
(483, 49, 56, 88, NULL, NULL),
(484, 49, 57, 92, NULL, NULL),
(485, 49, 57, 93, NULL, NULL),
(486, 49, 58, NULL, NULL, 5),
(487, 49, 59, NULL, '2026-10-05', NULL),
(488, 49, 60, NULL, '20:45', NULL),
(489, 49, 61, NULL, '介面滿順的，但匯出 PDF 格式偶爾會跑掉', NULL),
(490, 50, 62, 96, NULL, NULL),
(491, 50, 63, 98, NULL, NULL),
(492, 50, 64, 102, NULL, NULL),
(493, 50, 64, 103, NULL, NULL),
(494, 50, 65, NULL, NULL, 5),
(495, 50, 66, NULL, '2026-06-15', NULL),
(496, 50, 67, NULL, '09:30', NULL),
(497, 50, 68, NULL, '向量羽化效能在大檔案時會延遲卡頓，希望新引擎能強化 GPU 渲染加速', NULL),
(498, 51, 62, 96, NULL, NULL),
(499, 51, 63, 99, NULL, NULL),
(500, 51, 64, 102, NULL, NULL),
(501, 51, 65, NULL, NULL, 4),
(502, 51, 66, NULL, '2026-08-20', NULL),
(503, 51, 67, NULL, '14:15', NULL),
(504, 51, 68, NULL, '沒意見，快點開放外掛市集跟社群套件庫就好', NULL),
(505, 52, 62, 97, NULL, NULL),
(506, 52, 63, 98, NULL, NULL),
(507, 52, 64, 102, NULL, NULL),
(508, 52, 64, 103, NULL, NULL),
(509, 52, 65, NULL, NULL, 5),
(510, 52, 66, NULL, '2026-10-05', NULL),
(511, 52, 67, NULL, '20:45', NULL),
(512, 52, 68, NULL, NULL, NULL),
(513, 53, 69, 106, NULL, NULL),
(514, 53, 70, 108, NULL, NULL),
(515, 53, 71, 112, NULL, NULL),
(516, 53, 71, 113, NULL, NULL),
(517, 53, 72, NULL, NULL, 5),
(518, 53, 73, NULL, '2026-06-15', NULL),
(519, 53, 74, NULL, '09:30', NULL),
(520, 53, 75, NULL, '希望週五能統一試行無會議日 (No Meeting Friday)，讓研發專注衝刺程式碼', NULL),
(521, 54, 69, 106, NULL, NULL),
(522, 54, 70, 109, NULL, NULL),
(523, 54, 71, 112, NULL, NULL),
(524, 54, 72, NULL, NULL, 4),
(525, 54, 73, NULL, '2026-08-20', NULL),
(526, 54, 74, NULL, '14:15', NULL),
(527, 54, 75, NULL, '在家工作效率滿高的，主管的信任感與明確的交付驗收標準最重要', NULL),
(528, 55, 69, 107, NULL, NULL),
(529, 55, 70, 108, NULL, NULL),
(530, 55, 71, 112, NULL, NULL),
(531, 55, 71, 113, NULL, NULL),
(532, 55, 72, NULL, NULL, 5),
(533, 55, 73, NULL, '2026-10-05', NULL),
(534, 55, 74, NULL, '20:45', NULL),
(535, 55, 75, NULL, '還行，希望每年有遠距辦公設備採購補助款', NULL),
(536, 56, 76, 116, NULL, NULL),
(537, 56, 77, 118, NULL, NULL),
(538, 56, 78, 122, NULL, NULL),
(539, 56, 78, 123, NULL, NULL),
(540, 56, 79, NULL, NULL, 5),
(541, 56, 80, NULL, '2026-06-15', NULL),
(542, 56, 81, NULL, '09:30', NULL),
(543, 56, 82, NULL, '肩膀脖子很容易僵硬痠痛，最近開始嘗試站立式辦公桌有稍微改善', NULL),
(544, 57, 76, 116, NULL, NULL),
(545, 57, 77, 119, NULL, NULL),
(546, 57, 78, 122, NULL, NULL),
(547, 57, 79, NULL, NULL, 4),
(548, 57, 80, NULL, '2026-08-20', NULL),
(549, 57, 81, NULL, '14:15', NULL),
(550, 57, 82, NULL, '每天專案趕進度很難不熬夜，沒什麼好說的，需要充足睡眠', NULL),
(551, 58, 76, 117, NULL, NULL),
(552, 58, 77, 118, NULL, NULL),
(553, 58, 78, 122, NULL, NULL),
(554, 58, 78, 123, NULL, NULL),
(555, 58, 79, NULL, NULL, 5),
(556, 58, 80, NULL, '2026-10-05', NULL),
(557, 58, 81, NULL, '20:45', NULL),
(558, 58, 82, NULL, '？？？就想早點下班運動！', NULL),
(559, 59, 83, 126, NULL, NULL),
(560, 59, 84, 128, NULL, NULL),
(561, 59, 85, 132, NULL, NULL),
(562, 59, 85, 133, NULL, NULL),
(563, 59, 86, NULL, NULL, 5),
(564, 59, 87, NULL, '2026-06-15', NULL),
(565, 59, 88, NULL, '09:30', NULL),
(566, 59, 89, NULL, '贊助內容如果標記清楚、不偽裝成客觀搜尋結果就可以接受，最忌諱誤導', NULL),
(567, 60, 83, 126, NULL, NULL),
(568, 60, 84, 129, NULL, NULL),
(569, 60, 85, 132, NULL, NULL),
(570, 60, 86, NULL, NULL, 4),
(571, 60, 87, NULL, '2026-08-20', NULL),
(572, 60, 88, NULL, '14:15', NULL),
(573, 60, 89, NULL, '只要答案正確有用，稍微有精準推薦我是完全可以接受的', NULL),
(574, 61, 83, 127, NULL, NULL),
(575, 61, 84, 128, NULL, NULL),
(576, 61, 85, 132, NULL, NULL),
(577, 61, 85, 133, NULL, NULL),
(578, 61, 86, NULL, NULL, 5),
(579, 61, 87, NULL, '2026-10-05', NULL),
(580, 61, 88, NULL, '20:45', NULL),
(581, 61, 89, NULL, '無', NULL),
(582, 62, 90, 136, NULL, NULL),
(583, 62, 91, 138, NULL, NULL),
(584, 62, 92, 142, NULL, NULL),
(585, 62, 92, 143, NULL, NULL),
(586, 62, 93, NULL, NULL, 5),
(587, 62, 94, NULL, '2026-06-15', NULL),
(588, 62, 95, NULL, '09:30', NULL),
(589, 62, 96, NULL, '某運動品牌鞋盒直接改用 100% 再生瓦楞紙且完全無塑膠封箱膠帶，深感佩服', NULL),
(590, 63, 90, 136, NULL, NULL),
(591, 63, 91, 139, NULL, NULL),
(592, 63, 92, 142, NULL, NULL),
(593, 63, 93, NULL, NULL, 4),
(594, 63, 94, NULL, '2026-08-20', NULL),
(595, 63, 95, NULL, '14:15', NULL),
(596, 63, 96, NULL, '大部分感覺都在漂綠行銷，希望碳足跡第三方認證能更透明公正', NULL),
(597, 64, 90, 137, NULL, NULL),
(598, 64, 91, 138, NULL, NULL),
(599, 64, 92, 142, NULL, NULL),
(600, 64, 92, 143, NULL, NULL),
(601, 64, 93, NULL, NULL, 5),
(602, 64, 94, NULL, '2026-10-05', NULL),
(603, 64, 95, NULL, '20:45', NULL),
(604, 64, 96, NULL, '支持環保包裝，但希望商品定價不要因此翻倍成長', NULL),
(605, 65, 97, 146, NULL, NULL),
(606, 65, 98, 148, NULL, NULL),
(607, 65, 99, 152, NULL, NULL),
(608, 65, 99, 153, NULL, NULL),
(609, 65, 100, NULL, NULL, 5),
(610, 65, 101, NULL, '2026-06-15', NULL),
(611, 65, 102, NULL, '09:30', NULL),
(612, 65, 103, NULL, '今年會場 WiFi 穩定度滿分！下午分散式架構 Keynote 講題非常硬核收穫滿滿', NULL),
(613, 66, 97, 146, NULL, NULL),
(614, 66, 98, 149, NULL, NULL),
(615, 66, 99, 152, NULL, NULL),
(616, 66, 100, NULL, NULL, 4),
(617, 66, 101, NULL, '2026-08-20', NULL),
(618, 66, 102, NULL, '14:15', NULL),
(619, 66, 103, NULL, '會議室冷氣太冷了，午餐便當希望明年度能多提供蛋奶素健康選項', NULL),
(620, 67, 97, 147, NULL, NULL),
(621, 67, 98, 148, NULL, NULL),
(622, 67, 99, 152, NULL, NULL),
(623, 67, 99, 153, NULL, NULL),
(624, 67, 100, NULL, NULL, 5),
(625, 67, 101, NULL, '2026-10-05', NULL),
(626, 67, 102, NULL, '20:45', NULL),
(627, 67, 103, NULL, '明年一定會繼續報名！希望可以增加更多 Hands-on 實作工作坊', NULL),
(628, 68, 104, 156, NULL, NULL),
(629, 68, 105, 158, NULL, NULL),
(630, 68, 106, 162, NULL, NULL),
(631, 68, 106, 163, NULL, NULL),
(632, 68, 107, NULL, NULL, 5),
(633, 68, 108, NULL, '2026-06-15', NULL),
(634, 68, 109, NULL, '09:30', NULL),
(635, 68, 110, NULL, '原創創作者的真實情感與經歷是 AI 無法取代的，但平台抽成過重讓小作者很難維生', NULL),
(636, 69, 104, 156, NULL, NULL),
(637, 69, 105, 159, NULL, NULL),
(638, 69, 106, 162, NULL, NULL),
(639, 69, 107, NULL, NULL, 4),
(640, 69, 108, NULL, '2026-08-20', NULL),
(641, 69, 109, NULL, '14:15', NULL),
(642, 69, 110, NULL, '短影音充斥劣幣逐良幣，現在只訂閱願意深耕專業長文的創作者', NULL),
(643, 70, 104, 157, NULL, NULL),
(644, 70, 105, 158, NULL, NULL),
(645, 70, 106, 162, NULL, NULL),
(646, 70, 106, 163, NULL, NULL),
(647, 70, 107, NULL, NULL, 5),
(648, 70, 108, NULL, '2026-10-05', NULL),
(649, 70, 109, NULL, '20:45', NULL),
(650, 70, 110, NULL, NULL, NULL),
(651, 71, 111, 166, NULL, NULL),
(652, 71, 112, 168, NULL, NULL),
(653, 71, 113, 172, NULL, NULL),
(654, 71, 113, 173, NULL, NULL),
(655, 71, 114, NULL, NULL, 5),
(656, 71, 115, NULL, '2026-06-15', NULL),
(657, 71, 116, NULL, '09:30', NULL),
(658, 71, 117, NULL, '一次性保冷保麗龍盒太佔空間很難回收，希望能全面推行循環保冷袋機制', NULL),
(659, 72, 111, 166, NULL, NULL),
(660, 72, 112, 169, NULL, NULL),
(661, 72, 113, 172, NULL, NULL),
(662, 72, 114, NULL, NULL, 4),
(663, 72, 115, NULL, '2026-08-20', NULL),
(664, 72, 116, NULL, '14:15', NULL),
(665, 72, 117, NULL, '司機配送態度極佳，抵達前 10 分鐘有先以簡訊與電話確認在不在家', NULL),
(666, 73, 111, 167, NULL, NULL),
(667, 73, 112, 168, NULL, NULL),
(668, 73, 113, 172, NULL, NULL),
(669, 73, 113, 173, NULL, NULL),
(670, 73, 114, NULL, NULL, 5),
(671, 73, 115, NULL, '2026-10-05', NULL),
(672, 73, 116, NULL, '20:45', NULL),
(673, 73, 117, NULL, '全程溫控確實無失溫變質，包裝完整，大推！', NULL),
(674, 74, 118, 176, NULL, NULL),
(675, 74, 119, 178, NULL, NULL),
(676, 74, 120, 182, NULL, NULL),
(677, 74, 120, 183, NULL, NULL),
(678, 74, 121, NULL, NULL, 5),
(679, 74, 122, NULL, '2026-06-15', NULL),
(680, 74, 123, NULL, '09:30', NULL),
(681, 74, 124, NULL, '國道休息站超充站假日尖峰排隊較長，希望能多增設幾座並支援動態預約分流', NULL),
(682, 75, 118, 176, NULL, NULL),
(683, 75, 119, 179, NULL, NULL),
(684, 75, 120, 182, NULL, NULL),
(685, 75, 121, NULL, NULL, 4),
(686, 75, 122, NULL, '2026-08-20', NULL),
(687, 75, 123, NULL, '14:15', NULL),
(688, 75, 124, NULL, '希望能開放第三方充電樁即時空位地圖直接整合至原廠車機螢幕', NULL),
(689, 76, 118, 177, NULL, NULL),
(690, 76, 119, 178, NULL, NULL),
(691, 76, 120, 182, NULL, NULL),
(692, 76, 120, 183, NULL, NULL),
(693, 76, 121, NULL, NULL, 5),
(694, 76, 122, NULL, '2026-10-05', NULL),
(695, 76, 123, NULL, '20:45', NULL),
(696, 76, 124, NULL, '車機語音助理偶爾聽不懂臺灣腔中文，希望能進一步提升語音模型辨識率', NULL),
(697, 77, 125, 186, NULL, NULL),
(698, 77, 126, 188, NULL, NULL),
(699, 77, 127, 192, NULL, NULL),
(700, 77, 127, 193, NULL, NULL),
(701, 77, 128, NULL, NULL, 5),
(702, 77, 129, NULL, '2026-06-15', NULL),
(703, 77, 130, NULL, '09:30', NULL),
(704, 77, 131, NULL, 'Event Storming 與微服務領域驅動那堂工作坊收穫最大，直接解開團隊多年架構痛點', NULL),
(705, 78, 125, 186, NULL, NULL),
(706, 78, 126, 189, NULL, NULL),
(707, 78, 127, 192, NULL, NULL),
(708, 78, 128, NULL, NULL, 4),
(709, 78, 129, NULL, '2026-08-20', NULL),
(710, 78, 130, NULL, '14:15', NULL),
(711, 78, 131, NULL, '觀念講述很精彩，但期盼後續能有針對大型 Legacy Code 重構與重寫的實務教練課', NULL),
(712, 79, 125, 187, NULL, NULL),
(713, 79, 126, 188, NULL, NULL),
(714, 79, 127, 192, NULL, NULL),
(715, 79, 127, 193, NULL, NULL),
(716, 79, 128, NULL, NULL, 5),
(717, 79, 129, NULL, '2026-10-05', NULL),
(718, 79, 130, NULL, '20:45', NULL),
(719, 79, 131, NULL, '內容偏難且資訊密度極高，需要回去多看兩次錄影回放消化實作', NULL),
(720, 80, 132, 196, NULL, NULL),
(721, 80, 133, 198, NULL, NULL),
(722, 80, 134, 202, NULL, NULL),
(723, 80, 134, 203, NULL, NULL),
(724, 80, 135, NULL, NULL, 5),
(725, 80, 136, NULL, '2026-06-15', NULL),
(726, 80, 137, NULL, '09:30', NULL),
(727, 80, 138, NULL, '長輩看不懂太小的英文縮寫，介面圖示應該加大、並增加台語語音朗讀輔助', NULL),
(728, 81, 132, 196, NULL, NULL),
(729, 81, 133, 199, NULL, NULL),
(730, 81, 134, 202, NULL, NULL),
(731, 81, 135, NULL, NULL, 4),
(732, 81, 136, NULL, '2026-08-20', NULL),
(733, 81, 137, NULL, '14:15', NULL),
(734, 81, 138, NULL, '血壓血糖量測數據能秒級同步到醫院遠距照護雲端，讓遠在異鄉的子女放心許多', NULL),
(735, 82, 132, 197, NULL, NULL),
(736, 82, 133, 198, NULL, NULL),
(737, 82, 134, 202, NULL, NULL),
(738, 82, 134, 203, NULL, NULL),
(739, 82, 135, NULL, NULL, 5),
(740, 82, 136, NULL, '2026-10-05', NULL),
(741, 82, 137, NULL, '20:45', NULL),
(742, 82, 138, NULL, '老人家還是很依賴實體診所看診的親切感，數位化轉型要循序漸進包容長者習慣', NULL),
(743, 83, 139, 206, NULL, NULL),
(744, 83, 140, 208, NULL, NULL),
(745, 83, 141, 212, NULL, NULL),
(746, 83, 141, 213, NULL, NULL),
(747, 83, 142, NULL, NULL, 5),
(748, 83, 143, NULL, '2026-06-15', NULL),
(749, 83, 144, NULL, '09:30', NULL),
(750, 83, 145, NULL, '現在超商標示高蛋白低碳水非常一目了然，結帳前一定會翻過來看營養成分表', NULL),
(751, 84, 139, 206, NULL, NULL),
(752, 84, 140, 209, NULL, NULL),
(753, 84, 141, 212, NULL, NULL),
(754, 84, 142, NULL, NULL, 4),
(755, 84, 143, NULL, '2026-08-20', NULL),
(756, 84, 144, NULL, '14:15', NULL),
(757, 84, 145, NULL, '平時每天自備水煮蛋與無糖豆漿，偶爾週末吃大餐維持身心平衡', NULL),
(758, 85, 139, 207, NULL, NULL),
(759, 85, 140, 208, NULL, NULL),
(760, 85, 141, 212, NULL, NULL),
(761, 85, 141, 213, NULL, NULL),
(762, 85, 142, NULL, NULL, 5),
(763, 85, 143, NULL, '2026-10-05', NULL),
(764, 85, 144, NULL, '20:45', NULL),
(765, 85, 145, NULL, '工作太忙根本沒空細看，只要標榜少油少鹽就會優先抓了結帳', NULL),
(766, 86, 146, 216, NULL, NULL),
(767, 86, 147, 218, NULL, NULL),
(768, 86, 148, 222, NULL, NULL),
(769, 86, 148, 223, NULL, NULL),
(770, 86, 149, NULL, NULL, 5),
(771, 86, 150, NULL, '2026-06-15', NULL),
(772, 86, 151, NULL, '09:30', NULL),
(773, 86, 152, NULL, '尖峰時段捷運轉乘接駁公車希望班次再密集一些，等超過 15 分鐘常忍不住改搭計程車', NULL),
(774, 87, 146, 216, NULL, NULL),
(775, 87, 147, 219, NULL, NULL),
(776, 87, 148, 222, NULL, NULL),
(777, 87, 149, NULL, NULL, 4),
(778, 87, 150, NULL, '2026-08-20', NULL),
(779, 87, 151, NULL, '14:15', NULL),
(780, 87, 152, NULL, '通勤月票 1200 方案極具性價比，每月節省了上千元交通開銷，強烈支持續辦！', NULL),
(781, 88, 146, 217, NULL, NULL),
(782, 88, 147, 218, NULL, NULL),
(783, 88, 148, 222, NULL, NULL),
(784, 88, 148, 223, NULL, NULL),
(785, 88, 149, NULL, NULL, 5),
(786, 88, 150, NULL, '2026-10-05', NULL),
(787, 88, 151, NULL, '20:45', NULL),
(788, 88, 152, NULL, '熱門捷運站周邊 YouBike 2.0 經常無車可借或無位可還，車輛動態調度需再加強', NULL),
(789, 89, 153, 226, NULL, NULL),
(790, 89, 154, 228, NULL, NULL),
(791, 89, 155, 232, NULL, NULL),
(792, 89, 155, 233, NULL, NULL),
(793, 89, 156, NULL, NULL, 5),
(794, 89, 157, NULL, '2026-06-15', NULL),
(795, 89, 158, NULL, '09:30', NULL),
(796, 89, 159, NULL, '免密碼指紋與 Face ID 報稅認證體驗極佳，但資安教育宣導需深入偏鄉避免個資外洩', NULL),
(797, 90, 153, 226, NULL, NULL),
(798, 90, 154, 229, NULL, NULL),
(799, 90, 155, 232, NULL, NULL),
(800, 90, 156, NULL, NULL, 4),
(801, 90, 157, NULL, '2026-08-20', NULL),
(802, 90, 158, NULL, '14:15', NULL),
(803, 90, 159, NULL, '希望中央與地方各便民服務平台能統一單一簽入 (SSO) 標準，避免重複綁定裝置', NULL),
(804, 91, 153, 227, NULL, NULL),
(805, 91, 154, 228, NULL, NULL),
(806, 91, 155, 232, NULL, NULL),
(807, 91, 155, 233, NULL, NULL),
(808, 91, 156, NULL, NULL, 5),
(809, 91, 157, NULL, '2026-10-05', NULL),
(810, 91, 158, NULL, '20:45', NULL),
(811, 91, 159, NULL, '樂見全面導入 FIDO2 國際標準，儘早淘汰容易遭攔截釣魚的傳統 SMS 簡訊驗證碼', NULL),
(812, 92, 160, 236, NULL, NULL),
(813, 92, 161, 238, NULL, NULL),
(814, 92, 162, 242, NULL, NULL),
(815, 92, 162, 243, NULL, NULL),
(816, 92, 163, NULL, NULL, 5),
(817, 92, 164, NULL, '2026-06-15', NULL),
(818, 92, 165, NULL, '09:30', NULL),
(819, 92, 166, NULL, '週末全家約定不開電視與平板，帶小朋友去戶外露營、騎腳踏車是保護視力最自然的方法', NULL),
(820, 93, 160, 236, NULL, NULL),
(821, 93, 161, 239, NULL, NULL),
(822, 93, 162, 242, NULL, NULL),
(823, 93, 163, NULL, NULL, 4),
(824, 93, 164, NULL, '2026-08-20', NULL),
(825, 93, 165, NULL, '14:15', NULL),
(826, 93, 166, NULL, '用餐時間嚴格禁止邊吃邊看 YouTube，大人自己也必須以身作則放下手機溝通傾聽', NULL),
(827, 94, 160, 237, NULL, NULL),
(828, 94, 161, 238, NULL, NULL),
(829, 94, 162, 242, NULL, NULL),
(830, 94, 162, 243, NULL, NULL),
(831, 94, 163, NULL, NULL, 5),
(832, 94, 164, NULL, '2026-10-05', NULL),
(833, 94, 165, NULL, '20:45', NULL),
(834, 94, 166, NULL, '現代完全禁止太難了，我們家是用定時器約定每天放學後最多觀看 30 分鐘優質節目', NULL),
(835, 95, 167, 246, NULL, NULL),
(836, 95, 168, 248, NULL, NULL),
(837, 95, 169, 252, NULL, NULL),
(838, 95, 169, 253, NULL, NULL),
(839, 95, 170, NULL, NULL, 5),
(840, 95, 171, NULL, '2026-06-15', NULL),
(841, 95, 172, NULL, '09:30', NULL),
(842, 95, 173, NULL, '臺灣獨立團隊加油！美術風格與在地文化題材非常吸睛，已經贊助支持豪華套裝！', NULL),
(843, 96, 167, 246, NULL, NULL),
(844, 96, 168, 249, NULL, NULL),
(845, 96, 169, 252, NULL, NULL),
(846, 96, 170, NULL, NULL, 4),
(847, 96, 171, NULL, '2026-08-20', NULL),
(848, 96, 172, NULL, '14:15', NULL),
(849, 96, 173, NULL, 'Demo 試玩版的打擊感與視角反饋很不錯，期待正式發行後能有更豐富的分支劇情！', NULL),
(850, 97, 167, 247, NULL, NULL),
(851, 97, 168, 248, NULL, NULL),
(852, 97, 169, 252, NULL, NULL),
(853, 97, 169, 253, NULL, NULL),
(854, 97, 170, NULL, NULL, 5),
(855, 97, 171, NULL, '2026-10-05', NULL),
(856, 97, 172, NULL, '20:45', NULL),
(857, 97, 173, NULL, '只要劇本不爛尾、發售不跳票就是好作品，願意耐心等待！', NULL),
(858, 98, 174, 256, NULL, NULL),
(859, 98, 175, 258, NULL, NULL),
(860, 98, 176, 262, NULL, NULL),
(861, 98, 176, 263, NULL, NULL),
(862, 98, 177, NULL, NULL, 5),
(863, 98, 178, NULL, '2026-06-15', NULL),
(864, 98, 179, NULL, '09:30', NULL),
(865, 98, 180, NULL, '個人鍾愛衣索比亞古吉水洗豆，柑橘與白花香氣明亮乾淨，手沖水溫維持 91 度風味最佳', NULL),
(866, 99, 174, 256, NULL, NULL),
(867, 99, 175, 259, NULL, NULL),
(868, 99, 176, 262, NULL, NULL),
(869, 99, 177, NULL, NULL, 4),
(870, 99, 178, NULL, '2026-08-20', NULL),
(871, 99, 179, NULL, '14:15', NULL),
(872, 99, 180, NULL, '上班日喝美式黑咖啡提神，週末放假才會拿出手搖磨豆機與濾杯慢慢享受手沖過程', NULL),
(873, 100, 174, 257, NULL, NULL),
(874, 100, 175, 258, NULL, NULL),
(875, 100, 176, 262, NULL, NULL),
(876, 100, 176, 263, NULL, NULL),
(877, 100, 177, NULL, NULL, 5),
(878, 100, 178, NULL, '2026-10-05', NULL),
(879, 100, 179, NULL, '20:45', NULL),
(880, 100, 180, NULL, '喝起來順口回甘、不苦不澀就可以，沒有特別鑽研器材但很享受現磨的香氣', NULL);


-- -------------------------------------------------------------
-- 重新啟用外鍵檢查並提交事務
-- -------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 1;
COMMIT;
