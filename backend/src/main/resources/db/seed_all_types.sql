-- ============================================================================
-- 腳本名稱: seed_all_types.sql
-- 責任描述: 100% 繁體中文，修復分類對齊 12 大官方標準、包含全部 7 種多元題型、
--           修復姓名編碼、並為 testuser 的 4 份問卷注入各 10 筆真實作答記錄
-- ============================================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
SET character_set_connection=utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 清空所有舊資料
TRUNCATE TABLE submission_answers;
TRUNCATE TABLE submission_basic_infos;
TRUNCATE TABLE submissions;
TRUNCATE TABLE survey_options;
TRUNCATE TABLE survey_questions;
TRUNCATE TABLE survey_basic_info_configs;
TRUNCATE TABLE surveys;

-- 2. 確保使用者 (admin 與 testuser，繁體中文全名 100% 正確)
INSERT INTO users (id, username, email, password_hash, full_name, phone, identity_card, role, status, created_at, updated_at)
VALUES 
  (1, 'admin', 'admin@surveyflow.com', '$argon2id$v=19$m=65536,t=3,p=1$B7K0K8v8R6qV8J9L1X2Y3A$O9m5V2e8X3Y7Q1W9E5R2T8Y3U7I1O5P9A3S7D1F5G9H', '系統最高管理員', '0912345678', 'A123456789', 'ROLE_ADMIN', 'ACTIVE', NOW(), NOW()),
  (2, 'testuser', 'testuser@surveyflow.com', '$argon2id$v=19$m=65536,t=3,p=1$L1X2Y3A4B5C6D7E8F9G0H1$P9A3S7D1F5G9H1J3K5L7Z9X1C3V5B7N9M1Q3W5E7R9T', '測試會員小明', '0987654321', 'B198765432', 'ROLE_USER', 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE 
  full_name = VALUES(full_name),
  status = 'ACTIVE';

-- 3. 寫入 4 筆 testuser 所擁有的問卷 (3 筆公開 PUBLISHED + 1 筆非公開 DRAFT)
-- 分類對齊官方 12 大主題：產品體驗研究、顧客滿意度、市場商業調研
INSERT INTO surveys (id, user_id, title, description, category, is_public_to_lobby, status, start_date, end_date, view_count, created_at, updated_at)
VALUES 
  (1, 2, '2026 數位轉型與遠端辦公協作體驗調研', '本調研旨在深入了解知識工作者在混合辦公、非同步溝通與跨國團隊協作中的痛點與數位工具偏好，研究成果將作為未來團隊生產力解決方案優化之重要依據。', '產品體驗研究', 1, 'PUBLISHED', '2026-10-01 00:00:00', '2026-11-30 23:59:59', 188, NOW(), NOW()),
  (2, 2, '2026 消費者品牌認知與電商購物滿意度調查', '感謝您撥冗參與本調查，本研究聚焦消費者於跨通路購物旅程之決策因素、到貨物流滿意度及會員忠誠計畫回饋，填答內容僅供學術與商業統計分析使用。', '顧客滿意度', 1, 'PUBLISHED', '2026-10-05 00:00:00', '2026-12-15 23:59:59', 142, NOW(), NOW()),
  (3, 2, '2026 軟體工程師開發工具與 AI 輔助寫碼偏好評測', '探索現代軟體工程師在 IDE 整合、AI 程式碼補全輔助工具、CI/CD 自動化與雲端原生環境中的開發工作流與效能體驗。', '市場商業調研', 1, 'PUBLISHED', '2026-10-08 00:00:00', '2026-11-20 23:59:59', 265, NOW(), NOW()),
  (4, 2, '【內部封測／非公開】次世代新產品核心功能體驗問卷', '此問卷為內部專案非公開封閉測試專用，僅持有專屬分享直達連結之受邀受訪者方可進行功能回饋與可用性評測。', '產品體驗研究', 0, 'DRAFT', '2026-10-10 00:00:00', '2026-12-31 23:59:59', 35, NOW(), NOW());

-- 4. 寫入問卷基本資料配置
INSERT INTO survey_basic_info_configs (id, survey_id, require_name, require_gender, require_age, require_phone, require_email, require_industry, require_residence)
VALUES 
  (1, 1, 1, 1, 1, 1, 1, 1, 1),
  (2, 2, 1, 1, 1, 1, 1, 1, 1),
  (3, 3, 1, 1, 1, 1, 1, 1, 1),
  (4, 4, 1, 1, 1, 1, 1, 1, 1);

-- 5. 寫入多元題型 (包含是非題 BOOLEAN、單選 SINGLE、多選 MULTI、量表 RATING、簡答 TEXT、時間 TIME、日期 DATE)
-- 問卷 1 題目
INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating)
VALUES 
  (1, 1, 'Q1', 'BOOLEAN', '您目前每週是否有 2 天（含）以上採用遠端或混合辦公模式？', 1, 1, 1, 5),
  (2, 1, 'Q2', 'SINGLE', '您在遠端協作時最常遭遇的溝通痛點為何？', 1, 2, 1, 5),
  (3, 1, 'Q3', 'MULTI', '貴團隊主要依賴哪些數位協作與專案管理工具？（可複選）', 1, 3, 1, 5),
  (4, 1, 'Q4', 'RATING', '整體而言，您對目前遠端辦公模式提升個人工作產出的滿意程度評分（1~5 分）：', 1, 4, 1, 5),
  (5, 1, 'Q5', 'TEXT', '您認為公司在推動混合辦公時，最需要優先改善的制度或軟硬體支援為何？', 0, 5, 1, 5),
  (6, 1, 'Q6', 'TIME', '您在遠端辦公日通常於每日幾點開始專注工作？', 0, 6, 1, 5),
  (7, 1, 'Q7', 'DATE', '您預計下次何時會回到公司實體辦公室參與全員團隊會議？', 0, 7, 1, 5);

