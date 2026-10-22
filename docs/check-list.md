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
- [x] **testuser 代表性問卷多態場景配置 (`data.sql`)**: 為 testuser 實作多元調研情境問卷——問卷 1（職場組織+通訊）、問卷 2（只用一個：僅姓名）、問卷 3（學校教育背景）、問卷 4（一個不用：完全匿名免填）。
- [x] **10 位真實專家使用者 × 20 份多元高擬真問卷專案 (`data.sql`)**: 新增陳思翰副教授、林品妤產品長、黃彥碩人資主管、吳芷晴行銷總監、張銘宇活動策劃、李冠廷客服副總、王靜雯培訓講師、蔡承翰健康顧問、楊心慈政策研究員、廖俊翔社群經理等 10 位真實帳號，名下各自擁有 2 份高品質問卷（問卷 5 至 24），100% 覆蓋大廳全部 12 大主題分類。
- [x] **MySQL 8.x 種子重設腳本 100% 冪等性與主鍵唯一性驗證 (`data.sql`)**: 徹底解決 `survey_options` 重複主鍵衝突（ID 自 66 號依序遞增至 265 號），經全量稽核 8 張資料表共 1500+ 筆記錄 0 重複，可作為穩定之一鍵資料庫 Reset 腳本。
- [x] **真實填答樣本數據持久化 (`submissions` & `submission_answers`)**: 為問卷 5 至 24 注入 60 筆真實受訪者基本資料與 460 筆具體作答記錄，確保大廳展示回覆數與後台分析報表全鏈路具備生動統計數據。
- [x] **問卷基本資料多態收集與真實隨機填答情境 (`data.sql`)**: 涵蓋 24 張問卷各自不同之基本資料收集欄位（含完全匿名與極簡模式），並為填答紀錄注入多樣化口語反饋與隨機略過，呈現最真實之受訪者情境。

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
- [x] **安全認證核心 (Argon2id + 零信任雙 Token 憑證架構)**: 實作高內聚核心類別（`SecurityConfig`, `JwtAuthenticationFilter`, `TokenProvider`, `CustomUserDetailsService`），整合 `TokenRevocationService` 支援 15m Access Token + 7d Refresh Token 與 RTR 輪替吊銷，實現極簡易學、高內聚與零循環依賴。
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
  - [x] **動態憑證同步機制 (`UserCredentialSynchronizer`)**: 徹底移除硬編碼或記憶體假資料，改於 Spring Boot 啟動時自 `application.properties` 動態讀取 `admin` 與 `testuser` 帳密並同步至 MySQL Argon2id 密碼雜湊，支援即時修改密碼與真實登入。
  - [x] **全型態問卷專屬填寫連結與作答支援**: 問卷管理後台支援不論公開或私人（DRAFT），一鍵複製專屬直達連結，後端放寬非公開問卷持連結可正常載入與提交作答（僅於 CLOSED 狀態阻斷）。
  - [x] **前後端 DTO Validation 與 Database Constraint 完全對齊**: 登入長度、問卷建立與填答回答明細之 `@Valid`、`@Size`、`@NotBlank` 與資料庫欄位 100% 對齊無衝突。
  - [x] **真實測試數據持久化**: 建立 4 筆 `testuser` 問卷（3 筆公開 + 1 筆私人）與 40 筆真實作答記錄（191 筆題型回答明細），支援數據分析儀表板真實查詢。
  - [x] **問卷上傳不可溯及修改題目規範**: 上傳發布後鎖定問卷題目結構，避免修改既有題項導致既有回覆資料失真或關聯錯亂。
  - [x] **問卷雙維度狀態與時程 API (`PUT /api/user/surveys/{id}/settings`)**:
    - [x] 維度一：是否公開至問卷大廳（`PUBLISHED` vs `UNPUBLISHED`）。
    - [x] 維度二：開放填答時段判定（由 `startDate` 與 `endDate` 決定，過期或未達時程自動攔截）。
  - [x] **會員身分證字號不可竄改性 (`identityCard` 唯讀鎖定)**: 前端個人檔案頁面將身分證號鎖定為唯讀狀態，後端 `UserService.updateProfile` 徹底移除身分證號更新賦值，確保實名綁定無漏洞。
  - [x] **單選題條件跳題解鎖關聯 (`trigger_question_uid`)**: 資料庫 `survey_options` 與後端 `SurveyOption` 實體擴展支援觸發題號，前端於載入時自動將受觸發題預設鎖定為隱藏，勾選指定選項時動態解鎖。
  - [x] **數據分析儀表板全端 SaaS 純白風格重構**: 根除殘留的深黑 HUD 破版樣式，還原現代清新純白 SaaS 卡片風格，時程設定彈窗與簡答題無內容優雅提示皆一併升級。
  - [x] **訪客角色 (ROLE_GUEST) 與未登入問卷設計阻斷守衛 (`authGuard`)**: 前端路由對 `/survey-design` 等頁面進行登入檢驗，未登入者僅能填寫問卷與瀏覽大廳，點擊設計自動彈窗提示並引導登入。
  - [x] **高畫質開源圖片整合 (Unsplash)**: 資料庫支援 `cover_image_url` 與 `image_url`，前後端完整映射並於問卷封面與題目中渲染真實商業/學術情境視覺。
  - [x] **受訪者基本資料防呆防溢出**: 姓名、手機（10碼）、信箱、現居地址與產業全面加上 `maxlength` 與數值上下限防護。
  - [x] **Admin 會員列表資訊完整化與管理員防護**: 會員名冊展示真實 `username`、姓名、ID 與電話，角色正確顯示「一般會員」，並徹底排除與保護 admin 帳號。
  - [x] **資料庫目錄專業化命名與結構/資料分離架構 (`backend/src/main/resources/database/`)**: 移除簡陋的 `db` 命名，升級為企業級規範之 `database` 目錄，完整拆分 DDL 表結構腳本 (`schema.sql`)、DML 種子數據腳本 (`data.sql`) 與一鍵全量整合腳本 (`init.sql`)，支援直接於 MySQL Workbench/CLI 執行，或由 Spring Boot 自動載入。
  - [x] **問卷雙維度布林狀態資料庫純化 (`is_public_to_lobby` 與 `is_open` 均為 TINYINT)**: 徹底將資料庫 `surveys` 表原字串 `status` 欄位替換為 `is_open TINYINT(1)`，與 `is_public_to_lobby TINYINT(1)` 共同精準定義「是否公開至大廳」與「私人連結是否開放填寫」，消除冗贅字串 Enum 並全鏈路對齊。
  - [x] **問卷題目 7 大型態全端完整支援 (是非、單選、複選、評分量表、簡答、時間、日期)**:
    - [x] 後端 `QuestionType` 擴展 `BOOLEAN`, `TIME`, `DATE`，達成完整 7 大題型定義。
    - [x] 前端 `SurveyGetterService` 與 `UserEmitterService` 完成 7 種題型雙向完整映射。
    - [x] `AnalyticsService` 支援 `BOOLEAN` 百分比統計與 `TIME`/`DATE` 答案彙整。
  - [x] **全域編碼防禦機制 (Unicode Escaping + fixEncoding)**:
    - [x] `application.properties` 中文值轉為標準 Unicode 轉義字符，防止 Properties 讀取時因 ISO-8859-1 解碼產生 latin1 亂碼。
    - [x] `UserCredentialSynchronizer` 實作字元集校正防禦，確保使用者全名於資料庫與前端 Header 100% 正確顯示為臺灣繁體中文。
  - [x] **管理員會員名冊與狀態管理 API**:
    - [x] `GET /api/admin/users`: 取得全站會員清單與統計數據。
    - [x] `PUT /api/admin/users/{userId}/status`: 即時切換會員啟用/凍結狀態。
    - [x] `DELETE /api/admin/users/{userId}`: 管理員安全移除會員帳號（受系統管理員保護機制防護）。
  - [x] **會員個人資料與密碼維護 API (一對一 SRP 分層架構)**:
    - [x] `GET /api/user/profile`: 讀取當前會員真實個資。
    - [x] `PUT /api/user/profile`: 更新會員真實姓名、身分證、電話、性別、生日與身分。
    - [x] `PUT /api/user/password`: 驗證舊密碼並以 Argon2id 加密存儲新密碼。
  - [x] **忘記密碼與重設密碼 API**:
    - [x] `POST /api/public/auth/forget-password`: 驗證信箱並產出重設 Token。
    - [x] `POST /api/public/auth/reset-password`: 驗證 Token 並安全置換密碼與撤銷舊 Token。
  - [x] **Clean Architecture 領域實體純淨化 (`User.java`)**: 徹底移除 `User` 實體上展示層 `@JsonIgnore` 註解，解除 Entity 與 Web 展示層 JSON 序列化框架之耦合，密碼欄位防護與個資脫敏 100% 由展示層專屬 DTO (`UserSummaryResponse`, `UserProfileResponse`) 單一職責守護，並清理誤植之 Controller 端點代碼。
  - [x] **問卷大廳時程起訖時間全鏈路映射**: 前端 `SurveyGetterService.getLobbySurveys()` 補齊後端 API `SurveySummaryResponse` 之 `startDate` 與 `endDate` 欄位雙向映射，徹底根除有效填答期間降級破折號 `—` 缺陷。
  - [x] **單選/複選作答全鏈路 optionId 精確綁定與後端容錯防禦**: 徹底解決線上填答選項得票歸零斷層；前端在 `SurveyRunnerComponent` 精準比對選取之 `selectedOptionId`（單選/是非）與 `selectedOptionIds`（複選）並由 `UserEmitterService` 傳入後端；後端 `SubmissionService` 實作精確綁定與文字反查雙重守衛，確保 `submission_answers.option_id` 100% 入庫，聚合統計 100% 即時反應。
  - [x] **問卷設計工作台「條件跳題解鎖」全鏈路持久化 (`trigger_question_uid`)**: `UserEmitterService.emitDesignSurvey` 完整提取單選題各選項之 `triggerQuestionUid` 並傳入後端 `OptionDto`，打通設計端選取、後端資料庫存儲到填答端動態解鎖的完整閉環。
  - [x] **數據分析報表受訪者人口特徵輪廓全端對齊 (`demographics`)**: `SurveyAnalyticsService` 實作 `DemographicsDto` 至前端 `DemographicStats` 之精準轉譯管線，打通性別比、年齡層金字塔與前五大任職產業之全端展示。
  - [x] **大廳與會員後台分頁器 Setter 響應式綁定 (`lobby.component.ts`, `user-management.component.ts`)**: 採用 setter 模式確保 `@if` 延遲渲染時自動完成 `dataSource.paginator` 與 `sort` 綁定，徹底修復分頁失效與統計為 0 缺陷。
  - [x] **受訪者與訪客資料全量統計與結構化展示 (`SubmissionAnswerRepository`, `survey-analytics.service.ts`, `survey-management.component.html`)**: 透過 `submissions` 主表 LEFT JOIN 確保每一筆填答者完整呈現，未留姓名者自動賦予「訪客」標籤，未填文字者呈現未填寫反饋，杜絕漏單或錯誤排除。
  - [x] **整合 JavaMailSender 真實 Gmail SMTP 寄信服務 (忘記密碼與註冊啟用驗證信)**: 串接 Gmail SMTP 發送安全 HTML 繁中驗證信，配合 `verification_tokens` 資料表管理 15 分鐘時效之 6 碼 OTP 驗證碼。

