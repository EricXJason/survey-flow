USE `survey_flow`;

-- 1. 確保 admin 與 testuser 帳號存在 (密碼分別為 Admin123! 與 User1234!)
INSERT INTO `users` (`id`, `username`, `email`, `password_hash`, `full_name`, `phone`, `role`, `status`)
VALUES 
(1, 'admin', 'admin@surveyflow.com', '$argon2id$v=19$m=4096,t=3,p=1$nZ+LYNg2uDIXui5hfl1jmQ$M+e7kMS6aVWA4pgd8R9wlrtIdhgx3RsdXISL3Mp+Ie8', '系統最高管理員', '0912345678', 'ROLE_ADMIN', 'ACTIVE'),
(2, 'testuser', 'testuser@surveyflow.com', '$argon2id$v=19$m=4096,t=3,p=1$W11mfIjuLSj3tIpgwP8cHw$BKI4iGeP6pnltXIxralk59jze902teGlCBTdlQSJSBA', '測試會員小明', '0987654321', 'ROLE_USER', 'ACTIVE')
ON DUPLICATE KEY UPDATE 
`username` = VALUES(`username`),
`password_hash` = VALUES(`password_hash`),
`email` = VALUES(`email`),
`full_name` = VALUES(`full_name`),
`role` = VALUES(`role`),
`status` = 'ACTIVE';

-- 清理舊問卷以保持純淨乾淨的 4 筆問卷展示
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE `submission_answers`;
TRUNCATE TABLE `submission_basic_infos`;
TRUNCATE TABLE `submissions`;
TRUNCATE TABLE `survey_options`;
TRUNCATE TABLE `survey_questions`;
TRUNCATE TABLE `survey_basic_info_configs`;
TRUNCATE TABLE `surveys`;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 問卷 1: 2026 數位轉型與遠端辦公協作體驗調研 (公開 PUBLISHED)
-- ============================================================================
INSERT INTO `surveys` (`id`, `user_id`, `title`, `description`, `category`, `status`, `start_date`, `end_date`, `view_count`)
VALUES (1, 2, '2026 數位轉型與遠端辦公協作體驗調研', '感謝您參與本次調研，本問卷旨在評估新形態混合辦公機制之滿意度與協作效率，全數題目均嚴格匿名處理。', '企業組織', 'PUBLISHED', '2026-10-01 00:00:00', '2026-11-30 23:59:59', 156);

INSERT INTO `survey_basic_info_configs` (`survey_id`, `require_name`, `require_gender`, `require_age`, `require_phone`, `require_email`, `require_industry`, `require_residence`)
VALUES (1, 1, 1, 1, 0, 1, 1, 1);

INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `is_required`, `sort_order`, `min_rating`, `max_rating`)
VALUES 
(1, 1, 'Q1', 'SINGLE', '您目前的每週遠端居家辦公天數為何？', 1, 1, 1, 5),
(2, 1, 'Q2', 'MULTI', '在遠端協作過程中，您最常使用的通訊或專案管理軟體有哪些？(可複選)', 1, 2, 1, 5),
(3, 1, 'Q3', 'RATING', '總體而言，您對目前團隊的遠端溝通流暢度滿意評分為？(1-5分)', 1, 3, 1, 5),
(4, 1, 'Q4', 'TEXT', '您認為公司在推行混合辦公制度上，目前最急需改善的痛點或建議為何？', 0, 4, 1, 5);

INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`)
VALUES 
(1, 1, '完全實體進辦公室 (0天)', 1),
(2, 1, '1 至 2 天遠端', 2),
(3, 1, '3 至 4 天遠端', 3),
(4, 1, '完全遠端辦公 (5天)', 4),
(5, 2, 'Slack / Discord', 1),
(6, 2, 'Microsoft Teams', 2),
(7, 2, 'Notion / Jira', 3),
(8, 2, 'Google Workspace', 4);

-- 問卷 1 的 10 筆填答資料
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`) VALUES
(1, 1, '192.168.1.101', 120, '2026-10-07 09:15:00'),
(2, 1, '192.168.1.102', 145, '2026-10-07 14:22:00'),
(3, 1, '192.168.1.103', 95,  '2026-10-08 10:05:00'),
(4, 1, '192.168.1.104', 210, '2026-10-08 16:40:00'),
(5, 1, '192.168.1.105', 180, '2026-10-09 11:30:00'),
(6, 1, '192.168.1.106', 135, '2026-10-10 15:18:00'),
(7, 1, '192.168.1.107', 160, '2026-10-11 13:50:00'),
(8, 1, '192.168.1.108', 190, '2026-10-12 17:10:00'),
(9, 1, '192.168.1.109', 110, '2026-10-13 10:20:00'),
(10, 1, '192.168.1.110', 175, '2026-10-13 14:45:00');

INSERT INTO `submission_basic_infos` (`submission_id`, `name`, `gender`, `age_group`, `email`, `industry`, `residence`) VALUES
(1, '陳大明', '男', '25-34歲', 'daming@example.com', '資訊軟體業', '臺北市'),
(2, '林美華', '女', '35-44歲', 'meihua@example.com', '金融保險業', '新北市'),
(3, '張建宏', '男', '18-24歲', 'jianhong@example.com', '教育研究業', '臺中市'),
(4, '黃雅婷', '女', '25-34歲', 'yating@example.com', '行銷傳媒業', '臺北市'),
(5, '王志偉', '男', '35-44歲', 'zhiwei@example.com', '生技醫療業', '高雄市'),
(6, '李怡君', '女', '25-34歲', 'yijun@example.com', '資訊軟體業', '新竹市'),
(7, '吳冠宇', '男', '45-54歲', 'guanyu@example.com', '傳統製造業', '臺南市'),
(8, '周佩蓉', '女', '18-24歲', 'peirong@example.com', '學生／研究', '臺北市'),
(9, '蔡宗翰', '男', '25-34歲', 'zonghan@example.com', '資訊軟體業', '桃園市'),
(10, '鄭惠文', '女', '35-44歲', 'huiwen@example.com', '顧問諮詢業', '臺中市');

INSERT INTO `submission_answers` (`submission_id`, `question_id`, `option_id`, `rating_value`, `text_answer`) VALUES
-- 填答 1
(1, 1, 2, NULL, NULL), (1, 2, 5, NULL, NULL), (1, 2, 7, NULL, NULL), (1, 3, NULL, 4, NULL), (1, 4, NULL, NULL, '希望能提供更充裕的居家辦公設備補助與螢幕借用'),
-- 填答 2
(2, 1, 3, NULL, NULL), (2, 2, 6, NULL, NULL), (2, 2, 8, NULL, NULL), (2, 3, NULL, 5, NULL), (2, 4, NULL, NULL, '跨部門溝通流暢，遠端開會效率極高'),
-- 填答 3
(3, 1, 4, NULL, NULL), (3, 2, 5, NULL, NULL), (3, 2, 7, NULL, NULL), (3, 3, NULL, 5, NULL), (3, 4, NULL, NULL, '完全遠端省去通勤時間，工作專注度大幅提升'),
-- 填答 4
(4, 1, 2, NULL, NULL), (4, 2, 6, NULL, NULL), (4, 3, NULL, 4, NULL), (4, 4, NULL, NULL, '建議明確定義下班離線時間，避免訊息隨時通知干擾生活'),
-- 填答 5
(5, 1, 1, NULL, NULL), (5, 2, 6, NULL, NULL), (5, 2, 8, NULL, NULL), (5, 3, NULL, 3, NULL), (5, 4, NULL, NULL, '某些實驗室器材仍需實體操作，混合模式較為合適'),
-- 填答 6
(6, 1, 3, NULL, NULL), (6, 2, 5, NULL, NULL), (6, 2, 7, NULL, NULL), (6, 3, NULL, 5, NULL), (6, 4, NULL, NULL, '使用 Slack 搭配 Jira 追蹤敏捷衝刺非常順利'),
-- 填答 7
(7, 1, 2, NULL, NULL), (7, 2, 6, NULL, NULL), (7, 3, NULL, 4, NULL), (7, 4, NULL, NULL, '需加強資安 VPN 連線穩定度'),
-- 填答 8
(8, 1, 4, NULL, NULL), (8, 2, 5, NULL, NULL), (8, 2, 8, NULL, NULL), (8, 3, NULL, 5, NULL), (8, 4, NULL, NULL, '線上協作文件非常方便，建議定期舉辦非正式線上茶會'),
-- 填答 9
(9, 1, 3, NULL, NULL), (9, 2, 5, NULL, NULL), (9, 2, 7, NULL, NULL), (9, 3, NULL, 4, NULL), (9, 4, NULL, NULL, '希望每週有一天固定為無會議專注日 (No-Meeting Day)'),
-- 填答 10
(10, 1, 3, NULL, NULL), (10, 2, 6, NULL, NULL), (10, 2, 8, NULL, NULL), (10, 3, NULL, 5, NULL), (10, 4, NULL, NULL, '總體制度完善，期待長期持續推行');