-- 問卷 1 選項
INSERT INTO survey_options (id, question_id, option_text, sort_order)
VALUES 
  (1, 1, '是 (Yes)', 1),
  (2, 1, '否 (No)', 2),
  (3, 2, '訊息回應延遲，溝通步調難以同步', 1),
  (4, 2, '線上會議過度頻繁，碎片化干擾嚴重', 2),
  (5, 2, '文件與任務資訊分散，版本難以追蹤', 3),
  (6, 2, '缺乏實體互動，團隊向心力與信任感減弱', 4),
  (7, 3, 'Slack / Microsoft Teams (即時通訊)', 1),
  (8, 3, 'Notion / Confluence (知識庫與文檔)', 2),
  (9, 3, 'Jira / Linear / Asana (專案任務看板)', 3),
  (10, 3, 'Figma / Miro (視覺化設計與白板)', 4),
  (11, 3, 'Google Workspace (文件與試算表)', 5);

-- 問卷 2 題目
INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating)
VALUES 
  (8, 2, 'Q1', 'BOOLEAN', '您過去三個月內是否有在大型品牌電商平台下單購物的經驗？', 1, 1, 1, 5),
  (9, 2, 'Q2', 'SINGLE', '影響您決定在特定電商下單的最主要關鍵因素為何？', 1, 2, 1, 5),
  (10, 2, 'Q3', 'MULTI', '您最常使用或偏好的結帳與支付方式為何？（可複選）', 1, 3, 1, 5),
  (11, 2, 'Q4', 'RATING', '您對本平台商品物流到貨速度與包裝完整性之滿意度評分（1~5 分）：', 1, 4, 1, 5),
  (12, 2, 'Q5', 'TEXT', '您對我們未來的會員權益與購物體驗有何具體建議？', 0, 5, 1, 5);

-- 問卷 2 選項
INSERT INTO survey_options (id, question_id, option_text, sort_order)
VALUES 
  (12, 8, '是 (Yes)', 1),
  (13, 8, '否 (No)', 2),
  (14, 9, '商品價格優惠與免運折扣門檻', 1),
  (15, 9, '現貨出貨速度與配送準時度', 2),
  (16, 9, '退換貨流程簡便與客服即時性', 3),
  (17, 9, '正品保障與商品真實評價豐富度', 4),
  (18, 10, '信用卡 / 簽帳金融卡', 1),
  (19, 10, 'LINE Pay / Apple Pay 行動支付', 2),
  (20, 10, '超商取貨付款', 3),
  (21, 10, '街口支付 / 全支付', 4);

-- 問卷 3 題目
INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating)
VALUES 
  (13, 3, 'Q1', 'BOOLEAN', '您在日常軟體開發工作中是否已開始深度使用 AI 輔助寫碼工具？', 1, 1, 1, 5),
  (14, 3, 'Q2', 'SINGLE', '您目前首選的主力程式碼編輯器或 IDE 為何？', 1, 2, 1, 5),
  (15, 3, 'Q3', 'MULTI', 'AI 工具主要在哪些開發環節為您帶來最顯著的效率提升？（可複選）', 1, 3, 1, 5),
  (16, 3, 'Q4', 'RATING', '整體而言，您對 AI 程式碼補全工具產出品質之信任與滿意評分（1~5 分）：', 1, 4, 1, 5),
  (17, 3, 'Q5', 'TEXT', '您在使用 AI 工具時最擔憂的潛在風險為何（如資安、授權或幻覺）？', 0, 5, 1, 5);