---

## 前端介面與互動狀態

- [x] **全域佈局架構**: 建立 `AppComponent`、`HeaderComponent`（含行動版漢堡抽屜與權限標籤）、`FooterComponent`、`DialogComponent` 與 `ScrollToTopButtonComponent`。
- [x] **全域對話框 ESC 快捷鍵無障礙退出支援**: 包括通用確認對話框 (`DialogComponent`)、題目順序重排彈窗 (`SurveyReorderDialogComponent`)、時限與發布設定彈窗 (`SurveyManagementComponent`) 及行動版導航抽屜 (`HeaderComponent`)，按下 ESC / Escape 鍵皆能立即平順關閉。
- [x] **置頂按鈕頂部動態能見度邏輯 (`ScrollToTopButtonComponent`)**: 於頁面最上方 (`scrollY <= 200px`) 自動完全隱藏 (`opacity: 0, visibility: hidden, pointer-events: none`)，向下捲動離開頂端後才流暢淡入浮現，點擊平滑置頂後自動隨滾動隱藏。
- [x] **路由與離開守衛**: 配置 `app.routes.ts`，並在問卷設計 (`SurveyDesignComponent`)、問卷填答 (`SurveyRunnerComponent`) 及個人資料 (`UserProfileComponent`) 導入 `canDeactivateGuard` 攔截未儲存跳出防呆。
- [x] **平台官方首頁**: 建立 `HomeComponent` 與 `HeroComponent`，包含 macOS 擬真問卷即時預覽視窗與特色介紹。
  - [x] **首頁 Hero 區塊 PC 模式 100% 視窗精確貼合**: 桌面端 (>=1025px) 高度精確對齊 `calc(100vh - var(--header-h, 6rem))`，在 PC 模式 100% 縮放下首屏完整呈現 Hero 全部內容，徹底杜絕下方區塊露底。
  - [x] **會員登出自動導向產品首頁**: `UserEmitterService.emitLogout()` 登出後跳轉由 `/lobby` 修正導向產品首頁 `/home`。
  - [x] **忘記密碼純 Email 流程與真實郵件收信驗證**: 忘記密碼頁面簡化為「僅需提供 Email」，後端發送 15 分鐘時效 6 碼重設碼至信箱進行新密碼更換。
  - [x] **新會員註冊 Email 信箱驗證啟用機制**: 註冊完成後即刻寄發 6 碼啟用碼，前端呈現驗證碼啟用面板（具 60 秒防刷倒數），驗證通過後自動開通帳號並登入。
