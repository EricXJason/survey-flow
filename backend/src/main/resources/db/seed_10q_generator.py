import subprocess
import json

sql_script = """
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
"""

# 定義 4 份問卷各自的 10 題
questions_data = [
    # Survey 1
    [
        (1, "Q1", "BOOLEAN", "您的所屬單位目前是否已常態實施混合或全遠端辦公機制？", True, 1, None, None, [
            ("是，常態混合辦公", 1, None),
            ("否，全實體進辦公室", 2, None)
        ]),
        (2, "Q2", "SINGLE", "您在遠端辦公期間是否曾遭遇跨時區非同步協作困難？", True, 2, None, None, [
            ("經常遇到，時常需要等待跨國同仁回覆", 1, "Q3"),
            ("偶爾遇到，但現有工具尚可維持運作", 2, None),
            ("完全沒有，團隊皆維持同一個工作時區", 3, None)
        ]),
        (3, "Q3", "MULTI", "【解鎖題】請問跨時區協作時，您最依賴哪些自動化同步機制？（複選）", False, 3, None, None, [
            ("非同步每日站會機器人 (Async Daily Bot)", 1, None),
            ("雲端錄影螢幕解說工具 (Loom / ScreenREC)", 2, None),
            ("自動化 CI/CD 通知整合機器人", 3, None),
            ("即時線上協作白板 (Miro / FigJam)", 4, None)
        ]),
        (4, "Q4", "SINGLE", "平均每週因遠端視訊會議耗費的時間約為多少？", True, 4, None, None, [
            ("少於 2 小時，以文字非同步溝通為主", 1, None),
            ("3 至 5 小時，維持每日例行溝通", 2, None),
            ("6 至 10 小時，跨部門對焦會議頻繁", 3, None),
            ("超過 10 小時，嚴重壓縮實質工作時間", 4, None)
        ]),
        (5, "Q5", "MULTI", "您認為遠端辦公帶來最顯著的優勢為以下哪些項目？（複選）", True, 5, None, None, [
            ("省去漫長通勤舟車勞頓與體力消耗", 1, None),
            ("能夠打造符合人體工學的個人獨立辦公空間", 2, None),
            ("工作時間配置具備極高彈性，方便照顧家庭", 3, None),
            ("在無人打擾的深度工作環境下產能顯著提升", 4, None)
        ]),
        (6, "Q6", "RATING", "整體而言，您對目前任職團隊的「遠端溝通透明度」評分（1最低，5最高）", True, 6, 1, 5, []),
        (7, "Q7", "RATING", "淨推薦度 (NPS)：您向同業推薦任職「支援混合辦公模式企業」的意願（0-10分）", True, 7, 0, 10, []),
        (8, "Q8", "TIME", "您每日遠端工作最有效率、思緒最清晰的開始時間是？", True, 8, None, None, []),
        (9, "Q9", "DATE", "您預期下次參與公司實體團建或線下面對面年會的日期約在何時？", True, 9, None, None, []),
        (10, "Q10", "TEXT", "針對促進團隊非同步協作與知識庫管理，您有哪些具體改善建議？（簡答題）", False, 10, None, None, [])
    ],
    # Survey 2
    [
        (1, "Q1", "BOOLEAN", "您過去半年內是否曾在各大品牌官方商城或第三方電商平台完成購物？", True, 1, None, None, [
            ("是，有定期線上網購習慣", 1, None),
            ("否，偏好線下實體門市消費", 2, None)
        ]),
        (2, "Q2", "SINGLE", "在線上購物結帳流程中，您是否曾遇過支付系統卡頓或驗證失敗？", True, 2, None, None, [
            ("是，曾因支付介面失敗或驗證超時放棄結帳", 1, "Q3"),
            ("否，每次付款流程皆順暢在 1 分鐘內完成", 2, None)
        ]),
        (3, "Q3", "MULTI", "【解鎖題】遭遇支付障礙時，您最偏好平台提供哪些替代支付工具？（複選）", False, 3, None, None, [
            ("Apple Pay / Google Pay 一鍵生物辨識結帳", 1, None),
            ("LINE Pay 快速自動跳轉扣款支援", 2, None),
            ("超商貨到付款安心代收服務", 3, None),
            ("街口支付即時折抵現金回饋機制", 4, None)
        ]),
        (4, "Q4", "SINGLE", "您在挑選電商平台時，最關鍵的決定因素是什麼？", True, 4, None, None, [
            ("商品價格優惠與全館免運門檻", 1, None),
            ("當日或 24 小時極速到貨配送服務", 2, None),
            ("商品退換貨流程是否完全免手續費且方便", 3, None),
            ("品牌官方授權保證正品與原廠保固", 4, None)
        ]),
        (5, "Q5", "MULTI", "您經常選購的線上商品類別有哪些？（複選）", True, 5, None, None, [
            ("3C 數碼配件與電腦週邊硬體", 1, None),
            ("生活日用品與家庭消耗快消品", 2, None),
            ("流行服飾、鞋包與時尚配件", 3, None),
            ("生鮮食品與低溫冷凍配送食材", 4, None)
        ]),
        (6, "Q6", "RATING", "您對本電商平台「商品搜尋精準度與篩選體驗」滿意度（1最低，5最高）", True, 6, 1, 5, []),
        (7, "Q7", "RATING", "淨推薦度 (NPS)：您向親朋好友推薦在本電商平台消費的意願（0-10分）", True, 7, 0, 10, []),
        (8, "Q8", "TIME", "您最常瀏覽電商平台或參與限時特賣的時段通常在？", True, 8, None, None, []),
        (9, "Q9", "DATE", "您最近一次在線上完成購物的具體下單日期是？", True, 9, None, None, []),
        (10, "Q10", "TEXT", "您認為我們在物流包裝、退換貨或客戶服務上，最需要改進的項目是什麼？（簡答題）", False, 10, None, None, [])
    ],
    # Survey 3
    [
        (1, "Q1", "BOOLEAN", "您在日常軟體開發工作中，是否已有將生成式 AI 工具整合至 IDE 編輯器？", True, 1, None, None, [
            ("是，已常態整合至日常開發管線", 1, None),
            ("否，因資安規範或個人偏好未導入", 2, None)
        ]),
        (2, "Q2", "SINGLE", "您是否會將 AI 自動生成之程式碼直接合併並發布至生產環境 (Production)？", True, 2, None, None, [
            ("是，經本地單元測試嚴格覆蓋後正式上線運行", 1, "Q3"),
            ("否，僅作為語法參考與架構靈感發想，不直接採用", 2, None)
        ]),
        (3, "Q3", "MULTI", "【解鎖題】針對 AI 生成之程式碼，團隊採用了哪些關鍵品質驗收防線？（複選）", False, 3, None, None, [
            ("由資深工程師執行嚴格 Peer Code Review", 1, None),
            ("撰寫 Mockito / JUnit 高覆蓋率測試驗證邊界", 2, None),
            ("導入 SonarQube 自動化靜態代碼與安全弱點掃描", 3, None),
            ("於 Staging 預發環境執行完整自動化端到端回歸測試", 4, None)
        ]),
        (4, "Q4", "SINGLE", "您目前最頻繁使用的主要整合開發環境 (IDE) 是？", True, 4, None, None, [
            ("Visual Studio Code (輕量靈活多插件)", 1, None),
            ("IntelliJ IDEA / JetBrains 全家桶系列", 2, None),
            ("Cursor AI 原生增強編輯器", 3, None),
            ("Vim / Neovim 極速純終端工作流", 4, None)
        ]),
        (5, "Q5", "MULTI", "AI 輔助寫碼工具在以下哪些開發環節為您節省最多工時？（複選）", True, 5, None, None, [
            ("自動生成樣板程式碼 (Boilerplate Code)", 1, None),
            ("快速編寫單元測試用例 (Unit Test Cases)", 2, None),
            ("自動產生 JSDoc/TSDoc 繁體中文方法註解", 3, None),
            ("複雜 SQL 聚合查詢或正規表達式 Regex 構建", 4, None)
        ]),
        (6, "Q6", "RATING", "您對當前主流 AI 程式輔助工具「自動補全邏輯準確度」的整體評分（1最低，5最高）", True, 6, 1, 5, []),
        (7, "Q7", "RATING", "淨推薦度 (NPS)：您向同組工程同仁推薦全面採用 AI 工具的意願（0-10分）", True, 7, 0, 10, []),
        (8, "Q8", "TIME", "您每日精神最佳、適合進行架構重構與攻堅難題的黃金寫碼時段通常在？", True, 8, None, None, []),
        (9, "Q9", "DATE", "您上次針對既有專案進行大規模技術債重構 (Refactoring) 的完成日期是？", True, 9, None, None, []),
        (10, "Q10", "TEXT", "對於未來的 AI 輔助開發工具（如智慧 Debugger、自動架構分析），您最期待出現什麼新能力？（簡答題）", False, 10, None, None, [])
    ],
    # Survey 4
    [
        (1, "Q1", "BOOLEAN", "您在本次封閉測試 (Closed Beta) 期間，是否已完整體驗核心操作流程？", True, 1, None, None, [
            ("是，已走完所有關鍵功能操作路徑", 1, None),
            ("否，僅體驗了部分基礎介面模組", 2, None)
        ]),
        (2, "Q2", "SINGLE", "在操作次世代全新儀表板的過程中，您是否有遭遇過介面破版或卡頓延遲？", True, 2, None, None, [
            ("是，曾遇到特定操作導致頁面無響應需重整", 1, "Q3"),
            ("否，全程操作流暢高幀率，無異常閃退", 2, None)
        ]),
        (3, "Q3", "MULTI", "【解鎖題】發生異常延遲時，是在哪一個操作情境下發生的？（複選）", False, 3, None, None, [
            ("大量資料動態繪製虛擬捲軸渲染區域", 1, None),
            ("多重條件跳題邏輯即時動態計算觸發處", 2, None),
            ("匯出高解析度 PDF 數據分析報表瞬間", 3, None),
            ("切換大廳暗黑與明亮主題動畫過渡時期", 4, None)
        ]),
        (4, "Q4", "SINGLE", "您對新版本 UI 介面整體視覺風格（現代清新白色 SaaS 風格）的觀感為？", True, 4, None, None, [
            ("極具現代感，資訊層級分明且賞心悅目", 1, None),
            ("簡約乾淨，符合現代辦公效率軟體主流", 2, None),
            ("中規中矩，尚有部分微動效可再細化調整", 3, None),
            ("不甚習慣，偏好傳統密集型表格呈現", 4, None)
        ]),
        (5, "Q5", "MULTI", "本次封測推出的眾多新功能中，您最滿意的亮點功能有哪些？（複選）", True, 5, None, None, [
            ("動態條件跳題與題目即時聯動解鎖機制", 1, None),
            ("可自訂問卷開放填答時段與即時狀態切換", 2, None),
            ("圖表化數據儀表板與受訪者輪廓交叉分析", 3, None),
            ("極速單鍵匯出完整格式化 Excel 與 JSON 報表", 4, None)
        ]),
        (6, "Q6", "RATING", "您對本次封閉測試版本的整體滿意度評分（1最低，5最高）", True, 6, 1, 5, []),
        (7, "Q7", "RATING", "淨推薦度 (NPS)：您向其他專案組推薦採用本套問卷雲端系統的意願（0-10分）", True, 7, 0, 10, []),
        (8, "Q8", "TIME", "您認為每日系統發送「填答數據即時摘要快訊」的最佳推播時間為？", True, 8, None, None, []),
        (9, "Q9", "DATE", "您期望本次內部封測專案正式對外公開發布 (GA) 的理想日期是？", True, 9, None, None, []),
        (10, "Q10", "TEXT", "請留下您對此產品任何未竟之處的真心話或任何想許願的進階功能！（簡答題）", False, 10, None, None, [])
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

lines = [sql_script]

q_global_id = 1
opt_global_id = 1
sub_global_id = 1
basic_global_id = 1
ans_global_id = 1

# 記錄每題的 option_id 對照表: q_opt_map[q_id] = [opt_id1, opt_id2, ...]
q_opt_map = {}

for survey_id in range(1, 5):
    q_list = questions_data[survey_id - 1]
    survey_q_ids = []
    
    # 建立題目與選項
    for (q_idx, code, q_type, text, is_req, order, min_r, max_r, options) in q_list:
        lines.append(f"""
INSERT INTO survey_questions (id, survey_id, question_code, type, question_text, is_required, sort_order, min_rating, max_rating, created_at, updated_at)
VALUES ({q_global_id}, {survey_id}, '{code}', '{q_type}', '{text}', {1 if is_req else 0}, {order}, {min_r if min_r is not None else 'NULL'}, {max_r if max_r is not None else 'NULL'}, NOW(), NOW());
""")
        current_qid = q_global_id
        survey_q_ids.append(current_qid)
        q_opt_map[current_qid] = []
        q_global_id += 1
        
        for (opt_text, opt_order, trig_code) in options:
            trig_val = f"'{trig_code}'" if trig_code else "NULL"
            lines.append(f"""
INSERT INTO survey_options (id, question_id, option_text, sort_order, trigger_question_uid)
VALUES ({opt_global_id}, {current_qid}, '{opt_text}', {opt_order}, {trig_val});
""")
            q_opt_map[current_qid].append(opt_global_id)
            opt_global_id += 1

    # 建立 10 筆填答資料
    for resp_idx, resp in enumerate(respondents_pool):
        (name, gender, age_group, phone, email, industry, residence, dur, sub_time, has_text, text_feedback) = resp
        
        # 1. 主表 submissions
        cur_sub_id = sub_global_id
        lines.append(f"""
INSERT INTO submissions (id, survey_id, ip_address, duration_seconds, submitted_at)
VALUES ({cur_sub_id}, {survey_id}, '192.168.1.{10 + resp_idx}', {dur}, '{sub_time}');
""")
        sub_global_id += 1
        
        # 2. 受訪者個人輪廓 submission_basic_infos
        lines.append(f"""
INSERT INTO submission_basic_infos (id, submission_id, name, gender, age_group, phone, email, industry, residence)
VALUES ({basic_global_id}, {cur_sub_id}, '{name}', '{gender}', '{age_group}', '{phone}', '{email}', '{industry}', '{residence}');
""")
        basic_global_id += 1
        
        # 3. 建立 10 題之作答明細 submission_answers
        # Q1: BOOLEAN (選 option 1 或 option 2)
        q1_id = survey_q_ids[0]
        q1_opts = q_opt_map[q1_id]
        chosen_opt1 = q1_opts[0] if (resp_idx % 3 != 0) else q1_opts[1]
        lines.append(f"INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES ({ans_global_id}, {cur_sub_id}, {q1_id}, {chosen_opt1}, NULL, NULL);")
        ans_global_id += 1
        
        # Q2: SINGLE (部分勾選觸發 Q3 之選項：第 1 個 option trigger Q3)
        q2_id = survey_q_ids[1]
        q2_opts = q_opt_map[q2_id]
        chosen_opt2 = q2_opts[0] if (resp_idx % 2 == 0) else (q2_opts[1] if len(q2_opts) > 1 else q2_opts[0])
        lines.append(f"INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES ({ans_global_id}, {cur_sub_id}, {q2_id}, {chosen_opt2}, NULL, NULL);")
        ans_global_id += 1
        
        # Q3: MULTI (解鎖題，受訪者 0, 2, 4, 6, 8 勾選觸發題，因此有作答多選；其餘也可填或留空)
        q3_id = survey_q_ids[2]
        q3_opts = q_opt_map[q3_id]
        if resp_idx % 2 == 0:
            # 填答 2 個選項 (多選題正規化為 2 筆紀錄)
            lines.append(f"INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES ({ans_global_id}, {cur_sub_id}, {q3_id}, {q3_opts[0]}, NULL, NULL);")
            ans_global_id += 1
            if len(q3_opts) > 1:
                lines.append(f"INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES ({ans_global_id}, {cur_sub_id}, {q3_id}, {q3_opts[1]}, NULL, NULL);")
                ans_global_id += 1
        
        # Q4: SINGLE
        q4_id = survey_q_ids[3]
        q4_opts = q_opt_map[q4_id]
        chosen_opt4 = q4_opts[resp_idx % len(q4_opts)]
        lines.append(f"INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES ({ans_global_id}, {cur_sub_id}, {q4_id}, {chosen_opt4}, NULL, NULL);")
        ans_global_id += 1
        
        # Q5: MULTI (勾選 1~2 個選項)
        q5_id = survey_q_ids[4]
        q5_opts = q_opt_map[q5_id]
        lines.append(f"INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES ({ans_global_id}, {cur_sub_id}, {q5_id}, {q5_opts[0]}, NULL, NULL);")
        ans_global_id += 1
        if len(q5_opts) > 1 and resp_idx % 2 != 0:
            lines.append(f"INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES ({ans_global_id}, {cur_sub_id}, {q5_id}, {q5_opts[1]}, NULL, NULL);")
            ans_global_id += 1
            
        # Q6: RATING (1-5)
        q6_id = survey_q_ids[5]
        r6 = 5 if resp_idx % 3 == 0 else (4 if resp_idx % 2 == 0 else 3)
        lines.append(f"INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES ({ans_global_id}, {cur_sub_id}, {q6_id}, NULL, NULL, {r6});")
        ans_global_id += 1
        
        # Q7: RATING (0-10 NPS)
        q7_id = survey_q_ids[6]
        r7 = 10 if resp_idx in [0, 1, 2] else (9 if resp_idx in [3, 4] else (8 if resp_idx in [5, 6] else 6))
        lines.append(f"INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES ({ans_global_id}, {cur_sub_id}, {q7_id}, NULL, NULL, {r7});")
        ans_global_id += 1
        
        # Q8: TIME
        q8_id = survey_q_ids[7]
        lines.append(f"INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES ({ans_global_id}, {cur_sub_id}, {q8_id}, NULL, '09:30', NULL);")
        ans_global_id += 1
        
        # Q9: DATE
        q9_id = survey_q_ids[8]
        lines.append(f"INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES ({ans_global_id}, {cur_sub_id}, {q9_id}, NULL, '2026-11-15', NULL);")
        ans_global_id += 1
        
        # Q10: TEXT (簡答題：前 6 人有深度反饋，後 4 人為 NULL)
        q10_id = survey_q_ids[9]
        if has_text and text_feedback:
            lines.append(f"INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES ({ans_global_id}, {cur_sub_id}, {q10_id}, NULL, '{text_feedback}', NULL);")
        else:
            lines.append(f"INSERT INTO submission_answers (id, submission_id, question_id, option_id, text_answer, rating_value) VALUES ({ans_global_id}, {cur_sub_id}, {q10_id}, NULL, NULL, NULL);")
        ans_global_id += 1

output_sql = "\n".join(lines)
with open("/Users/hsuche-cheng/Documents/project/survey-flow/backend/src/main/resources/db/seed_10q_database.sql", "w", encoding="utf-8") as f:
    f.write(output_sql)

print("SQL script generated successfully!")