-- 問卷 3 選項
INSERT INTO survey_options (id, question_id, option_text, sort_order)
VALUES 
  (22, 13, '是 (Yes)', 1),
  (23, 13, '否 (No)', 2),
  (24, 14, 'VS Code', 1),
  (25, 14, 'IntelliJ IDEA / JetBrains 系列', 2),
  (26, 14, 'Cursor', 3),
  (27, 14, 'NeoVim / Vim', 4),
  (28, 15, '樣板代碼生成 (Boilerplate)', 1),
  (29, 15, '單元測試案例自動編撰', 2),
  (30, 15, '錯誤排查與 Debug 建議', 3),
  (31, 15, 'API 規格文件解讀與註解補齊', 4);

-- 問卷 4 題目 (非公開 DRAFT)
INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating)
VALUES 
  (18, 4, 'Q1', 'BOOLEAN', '您是否已完整試用過本次釋出的新版核心操作流程？', 1, 1, 1, 5),
  (19, 4, 'Q2', 'SINGLE', '整體新版 UI 介面設計的視覺直覺性與操作流暢度為何？', 1, 2, 1, 5),
  (20, 4, 'Q3', 'MULTI', '您認為本次改版中最具吸引力或最實用的亮點功能有哪些？（可複選）', 1, 3, 1, 5),
  (21, 4, 'Q4', 'RATING', '您將本產品推薦給同業或合作夥伴之意願評分（1~5 分）：', 1, 4, 1, 5),
  (22, 4, 'Q5', 'TEXT', '請留下您在試用過程中遭遇的任何 Bug 或操作不順暢的體驗細節：', 0, 5, 1, 5);

-- 問卷 4 選項
INSERT INTO survey_options (id, question_id, option_text, sort_order)
VALUES 
  (32, 18, '是 (Yes)', 1),
  (33, 18, '否 (No)', 2),
  (34, 19, '非常直覺且載入極為順暢', 1),
  (35, 19, '容易上手但仍有部分細節待優化', 2),
  (36, 19, '操作層級稍顯複雜，需要時間適應', 3),
  (37, 20, '即時跨平台即時數據分析儀表板', 1),
  (38, 20, '可填寫時段與大廳雙狀態動態配置', 2),
  (39, 20, '支援 Excel、SPSS 與 JSON 多格式匯出', 3),
  (40, 20, '條件邏輯跳題與多樣化題型支援', 4);

-- 6. 為 4 份問卷注入各 10 筆真實作答紀錄 (共 40 筆受訪者 submission + 40 筆 basic info + 答題明細)
-- 問卷 1: 10 筆
INSERT INTO submissions (id, survey_id, duration_seconds, submitted_at, ip_address) VALUES
  (1, 1, 120, '2026-10-10 10:15:20', '192.168.1.101'),
  (2, 1, 95,  '2026-10-10 11:30:15', '192.168.1.102'),
  (3, 1, 140, '2026-10-11 09:20:45', '192.168.1.103'),
  (4, 1, 110, '2026-10-11 14:45:10', '192.168.1.104'),
  (5, 1, 130, '2026-10-12 10:05:30', '192.168.1.105'),
  (6, 1, 85,  '2026-10-12 13:12:00', '192.168.1.106'),
  (7, 1, 160, '2026-10-12 16:50:22', '192.168.1.107'),
  (8, 1, 105, '2026-10-13 08:35:10', '192.168.1.108'),
  (9, 1, 125, '2026-10-13 11:40:55', '192.168.1.109'),
  (10, 1, 115, '2026-10-13 14:20:40', '192.168.1.110');

-- 問卷 2: 10 筆
INSERT INTO submissions (id, survey_id, duration_seconds, submitted_at, ip_address) VALUES
  (11, 2, 80,  '2026-10-09 12:10:00', '192.168.2.101'),
  (12, 2, 90,  '2026-10-10 15:22:10', '192.168.2.102'),
  (13, 2, 105, '2026-10-10 18:40:30', '192.168.2.103'),
  (14, 2, 75,  '2026-10-11 11:15:25', '192.168.2.104'),
  (15, 2, 110, '2026-10-11 16:30:40', '192.168.2.105'),
  (16, 2, 95,  '2026-10-12 09:45:15', '192.168.2.106'),
  (17, 2, 130, '2026-10-12 14:10:50', '192.168.2.107'),
  (18, 2, 85,  '2026-10-13 10:25:05', '192.168.2.108'),
  (19, 2, 100, '2026-10-13 13:50:35', '192.168.2.109'),
  (20, 2, 120, '2026-10-13 17:15:18', '192.168.2.110');

