# 10 - 商業路演與高階技術簡報提煉大綱 (Executive Pitch Deck Blueprint)

> **專案作者**: 許哲誠 (HSU, CHE-CHENG)  
> **更新日期**: 2026-10-15  
> **文件定位**: 將 `docs/system-design/` 01~09 的高密度工程事實與商業價值，精準萃取為 10 頁專業簡報大綱（Slide Deck），供專案展示、投資路演（Pitch Deck）或架構評審（Arch Review）直接取用。

---

## Slide 01: 封面與核心定位 (Title & Value Proposition)
- **簡報標題**: SurveyFlow — 企業級全端多維度問卷調研與即時數據分析平臺
- **副標題**: 兼具現代工藝級去 AI 化視覺、WCAG 2.2 無障礙體驗與深度縱深防禦的專業調研解決方案
- **主講人/架構師**: 許哲誠 (HSU, CHE-CHENG)
- **視覺錨點**: 系統架構深色科技 HUD 圖表，展現高精準工程質感。

---

## Slide 02: 市場痛點與商業機會 (Problem & Opportunity)
- **痛點 1 (資料孤島與延遲)**: 傳統表單工具缺乏深度分析，需手動匯出 Excel 二次加工，效率低下。
- **痛點 2 (AI 模板疲乏與無障礙缺失)**: 市面產品充斥千篇一律的 AI 模板，排版脆弱易崩潰，且未符合國際無障礙法規。
- **痛點 3 (資安與個資外洩隱憂)**: 一般公有問卷系統權限鬆散，缺乏身分證/電話自動脫敏與高強度加密保護。
- **SurveyFlow 破局點**: 「設計 ➔ 發布 ➔ 填答 ➔ 交叉統計 ➔ 安全合規」的一體化閉環。

---

## Slide 03: 產品核心能力矩陣 (Core Solution & Capabilities)
- **直觀可視化問卷設計器**: 支援單選、複選、1~10 分量表題與簡答題，即時所見即所得驗證。
- **靈活雙狀態發布系統**: 獨立控管「大廳公開 (Lobby)」與「開放填答 (IsOpen)」，並支援時效性自動啟閉。
- **多維度即時統計分析引擎**: 柱狀幾何圖形、甜甜圈分佈圖、多色階量表直方圖與智慧長文字檢索。
- **雙重視角編號語意分離**: 一般會員專注個人專案序列號（#1, #2...），管理員全盤掌控系統全域流水號。

---

## Slide 04: 前端美學與去 AI 化工藝哲學 (Design System & De-AI)
- **拒絕 AI 模板病**: 消除油膩漸層光球、居中三欄圖示與假大空口號，堅持高資訊密度真實介面展示。
- **頂級標竿庫對齊**: 深度借鏡 Supahero、Pageflows、Land-book、Awwwards 之編排與微互動工藝。
- **Editorial Typography**: 模組化字階、字距緊縮與精準行高控制，展現穩健力量感。
- **零橫向拖曳之頂級 RWD**: 小螢幕自動將資料表格轉譯為獨立卡片流（Responsive Card List），直向滑動極致流暢。

---

## Slide 05: WCAG 2.2 國際無障礙標準全量落實 (Accessibility)
- **超規格色彩對比度**: 核心正文達 16.0:1（遠超 WCAG 2.2 AA 4.5:1 底線）。
- **可視化高對比焦點外環**: 徹底杜絕 `outline: none`，保障鍵盤無障礙操作友善。
- **防拖曳依賴與目標尺寸**: 所有題目重排均提供單擊按鈕替代；觸控目標全面大於等於 44x44px。
- **螢幕閱讀器深度適配**: 完整宣告 ARIA Live Region、Expanded 與 Controls 屬性。

---

## Slide 06: 技術架構拓撲與技術選型 (Architecture Topology)
- **現代雙核心分層選型**:
  - 前端：Angular 19.0.0 / TypeScript 5.6.2 / SCSS / Angular Signals 響應式狀態管理。
  - 後端：Java 21.0.12 / Spring Boot 4.1.1 / Spring Data JPA 原生 SQL 高效能查詢。
  - 資料庫：MySQL 8.0.36（支援 UTF8MB4 與高並發交易安全）。
- **SSOT 單一真實來源原則**: 狀態、資料模型與枚舉嚴禁重複定義，上下游完全聯動。

---

## Slide 07: 縱深防禦與個人隱私安全 (Enterprise Security)
- **密碼學頂級標準**: 全面實施 Argon2id 密碼雜湊，抵禦暴力破解。
- **單一高安全 JWT 憑證架構**: 24 小時精確時限，純無狀態極速驗證，兼顧便利與簡約維護。
- **OWASP Top 10 全面防禦**: 原生參數化防禦 SQLi、上下文消毒防禦 XSS、CORS 嚴格白名單。
- **敏感個資動態脫敏**: 身分證字號、手機號碼、電子郵件自動掩碼顯示，合規保障無死角。

---

## Slide 08: 資料模型與統計運算效能 (Data Model & Performance)
- **高擴充性關聯設計**: `surveys` ➔ `survey_questions` ➔ `survey_options` ➔ `submissions`。
- **原生 SQL 運算加速**: 數據分析報表由後端直接執行聚合運算（COUNT, AVG），前端零二次計算負擔。
- **極低 Token 與靜態打包**: 前端 Bundle 僅約 230KB（Gzip），秒級冷啟動。

---

## Slide 09: 實際成果與驗證指標 (Verification & Milestones)
- **零錯誤編譯與打包**: 後端 Maven 0 警告封裝，前端 Angular AOT 編譯 0 錯誤。
- **自動化版控閉環**: 本地原子 Commit 守衛，保障工作區 100% 乾淨無斷層。
- **真實資料庫數據覆蓋**: 涵蓋 10 道題型、真實樣本與多維度統計圖表。

---

## Slide 10: 未來藍圖與商業展望 (Future Roadmap & Call to Action)
- **Phase 1 (當前完成)**: 核心問卷閉環、去 AI 化視覺、多色直方圖、權限分離與無障礙標準。
- **Phase 2 (近期規劃)**: 題目跳題邏輯引擎（Skip Logic Branching）、自訂主題色票編輯器。
- **Phase 3 (商業延展)**: 團隊多租戶協作（Multi-tenancy Workspaces）與企業級 SSO (SAML/OIDC) 整合。
- **結論**: SurveyFlow 以工程事實與專業工藝為基石，為企業帶來真正可靠、安全且視覺出眾的問卷調研體驗。