-- ============================================================================
-- 問卷 2: 2026 消費者品牌認知與電商購物滿意度調查 (公開 PUBLISHED)
-- ============================================================================
INSERT INTO `surveys` (`id`, `user_id`, `title`, `description`, `category`, `status`, `start_date`, `end_date`, `view_count`)
VALUES (2, 2, '2026 消費者品牌認知與電商購物滿意度調查', '探討現代消費者於各大電商平台之購物頻率、物流體驗與價格敏感度評估。', '消費生活', 'PUBLISHED', '2026-10-01 00:00:00', '2026-11-30 23:59:59', 189);

INSERT INTO `survey_basic_info_configs` (`survey_id`, `require_name`, `require_gender`, `require_age`, `require_phone`, `require_email`, `require_industry`, `require_residence`)
VALUES (2, 1, 1, 1, 0, 1, 1, 1);

INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `is_required`, `sort_order`, `min_rating`, `max_rating`)
VALUES 
(5, 2, 'Q1', 'SINGLE', '您平時最常使用的綜合型電商購物平台為何？', 1, 1, 1, 5),
(6, 2, 'Q2', 'MULTI', '吸引您在該平台下單的主要誘因為何？(可複選)', 1, 2, 1, 5),
(7, 2, 'Q3', 'RATING', '針對整體下單至收貨的物流運送體驗，您的滿意評分為？(1-5分)', 1, 3, 1, 5),
(8, 2, 'Q4', 'TEXT', '對於各大電商之促銷活動或會員機制，您最期待未來有哪些改進？', 0, 4, 1, 5);

INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`)
VALUES 
(9, 5, '蝦皮購物 Shopee', 1),
(10, 5, 'momo 購物網', 2),
(11, 5, 'PChome 24h 購物', 3),
(12, 5, 'Coupang 酷澎', 4),
(13, 6, '免運券與免運門檻低', 1),
(14, 6, '商品價格優惠折扣大', 2),
(15, 6, '隔日或24小時快速到貨', 3),
(16, 6, '售後退貨無負擔保障', 4);

-- 問卷 2 的 10 筆填答資料
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`) VALUES
(11, 2, '192.168.2.101', 115, '2026-10-07 11:20:00'),
(12, 2, '192.168.2.102', 130, '2026-10-08 13:10:00'),
(13, 2, '192.168.2.103', 140, '2026-10-09 15:45:00'),
(14, 2, '192.168.2.104', 160, '2026-10-09 20:10:00'),
(15, 2, '192.168.2.105', 105, '2026-10-10 10:30:00'),
(16, 2, '192.168.2.106', 170, '2026-10-11 12:25:00'),
(17, 2, '192.168.2.107', 125, '2026-10-11 16:50:00'),
(18, 2, '192.168.2.108', 195, '2026-10-12 11:15:00'),
(19, 2, '192.168.2.109', 150, '2026-10-13 09:40:00'),
(20, 2, '192.168.2.110', 135, '2026-10-13 15:30:00');