-- 問卷 3: 10 筆
INSERT INTO submissions (id, survey_id, duration_seconds, submitted_at, ip_address) VALUES
  (21, 3, 150, '2026-10-09 14:00:10', '192.168.3.101'),
  (22, 3, 135, '2026-10-10 10:10:20', '192.168.3.102'),
  (23, 3, 160, '2026-10-10 16:45:30', '192.168.3.103'),
  (24, 3, 110, '2026-10-11 08:30:15', '192.168.3.104'),
  (25, 3, 145, '2026-10-11 13:20:40', '192.168.3.105'),
  (26, 3, 125, '2026-10-12 11:05:50', '192.168.3.106'),
  (27, 3, 170, '2026-10-12 15:35:10', '192.168.3.107'),
  (28, 3, 140, '2026-10-13 09:15:25', '192.168.3.108'),
  (29, 3, 130, '2026-10-13 12:40:40', '192.168.3.109'),
  (30, 3, 155, '2026-10-13 16:55:00', '192.168.3.110');

-- 問卷 4: 10 筆
INSERT INTO submissions (id, survey_id, duration_seconds, submitted_at, ip_address) VALUES
  (31, 4, 90,  '2026-10-10 11:00:00', '192.168.4.101'),
  (32, 4, 105, '2026-10-10 14:15:20', '192.168.4.102'),
  (33, 4, 115, '2026-10-11 09:40:30', '192.168.4.103'),
  (34, 4, 85,  '2026-10-11 15:10:45', '192.168.4.104'),
  (35, 4, 120, '2026-10-12 10:25:10', '192.168.4.105'),
  (36, 4, 95,  '2026-10-12 13:50:25', '192.168.4.106'),
  (37, 4, 130, '2026-10-12 17:05:40', '192.168.4.107'),
  (38, 4, 100, '2026-10-13 08:45:55', '192.168.4.108'),
  (39, 4, 110, '2026-10-13 11:30:10', '192.168.4.109'),
  (40, 4, 125, '2026-10-13 15:15:35', '192.168.4.110');

