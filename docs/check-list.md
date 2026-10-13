# SurveyFlow 專案功能待辦與驗收點收表

> **專案作者**: 許哲誠 (HSU, CHE-CHENG)  
> **更新日期**: 2026-10-06  
> **說明**: 本表僅記錄與驗收 SurveyFlow 專案本身之實體軟體業務需求、技術實現與功能點。

---

## 資料庫與持久化層

- [x] **會話狀態持久化**: 透過 `UserStateService` 與瀏覽器 `sessionStorage` 進行使用者登入身分與個人資料雙向同步，防止重新整理分頁 (F5) 遺失狀態。
- [x] **本地離線註冊資料集**: 實作 `SURVEY_REGISTERED_USERS` 本地快取，支援註冊後即刻無縫登入驗證。
- [x] **問卷與題型結構定義**: 完成 `surveys.json` 與 `users.json` 預設種子資料，定義題目 UID 與選項關聯模型。
- [x] **MySQL 8.x 全端接軌 DDL 與統計查詢規格**: 於規格書完整定義問卷、題目、選項、填答主表、題型回答明細表結構、複合索引、外鍵約束及高效聚合 SQL。
- [x] **MySQL 8.x 實體資料表建立與 DDL 腳本 (`backend/src/main/resources/schema.sql`)**: 建立 8 張正規化資料表，包含級聯刪除、非對稱加密與索引優化。
- [x] **JPA 實體類別映射與審計支援 (`AuditingEntityListener`)**: 實作 `User`, `Survey`, `SurveyQuestion`, `SurveyOption`, `Submission` 等 Entity，並整合自動建立/更新時間戳。

---

## 後端 API 與商務邏輯層

- [x] **資料檢索抽象層**: 建立 `SurveyGetterService` 提供大廳公開問卷、會員問卷與單一問卷查詢 Observable 介面。
- [x] **使用者事件派發中心**: 建立 `UserEmitterService` 統一管理登入、註冊、登出、問卷發布、作答提交與二次確認對話框回呼。
- [x] **全域對話框服務**: 建立 `DialogService` 管理 noticeMode、checkMode、errorMode 與自動定時關閉機制。
- [x] **通用格式驗證服務**: 建立 `ValidationService`，實作中華民國國民身分證字號雙向權重校驗（Modulo 10）、臺灣 09 開頭手機號碼、臺灣室內電話、中文姓名 (2-6字)、複合高強度密碼與本地時區日期計算。
- [x] **通用工具服務**: 建立 `UtilityService` 提供平滑置頂 (scrollToTopSmooth)、路由自動捲動重設、嚴格正整數驗證與無空格字串檢核。
- [x] **表單數據分析聚合服務 (`SurveyAnalyticsService`)**: 提供問卷統計計算、近 7 日每日回覆時間走勢、受訪者輪廓（性別、年齡層）、題型作答聚合分析（單選/多選比例、量表評分分佈、文字反饋抽樣），並內建管理員全域與會員所屬問卷之權限檢查守衛 (`canAccessAnalytics`)。
- [x] **未來全端 Java Spring Boot API 規範對齊**: 於 API 規格書定義 `GET /api/surveys/{surveyId}/analytics` 端點與 Spring Security 權限防護（`@PreAuthorize` 管理員或擁有者），提供全端接軌 DTO。
- [x] **Spring Boot 4.1.1 基礎架構骨架 (Java 21 + Maven)**: 配置 `pom.xml`，整合 Spring Web, Spring Data JPA, Spring Security, MySQL Connector, Validation, Lombok，並通過編譯測試。
- [x] **設定檔格式標準化**: 將設定檔完全轉換為標準 `application.properties`，支援 MySQL HikariCP 連線池、SQL DDL 初始化模式與 JWT 參數。
- [x] **安全認證核心 (Argon2id + JWT 雙 Token 旋轉機制)**: 實作 `JwtAuthenticationFilter`, `Argon2PasswordEncoder`, `TokenProvider`。
- [x] **API 路由階層分流架構 (Admin / Public / Member)**:
  - [x] **公開端點 (`/api/public/**`)**: 註冊、登入、Token 換發、大廳公開分頁檢索 (`/api/public/surveys/lobby`)、公開問卷填答取得與受訪者填答提交 (`/api/public/submissions`)。
  - [x] **管理員專用端點 (`/api/admin/**`)**: 嚴格受 `@PreAuthorize("hasRole('ADMIN')")` 防護，提供全站會員名冊、狀態變更、強制下架問卷與全站營運 KPI (`/api/admin/analytics/overview`)。
  - [x] **會員專用端點 (`/api/user/**`)**: 問卷建立、修改、軟刪除、自有問卷清單與個人數據報表調閱。
