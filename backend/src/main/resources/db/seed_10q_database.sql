
USE survey_flow;

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE submission_answers;
TRUNCATE TABLE submission_basic_infos;
TRUNCATE TABLE submissions;
TRUNCATE TABLE survey_options;
TRUNCATE TABLE survey_questions;
TRUNCATE TABLE survey_basic_info_configs;
TRUNCATE TABLE surveys;
TRUNCATE TABLE refresh_tokens;
DELETE FROM users WHERE id > 2;
SET FOREIGN_KEY_CHECKS = 1;

-- 重設使用者帳號與密碼 (Argon2id Hash 對應 Admin123! 與 User1234!)
INSERT INTO users (id, username, password_hash, full_name, email, phone, identity_card, role, status, created_at, updated_at)
VALUES 
(1, 'admin', '$argon2id$v=19$m=65536,t=3,p=1$4Qz9yO2hX0yK1vP8rS3uTw$1N6tV8eX4qZ9wM2yK7vP8rS3uTw1N6tV8eX4qZ9wM2y', '系統最高管理員', 'admin@surveyflow.tw', '0912345678', 'A123456789', 'ROLE_ADMIN', 'ACTIVE', NOW(), NOW()),
(2, 'testuser', '$argon2id$v=19$m=65536,t=3,p=1$7X1wM2yK7vP8rS3uTw1N6t$4Qz9yO2hX0yK1vP8rS3uTw1N6tV8eX4qZ9wM2yK7vP8r', '測試會員小明', 'testuser@surveyflow.tw', '0987654321', 'B234567890', 'ROLE_USER', 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    username = VALUES(username),
    full_name = VALUES(full_name),
    email = VALUES(email),
    status = 'ACTIVE';

-- -------------------------------------------------------------
-- 建立 4 筆問卷專案 (user_id = 2 擁有)
-- -------------------------------------------------------------
INSERT INTO surveys (id, user_id, title, category, description, is_public_to_lobby, status, start_date, end_date, view_count, created_at, updated_at)
VALUES
(1, 2, '2026 數位轉型與遠端辦公協作體驗調研', '產品體驗研究', '本問卷旨在探討跨國團隊在遠端協作時所面臨的溝通痛點、效能瓶頸與軟硬體工具滿意度，協助優化次世代數位辦公體驗。', 1, 'PUBLISHED', '2026-01-01 00:00:00', '2026-12-31 23:59:59', 142, NOW(), NOW()),
(2, 2, '2026 消費者品牌認知與電商購物滿意度調查', '顧客滿意度', '感謝您長期支持我們的產品與服務，為提升線上選購與智慧售後流程，懇請撥冗提供寶貴建議，共同打造極致體驗。', 1, 'PUBLISHED', '2026-01-01 00:00:00', '2026-12-31 23:59:59', 215, NOW(), NOW()),
(3, 2, '2026 軟體工程師開發工具與 AI 輔助寫碼偏好評測', '市場商業調研', '探索研發團隊對於各類 AI 輔助寫碼工具 (如 GitHub Copilot, Gemini Code Assist, Cursor) 的真實依賴程度與代碼品質信任度評估。', 1, 'PUBLISHED', '2026-01-01 00:00:00', '2026-12-31 23:59:59', 308, NOW(), NOW()),
(4, 2, '【內部封測／非公開】次世代新產品核心功能體驗問卷', '產品體驗研究', '【本專案為內部機密問卷，未公開展示於大廳，僅供特定持專屬連結之測試同仁填答】請針對前瞻功能回饋第一手體驗感想。', 0, 'DRAFT', '2026-01-01 00:00:00', '2026-12-31 23:59:59', 45, NOW(), NOW());

-- 基本資料收集配置 (4 份問卷皆啟用基本資料)
INSERT INTO survey_basic_info_configs (survey_id, require_name, require_gender, require_age, require_phone, require_email, require_industry, require_residence)
VALUES
(1, 1, 1, 1, 1, 1, 1, 1),
(2, 1, 1, 1, 1, 1, 1, 1),
(3, 1, 1, 1, 1, 1, 1, 1),
(4, 1, 1, 1, 1, 1, 1, 1);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (1, 1, 'Q1', 'BOOLEAN', '您的所屬單位目前是否已常態實施混合或全遠端辦公機制？', 1, 1, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (1, 1, '是，常態混合辦公', 1, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (2, 1, '否，全實體進辦公室', 2, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (2, 1, 'Q2', 'SINGLE', '您在遠端辦公期間是否曾遭遇跨時區非同步協作困難？', 1, 2, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (3, 2, '經常遇到，時常需要等待跨國同仁回覆', 1, 'Q3');


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (4, 2, '偶爾遇到，但現有工具尚可維持運作', 2, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (5, 2, '完全沒有，團隊皆維持同一個工作時區', 3, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (3, 1, 'Q3', 'MULTI', '【解鎖題】請問跨時區協作時，您最依賴哪些自動化同步機制？（複選）', 0, 3, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (6, 3, '非同步每日站會機器人 (Async Daily Bot)', 1, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (7, 3, '雲端錄影螢幕解說工具 (Loom / ScreenREC)', 2, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (8, 3, '自動化 CI/CD 通知整合機器人', 3, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (9, 3, '即時線上協作白板 (Miro / FigJam)', 4, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (4, 1, 'Q4', 'SINGLE', '平均每週因遠端視訊會議耗費的時間約為多少？', 1, 4, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (10, 4, '少於 2 小時，以文字非同步溝通為主', 1, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (11, 4, '3 至 5 小時，維持每日例行溝通', 2, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (12, 4, '6 至 10 小時，跨部門對焦會議頻繁', 3, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (13, 4, '超過 10 小時，嚴重壓縮實質工作時間', 4, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (5, 1, 'Q5', 'MULTI', '您認為遠端辦公帶來最顯著的優勢為以下哪些項目？（複選）', 1, 5, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (14, 5, '省去漫長通勤舟車勞頓與體力消耗', 1, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (15, 5, '能夠打造符合人體工學的個人獨立辦公空間', 2, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (16, 5, '工作時間配置具備極高彈性，方便照顧家庭', 3, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (17, 5, '在無人打擾的深度工作環境下產能顯著提升', 4, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (6, 1, 'Q6', 'RATING', '整體而言，您對目前任職團隊的「遠端溝通透明度」評分（1最低，5最高）', 1, 6, 1, 5, NOW(), NOW());


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (7, 1, 'Q7', 'RATING', '淨推薦度 (NPS)：您向同業推薦任職「支援混合辦公模式企業」的意願（0-10分）', 1, 7, 0, 10, NOW(), NOW());


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (8, 1, 'Q8', 'TIME', '您每日遠端工作最有效率、思緒最清晰的開始時間是？', 1, 8, NULL, NULL, NOW(), NOW());


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (9, 1, 'Q9', 'DATE', '您預期下次參與公司實體團建或線下面對面年會的日期約在何時？', 1, 9, NULL, NULL, NOW(), NOW());


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (10, 1, 'Q10', 'TEXT', '針對促進團隊非同步協作與知識庫管理，您有哪些具體改善建議？（簡答題）', 0, 10, NULL, NULL, NOW(), NOW());


INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (1, 1, '192.168.1.10', 145, '2026-10-10 14:20:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (1, 1, '陳建宇', '男', '25-34歲', '0911222333', 'jy.chen@example.com', '資訊科技業', '臺北市信義區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (1, 1, 1, 2, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (2, 1, 2, 3, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (3, 1, 3, 6, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (4, 1, 3, 7, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (5, 1, 4, 10, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (6, 1, 5, 14, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (7, 1, 6, NULL, NULL, 5);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (8, 1, 7, NULL, NULL, 10);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (9, 1, 8, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (10, 1, 9, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (11, 1, 10, NULL, '系統整體操作非常流暢，條件跳題在前端反應十分迅速，體驗超出預期！', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (2, 1, '192.168.1.11', 180, '2026-10-10 16:45:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (2, 2, '林雅婷', '女', '25-34歲', '0922333444', 'yt.lin@example.com', '金融保險業', '新北市板橋區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (12, 2, 1, 1, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (13, 2, 2, 4, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (14, 2, 4, 11, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (15, 2, 5, 14, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (16, 2, 5, 15, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (17, 2, 6, NULL, NULL, 3);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (18, 2, 7, NULL, NULL, 10);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (19, 2, 8, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (20, 2, 9, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (21, 2, 10, NULL, '白色 SaaS 界面乾淨俐落，字體排版極佳，看起來非常舒服且專業。', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (3, 1, '192.168.1.12', 95, '2026-10-11 10:15:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (3, 3, '黃志偉', '男', '35-44歲', '0933444555', 'cw.huang@example.com', '電子科技業', '臺中市西屯區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (22, 3, 1, 1, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (23, 3, 2, 3, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (24, 3, 3, 6, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (25, 3, 3, 7, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (26, 3, 4, 12, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (27, 3, 5, 14, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (28, 3, 6, NULL, NULL, 4);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (29, 3, 7, NULL, NULL, 10);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (30, 3, 8, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (31, 3, 9, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (32, 3, 10, NULL, '希望能支援更多匯出格式，目前 JSON 導出結構非常完整且符合標準。', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (4, 1, '192.168.1.13', 210, '2026-10-11 11:05:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (4, 4, '張淑芬', '女', '35-44歲', '0944555666', 'sf.chang@example.com', '製造業', '桃園市中壢區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (33, 4, 1, 2, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (34, 4, 2, 4, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (35, 4, 4, 13, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (36, 4, 5, 14, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (37, 4, 5, 15, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (38, 4, 6, NULL, NULL, 5);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (39, 4, 7, NULL, NULL, 9);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (40, 4, 8, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (41, 4, 9, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (42, 4, 10, NULL, '後台數據分析報表很清楚，特別是 NPS 淨推薦值與受訪者人口輪廓，對決策非常有幫助。', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (5, 1, '192.168.1.14', 130, '2026-10-12 09:30:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (5, 5, '王俊賢', '男', '25-34歲', '0955666777', 'jx.wang@example.com', '專業服務業', '新竹市東區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (43, 5, 1, 1, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (44, 5, 2, 3, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (45, 5, 3, 6, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (46, 5, 3, 7, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (47, 5, 4, 10, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (48, 5, 5, 14, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (49, 5, 6, NULL, NULL, 4);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (50, 5, 7, NULL, NULL, 9);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (51, 5, 8, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (52, 5, 9, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (53, 5, 10, NULL, '題型完整度很高，是非、單選、量表與時間日期一應俱全，真正符合企業級調研需求。', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (6, 1, '192.168.1.15', 160, '2026-10-12 15:50:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (6, 6, '李佩玲', '女', '25-34歲', '0966777888', 'pl.lee@example.com', '醫療保健業', '高雄市左營區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (54, 6, 1, 1, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (55, 6, 2, 4, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (56, 6, 4, 11, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (57, 6, 5, 14, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (58, 6, 5, 15, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (59, 6, 6, NULL, NULL, 3);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (60, 6, 7, NULL, NULL, 8);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (61, 6, 8, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (62, 6, 9, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (63, 6, 10, NULL, '視覺設計清新專業，問卷大廳的分類選單篩選極為順手，整體完成度極高！', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (7, 1, '192.168.1.16', 85, '2026-10-13 13:10:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (7, 7, '郭庭安', '女', '18-24歲', '0977888999', 'ta.kuo@example.com', '教育學術界', '臺北市大安區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (64, 7, 1, 2, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (65, 7, 2, 3, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (66, 7, 3, 6, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (67, 7, 3, 7, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (68, 7, 4, 12, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (69, 7, 5, 14, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (70, 7, 6, NULL, NULL, 5);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (71, 7, 7, NULL, NULL, 8);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (72, 7, 8, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (73, 7, 9, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (74, 7, 10, NULL, NULL, NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (8, 1, '192.168.1.17', 115, '2026-10-13 16:22:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (8, 8, '趙家豪', '男', '35-44歲', '0988999000', 'jh.chao@example.com', '交通物流業', '臺南市東區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (75, 8, 1, 1, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (76, 8, 2, 4, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (77, 8, 4, 13, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (78, 8, 5, 14, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (79, 8, 5, 15, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (80, 8, 6, NULL, NULL, 3);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (81, 8, 7, NULL, NULL, 6);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (82, 8, 8, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (83, 8, 9, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (84, 8, 10, NULL, NULL, NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (9, 1, '192.168.1.18', 140, '2026-10-13 18:05:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (9, 9, '周雅婷', '女', '35-44歲', '0911000111', 'yt.chou@example.com', '批發零售業', '新北市中和區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (85, 9, 1, 1, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (86, 9, 2, 3, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (87, 9, 3, 6, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (88, 9, 3, 7, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (89, 9, 4, 10, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (90, 9, 5, 14, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (91, 9, 6, NULL, NULL, 4);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (92, 9, 7, NULL, NULL, 6);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (93, 9, 8, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (94, 9, 9, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (95, 9, 10, NULL, NULL, NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (10, 1, '192.168.1.19', 125, '2026-10-13 20:45:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (10, 10, '劉建銘', '男', '45-54歲', '0922111222', 'cm.liu@example.com', '電子科技業', '新竹縣竹北市');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (96, 10, 1, 2, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (97, 10, 2, 4, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (98, 10, 4, 11, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (99, 10, 5, 14, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (100, 10, 5, 15, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (101, 10, 6, NULL, NULL, 5);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (102, 10, 7, NULL, NULL, 6);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (103, 10, 8, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (104, 10, 9, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (105, 10, 10, NULL, NULL, NULL);

INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (11, 2, 'Q1', 'BOOLEAN', '您過去半年內是否曾在各大品牌官方商城或第三方電商平台完成購物？', 1, 1, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (18, 11, '是，有定期線上網購習慣', 1, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (19, 11, '否，偏好線下實體門市消費', 2, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (12, 2, 'Q2', 'SINGLE', '在線上購物結帳流程中，您是否曾遇過支付系統卡頓或驗證失敗？', 1, 2, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (20, 12, '是，曾因支付介面失敗或驗證超時放棄結帳', 1, 'Q3');


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (21, 12, '否，每次付款流程皆順暢在 1 分鐘內完成', 2, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (13, 2, 'Q3', 'MULTI', '【解鎖題】遭遇支付障礙時，您最偏好平台提供哪些替代支付工具？（複選）', 0, 3, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (22, 13, 'Apple Pay / Google Pay 一鍵生物辨識結帳', 1, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (23, 13, 'LINE Pay 快速自動跳轉扣款支援', 2, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (24, 13, '超商貨到付款安心代收服務', 3, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (25, 13, '街口支付即時折抵現金回饋機制', 4, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (14, 2, 'Q4', 'SINGLE', '您在挑選電商平台時，最關鍵的決定因素是什麼？', 1, 4, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (26, 14, '商品價格優惠與全館免運門檻', 1, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (27, 14, '當日或 24 小時極速到貨配送服務', 2, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (28, 14, '商品退換貨流程是否完全免手續費且方便', 3, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (29, 14, '品牌官方授權保證正品與原廠保固', 4, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (15, 2, 'Q5', 'MULTI', '您經常選購的線上商品類別有哪些？（複選）', 1, 5, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (30, 15, '3C 數碼配件與電腦週邊硬體', 1, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (31, 15, '生活日用品與家庭消耗快消品', 2, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (32, 15, '流行服飾、鞋包與時尚配件', 3, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (33, 15, '生鮮食品與低溫冷凍配送食材', 4, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (16, 2, 'Q6', 'RATING', '您對本電商平台「商品搜尋精準度與篩選體驗」滿意度（1最低，5最高）', 1, 6, 1, 5, NOW(), NOW());


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (17, 2, 'Q7', 'RATING', '淨推薦度 (NPS)：您向親朋好友推薦在本電商平台消費的意願（0-10分）', 1, 7, 0, 10, NOW(), NOW());


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (18, 2, 'Q8', 'TIME', '您最常瀏覽電商平台或參與限時特賣的時段通常在？', 1, 8, NULL, NULL, NOW(), NOW());


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (19, 2, 'Q9', 'DATE', '您最近一次在線上完成購物的具體下單日期是？', 1, 9, NULL, NULL, NOW(), NOW());


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (20, 2, 'Q10', 'TEXT', '您認為我們在物流包裝、退換貨或客戶服務上，最需要改進的項目是什麼？（簡答題）', 0, 10, NULL, NULL, NOW(), NOW());


INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (11, 2, '192.168.1.10', 145, '2026-10-10 14:20:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (11, 11, '陳建宇', '男', '25-34歲', '0911222333', 'jy.chen@example.com', '資訊科技業', '臺北市信義區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (106, 11, 11, 19, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (107, 11, 12, 20, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (108, 11, 13, 22, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (109, 11, 13, 23, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (110, 11, 14, 26, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (111, 11, 15, 30, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (112, 11, 16, NULL, NULL, 5);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (113, 11, 17, NULL, NULL, 10);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (114, 11, 18, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (115, 11, 19, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (116, 11, 20, NULL, '系統整體操作非常流暢，條件跳題在前端反應十分迅速，體驗超出預期！', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (12, 2, '192.168.1.11', 180, '2026-10-10 16:45:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (12, 12, '林雅婷', '女', '25-34歲', '0922333444', 'yt.lin@example.com', '金融保險業', '新北市板橋區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (117, 12, 11, 18, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (118, 12, 12, 21, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (119, 12, 14, 27, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (120, 12, 15, 30, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (121, 12, 15, 31, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (122, 12, 16, NULL, NULL, 3);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (123, 12, 17, NULL, NULL, 10);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (124, 12, 18, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (125, 12, 19, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (126, 12, 20, NULL, '白色 SaaS 界面乾淨俐落，字體排版極佳，看起來非常舒服且專業。', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (13, 2, '192.168.1.12', 95, '2026-10-11 10:15:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (13, 13, '黃志偉', '男', '35-44歲', '0933444555', 'cw.huang@example.com', '電子科技業', '臺中市西屯區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (127, 13, 11, 18, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (128, 13, 12, 20, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (129, 13, 13, 22, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (130, 13, 13, 23, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (131, 13, 14, 28, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (132, 13, 15, 30, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (133, 13, 16, NULL, NULL, 4);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (134, 13, 17, NULL, NULL, 10);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (135, 13, 18, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (136, 13, 19, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (137, 13, 20, NULL, '希望能支援更多匯出格式，目前 JSON 導出結構非常完整且符合標準。', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (14, 2, '192.168.1.13', 210, '2026-10-11 11:05:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (14, 14, '張淑芬', '女', '35-44歲', '0944555666', 'sf.chang@example.com', '製造業', '桃園市中壢區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (138, 14, 11, 19, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (139, 14, 12, 21, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (140, 14, 14, 29, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (141, 14, 15, 30, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (142, 14, 15, 31, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (143, 14, 16, NULL, NULL, 5);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (144, 14, 17, NULL, NULL, 9);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (145, 14, 18, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (146, 14, 19, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (147, 14, 20, NULL, '後台數據分析報表很清楚，特別是 NPS 淨推薦值與受訪者人口輪廓，對決策非常有幫助。', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (15, 2, '192.168.1.14', 130, '2026-10-12 09:30:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (15, 15, '王俊賢', '男', '25-34歲', '0955666777', 'jx.wang@example.com', '專業服務業', '新竹市東區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (148, 15, 11, 18, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (149, 15, 12, 20, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (150, 15, 13, 22, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (151, 15, 13, 23, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (152, 15, 14, 26, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (153, 15, 15, 30, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (154, 15, 16, NULL, NULL, 4);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (155, 15, 17, NULL, NULL, 9);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (156, 15, 18, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (157, 15, 19, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (158, 15, 20, NULL, '題型完整度很高，是非、單選、量表與時間日期一應俱全，真正符合企業級調研需求。', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (16, 2, '192.168.1.15', 160, '2026-10-12 15:50:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (16, 16, '李佩玲', '女', '25-34歲', '0966777888', 'pl.lee@example.com', '醫療保健業', '高雄市左營區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (159, 16, 11, 18, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (160, 16, 12, 21, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (161, 16, 14, 27, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (162, 16, 15, 30, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (163, 16, 15, 31, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (164, 16, 16, NULL, NULL, 3);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (165, 16, 17, NULL, NULL, 8);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (166, 16, 18, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (167, 16, 19, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (168, 16, 20, NULL, '視覺設計清新專業，問卷大廳的分類選單篩選極為順手，整體完成度極高！', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (17, 2, '192.168.1.16', 85, '2026-10-13 13:10:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (17, 17, '郭庭安', '女', '18-24歲', '0977888999', 'ta.kuo@example.com', '教育學術界', '臺北市大安區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (169, 17, 11, 19, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (170, 17, 12, 20, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (171, 17, 13, 22, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (172, 17, 13, 23, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (173, 17, 14, 28, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (174, 17, 15, 30, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (175, 17, 16, NULL, NULL, 5);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (176, 17, 17, NULL, NULL, 8);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (177, 17, 18, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (178, 17, 19, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (179, 17, 20, NULL, NULL, NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (18, 2, '192.168.1.17', 115, '2026-10-13 16:22:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (18, 18, '趙家豪', '男', '35-44歲', '0988999000', 'jh.chao@example.com', '交通物流業', '臺南市東區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (180, 18, 11, 18, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (181, 18, 12, 21, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (182, 18, 14, 29, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (183, 18, 15, 30, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (184, 18, 15, 31, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (185, 18, 16, NULL, NULL, 3);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (186, 18, 17, NULL, NULL, 6);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (187, 18, 18, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (188, 18, 19, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (189, 18, 20, NULL, NULL, NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (19, 2, '192.168.1.18', 140, '2026-10-13 18:05:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (19, 19, '周雅婷', '女', '35-44歲', '0911000111', 'yt.chou@example.com', '批發零售業', '新北市中和區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (190, 19, 11, 18, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (191, 19, 12, 20, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (192, 19, 13, 22, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (193, 19, 13, 23, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (194, 19, 14, 26, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (195, 19, 15, 30, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (196, 19, 16, NULL, NULL, 4);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (197, 19, 17, NULL, NULL, 6);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (198, 19, 18, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (199, 19, 19, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (200, 19, 20, NULL, NULL, NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (20, 2, '192.168.1.19', 125, '2026-10-13 20:45:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (20, 20, '劉建銘', '男', '45-54歲', '0922111222', 'cm.liu@example.com', '電子科技業', '新竹縣竹北市');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (201, 20, 11, 19, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (202, 20, 12, 21, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (203, 20, 14, 27, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (204, 20, 15, 30, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (205, 20, 15, 31, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (206, 20, 16, NULL, NULL, 5);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (207, 20, 17, NULL, NULL, 6);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (208, 20, 18, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (209, 20, 19, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (210, 20, 20, NULL, NULL, NULL);

INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (21, 3, 'Q1', 'BOOLEAN', '您在日常軟體開發工作中，是否已有將生成式 AI 工具整合至 IDE 編輯器？', 1, 1, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (34, 21, '是，已常態整合至日常開發管線', 1, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (35, 21, '否，因資安規範或個人偏好未導入', 2, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (22, 3, 'Q2', 'SINGLE', '您是否會將 AI 自動生成之程式碼直接合併並發布至生產環境 (Production)？', 1, 2, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (36, 22, '是，經本地單元測試嚴格覆蓋後正式上線運行', 1, 'Q3');


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (37, 22, '否，僅作為語法參考與架構靈感發想，不直接採用', 2, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (23, 3, 'Q3', 'MULTI', '【解鎖題】針對 AI 生成之程式碼，團隊採用了哪些關鍵品質驗收防線？（複選）', 0, 3, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (38, 23, '由資深工程師執行嚴格 Peer Code Review', 1, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (39, 23, '撰寫 Mockito / JUnit 高覆蓋率測試驗證邊界', 2, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (40, 23, '導入 SonarQube 自動化靜態代碼與安全弱點掃描', 3, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (41, 23, '於 Staging 預發環境執行完整自動化端到端回歸測試', 4, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (24, 3, 'Q4', 'SINGLE', '您目前最頻繁使用的主要整合開發環境 (IDE) 是？', 1, 4, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (42, 24, 'Visual Studio Code (輕量靈活多插件)', 1, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (43, 24, 'IntelliJ IDEA / JetBrains 全家桶系列', 2, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (44, 24, 'Cursor AI 原生增強編輯器', 3, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (45, 24, 'Vim / Neovim 極速純終端工作流', 4, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (25, 3, 'Q5', 'MULTI', 'AI 輔助寫碼工具在以下哪些開發環節為您節省最多工時？（複選）', 1, 5, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (46, 25, '自動生成樣板程式碼 (Boilerplate Code)', 1, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (47, 25, '快速編寫單元測試用例 (Unit Test Cases)', 2, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (48, 25, '自動產生 JSDoc/TSDoc 繁體中文方法註解', 3, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (49, 25, '複雜 SQL 聚合查詢或正規表達式 Regex 構建', 4, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (26, 3, 'Q6', 'RATING', '您對當前主流 AI 程式輔助工具「自動補全邏輯準確度」的整體評分（1最低，5最高）', 1, 6, 1, 5, NOW(), NOW());


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (27, 3, 'Q7', 'RATING', '淨推薦度 (NPS)：您向同組工程同仁推薦全面採用 AI 工具的意願（0-10分）', 1, 7, 0, 10, NOW(), NOW());


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (28, 3, 'Q8', 'TIME', '您每日精神最佳、適合進行架構重構與攻堅難題的黃金寫碼時段通常在？', 1, 8, NULL, NULL, NOW(), NOW());


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (29, 3, 'Q9', 'DATE', '您上次針對既有專案進行大規模技術債重構 (Refactoring) 的完成日期是？', 1, 9, NULL, NULL, NOW(), NOW());


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (30, 3, 'Q10', 'TEXT', '對於未來的 AI 輔助開發工具（如智慧 Debugger、自動架構分析），您最期待出現什麼新能力？（簡答題）', 0, 10, NULL, NULL, NOW(), NOW());


INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (21, 3, '192.168.1.10', 145, '2026-10-10 14:20:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (21, 21, '陳建宇', '男', '25-34歲', '0911222333', 'jy.chen@example.com', '資訊科技業', '臺北市信義區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (211, 21, 21, 35, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (212, 21, 22, 36, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (213, 21, 23, 38, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (214, 21, 23, 39, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (215, 21, 24, 42, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (216, 21, 25, 46, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (217, 21, 26, NULL, NULL, 5);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (218, 21, 27, NULL, NULL, 10);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (219, 21, 28, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (220, 21, 29, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (221, 21, 30, NULL, '系統整體操作非常流暢，條件跳題在前端反應十分迅速，體驗超出預期！', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (22, 3, '192.168.1.11', 180, '2026-10-10 16:45:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (22, 22, '林雅婷', '女', '25-34歲', '0922333444', 'yt.lin@example.com', '金融保險業', '新北市板橋區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (222, 22, 21, 34, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (223, 22, 22, 37, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (224, 22, 24, 43, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (225, 22, 25, 46, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (226, 22, 25, 47, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (227, 22, 26, NULL, NULL, 3);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (228, 22, 27, NULL, NULL, 10);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (229, 22, 28, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (230, 22, 29, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (231, 22, 30, NULL, '白色 SaaS 界面乾淨俐落，字體排版極佳，看起來非常舒服且專業。', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (23, 3, '192.168.1.12', 95, '2026-10-11 10:15:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (23, 23, '黃志偉', '男', '35-44歲', '0933444555', 'cw.huang@example.com', '電子科技業', '臺中市西屯區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (232, 23, 21, 34, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (233, 23, 22, 36, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (234, 23, 23, 38, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (235, 23, 23, 39, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (236, 23, 24, 44, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (237, 23, 25, 46, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (238, 23, 26, NULL, NULL, 4);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (239, 23, 27, NULL, NULL, 10);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (240, 23, 28, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (241, 23, 29, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (242, 23, 30, NULL, '希望能支援更多匯出格式，目前 JSON 導出結構非常完整且符合標準。', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (24, 3, '192.168.1.13', 210, '2026-10-11 11:05:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (24, 24, '張淑芬', '女', '35-44歲', '0944555666', 'sf.chang@example.com', '製造業', '桃園市中壢區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (243, 24, 21, 35, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (244, 24, 22, 37, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (245, 24, 24, 45, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (246, 24, 25, 46, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (247, 24, 25, 47, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (248, 24, 26, NULL, NULL, 5);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (249, 24, 27, NULL, NULL, 9);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (250, 24, 28, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (251, 24, 29, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (252, 24, 30, NULL, '後台數據分析報表很清楚，特別是 NPS 淨推薦值與受訪者人口輪廓，對決策非常有幫助。', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (25, 3, '192.168.1.14', 130, '2026-10-12 09:30:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (25, 25, '王俊賢', '男', '25-34歲', '0955666777', 'jx.wang@example.com', '專業服務業', '新竹市東區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (253, 25, 21, 34, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (254, 25, 22, 36, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (255, 25, 23, 38, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (256, 25, 23, 39, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (257, 25, 24, 42, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (258, 25, 25, 46, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (259, 25, 26, NULL, NULL, 4);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (260, 25, 27, NULL, NULL, 9);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (261, 25, 28, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (262, 25, 29, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (263, 25, 30, NULL, '題型完整度很高，是非、單選、量表與時間日期一應俱全，真正符合企業級調研需求。', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (26, 3, '192.168.1.15', 160, '2026-10-12 15:50:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (26, 26, '李佩玲', '女', '25-34歲', '0966777888', 'pl.lee@example.com', '醫療保健業', '高雄市左營區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (264, 26, 21, 34, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (265, 26, 22, 37, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (266, 26, 24, 43, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (267, 26, 25, 46, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (268, 26, 25, 47, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (269, 26, 26, NULL, NULL, 3);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (270, 26, 27, NULL, NULL, 8);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (271, 26, 28, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (272, 26, 29, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (273, 26, 30, NULL, '視覺設計清新專業，問卷大廳的分類選單篩選極為順手，整體完成度極高！', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (27, 3, '192.168.1.16', 85, '2026-10-13 13:10:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (27, 27, '郭庭安', '女', '18-24歲', '0977888999', 'ta.kuo@example.com', '教育學術界', '臺北市大安區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (274, 27, 21, 35, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (275, 27, 22, 36, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (276, 27, 23, 38, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (277, 27, 23, 39, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (278, 27, 24, 44, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (279, 27, 25, 46, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (280, 27, 26, NULL, NULL, 5);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (281, 27, 27, NULL, NULL, 8);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (282, 27, 28, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (283, 27, 29, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (284, 27, 30, NULL, NULL, NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (28, 3, '192.168.1.17', 115, '2026-10-13 16:22:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (28, 28, '趙家豪', '男', '35-44歲', '0988999000', 'jh.chao@example.com', '交通物流業', '臺南市東區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (285, 28, 21, 34, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (286, 28, 22, 37, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (287, 28, 24, 45, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (288, 28, 25, 46, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (289, 28, 25, 47, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (290, 28, 26, NULL, NULL, 3);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (291, 28, 27, NULL, NULL, 6);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (292, 28, 28, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (293, 28, 29, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (294, 28, 30, NULL, NULL, NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (29, 3, '192.168.1.18', 140, '2026-10-13 18:05:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (29, 29, '周雅婷', '女', '35-44歲', '0911000111', 'yt.chou@example.com', '批發零售業', '新北市中和區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (295, 29, 21, 34, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (296, 29, 22, 36, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (297, 29, 23, 38, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (298, 29, 23, 39, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (299, 29, 24, 42, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (300, 29, 25, 46, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (301, 29, 26, NULL, NULL, 4);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (302, 29, 27, NULL, NULL, 6);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (303, 29, 28, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (304, 29, 29, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (305, 29, 30, NULL, NULL, NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (30, 3, '192.168.1.19', 125, '2026-10-13 20:45:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (30, 30, '劉建銘', '男', '45-54歲', '0922111222', 'cm.liu@example.com', '電子科技業', '新竹縣竹北市');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (306, 30, 21, 35, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (307, 30, 22, 37, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (308, 30, 24, 43, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (309, 30, 25, 46, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (310, 30, 25, 47, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (311, 30, 26, NULL, NULL, 5);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (312, 30, 27, NULL, NULL, 6);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (313, 30, 28, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (314, 30, 29, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (315, 30, 30, NULL, NULL, NULL);

INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (31, 4, 'Q1', 'BOOLEAN', '您在本次封閉測試 (Closed Beta) 期間，是否已完整體驗核心操作流程？', 1, 1, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (50, 31, '是，已走完所有關鍵功能操作路徑', 1, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (51, 31, '否，僅體驗了部分基礎介面模組', 2, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (32, 4, 'Q2', 'SINGLE', '在操作次世代全新儀表板的過程中，您是否有遭遇過介面破版或卡頓延遲？', 1, 2, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (52, 32, '是，曾遇到特定操作導致頁面無響應需重整', 1, 'Q3');


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (53, 32, '否，全程操作流暢高幀率，無異常閃退', 2, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (33, 4, 'Q3', 'MULTI', '【解鎖題】發生異常延遲時，是在哪一個操作情境下發生的？（複選）', 0, 3, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (54, 33, '大量資料動態繪製虛擬捲軸渲染區域', 1, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (55, 33, '多重條件跳題邏輯即時動態計算觸發處', 2, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (56, 33, '匯出高解析度 PDF 數據分析報表瞬間', 3, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (57, 33, '切換大廳暗黑與明亮主題動畫過渡時期', 4, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (34, 4, 'Q4', 'SINGLE', '您對新版本 UI 介面整體視覺風格（現代清新白色 SaaS 風格）的觀感為？', 1, 4, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (58, 34, '極具現代感，資訊層級分明且賞心悅目', 1, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (59, 34, '簡約乾淨，符合現代辦公效率軟體主流', 2, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (60, 34, '中規中矩，尚有部分微動效可再細化調整', 3, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (61, 34, '不甚習慣，偏好傳統密集型表格呈現', 4, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (35, 4, 'Q5', 'MULTI', '本次封測推出的眾多新功能中，您最滿意的亮點功能有哪些？（複選）', 1, 5, NULL, NULL, NOW(), NOW());


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (62, 35, '動態條件跳題與題目即時聯動解鎖機制', 1, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (63, 35, '可自訂問卷開放填答時段與即時狀態切換', 2, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (64, 35, '圖表化數據儀表板與受訪者輪廓交叉分析', 3, NULL);


INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES (65, 35, '極速單鍵匯出完整格式化 Excel 與 JSON 報表', 4, NULL);


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (36, 4, 'Q6', 'RATING', '您對本次封閉測試版本的整體滿意度評分（1最低，5最高）', 1, 6, 1, 5, NOW(), NOW());


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (37, 4, 'Q7', 'RATING', '淨推薦度 (NPS)：您向其他專案組推薦採用本套問卷雲端系統的意願（0-10分）', 1, 7, 0, 10, NOW(), NOW());


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (38, 4, 'Q8', 'TIME', '您認為每日系統發送「填答數據即時摘要快訊」的最佳推播時間為？', 1, 8, NULL, NULL, NOW(), NOW());


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (39, 4, 'Q9', 'DATE', '您期望本次內部封測專案正式對外公開發布 (GA) 的理想日期是？', 1, 9, NULL, NULL, NOW(), NOW());


INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES (40, 4, 'Q10', 'TEXT', '請留下您對此產品任何未竟之處的真心話或任何想許願的進階功能！（簡答題）', 0, 10, NULL, NULL, NOW(), NOW());


INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (31, 4, '192.168.1.10', 145, '2026-10-10 14:20:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (31, 31, '陳建宇', '男', '25-34歲', '0911222333', 'jy.chen@example.com', '資訊科技業', '臺北市信義區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (316, 31, 31, 51, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (317, 31, 32, 52, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (318, 31, 33, 54, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (319, 31, 33, 55, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (320, 31, 34, 58, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (321, 31, 35, 62, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (322, 31, 36, NULL, NULL, 5);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (323, 31, 37, NULL, NULL, 10);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (324, 31, 38, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (325, 31, 39, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (326, 31, 40, NULL, '系統整體操作非常流暢，條件跳題在前端反應十分迅速，體驗超出預期！', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (32, 4, '192.168.1.11', 180, '2026-10-10 16:45:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (32, 32, '林雅婷', '女', '25-34歲', '0922333444', 'yt.lin@example.com', '金融保險業', '新北市板橋區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (327, 32, 31, 50, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (328, 32, 32, 53, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (329, 32, 34, 59, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (330, 32, 35, 62, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (331, 32, 35, 63, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (332, 32, 36, NULL, NULL, 3);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (333, 32, 37, NULL, NULL, 10);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (334, 32, 38, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (335, 32, 39, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (336, 32, 40, NULL, '白色 SaaS 界面乾淨俐落，字體排版極佳，看起來非常舒服且專業。', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (33, 4, '192.168.1.12', 95, '2026-10-11 10:15:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (33, 33, '黃志偉', '男', '35-44歲', '0933444555', 'cw.huang@example.com', '電子科技業', '臺中市西屯區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (337, 33, 31, 50, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (338, 33, 32, 52, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (339, 33, 33, 54, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (340, 33, 33, 55, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (341, 33, 34, 60, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (342, 33, 35, 62, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (343, 33, 36, NULL, NULL, 4);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (344, 33, 37, NULL, NULL, 10);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (345, 33, 38, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (346, 33, 39, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (347, 33, 40, NULL, '希望能支援更多匯出格式，目前 JSON 導出結構非常完整且符合標準。', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (34, 4, '192.168.1.13', 210, '2026-10-11 11:05:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (34, 34, '張淑芬', '女', '35-44歲', '0944555666', 'sf.chang@example.com', '製造業', '桃園市中壢區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (348, 34, 31, 51, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (349, 34, 32, 53, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (350, 34, 34, 61, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (351, 34, 35, 62, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (352, 34, 35, 63, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (353, 34, 36, NULL, NULL, 5);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (354, 34, 37, NULL, NULL, 9);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (355, 34, 38, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (356, 34, 39, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (357, 34, 40, NULL, '後台數據分析報表很清楚，特別是 NPS 淨推薦值與受訪者人口輪廓，對決策非常有幫助。', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (35, 4, '192.168.1.14', 130, '2026-10-12 09:30:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (35, 35, '王俊賢', '男', '25-34歲', '0955666777', 'jx.wang@example.com', '專業服務業', '新竹市東區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (358, 35, 31, 50, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (359, 35, 32, 52, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (360, 35, 33, 54, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (361, 35, 33, 55, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (362, 35, 34, 58, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (363, 35, 35, 62, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (364, 35, 36, NULL, NULL, 4);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (365, 35, 37, NULL, NULL, 9);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (366, 35, 38, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (367, 35, 39, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (368, 35, 40, NULL, '題型完整度很高，是非、單選、量表與時間日期一應俱全，真正符合企業級調研需求。', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (36, 4, '192.168.1.15', 160, '2026-10-12 15:50:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (36, 36, '李佩玲', '女', '25-34歲', '0966777888', 'pl.lee@example.com', '醫療保健業', '高雄市左營區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (369, 36, 31, 50, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (370, 36, 32, 53, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (371, 36, 34, 59, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (372, 36, 35, 62, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (373, 36, 35, 63, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (374, 36, 36, NULL, NULL, 3);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (375, 36, 37, NULL, NULL, 8);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (376, 36, 38, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (377, 36, 39, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (378, 36, 40, NULL, '視覺設計清新專業，問卷大廳的分類選單篩選極為順手，整體完成度極高！', NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (37, 4, '192.168.1.16', 85, '2026-10-13 13:10:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (37, 37, '郭庭安', '女', '18-24歲', '0977888999', 'ta.kuo@example.com', '教育學術界', '臺北市大安區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (379, 37, 31, 51, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (380, 37, 32, 52, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (381, 37, 33, 54, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (382, 37, 33, 55, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (383, 37, 34, 60, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (384, 37, 35, 62, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (385, 37, 36, NULL, NULL, 5);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (386, 37, 37, NULL, NULL, 8);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (387, 37, 38, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (388, 37, 39, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (389, 37, 40, NULL, NULL, NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (38, 4, '192.168.1.17', 115, '2026-10-13 16:22:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (38, 38, '趙家豪', '男', '35-44歲', '0988999000', 'jh.chao@example.com', '交通物流業', '臺南市東區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (390, 38, 31, 50, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (391, 38, 32, 53, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (392, 38, 34, 61, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (393, 38, 35, 62, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (394, 38, 35, 63, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (395, 38, 36, NULL, NULL, 3);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (396, 38, 37, NULL, NULL, 6);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (397, 38, 38, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (398, 38, 39, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (399, 38, 40, NULL, NULL, NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (39, 4, '192.168.1.18', 140, '2026-10-13 18:05:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (39, 39, '周雅婷', '女', '35-44歲', '0911000111', 'yt.chou@example.com', '批發零售業', '新北市中和區');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (400, 39, 31, 50, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (401, 39, 32, 52, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (402, 39, 33, 54, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (403, 39, 33, 55, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (404, 39, 34, 58, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (405, 39, 35, 62, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (406, 39, 36, NULL, NULL, 4);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (407, 39, 37, NULL, NULL, 6);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (408, 39, 38, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (409, 39, 39, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (410, 39, 40, NULL, NULL, NULL);

INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES (40, 4, '192.168.1.19', 125, '2026-10-13 20:45:00');


INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES (40, 40, '劉建銘', '男', '45-54歲', '0922111222', 'cm.liu@example.com', '電子科技業', '新竹縣竹北市');

INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (411, 40, 31, 51, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (412, 40, 32, 53, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (413, 40, 34, 59, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (414, 40, 35, 62, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (415, 40, 35, 63, NULL, NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (416, 40, 36, NULL, NULL, 5);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (417, 40, 37, NULL, NULL, 6);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (418, 40, 38, NULL, '09:30', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (419, 40, 39, NULL, '2026-11-15', NULL);
INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES (420, 40, 40, NULL, NULL, NULL);