-- 7. 注入受訪者基本資料 (40 筆)
INSERT INTO submission_basic_infos (submission_id, name, gender, age_group, phone, email, industry, residence) VALUES
  (1, '陳怡君', '女', '25-34歲', '0912345001', 'yijun@gmail.com', '軟體資訊業', '臺北市'),
  (2, '林冠宇', '男', '25-34歲', '0912345002', 'guanyu@gmail.com', '電子半導體', '新竹市'),
  (3, '張雅婷', '女', '25-34歲', '0912345003', 'yating@gmail.com', '金融保險業', '新北市'),
  (4, '黃建宏', '男', '35-44歲', '0912345004', 'jianhong@gmail.com', '電子商務業', '臺中市'),
  (5, '吳敏芳', '女', '25-34歲', '0912345005', 'minfang@gmail.com', '專業服務諮詢', '臺北市'),
  (6, '周志遠', '男', '25-34歲', '0912345006', 'zhiyuan@gmail.com', '軟體資訊業', '桃園市'),
  (7, '蔡淑芬', '女', '35-44歲', '0912345007', 'shufen@gmail.com', '教育培訓業', '臺南市'),
  (8, '楊宗翰', '男', '25-34歲', '0912345008', 'zonghan@gmail.com', '生技醫療業', '高雄市'),
  (9, '許家豪', '男', '25-34歲', '0912345009', 'jiahao@gmail.com', '行銷廣告傳播', '臺北市'),
  (10, '鄭佩君', '女', '35-44歲', '0912345010', 'peijun@gmail.com', '製造加工業', '新北市'),
  (11, '劉建國', '男', '45-54歲', '0922345011', 'jianguo@gmail.com', '批發零售業', '臺北市'),
  (12, '謝心怡', '女', '18-24歲', '0922345012', 'xinyi@gmail.com', '學生', '臺中市'),
  (13, '郭銘輝', '男', '35-44歲', '0922345013', 'minghui@gmail.com', '餐飲住宿業', '高雄市'),
  (14, '曾雅慧', '女', '25-34歲', '0922345014', 'yahui@gmail.com', '金融保險業', '新北市'),
  (15, '洪偉倫', '男', '25-34歲', '0922345015', 'weilun@gmail.com', '軟體資訊業', '臺北市'),
  (16, '邱美玲', '女', '25-34歲', '0922345016', 'meiling@gmail.com', '大眾傳播業', '新竹縣'),
  (17, '廖冠傑', '男', '25-34歲', '0922345017', 'guanjie@gmail.com', '公家機關', '苗栗縣'),
  (18, '徐佳琳', '女', '25-34歲', '0922345018', 'jialin@gmail.com', '零售服務業', '彰化縣'),
  (19, '賴柏翰', '男', '25-34歲', '0922345019', 'bohan@gmail.com', '物流倉儲業', '雲林縣'),
  (20, '葉子晴', '女', '25-34歲', '0922345020', 'ziqing@gmail.com', '電子半導體', '嘉義市'),
  (21, '沈敬庭', '男', '25-34歲', '0932345021', 'jingting@gmail.com', '軟體資訊業', '臺北市'),
  (22, '蘇韻如', '女', '25-34歲', '0932345022', 'yunru@gmail.com', '軟體資訊業', '新北市'),
  (23, '柯正義', '男', '35-44歲', '0932345023', 'zhengyi@gmail.com', '電子半導體', '新竹市'),
  (24, '胡欣儀', '女', '25-34歲', '0932345024', 'xinyi2@gmail.com', '軟體資訊業', '臺中市'),
  (25, '施伯達', '男', '35-44歲', '0932345025', 'boda@gmail.com', '電信網路業', '臺北市'),
  (26, '盧怡婷', '女', '25-34歲', '0932345026', 'yiting@gmail.com', '金融科技業', '臺南市'),
  (27, '彭政閔', '男', '25-34歲', '0932345027', 'zhengmin@gmail.com', '遊戲娛樂業', '高雄市'),
  (28, '高詩晴', '女', '25-34歲', '0932345028', 'shiqing@gmail.com', '軟體資訊業', '新北市'),
  (29, '潘冠勳', '男', '35-44歲', '0932345029', 'guanxun@gmail.com', '人工智慧領域', '臺北市'),
  (30, '董家銘', '男', '25-34歲', '0932345030', 'jiaming@gmail.com', '區塊鏈雲端', '臺中市'),
  (31, '江明翰', '男', '25-34歲', '0942345031', 'minghan@gmail.com', '專業軟體研發', '臺北市'),
  (32, '簡婉婷', '女', '25-34歲', '0942345032', 'wanting@gmail.com', '專案管理諮詢', '新北市'),
  (33, '韓志偉', '男', '35-44歲', '0942345033', 'zhiwei@gmail.com', '技術架構設計', '新竹市'),
  (34, '龔雅雯', '女', '25-34歲', '0942345034', 'yawen@gmail.com', '使用者體驗設計', '臺北市'),
  (35, '范姜毅', '男', '35-44歲', '0942345035', 'jiangyi@gmail.com', '數據工程架構', '臺中市'),
  (36, '白采菱', '女', '25-34歲', '0942345036', 'cailing@gmail.com', '品質保證測試', '高雄市'),
  (37, '歐陽成', '男', '35-44歲', '0942345037', 'yangcheng@gmail.com', '系統維運DevOps', '臺南市'),
  (38, '莊佩玲', '女', '25-34歲', '0942345038', 'peiling@gmail.com', '產品規劃總監', '臺北市'),
  (39, '方立群', '男', '25-34歲', '0942345039', 'liqun@gmail.com', '前端工程架構', '新北市'),
  (40, '巫思涵', '女', '25-34歲', '0942345040', 'sihan@gmail.com', '資安合規評鑑', '新竹縣');