- [x] **問卷公開大廳 (`LobbyComponent`)**: 建立 `LobbyComponent`，支援現代化下拉式主題分類篩選、即時關鍵字模糊搜尋、⌘K 快捷鍵聚焦、Angular Material Table 排序與分頁。
  - [x] **表格資訊架構純化與冗贅欄位移除**: 徹底剔除大廳資料表格中之冗餘「狀態」欄位（大廳皆為公開問卷），保留完整寬度予有效時限以等寬數字排版；起訖時間完整呈現（支援 `常態開放` 與 `YYYY/MM/DD HH:mm ~ YYYY/MM/DD HH:mm`）。
- [x] **問卷設計工作台**: 建立 `SurveyDesignComponent`，支援問卷標題、分類、期限、受訪者身分欄位開關、動態題目增刪改、多維度量表題型、題目 UID 動態跳題解鎖與即時預覽模式（修復前言說明重複紅字問題）。
  - [x] **題目下方與題間自由插入題目 (UI/UX 體驗升級)**: 支援於每道題目下方點擊「在下方插入問題」，亦支援於相鄰題目之間透過微動態分割條「在此處插入問題」，並保留底部大按鈕與頂部快速新增。
  - [x] **本機圖片上傳/預覽/移除全功能正常運作**: 支援前言說明海報與各題目附圖本機選取轉 Base64、即時預覽、一鍵移除，修復未啟用前言卻阻斷之驗證缺陷，並打通前後端 API 與資料庫 LONGTEXT 持久化全鏈路。