INSERT INTO `submission_basic_infos` (`submission_id`, `name`, `gender`, `age_group`, `email`, `industry`, `residence`) VALUES
(11, '何嘉仁', '男', '25-34歲', 'jiaren@example.com', '批發零售業', '新北市'),
(12, '朱麗安', '女', '18-24歲', 'julian@example.com', '學生族群', '臺北市'),
(13, '施信宏', '男', '35-44歲', 'xinhong@example.com', '金融服務業', '臺中市'),
(14, '姜曉萱', '女', '25-34歲', 'xiaoxuan@example.com', '電子商務業', '高雄市'),
(15, '潘建安', '男', '45-54歲', 'jianan@example.com', '交通運輸業', '臺南市'),
(16, '宋美鈴', '女', '25-34歲', 'meiling@example.com', '餐飲服務業', '桃園市'),
(17, '姚志強', '男', '35-44歲', 'zhiqiang@example.com', '自由接案者', '新竹市'),
(18, '陸品妍', '女', '18-24歲', 'pinyan@example.com', '流行時尚業', '臺北市'),
(19, '韓立群', '男', '25-34歲', 'liqun@example.com', '軟體資訊業', '新北市'),
(20, '范芷芸', '女', '35-44歲', 'zhiyun@example.com', '教育文化業', '臺中市');

INSERT INTO `submission_answers` (`submission_id`, `question_id`, `option_id`, `rating_value`, `text_answer`) VALUES
(11, 5, 9, NULL, NULL), (11, 6, 13, NULL, NULL), (11, 6, 14, NULL, NULL), (11, 7, NULL, 5, NULL), (11, 8, NULL, NULL, '希望免運券可以自動套用，不用手動搶券'),
(12, 5, 10, NULL, NULL), (12, 6, 15, NULL, NULL), (12, 6, 16, NULL, NULL), (12, 7, NULL, 5, NULL), (12, 8, NULL, NULL, 'momo 到貨速度極快，包裝也非常完整安心'),
(13, 5, 12, NULL, NULL), (13, 6, 14, NULL, NULL), (13, 6, 15, NULL, NULL), (13, 7, NULL, 5, NULL), (13, 8, NULL, NULL, '酷澎的火箭跨境服務很有競爭力'),
(14, 5, 9, NULL, NULL), (14, 6, 13, NULL, NULL), (14, 7, NULL, 4, NULL), (14, 8, NULL, NULL, '希望加強買家隱私保護，外包裝不要直接印出商品名稱'),
(15, 5, 11, NULL, NULL), (15, 6, 15, NULL, NULL), (15, 7, NULL, 4, NULL), (15, 8, NULL, NULL, 'PChome 3C商品依然是首選，但介面可以更現代化'),
(16, 5, 10, NULL, NULL), (16, 6, 14, NULL, NULL), (16, 6, 15, NULL, NULL), (16, 7, NULL, 5, NULL), (16, 8, NULL, NULL, '折扣力度夠大，回饋幣折抵機制很滿意'),
(17, 5, 9, NULL, NULL), (17, 6, 13, NULL, NULL), (17, 6, 14, NULL, NULL), (17, 7, NULL, 4, NULL), (17, 8, NULL, NULL, '店到店取貨據點很多，非常便利'),
(18, 5, 12, NULL, NULL), (18, 6, 14, NULL, NULL), (18, 6, 15, NULL, NULL), (18, 7, NULL, 5, NULL), (18, 8, NULL, NULL, '常買日韓零食與生活用品，價格比實體通路便宜'),
(19, 5, 10, NULL, NULL), (19, 6, 15, NULL, NULL), (19, 7, NULL, 5, NULL), (19, 8, NULL, NULL, '雙十一或促銷期物流依然能在兩天內送達，非常厲害'),
(20, 5, 9, NULL, NULL), (20, 6, 13, NULL, NULL), (20, 6, 14, NULL, NULL), (20, 7, NULL, 4, NULL), (20, 8, NULL, NULL, '期望滿額免運條件能再降低一點');