-- 8. 注入題目作答明細 (包含單選、多選、量表評分、文字簡答、時間、日期)
-- 問卷 1 回答 (submissions 1-10)
INSERT INTO submission_answers (submission_id, question_id, option_id, rating_value, text_answer) VALUES
  (1, 1, 1, NULL, NULL), (1, 2, 3, NULL, NULL), (1, 3, 7, NULL, NULL), (1, 3, 8, NULL, NULL), (1, 4, NULL, 5, NULL), (1, 5, NULL, NULL, '希望公司補貼居家辦公人體工學椅與雙螢幕設備。'), (1, 6, NULL, NULL, '09:00'), (1, 7, NULL, NULL, '2026-10-25'),
  (2, 1, 1, NULL, NULL), (2, 2, 4, NULL, NULL), (2, 3, 7, NULL, NULL), (2, 3, 9, NULL, NULL), (2, 4, NULL, 4, NULL), (2, 5, NULL, NULL, '線上會議軟體應訂立每週無會議日（No Meeting Day）。'), (2, 6, NULL, NULL, '09:30'), (2, 7, NULL, NULL, '2026-10-28'),
  (3, 1, 2, NULL, NULL), (3, 2, 5, NULL, NULL), (3, 3, 8, NULL, NULL), (3, 3, 11, NULL, NULL), (3, 4, NULL, 4, NULL), (3, 5, NULL, NULL, '非同步溝通規範應該寫得更具體，避免主管一直隨時傳訊追蹤。'), (3, 6, NULL, NULL, '08:45'), (3, 7, NULL, NULL, '2026-11-02'),
  (4, 1, 1, NULL, NULL), (4, 2, 3, NULL, NULL), (4, 3, 7, NULL, NULL), (4, 3, 8, NULL, NULL), (4, 4, NULL, 5, NULL), (4, 5, NULL, NULL, '遠端協作效率極佳，省下通勤時間對工作專注度大有助益。'), (4, 6, NULL, NULL, '09:00'), (4, 7, NULL, NULL, '2026-10-30'),
  (5, 1, 1, NULL, NULL), (5, 2, 6, NULL, NULL), (5, 3, 9, NULL, NULL), (5, 3, 10, NULL, NULL), (5, 4, NULL, 3, NULL), (5, 5, NULL, NULL, '偶爾還是需要固定實體聚餐或月會，增進新進同仁的熟稔度。'), (5, 6, NULL, NULL, '10:00'), (5, 7, NULL, NULL, '2026-11-05'),
  (6, 1, 1, NULL, NULL), (6, 2, 4, NULL, NULL), (6, 3, 7, NULL, NULL), (6, 3, 11, NULL, NULL), (6, 4, NULL, 5, NULL), (6, 5, NULL, NULL, '建議加強雲端協作權限管控與跨部門文檔範本標準化。'), (6, 6, NULL, NULL, '09:15'), (6, 7, NULL, NULL, '2026-10-27'),
  (7, 1, 2, NULL, NULL), (7, 2, 5, NULL, NULL), (7, 3, 8, NULL, NULL), (7, 3, 9, NULL, NULL), (7, 4, NULL, 4, NULL), (7, 5, NULL, NULL, '目前工具有點過多，建議整合單一知識入口。'), (7, 6, NULL, NULL, '08:30'), (7, 7, NULL, NULL, '2026-11-10'),
  (8, 1, 1, NULL, NULL), (8, 2, 3, NULL, NULL), (8, 3, 7, NULL, NULL), (8, 3, 10, NULL, NULL), (8, 4, NULL, 5, NULL), (8, 5, NULL, NULL, '實施核心上班時段（Core Hours）制度，讓跨國溝通更協調。'), (8, 6, NULL, NULL, '09:00'), (8, 7, NULL, NULL, '2026-10-29'),
  (9, 1, 1, NULL, NULL), (9, 2, 4, NULL, NULL), (9, 3, 9, NULL, NULL), (9, 3, 11, NULL, NULL), (9, 4, NULL, 4, NULL), (9, 5, NULL, NULL, '線上協作看板大幅減少了無謂的口頭報告進度時間。'), (9, 6, NULL, NULL, '09:45'), (9, 7, NULL, NULL, '2026-11-01'),
  (10, 1, 1, NULL, NULL), (10, 2, 3, NULL, NULL), (10, 3, 7, NULL, NULL), (10, 3, 8, NULL, NULL), (10, 4, NULL, 5, NULL), (10, 5, NULL, NULL, '維持目前的混合模式即可，整體工作與生活平衡提升非常有感！'), (10, 6, NULL, NULL, '09:00'), (10, 7, NULL, NULL, '2026-11-04');