- [x] **全原生 SQL 查詢規範 (Native Query 100% 落地)**:
  - [x] `RefreshTokenRepository`: 原生 SQL 批量撤銷權杖與有效性檢查。
  - [x] `SurveyRepository`: 原生 SQL 大廳模糊搜尋、使用者問卷檢索與 Admin 總覽。
  - [x] `SubmissionRepository`: 原生 SQL 回收數統計、平均時長計算與全站總填答次數。
  - [x] `SubmissionAnswerRepository`: 原生 SQL 題型選項勾選統計、評分量表平均分與開放式文字提取。
  - [x] `UserRepository`: 原生 SQL 帳號/信箱唯一性檢查與 Admin 會員分頁。
- [x] **JUnit 5 與 Mockito 完整自動化測試套件 (14/14 全數 PASS)**:
  - [x] `AuthServiceTest`: 註冊、重複帳號阻斷、登入驗證、密碼錯誤阻斷、登出撤銷。
  - [x] `SurveyServiceTest`: 問卷建立、公開瀏覽累加 viewCount、非擁有者刪除權限阻斷。
  - [x] `AnalyticsServiceTest`: KPI 與 NPS 聚合運算、非擁有者無權調閱阻斷。
  - [x] `PublicControllerTest`: MockMvc 測試大廳分頁、參數校驗 400、填答提交 200。
  - [x] `AdminControllerTest`: MockMvc 測試管理員總覽指標 200。
- [x] **前後端全端即時無縫對接**:
  - [x] 前端 `app.config.ts` 註冊 `provideHttpClient(withFetch())`。
  - [x] 建立 `ApiClientService` 封裝 `http://localhost:8080` 與 Bearer Token 自動附帶。
  - [x] `SurveyGetterService` 實現雙軌運作（優先連線後端 Spring Boot，斷線時自動優雅降級回退 Mock Data）。

---

## 前端介面與互動狀態

- [x] **全域佈局架構**: 建立 `AppComponent`、`HeaderComponent`（含行動版漢堡抽屜與權限標籤）、`FooterComponent`、`DialogComponent` 與 `ScrollToTopButtonComponent`。
- [x] **路由與離開守衛**: 配置 `app.routes.ts`，並在問卷設計 (`SurveyDesignComponent`)、問卷填答 (`SurveyRunnerComponent`) 及個人資料 (`UserProfileComponent`) 導入 `canDeactivateGuard` 攔截未儲存跳出防呆。
- [x] **平台官方首頁**: 建立 `HomeComponent` 與 `HeroComponent`，包含 macOS 擬真問卷即時預覽視窗與特色介紹。
- [x] **問卷公開大廳**: 建立 `LobbyComponent`，支援 12 大主題分類標籤切換、即時關鍵字模糊搜尋、⌘K 快捷鍵聚焦、Angular Material Table 排序與分頁。
- [x] **問卷設計工作台**: 建立 `SurveyDesignComponent`，支援問卷標題、分類、期限、受訪者身分欄位開關、動態題目增刪改、多維度量表題型、題目 UID 動態跳題解鎖與即時預覽模式。
- [x] **題目順序重排彈窗**: 建立 `SurveyReorderDialogComponent`，採用 `@angular/cdk/drag-drop` 實現視覺化題型拖曳排序並原子化回寫表單。
- [x] **問卷填答運行器**: 建立 `SurveyRunnerComponent`，支援受訪者身分欄位校驗、單選動態跳題隱藏/顯現邏輯、量表選項切換與作答成果封裝。
- [x] **會員與後台管理**:
  - [x] 建立 `LogInComponent`（支援管理員與會員快速登入測試按鈕）。
  - [x] 建立 `RegisterComponent`（身分證與手機防呆校驗）。
  - [x] 建立 `ForgetPasswordComponent` 與 `ResetPasswordComponent`。
  - [x] 建立 `UserProfileComponent`（折疊式修改密碼面板）。
  - [x] 建立 `SurveyManagementComponent`（問卷專案管理、狀態篩選與刪除對話框）。
  - [x] 建立 `UserManagementComponent`（管理員會員管理後台、狀態/身分篩選與安全移除）。