-- ============================================================================
-- 問卷 3: 2026 軟體工程師開發工具與 AI 輔助寫碼偏好評測 (公開 PUBLISHED)
-- ============================================================================
INSERT INTO `surveys` (`id`, `user_id`, `title`, `description`, `category`, `status`, `start_date`, `end_date`, `view_count`)
VALUES (3, 2, '2026 軟體工程師開發工具與 AI 輔助寫碼偏好評測', '調研軟體工程師在現代開發流程中使用生成式 AI 與整合開發環境 (IDE) 之真實體驗與效率提升評鑑。', '科技資訊', 'PUBLISHED', '2026-10-01 00:00:00', '2026-11-30 23:59:59', 240);

INSERT INTO `survey_basic_info_configs` (`survey_id`, `require_name`, `require_gender`, `require_age`, `require_phone`, `require_email`, `require_industry`, `require_residence`)
VALUES (3, 1, 1, 1, 0, 1, 1, 1);

INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `is_required`, `sort_order`, `min_rating`, `max_rating`)
VALUES 
(9, 3, 'Q1', 'SINGLE', '您日常工作中最主要使用的程式碼編輯器或 IDE 為何？', 1, 1, 1, 5),
(10, 3, 'Q2', 'MULTI', '您目前最常使用的 AI 編程輔助工具或模型有哪些？(可複選)', 1, 2, 1, 5),
(11, 3, 'Q3', 'RATING', 'AI 工具對您提升整體寫碼效率與單元測試產出的幫助評分為？(1-5分)', 1, 3, 1, 5),
(12, 3, 'Q4', 'TEXT', '您認為當前 AI 輔助寫碼工具最需要突破或改善的缺陷是什麼？', 0, 4, 1, 5);

INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`)
VALUES 
(17, 9, 'Visual Studio Code', 1),
(18, 9, 'IntelliJ IDEA / JetBrains 系列', 2),
(19, 9, 'Cursor AI 編輯器', 3),
(20, 9, 'Neovim / Vim', 4),
(21, 10, 'GitHub Copilot', 1),
(22, 10, 'Claude 3.7 / Claude Code', 2),
(23, 10, 'OpenAI ChatGPT Plus', 3),
(24, 10, 'Antigravity AI Agent', 4);

-- 問卷 3 的 10 筆填答資料
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`) VALUES
(21, 3, '192.168.3.101', 150, '2026-10-07 16:30:00'),
(22, 3, '192.168.3.102', 180, '2026-10-08 10:45:00'),
(23, 3, '192.168.3.103', 210, '2026-10-09 14:15:00'),
(24, 3, '192.168.3.104', 165, '2026-10-10 11:00:00'),
(25, 3, '192.168.3.105', 130, '2026-10-10 17:35:00'),
(26, 3, '192.168.3.106', 190, '2026-10-11 15:20:00'),
(27, 3, '192.168.3.107', 140, '2026-10-12 09:50:00'),
(28, 3, '192.168.3.108', 220, '2026-10-12 16:05:00'),
(29, 3, '192.168.3.109', 175, '2026-10-13 11:30:00'),
(30, 3, '192.168.3.110', 160, '2026-10-13 16:15:00');