-- 問卷 2 回答 (submissions 11-20)
INSERT INTO submission_answers (submission_id, question_id, option_id, rating_value, text_answer) VALUES
  (11, 8, 12, NULL, NULL), (11, 9, 14, NULL, NULL), (11, 10, 18, NULL, NULL), (11, 11, NULL, 5, NULL), (11, 12, NULL, NULL, '希望常態免運門檻可以再調低一些，或是推出年費會員制免運。'),
  (12, 8, 12, NULL, NULL), (12, 9, 15, NULL, NULL), (12, 10, 19, NULL, NULL), (12, 11, NULL, 4, NULL), (12, 12, NULL, NULL, '出貨速度非常快，幾乎隔天就收到了！包裝完整乾淨。'),
  (13, 8, 12, NULL, NULL), (13, 9, 14, NULL, NULL), (13, 10, 18, NULL, NULL), (13, 10, 19, NULL, NULL), (13, 11, NULL, 5, NULL), (13, 12, NULL, NULL, '行動支付回饋點數很有感，常常為了點數加購。'),
  (14, 8, 12, NULL, NULL), (14, 9, 17, NULL, NULL), (14, 10, 20, NULL, NULL), (14, 11, NULL, 4, NULL), (14, 12, NULL, NULL, '商品評價若能附帶買家實拍照片會更有參考價值。'),
  (15, 8, 13, NULL, NULL), (15, 9, 14, NULL, NULL), (15, 10, 19, NULL, NULL), (15, 11, NULL, 3, NULL), (15, 12, NULL, NULL, '客服機器人有時候無法回答複雜問題，希望能有一鍵轉真人按鈕。'),
  (16, 8, 12, NULL, NULL), (16, 9, 15, NULL, NULL), (16, 10, 18, NULL, NULL), (16, 11, NULL, 5, NULL), (16, 12, NULL, NULL, '冷鏈食品配送品質極佳，沒有任何融化或破損。'),
  (17, 8, 12, NULL, NULL), (17, 9, 16, NULL, NULL), (17, 10, 21, NULL, NULL), (17, 11, NULL, 4, NULL), (17, 12, NULL, NULL, '退換貨到府取件非常方便，給售後服務滿分！'),
  (18, 8, 12, NULL, NULL), (18, 9, 14, NULL, NULL), (18, 10, 19, NULL, NULL), (18, 11, NULL, 5, NULL), (18, 12, NULL, NULL, '每週三的品牌會員日折扣很有誠意，會持續回購。'),
  (19, 8, 12, NULL, NULL), (19, 9, 17, NULL, NULL), (19, 10, 18, NULL, NULL), (19, 11, NULL, 4, NULL), (19, 12, NULL, NULL, '搜尋篩選功能的品牌與規格建議能再做得更精準。'),
  (20, 8, 12, NULL, NULL), (20, 9, 15, NULL, NULL), (20, 10, 18, NULL, NULL), (20, 10, 19, NULL, NULL), (20, 11, NULL, 5, NULL), (20, 12, NULL, NULL, '整體購物流程極度流暢，結帳一鍵搞定體驗極佳。');

-- 問卷 3 回答 (submissions 21-30)
INSERT INTO submission_answers (submission_id, question_id, option_id, rating_value, text_answer) VALUES
  (21, 13, 22, NULL, NULL), (21, 14, 26, NULL, NULL), (21, 15, 28, NULL, NULL), (21, 15, 29, NULL, NULL), (21, 16, NULL, 5, NULL), (21, 17, NULL, NULL, '最擔心私有企業程式碼上傳至公有雲可能帶來的機密洩漏風險。'),
  (22, 13, 22, NULL, NULL), (22, 14, 24, NULL, NULL), (22, 15, 28, NULL, NULL), (22, 15, 30, NULL, NULL), (22, 16, NULL, 4, NULL), (22, 17, NULL, NULL, '有時候 AI 生成的代碼會有隱蔽的邏輯漏洞（幻覺），仍需人工嚴格 Code Review。'),
  (23, 13, 22, NULL, NULL), (23, 14, 25, NULL, NULL), (23, 15, 29, NULL, NULL), (23, 15, 31, NULL, NULL), (23, 16, NULL, 5, NULL), (23, 17, NULL, NULL, '寫複雜單元測試時省下至少 60% 時間，簡直是工程師神器。'),
  (24, 13, 23, NULL, NULL), (24, 14, 24, NULL, NULL), (24, 15, 28, NULL, NULL), (24, 16, NULL, 3, NULL), (24, 17, NULL, NULL, '對於開源專案的授權協議（GPL / MIT）污染風險感到顧慮。'),
  (25, 13, 22, NULL, NULL), (25, 14, 26, NULL, NULL), (25, 15, 28, NULL, NULL), (25, 15, 30, NULL, NULL), (25, 16, NULL, 5, NULL), (25, 17, NULL, NULL, '跨檔案上下文檢索能力提升後，架構重構的效率翻倍。'),
  (26, 13, 22, NULL, NULL), (26, 14, 25, NULL, NULL), (26, 15, 30, NULL, NULL), (26, 15, 31, NULL, NULL), (26, 16, NULL, 4, NULL), (26, 17, NULL, NULL, '解讀冷門開源庫的原始碼時，AI 的行內註解摘要非常強大。'),
  (27, 13, 22, NULL, NULL), (27, 14, 27, NULL, NULL), (27, 15, 28, NULL, NULL), (27, 16, NULL, 4, NULL), (27, 17, NULL, NULL, '終端機內的 CLI AI 工具如果能更好整合 tmux 會更完美。'),
  (28, 13, 22, NULL, NULL), (28, 14, 26, NULL, NULL), (28, 15, 29, NULL, NULL), (28, 15, 30, NULL, NULL), (28, 16, NULL, 5, NULL), (28, 17, NULL, NULL, 'AI 輔助讓資淺工程師的成長曲線大幅縮短，是不可逆的趨勢。'),
  (29, 13, 22, NULL, NULL), (29, 14, 24, NULL, NULL), (29, 15, 28, NULL, NULL), (29, 15, 31, NULL, NULL), (29, 16, NULL, 4, NULL), (29, 17, NULL, NULL, '企業內部自建地端大模型（Self-hosted LLM）是解決資安顧慮的最佳解。'),
  (30, 13, 22, NULL, NULL), (30, 14, 25, NULL, NULL), (30, 15, 28, NULL, NULL), (30, 15, 29, NULL, NULL), (30, 16, NULL, 5, NULL), (30, 17, NULL, NULL, '現在幾乎已經無法回到沒有 AI 輔助寫碼的傳統開發模式了。');