- [x] **表單管理深度數據分析儀表板 (`SurveyManagementComponent` & `SurveyAnalyticsService`)**:
  - [x] 於問卷管理列表新增「數據分析」操作按鈕，支援狀態、關鍵字快速定位。
  - [x] 實作 5 大核心維度 KPI 指標卡片（總回覆數、平均填答時間、完成率、淨推薦值 NPS、今日新增回覆）。
  - [x] 實作近 30 日填答走勢直方圖與高點互動指示。
  - [x] 實作受訪者全維度人口特徵輪廓（年齡層金字塔比例、性別比圓環/條圖、前五大產業分佈、前五大居住地區分佈）。
  - [x] 實作題型深度聚合視覺化（單選題佔比、多選題複選率、5-10 階評分量表 NPS 情感三分組：貶損者/被動者/推薦者、開放式文字題正向/中性/關注標籤分析與關鍵字快速篩選）。
  - [x] 嚴格實作管理者（可查閱並分析全站所有問卷）與會員（僅能查閱自己發布之問卷）細粒度權限阻斷與防呆警示彈窗。
  - [x] **多格式統計數據匯出模組**:
    - [x] 支援 **Excel 相容 CSV 匯出**（含 UTF-8 BOM 避免微軟 Excel 繁中亂碼、標準逗號逸出與完整中繼時間戳記）。
    - [x] 支援 **SPSS 統計軟體專用格式匯出**（產出資料 CSV 及符合 IBM SPSS Statistics 語法之 `.sps` 腳本，含 `VARIABLE LABELS` 與 `VALUE LABELS` 自動對映）。
    - [x] 支援完整原始報表 **JSON** 備份匯出。
    - [x] 支援瀏覽器原生高品質列印與另存 PDF 報表模式。

---

## 前端樣式與互動規格

- [x] **現代品牌設計系統純原生 SCSS 樣式 (嚴格禁用 Bootstrap)**:
  - [x] 全面採用純原生 SCSS 變數、Flexbox、CSS Grid 與現代科技藍白設計系統（以 `#2563eb` 品牌藍、純白卡片底色、精緻 Slate 灰色調與柔和圓角陰影深度適配全站視覺）。
  - [x] 徹底排除任何 Bootstrap CSS/JS 依賴，體積輕盈且維持 100% 視覺統一度與高質感微互動體驗。
  - [x] 響應式佈局適配（支援 Desktop、平板與手機小螢幕多維度圖表重排）。
- [x] **無障礙鍵盤互動支援**: 支援全域 `ESC` 鍵關閉彈窗與行動抽屜，支援 `⌘K` 搜尋輸入框快速聚焦。
- [x] **繁體中文在地化語言包**: 在 `app.config.ts` 內聯實作 `getTaiwanPaginatorIntl`，為 Material Paginator 提供純臺灣繁體中文分頁文字。
- [x] **文字用字合規**: 全站文案與註解 100% 採用臺灣繁體中文，嚴格使用「臺」字，無簡體字與混用字。