INSERT INTO `submission_basic_infos` (`submission_id`, `name`, `gender`, `age_group`, `email`, `industry`, `residence`) VALUES
(21, '柯孟哲', '男', '25-34歲', 'mengzhe@example.com', '軟體開發工程師', '臺北市'),
(22, '盧曉芳', '女', '25-34歲', 'xiaofang@example.com', '前端架構師', '新北市'),
(23, '段嘉誠', '男', '35-44歲', 'jiacheng@example.com', '後端技術主管', '新竹市'),
(24, '童怡婷', '女', '25-34歲', 'yiting.t@example.com', '全端工程師', '臺北市'),
(25, '韋彥廷', '男', '18-24歲', 'yanting@example.com', '資工研究所學生', '臺南市'),
(26, '翁郁雯', '女', '25-34歲', 'yuwen@example.com', 'DevOps 維運工程師', '臺中市'),
(27, '邱博翔', '男', '35-44歲', 'boxiang@example.com', '資深後端工程師', '臺北市'),
(28, '湯詩涵', '女', '25-34歲', 'shihan@example.com', '資料工程師', '桃園市'),
(29, '顏志偉', '男', '25-34歲', 'zhiwei.y@example.com', '演算法工程師', '新北市'),
(30, '白家豪', '男', '35-44歲', 'jiahao@example.com', '系統架構師', '臺北市');

INSERT INTO `submission_answers` (`submission_id`, `question_id`, `option_id`, `rating_value`, `text_answer`) VALUES
(21, 9, 18, NULL, NULL), (21, 10, 21, NULL, NULL), (21, 10, 24, NULL, NULL), (21, 11, NULL, 5, NULL), (21, 12, NULL, NULL, '對於大型代碼庫的跨檔案依賴理解力希望再深化'),
(22, 9, 17, NULL, NULL), (22, 10, 22, NULL, NULL), (22, 10, 24, NULL, NULL), (22, 11, NULL, 5, NULL), (22, 12, NULL, NULL, '前端重構與 SCSS 調整非常神速，省去繁瑣手打時間'),
(23, 9, 18, NULL, NULL), (23, 10, 21, NULL, NULL), (23, 10, 22, NULL, NULL), (23, 11, NULL, 5, NULL), (23, 12, NULL, NULL, '生成單元測試與 Mock 物件的能力極佳，覆蓋率顯著提升'),
(24, 9, 19, NULL, NULL), (24, 10, 22, NULL, NULL), (24, 10, 24, NULL, NULL), (24, 11, NULL, 5, NULL), (24, 12, NULL, NULL, 'Agentic 自動執行指令與修復錯誤的閉環體驗令人驚艷'),
(25, 9, 17, NULL, NULL), (25, 10, 23, NULL, NULL), (25, 11, NULL, 4, NULL), (25, 12, NULL, NULL, '希望能強化在特殊開源框架或私有協定上的掌握度'),
(26, 9, 17, NULL, NULL), (26, 10, 21, NULL, NULL), (26, 10, 24, NULL, NULL), (26, 11, NULL, 5, NULL), (26, 12, NULL, NULL, 'Dockerfile 與 CI/CD pipeline 腳本生成精確度很高'),
(27, 9, 18, NULL, NULL), (27, 10, 21, NULL, NULL), (27, 10, 22, NULL, NULL), (27, 11, NULL, 4, NULL), (27, 12, NULL, NULL, '在複雜業務邏輯時偶爾有幻覺，需具備工程師親自核驗代碼能力'),
(28, 9, 17, NULL, NULL), (28, 10, 23, NULL, NULL), (28, 10, 24, NULL, NULL), (28, 11, NULL, 5, NULL), (28, 12, NULL, NULL, '寫複雜 SQL 查詢與分析統計聚合語句時極為省力'),
(29, 9, 18, NULL, NULL), (29, 10, 22, NULL, NULL), (29, 10, 24, NULL, NULL), (29, 11, NULL, 5, NULL), (29, 12, NULL, NULL, '自動遵循 SOLID 與 Clean Code 規範非常有幫助'),
(30, 9, 19, NULL, NULL), (30, 10, 21, NULL, NULL), (30, 10, 24, NULL, NULL), (30, 11, NULL, 5, NULL), (30, 12, NULL, NULL, '軟體工程師已進入與 AI 結對編程的高效新紀元');