-- 問卷 4 回答 (submissions 31-40)
INSERT INTO submission_answers (submission_id, question_id, option_id, rating_value, text_answer) VALUES
  (31, 18, 32, NULL, NULL), (31, 19, 34, NULL, NULL), (31, 20, 37, NULL, NULL), (31, 20, 38, NULL, NULL), (31, 21, NULL, 5, NULL), (31, 22, NULL, NULL, '新版後台儀表板的深色霓虹科技風視覺非常驚艷！直角卡片很硬派好看。'),
  (32, 18, 32, NULL, NULL), (32, 19, 34, NULL, NULL), (32, 20, 38, NULL, NULL), (32, 20, 40, NULL, NULL), (32, 21, NULL, 5, NULL), (32, 22, NULL, NULL, '雙狀態開關（大廳公開 + 開放填寫）的區分非常清晰，解決了既有系統的痛點。'),
  (33, 18, 32, NULL, NULL), (33, 19, 35, NULL, NULL), (33, 20, 37, NULL, NULL), (33, 20, 39, NULL, NULL), (33, 21, NULL, 4, NULL), (33, 22, NULL, NULL, '匯出 SPSS 格式的語法檔有正確附帶標籤對齊，統計研究者非常感激。'),
  (34, 18, 32, NULL, NULL), (34, 19, 34, NULL, NULL), (34, 20, 37, NULL, NULL), (34, 20, 40, NULL, NULL), (34, 21, NULL, 5, NULL), (34, 22, NULL, NULL, '問卷時限彈窗設定開始與結束時間非常順暢，點讚！'),
  (35, 18, 33, NULL, NULL), (35, 19, 35, NULL, NULL), (35, 20, 38, NULL, NULL), (35, 21, NULL, 4, NULL), (35, 22, NULL, NULL, '行動版手機上的彈窗高度建議加入 safe-area 適配。'),
  (36, 18, 32, NULL, NULL), (36, 19, 34, NULL, NULL), (36, 20, 39, NULL, NULL), (36, 20, 40, NULL, NULL), (36, 21, NULL, 5, NULL), (36, 22, NULL, NULL, '複製分享連結彈窗現在不論公開或私人都能產生專屬 URL，非常實用。'),
  (37, 18, 32, NULL, NULL), (37, 19, 34, NULL, NULL), (37, 20, 37, NULL, NULL), (37, 20, 38, NULL, NULL), (37, 21, NULL, 5, NULL), (37, 22, NULL, NULL, '前後端完全真實連線 MySQL，零 Mock 資料，運行速度非常快！'),
  (38, 18, 32, NULL, NULL), (38, 19, 35, NULL, NULL), (38, 20, 37, NULL, NULL), (38, 21, NULL, 4, NULL), (38, 22, NULL, NULL, '希望未來能再加入更多圖表匯出為向量 SVG 的功能。'),
  (39, 18, 32, NULL, NULL), (39, 19, 34, NULL, NULL), (39, 20, 38, NULL, NULL), (39, 20, 39, NULL, NULL), (39, 21, NULL, 5, NULL), (39, 22, NULL, NULL, '鎖定題目不可修改是正確的產品決定，避免了既有回覆資料結構損壞。'),
  (40, 18, 32, NULL, NULL), (40, 19, 34, NULL, NULL), (40, 20, 37, NULL, NULL), (40, 20, 40, NULL, NULL), (40, 21, NULL, 5, NULL), (40, 22, NULL, NULL, '整體功能完成度非常高，穩定度令人滿意！');

SET FOREIGN_KEY_CHECKS = 1;