- [x] **題目順序重排彈窗**: 建立 `SurveyReorderDialogComponent`，採用 `@angular/cdk/drag-drop` 實現視覺化題型拖曳排序並原子化回寫表單。
- [x] **問卷填答運行器**: 建立 `SurveyRunnerComponent`，支援受訪者身分欄位校驗、單選動態跳題隱藏/顯現邏輯、量表選項切換與作答成果封裝。
- [x] **會員與後台管理**:
  - [x] 建立 `LogInComponent`（固定 90rem 雙欄版面防止錯誤文字撐寬容器，正式移除開發測試面板）。
  - [x] 建立 `RegisterComponent`（身分證與手機防呆校驗）。
  - [x] 建立 `ForgetPasswordComponent` 與 `ResetPasswordComponent`。
  - [x] 建立 `UserProfileComponent`（折疊式修改密碼面板）。
  - [x] 建立 `SurveyManagementComponent`（問卷專案管理、狀態篩選與刪除對話框）。
  - [x] 建立 `UserManagementComponent`（管理員會員管理後台、狀態/身分篩選與安全移除）。
- [x] **表單管理深度數據分析儀表板 (`SurveyManagementComponent` & `SurveyAnalyticsService`)**:
  - [x] 於問卷管理列表新增「數據分析」操作按鈕，支援狀態、關鍵字快速定位。
  - [x] 實作 5 大核心維度 KPI 指標卡片（總回覆數、平均填答時間、完成率、淨推薦值 NPS、今日新增回覆）。
  - [x] 實作近 30 日填答走勢直方圖與高點互動指示。
  - [x] 實作受訪者全維度人口特徵輪廓（年齡層金字塔比例、性別比圓環/條圖、前五大產業分佈）。
  - [x] 實作題型深度聚合視覺化（單選題佔比、多選題複選率、5-10 階評分量表情感三分組、開放式文字題正向/中性/關注標籤分析與關鍵字快速篩選）。
  - [x] **最高得票者 👑 皇冠微徽章全題型支援**: 在是非題、單選題、多選題選項排行與評分量表分佈中，為最高票選項（含同票並列最高票）顯著新增 👑 皇冠微徽章與金色柔和醒目標記。
  - [x] 嚴格實作管理者與會員細粒度權限阻斷與防呆警示彈窗。
  - [x] **多格式統計數據匯出模組**: 支援 Excel 相容 CSV 匯出（含 UTF-8 BOM）、SPSS 統計軟體專用格式匯出（`.sps` 語法檔）、原始 JSON 匯出及瀏覽器原生高品質列印與 PDF 匯出。