-- ============================================================================
-- 問卷 4: 【內部封測／非公開】次世代新產品核心功能體驗問卷 (非公開 DRAFT)
-- ============================================================================
INSERT INTO `surveys` (`id`, `user_id`, `title`, `description`, `category`, `status`, `start_date`, `end_date`, `view_count`)
VALUES (4, 2, '【內部封測／非公開】次世代新產品核心功能體驗問卷', '本問卷為產品封測專用，不對問卷大廳公開展示。持有此專屬測試連結之種子受訪者可直接參與體驗評估。', '產品研發', 'DRAFT', '2026-10-01 00:00:00', '2026-11-30 23:59:59', 85);

INSERT INTO `survey_basic_info_configs` (`survey_id`, `require_name`, `require_gender`, `require_age`, `require_phone`, `require_email`, `require_industry`, `require_residence`)
VALUES (4, 1, 1, 1, 0, 1, 1, 1);

INSERT INTO `survey_questions` (`id`, `survey_id`, `question_code`, `type`, `question_text`, `is_required`, `sort_order`, `min_rating`, `max_rating`)
VALUES 
(13, 4, 'Q1', 'SINGLE', '您在體驗本次新產品原型時，核心操作流暢度評估為何？', 1, 1, 1, 5),
(14, 4, 'Q2', 'MULTI', '本次封測釋出的功能模組中，您認為最有實用價值的項目為何？(可複選)', 1, 2, 1, 5),
(15, 4, 'Q3', 'RATING', '若本產品未來正式商用發布，您推薦同仁或同業採用的意願評分為？(1-5分)', 1, 3, 1, 5),
(16, 4, 'Q4', 'TEXT', '在正式公開上線前，您認為開發團隊最急需改善或優化的細節是什麼？', 0, 4, 1, 5);

INSERT INTO `survey_options` (`id`, `question_id`, `option_text`, `sort_order`)
VALUES 
(25, 13, '極度直覺順暢，幾無學習曲線', 1),
(26, 13, '大致順暢，偶有操作小卡頓', 2),
(27, 13, '需要重新適應新版按鈕佈局', 3),
(28, 13, '流程繁複，容易迷失步驟', 4),
(29, 14, '即時大數據分析儀表板', 1),
(30, 14, '自訂條件邏輯動態跳題', 2),
(31, 14, '一鍵多格式報表匯出 (Excel/SPSS)', 3),
(32, 14, '直角深色霓虹 HUD 視覺設計', 4);

-- 問卷 4 的 10 筆填答資料
INSERT INTO `submissions` (`id`, `survey_id`, `ip_address`, `duration_seconds`, `submitted_at`) VALUES
(31, 4, '192.168.4.101', 140, '2026-10-07 18:20:00'),
(32, 4, '192.168.4.102', 170, '2026-10-08 11:30:00'),
(33, 4, '192.168.4.103', 195, '2026-10-09 16:50:00'),
(34, 4, '192.168.4.104', 160, '2026-10-10 13:40:00'),
(35, 4, '192.168.4.105', 125, '2026-10-10 20:15:00'),
(36, 4, '192.168.4.106', 185, '2026-10-11 10:05:00'),
(37, 4, '192.168.4.107', 150, '2026-10-12 14:30:00'),
(38, 4, '192.168.4.108', 210, '2026-10-12 19:25:00'),
(39, 4, '192.168.4.109', 175, '2026-10-13 12:45:00'),
(40, 4, '192.168.4.110', 165, '2026-10-13 17:00:00');