---

## 前端樣式與互動規格

- [x] **現代品牌設計系統純原生 SCSS 樣式 (嚴格禁用 Bootstrap)**:
  - [x] 全面採用純原生 SCSS 變數、Flexbox、CSS Grid 與現代科技藍白設計系統（以 `#2563eb` 品牌藍、純白卡片底色、精緻 Slate 灰色調與柔和圓角陰影深度適配全站視覺）。
  - [x] 徹底排除任何 Bootstrap CSS/JS 依賴，體積輕盈且維持 100% 視覺統一度與高質感微互動體驗。
  - [x] 響應式佈局適配（支援 Desktop、平板與手機小螢幕多維度圖表重排）。
- [x] **全站資料表格 (Table) 杜絕橫向拖動與填答時限垂直緊湊化**:
  - [x] 問卷管理、問卷大廳與會員名冊表格全面宣告 `overflow-x: hidden`，徹底根除需要橫向拖動或滾動的情況。
  - [x] 填答有效時限由單行過長字串改為垂直雙行起訖排列（`formatDateTimeShort` 精簡為 `YYYY/MM/DD HH:mm`）。
  - [x] 當螢幕寬度 $\le 960\text{px}$ 時自動優雅轉化為直立卡片流，完美相容各裝置瀏覽。
- [x] **問卷雙狀態維度清晰展現與設定彈窗優化**:
  - [x] 每張問卷清楚展示兩個狀態維度：維度一「大廳公開狀態（大廳公開 / 私人專案）」與維度二「填答開放狀態（開放填寫中 / 已停止填寫）」。
  - [x] 問卷設定彈窗開關文案更新為「開放私人連結填寫」，文字說明明確標註專屬直達連結存取規則。
- [x] **簡答題（TEXT 題型）填答內容與人次完整呈現**:
  - [x] 各題作答分佈詳細報表修復簡答題空白問題，正確展示填答者回覆內容、送出時間與平均字數。
  - [x] 標題膠囊「有效 筆」數字防呆，確保真實填答筆數完整顯示，無填答時展示優雅友善空狀態提示。
- [x] **全站視覺「去 AI 化」與學術實證調研中性設計語言全面重構**:
  - [x] **學術中性色階 Token (`variables.scss`)**: 徹底清除紫藍霓虹發光與油膩毛玻璃，以典藏紙張暖白 `#fbfbfa`、牛津深藍 `#0f172a` / `#1e3a8a`、深墨灰黑 `#111827`、學術森林綠 `#166534` 與精準 1px 結構邊框 `#e5e7eb` 建立學術出版物高可信度視覺。
  - [x] **Masthead 頂部導覽列 (`HeaderComponent`)**: 典藏徽記、等寬索引編碼（`01`, `02`, `03`）、研究員身分指示與無障礙抽屜。
  - [x] **首頁與主視覺全量重構 (`HeroComponent` & `HomeComponent`)**: 徹底剔除虛假照片與「精準洞察力」等 AI 模板黑話，重塑為「實證調研、學術問卷與結構化數據採集系統」，完整展示 7 大測量題型規格矩陣、三階段實施歷程、4 大應用領域與研究倫理說明。
  - [x] **公開調研庫大廳 (`LobbyComponent`)**: 轉化為學術調研目錄索引，提供研究代號 `#SRV-id`、主題領域分類、有效時程與行動端零橫向拖曳自適應。
  - [x] **學術頁尾 (`FooterComponent`)**: 標註系統規格、研究倫理宣告與精確環境版本號（Node.js 24.18.0, Angular 19.0.0, Spring Boot 4.1.1, MySQL 8.0.36）。