INSERT INTO `submission_basic_infos` (`submission_id`, `name`, `gender`, `age_group`, `email`, `industry`, `residence`) VALUES
(31, '金柏翰', '男', '25-34歲', 'bohan@example.com', '產品設計師 (UI/UX)', '臺北市'),
(32, '薛家寧', '女', '25-34歲', 'jianing@example.com', '產品經理 (PM)', '新北市'),
(33, '丁子軒', '男', '35-44歲', 'zixuan@example.com', '技術總監 (CTO)', '臺中市'),
(34, '莫雅文', '女', '18-24歲', 'yawen@example.com', 'QA 品質保證工程師', '臺北市'),
(35, '駱彥銘', '男', '25-34歲', 'yanming@example.com', '前端工程師', '新竹市'),
(36, '裘佩珊', '女', '35-44歲', 'peishan@example.com', '商務拓展主管', '高雄市'),
(37, '卞國榮', '男', '45-54歲', 'guorong@example.com', '執行長 (CEO)', '臺北市'),
(38, '閔思婷', '女', '25-34歲', 'siting@example.com', '行銷企劃專員', '桃園市'),
(39, '卓宗翰', '男', '25-34歲', 'zonghan.z@example.com', '全端工程師', '臺中市'),
(40, '冷婉晴', '女', '18-24歲', 'wanqing@example.com', '實習研究員', '臺北市');

INSERT INTO `submission_answers` (`submission_id`, `question_id`, `option_id`, `rating_value`, `text_answer`) VALUES
(31, 13, 25, NULL, NULL), (31, 14, 29, NULL, NULL), (31, 14, 32, NULL, NULL), (31, 15, NULL, 5, NULL), (31, 16, NULL, NULL, 'HUD 風格極有質感，圖表對比清晰一目了然'),
(32, 13, 25, NULL, NULL), (32, 14, 29, NULL, NULL), (32, 14, 30, NULL, NULL), (32, 15, NULL, 5, NULL), (32, 16, NULL, NULL, '條件跳題功能非常靈活，能大幅節省問卷設計時間'),
(33, 13, 26, NULL, NULL), (33, 14, 29, NULL, NULL), (33, 14, 31, NULL, NULL), (33, 15, NULL, 5, NULL), (33, 16, NULL, NULL, '支援 SPSS 匯出對學術研究與企業級統計非常實用'),
(34, 13, 25, NULL, NULL), (34, 14, 30, NULL, NULL), (34, 15, NULL, 5, NULL), (34, 16, NULL, NULL, '表單即時防呆與離開守衛 canDeactivate 體驗非常到位'),
(35, 13, 25, NULL, NULL), (35, 14, 29, NULL, NULL), (35, 14, 31, NULL, NULL), (35, 15, NULL, 5, NULL), (35, 16, NULL, NULL, '響應式在手機版與桌面版的佈局切換很自然流暢'),
(36, 13, 25, NULL, NULL), (36, 14, 29, NULL, NULL), (36, 15, NULL, 5, NULL), (36, 16, NULL, NULL, '數據指標完整，能快速向客戶展示統計結果'),
(37, 13, 25, NULL, NULL), (37, 14, 29, NULL, NULL), (37, 14, 31, NULL, NULL), (37, 15, NULL, 5, NULL), (37, 16, NULL, NULL, '系統架構紮實，具備企業級商業價值'),
(38, 13, 26, NULL, NULL), (38, 14, 30, NULL, NULL), (38, 14, 32, NULL, NULL), (38, 15, NULL, 4, NULL), (38, 16, NULL, NULL, '建議增加更多主題色彩樣式可供選擇'),
(39, 13, 25, NULL, NULL), (39, 14, 29, NULL, NULL), (39, 14, 30, NULL, NULL), (39, 15, NULL, 5, NULL), (39, 16, NULL, NULL, '前後端 API 整合嚴絲合縫， Native Query 效率極佳'),
(40, 13, 25, NULL, NULL), (40, 14, 31, NULL, NULL), (40, 15, NULL, 5, NULL), (40, 16, NULL, NULL, '期待早日正式對外商用發布！');