- [x] **文字用字合規**: 全站文案與註解 100% 採用臺灣繁體中文，嚴格使用「臺」字，無簡體字與混用字。
- [x] **全站突兀中英混雜標籤全面純化**: 全量排查並剔除突兀英文標籤，全面回歸純淨自然臺灣繁體中文；首頁去 NPS 突兀字樣，對齊為「評分量表」。
- [x] **數據視覺化調色盤校準與高對比鮮明色彩**: 徹底去除各類分析圖表飽和度過低之灰濁配色，採用 12 色高對比度且色彩諧和之鮮明調色盤，並為各類長條圖與圓餅圖提供醒目之層次反差。
- [x] **首頁問卷核心特色互動輪盤與全站 Hover 微動態升級**: 實作 6 大核心特色幾何互動輪盤；為全站題型卡片、流程卡片、主題卡片及全域按鈕注入柔和微浮起與按壓微縮放動態。
- [x] **首頁主視覺問卷即時預覽可互動化**: 將 Hero 右側原本靜態寫死的問卷預覽卡片全面升級為支援單選題點選、五點滿意度量表 1~5 分評分、條件分支展開、下方統計指標即時動態重算與一鍵重設的擬真互動視窗。
- [x] **全域 Dialog 訊息分行排版升級 (`DialogComponent`)**: 重構訊息格式化管線，凡包含換行符號、列點符號（`•`, `-`）或多筆錯誤清單之訊息，自動轉譯為獨立 `.dialog-msg-item` 卡片分行呈現，徹底根除多項必填與校驗提示黏在一塊之不良體驗。
- [x] **問卷填答誤判徹底排除與原生 DOM 雙向同步守衛 (`SurveyRunnerComponent`)**:
  - [x] 解決原生 `<input type="date">` 與 `<input type="time">` 雙重綁定衝突導致的輸入值被清空缺陷。
  - [x] 原生選取項目（單選、是非、量表、複選矩陣）全面注入冒泡防護與送出前原生 DOM 狀態深度掃描同步。
  - [x] 支援條件跳題（選 A 展開 B）的可見度判定：隱藏題目強制豁免必填驗證，展開可見題目嚴格校驗且不誤判。
- [x] **數據分析儀表板最新回覆真實時間與臺灣時間格式化 (`AnalyticsService` & `SurveyAnalyticsService`)**:
  - [x] 「最新回覆」時間真實取自資料庫作答記錄之最大時間戳，杜絕前端虛假當前時間；無作答時正確標記「尚無回覆」。
  - [x] 「報表產生」時間採用臺灣標準時區格式（`YYYY/MM/DD HH:mm:ss`）呈現，杜絕未格式化之 UTC ISO 字串。
- [x] **系統架構與身分矩陣規格建立 (`01-overview.md` & `AGENTS.md`)**:
  - [x] 建立 `Deployment` 雲端生產部署平台規格（Cloudflare Pages 前端 + Render 後端 + TiDB Cloud 分散式資料庫）。
  - [x] 建立 `RoleType`（訪客 guest、會員 member、管理員 admin）完整權限比較打勾矩陣。
- [x] **常駐專屬規格書建立與循序編號 (`02-role-type.md` & `10-deployment.md`)**:
  - [x] 建立 `02-role-type.md`：詳列三大角色權限對照比較表，純符號無混雜字，移除未規劃虛構項。
  - [x] 建立 `10-deployment.md`：整合雲端雙軌拓撲、Render 環境變數對照表、Dockerfile 多階段建置與 Actuator 保活探針。
  - [x] 完成 `docs/system-design/` 01 至 10 循序合理編號，敘述分則明確且杜絕重複冗餘。
- [x] **最少實體檔案原則與環境變數 OCP 徹底落地**:
  - [x] 前端刪除多餘的 `environments/` 目錄與 `auth.interceptor.ts`，以純原生主機名動態解析後端網址。
  - [x] 後端 `application.properties` 徹底移除硬編碼 Email 與應用程式密碼，100% 透過環境變數注入。
  - [x] 前端樣式 100% 原生 SCSS/CSS，嚴格禁止引用 Bootstrap、Tailwind 等任何外部樣式表。
- [x] **全量設定檔繁體中文註解補齊**:
  - [x] `Dockerfile`、`backend/pom.xml`、`frontend/tsconfig.json`、`application.properties` 等 100% 標註繁中用途說明與參數邊界。



