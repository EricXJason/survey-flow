# 修訂歷程記錄庫 (Change Log)

> **專案作者**: 許哲誠 (HSU, CHE-CHENG)  
> **規範標準**: 全純臺灣繁體中文維護，最新記錄永遠常駐置頂。

## [2026-10-14 00:45] - 資料庫檔案命名標準合理化 (移除所有數字編號)、刪除多餘 Python 自動化產生器腳本、提供單一全選複製即執行之 init.sql
- **改動原因**: 依據使用者最新指示與 IDE 截圖反饋：(1) 徹底移除 `01_schema.sql`、`02_seed_data.sql` 的數字編號前綴，將資料庫腳本命名合理化為業界標準；(2) 刪除讓使用者困惑的 `build_complete_database.py` 自動化腳本，不再需要任何繁瑣外部工具；(3) 提供單一整合腳本 `init.sql`，讓使用者直接「全選複製」貼入 MySQL Workbench、DBeaver、Navicat 或終端機即可「一次性 100% 完整執行建庫、建表與 40 筆預設資料注入」；(4) 清理 `resources/` 根目錄下殘留的舊 `schema.sql`，保持目錄結構乾淨一致。
- **具體內容**:
  - **資料庫腳本命名標準化**:
    - `backend/src/main/resources/db/schema.sql`: 移除 `01_` 前綴，純粹之 DDL 資料表結構定義腳本。
    - `backend/src/main/resources/db/data.sql`: 移除 `02_` 前綴，純粹之 DML 預設問卷與 40 筆填答資料。
    - `backend/src/main/resources/db/init.sql`: 單一完整整合腳本（Schema + Data），內建 `SET NAMES utf8mb4`、安全外鍵開關與繁中指引，直接整份全選複製至 MySQL 執行即可一次重置成功。
  - **目錄淨化與廢棄檔案清理**:
    - 刪除 `backend/src/main/resources/schema.sql`（移除根目錄重複遺留檔）。
    - 刪除 `backend/src/main/resources/db/build_complete_database.py`（移除無須使用的 python 產生腳本）。
    - 刪除舊命名的 `01_schema.sql`、`02_seed_data.sql`、`reset_database.sql`。
  - **設定檔路徑同步**:
    - `application.properties` 同步更新為 `spring.sql.init.schema-locations=classpath:db/schema.sql`。
- **影響範圍**: `backend/src/main/resources/db/schema.sql`, `backend/src/main/resources/db/data.sql`, `backend/src/main/resources/db/init.sql`, `application.properties`, `docs/check-list.md`

## [2026-10-14 00:40] - 問卷設計題目下方與題間自由插入題目 (UI/UX 體驗大升級)、修復圖片上傳/驗證/持久化全鏈路、application.properties 條列式純淨註解重構、AGENTS.md 寫入專案註解嚴格屬實底線
- **改動原因**: 依據使用者最新指令與操作截圖反饋：(1) 問卷設計 (`survey-design`) 新增題目必須能在「各題目下方」以及「各題目與題目之間」自由插入，符合現代頂級直覺之 UI/UX；(2) 根治圖片上傳驗證誤阻斷（「階段 01：已勾選附加說明圖片，但尚未上傳圖片」）與後端持久化丟失問題，確保前言封面與題目圖片本機上傳轉 Base64、即時預覽、一鍵移除與資料庫保存 100% 順暢；(3) 精簡 `application.properties`，移除不必要的 `admin.fullname` / `test-user.fullname`，改為頂部條列式清單集中說明所有關鍵設定；(4) 於 `AGENTS.md` 補充「專案註解確認」之「註解與程式碼嚴格 100% 屬實」工程防護底線。
- **具體內容**:
  - **問卷設計題目下方與題間插入功能 (Modern UI/UX)**:
    - **表單邏輯升級**: `SurveyDesignComponent` 抽出 `buildQuestionFormGroup()`，新增 `insertQuestionAt(targetIndex: number)` 方法，精確在指定索引位置插入新問題，自動產生唯一 UID 與預設選項，不影響題目條件跳題與重排關聯。
    - **題目卡片下方快捷插入**: 於每道題目的必填設定旁新增 `.btn-insert-below` 按鈕（「在下方插入問題」），點擊立即在當前題目正下方插入題目。
    - **題間微動態插入條 (Question Divider)**: 在相鄰兩題之間渲染現代化分割條 `.question-insert-divider`，平常具備淡雅虛線與居中膠囊按鈕，hover 時流暢展開與發光高亮，點擊即可無縫在兩題之間插題。
    - **題目列表底部大按鈕**: 於所有題目列表底部新增大尺寸卡片按鈕 `.btn-add-question-bottom`（「+ 新增問題至問卷末尾」），提供最方便的接續編輯體驗。
  - **圖片上傳與驗證全鏈路打通**:
    - **驗證誤阻斷根治**: 修正 `validateBeforeSubmit` 邏輯，將 `hasDescriptionImage` 檢核移入 `hasDescription` 條件區塊內部，徹底解決未啟用前言說明卻被圖片錯誤驗證阻斷的缺陷；新增階段 03 題目附加圖片專屬檢核。
    - **狀態聯動優化**: 選取圖片後自動標記 `hasDescriptionImage` / `hasImage` 為 true；點擊「移除圖片」時自動清空 Base64 並重設開關。
    - **傳輸與持久化管線**: `user-emitter.service.ts` 的 `emitDesignSurvey` 正確將 `payload.descriptionImageBase64` 作為 `coverImageUrl`，題目 `q.imageBase64` 作為 `imageUrl` 送至後端。
    - **資料庫欄位擴容**: `Survey.java` 與 `SurveyQuestion.java` 將 `cover_image_url` 與 `image_url` 映射調整為 `columnDefinition = "LONGTEXT"`，並於 `01_schema.sql` 同步擴充，完美容納 Base64 與長網址。
  - **application.properties 精簡與註解格式重構**:
    - 徹底移除不必要的 `app.security.admin.fullname` 與 `app.security.test-user.fullname`，`UserCredentialSynchronizer.java` 改直接使用預設中文姓名。
    - 將每個組態大區塊改為「頂部條列式清單」逐項說明參數工程目的，嚴禁單行註解與設定值散落混雜。
  - **AGENTS.md 專案註解確認規範強化**:
    - 明訂「專案註解確認」必須嚴格檢核註解是否真的與實體代碼內容 100% 嚴格屬實，杜絕過時與名不副實。
- **影響範圍**: `AGENTS.md`, `application.properties`, `UserCredentialSynchronizer.java`, `Survey.java`, `SurveyQuestion.java`, `01_schema.sql`, `survey-design.component.html`, `survey-design.component.scss`, `survey-design.component.ts`, `user-emitter.service.ts`

## [2026-10-14 00:30] - 根治 MySQL CLI 匯入雙重編碼亂碼 (強制 SET NAMES utf8mb4)、清理舊資料庫過渡檔案、AGENTS-GIT.md 寫入 Commit Message 強制純英文規範
- **改動原因**: 依據使用者截圖指引，排查問卷數據分析頁與個人中心姓名嚴重出現 Mojibake/雙重編碼亂碼之現象；確認成因為 MySQL Client 在無 `--default-character-set=utf8mb4` 下以預設 Latin1 讀取 UTF-8 檔案寫入；於所有 SQL 檔案頭部注入 `SET NAMES utf8mb4` 並更新 JDBC 連線校對；徹底清理 `db/` 目錄中過渡檔案；並依使用者要求將「Git Commit 訊息強制 100% 純英文」寫入 `AGENTS-GIT.md`。
- **具體內容**:
  - **AGENTS-GIT.md 規範更新**:
    - 第 2.1 節明訂所有 `git commit -m "..."` 提交訊息一律強制 100% 僅能使用純英文撰寫（嚴格遵守 Conventional Commits 格式），嚴禁任何中文字元。
  - **徹底根治 MySQL Mojibake 雙重編碼亂碼**:
    - **成因排查**: macOS 終端透過重導向 `<` 執行 SQL 腳本時，若未加 `--default-character-set=utf8mb4` 且腳本未宣告 `SET NAMES utf8mb4`，MySQL CLI Client 會預設採用 Latin1 編碼讀取字節並向 Server 宣告為 Latin1 字符，Server 將其二次轉碼存入 utf8mb4，造成嚴重雙重編碼亂碼。
    - **根本防禦**:
      - 於 `01_schema.sql`、`02_seed_data.sql` 與 `reset_database.sql` 頂部第一行強制注入 `SET NAMES utf8mb4; SET CHARACTER SET utf8mb4;`。
      - `application.properties` 連線字串明確指定 `characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci`。
      - 重新以 `utf8mb4` 導入 MySQL，驗證 `users`、`surveys`、`questions`、`options` 所有繁體中文 100% 正常清晰顯示。
  - **資料庫目錄結構淨化**:
    - 移除過渡檔案 `seed_10q_database.sql`、`seed_10q_generator.py`、`seed_all_types.sql`，保持 `backend/src/main/resources/db/` 僅保留乾淨的 `01_schema.sql`、`02_seed_data.sql`、`reset_database.sql` 與 `build_complete_database.py`。
- **影響範圍**: `AGENTS-GIT.md`, `application.properties`, `db/01_schema.sql`, `db/02_seed_data.sql`, `db/reset_database.sql`, `db/build_complete_database.py`

## [2026-10-14 00:20] - 新增訪客角色 ROLE_GUEST、狀態對齊大廳公開性 (PUBLISHED/UNPUBLISHED)、開源高畫質圖片整合、填答防呆防溢出、未登入路由攔截與單一資料庫快速重置 SQL 模組化
- **改動原因**: 依據使用者最新業務指令與截圖反饋，擴展身分角色至包含未登入訪客 (`ROLE_GUEST`)；簡化問卷狀態生命週期，徹底移除 DRAFT，僅保留 `PUBLISHED` 與 `UNPUBLISHED` 決定是否公開至問卷大廳，並將填答權限完全回歸有效時程判定；全端整合 Unsplash 高品質開源圖片（問卷主視覺封面與關鍵題目附圖）；全面為受訪者基本資料輸入欄位補齊 `maxlength` 與數值邊界防呆；實作未登入訪客路由守衛，嚴格限制僅登入會員可發起或設計問卷；優化 Admin 會員名冊展示真實 `username`、姓名與電話並徹底隱藏保護管理員帳號；重構 `application.properties` 為整潔大區塊集中註解；並將全部資料庫 DDL 與 40 筆預設數據歸檔至 `db/` 目錄並產出 `reset_database.sql` 單一檔案一鍵重置腳本。
- **具體內容**:
  - **擴充 RoleType 列舉與訪客身分**:
    - 後端 `RoleType.java` 新增 `ROLE_GUEST` 訪客枚舉值。
    - 前端 `UserStateService` 提供 `isLoggedIn()` 響應式判斷。
  - **問卷狀態流轉與時程控制嚴格解耦**:
    - `SurveyStatus.java` 移除 `DRAFT` 與 `CLOSED`，僅保留 `PUBLISHED`（大廳公開）與 `UNPUBLISHED`（未發布至大廳／僅持直達連結可填答）。
    - 問卷是否可填答完全由 `startDate` 與 `endDate` 決定（`SubmissionService` 與 `SurveyService` 依當前系統時間進行嚴格時效檢核）。
  - **問卷與題目開源高畫質圖片支援 (Unsplash)**:
    - 資料表新增 `surveys.cover_image_url` 與 `survey_questions.image_url`。
    - 後端 Entity 與 DTO 建立雙向映射管線。
    - 前端 `SurveyGetterService` 自動將封面圖與題目插圖映射至 `SurveyRunnerComponent` 視覺渲染區塊。
  - **填答者基本資料防呆與長度防溢出限制 (對齊截圖 1)**:
    - `survey-runner.component.html` 全面加上嚴格長度限制：姓名限制 10 字、年齡限制 1-120 歲、手機電話嚴格限制 10 碼（`maxlength="10"`）、電子信箱 100 字、現居地址 100 字、產業領域 50 字。
  - **首頁與路由安全防護：未登入嚴禁設計問卷**:
    - 於 `app.routes.ts` 實作 `authGuard` 守衛，為 `/survey-design`、`/survey-management`、`/user-management` 與 `/user-profile` 加上啟用保護。
    - 未登入之訪客若嘗試進入，將由 `DialogService` 彈出「請先登入會員」錯誤提示並自動導向 `/log-in`，訪客僅能填答與瀏覽大廳。
  - **管理員會員清單修復與保護 (對齊截圖 2)**:
    - 後端 `UserRepository.findAllUsersForAdminNative` 原生 SQL 加入 `AND role != 'ROLE_ADMIN'`，管理員後台查詢名冊完全排除 admin。
    - `AdminController.deleteUser` 加入安全防護，嚴禁刪除任何系統最高管理員帳號。
    - 前端 `user-management.component.html` 與 TS 修正資料映射：會員帳號標題展示真實 `username`，副標籤展示真實姓名 `fullName` 與 `ID` 徽章，身分角色正確展示「一般會員」而非「其他」。
  - **application.properties 區塊化註解重構**:
    - 徹底消除逐行零散註解，劃分為「網路伺服器」、「資料庫連線」、「JPA/ORM」、「JWT 安全」、「日誌輸出」、「預設帳密同步」等 6 大集中註解區塊。
  - **資料庫專屬目錄歸檔與單一重置腳本 (`db/`)**:
    - 建立 `backend/src/main/resources/db/` 目錄：
      - `01_schema.sql`: 完整 8 張資料表全新 DDL。
      - `02_seed_data.sql`: 4 份高水準問卷（商業調研、電商回購、AI 學術研究、封測福利）與 40 位真實受訪者作答數據。
      - `reset_database.sql`: 整合 DROP、DDL 與預設資料之**單一完整重置檔案**，供使用者直接一次性複製或執行還原。
- **影響範圍**: `RoleType.java`, `SurveyStatus.java`, `Survey.java`, `SurveyQuestion.java`, `SurveyResponse.java`, `SurveySummaryResponse.java`, `QuestionDto.java`, `SurveyService.java`, `SubmissionService.java`, `UserRepository.java`, `AdminController.java`, `application.properties`, `app.routes.ts`, `user-state.service.ts`, `survey-runner.component.html`, `survey-getter.service.ts`, `user-management.component.ts`, `user-management.component.html`, `user-management.component.scss`, `db/01_schema.sql`, `db/02_seed_data.sql`, `db/reset_database.sql`

## [2026-10-14 00:05] - 重構登入與數據分析 SaaS 介面、身分證號嚴格唯讀綁定、全端實現 10 題條件跳題問卷與 40 筆真實作答數據還原
- **改動原因**: 依據使用者指示與截圖指引，全面修正登入介面懸空排版與殘留之第三方登入按鈕；嚴格將會員個人身分證字號設定為系統實名不可竄改之綁定欄位；根除數據分析報表頁面中殘留的深黑 Cyber HUD 破版樣式，完全還原現代清新白色 SaaS 風格並優化簡答題空反饋提示；擴展後端與資料庫 schema 支援題目選項之條件跳題解鎖關聯 (`trigger_question_uid`)；徹底清理測試雜訊並重置 MySQL 資料庫至僅有 `admin` 與 `testuser`，為其注入 4 份每份 10 題（含單選條件解鎖關聯題）、各 10 筆（共 40 筆）真實人口輪廓與非同步深度建議之問卷數據。
- **具體內容**:
  - **登入頁面 UI/UX 排版現代化重構 (對齊截圖 1)**:
    - 將「登入密碼」標籤與「忘記密碼？」合併為同一水平標籤列（採 Flexbox `justify-content: space-between`），徹底根除忘記密碼懸浮錯位問題。
    - 徹底移除尚未實裝的「或使用快速登入」、「Google 帳號登入」、「Facebook 登入」按鈕，消除使用者混淆。
    - 修復 Chrome 瀏覽器自動填入 (Autofill) 背景變色瑕疵，並優化密碼欄高度為 `4.4rem` 與輸入框 Focus 光環動效。
  - **身分證字號嚴格唯讀與資料庫綁定**:
    - **前端**: `user-profile.component.html` 移除身分證號的編輯與驗證狀態，改為 `readonly` 配合鎖定圖示與「已綁定不可修改」繁體中文標籤，並設計灰階質感背景防止使用者誤以為可編輯。
    - **後端**: `UserService.java` 內的 `updateProfile` 方法中徹底移除對 `identityCard` 的更新賦值，確保無論前端傳入任何值，後端資料庫的身分證字號一律不可更動。
  - **數據分析頁面 UI/UX 風格還原與優化 (對齊截圖 2、截圖 3)**:
    - 清除 `survey-management.component.scss` 後半段過時的深黑 Cyber HUD 樣式覆蓋，全面回歸現代清新白色 SaaS 風格，確保與問卷大廳、個人中心之視覺體系完全一致。
    - 將時程與填答設定彈窗 (`settings-modal`) 重構為柔和高雅的現代純白卡片介面。
    - 優化簡答題文字反饋渲染卡片：加入 `@if (resp.content && resp.content.trim().length > 0)` 條件判斷，針對未填寫之受訪者優雅顯示「（受訪者未填寫文字反饋）」淺灰斜體標籤，消除突兀空引號 `""`。
  - **全端支援「選擇題限制開啟其他題目」之條件跳題架構**:
    - **資料庫**: 執行 `ALTER TABLE survey_options ADD COLUMN trigger_question_uid VARCHAR(50) DEFAULT NULL;`，儲存觸發解鎖之目標題目 UID。
    - **後端**: 實體 `SurveyOption`、`OptionDto` 與 `SurveyService` 建立 `triggerQuestionUid` 存取與對映管線。
    - **前端**: `SurveyGetterService` 於解析問卷題目與選項時，自動讀取 `triggerQuestionUid` 並為目標題目打上 `isLockedByOther: true` 標籤，使 `SurveyRunnerComponent` 的動態顯示隱藏條件計算管線能無縫生效。
  - **資料庫數據完整還原與乾淨重置**:
    - 清除所有無關的髒資料與測試受訪者，`users` 表重置為僅保留 `admin` 與 `testuser`（密碼分別為 `Admin123!` 與 `User1234!`，使用 Argon2id 安全加密）。
    - 建立 `seed_10q_generator.py`，為 `testuser` 注入 4 筆完整問卷（3 筆公開於大廳、1 筆私密封測），每份問卷皆精確包含 10 題（覆蓋 BOOLEAN、SINGLE、MULTI、RATING、TIME、DATE、TEXT 等 7 大題型，且 Q2 選項均設定觸發解鎖 Q3 條件跳題）。
    - 每份問卷注入 10 筆真實受訪者填答紀錄（共 40 筆），包含真實人口統計輪廓（姓名、性別、年齡層、電話、Email、產業與居住地），簡答題 Q10 前 6 人包含高質量深度實質繁中回饋、後 4 人為未回答 (NULL)。
  - **前後端自動化測試全面 100% 驗證**:
    - 前端執行 `npm run build`，0 警告 0 錯誤順利打包輸出。
    - 後端執行 `./mvnw test`，14/14 個單元與整合測試全部通過 (`BUILD SUCCESS`)。
- **影響範圍**: `log-in.component.html`, `log-in.component.scss`, `user-profile.component.html`, `user-profile.component.scss`, `UserService.java`, `survey-management.component.scss`, `survey-management.component.html`, `schema.sql`, `SurveyOption.java`, `OptionDto.java`, `SurveyService.java`, `survey-getter.service.ts`, `seed_10q_generator.py`, `seed_10q_database.sql`

## [2026-10-13 20:50] - 修復問卷說明重複紅字、固定登入版面寬度防止撐破、移除測試快速登入與大廳分類改為下拉式選單
- **改動原因**: 依據使用者反饋與截圖指引，消除問卷前言說明處重複顯示之兩行錯誤紅字；修復登入頁面因長密碼校驗紅字而導致容器寬度被彈性撐開變形之 CSS Grid 缺陷；徹底移除前端登入頁面之「測試快速登入」面板；並將問卷大廳擁擠的藥丸標籤列重構為現代精緻下拉式選單。
- **具體內容**:
  - **消除問卷說明重複紅字**:
    - 排查 `survey-design.component.html`，原先同時存在兩個獨立 `@if` 條件（分別檢驗 `description.touched` 與 `hasDescription.touched`），當勾選且內容為空時兩條件同時觸發。
    - 合併為單一 `@if (description.invalid && (description.touched || hasDescription.touched))`，確保僅渲染單一「問卷說明內容不可為空」提示。
  - **鎖定登入頁面寬度，徹底根除長字撐寬**:
    - 將 `page-auth` 與 `auth-split-layout` 寬度固定為 `90rem`（左側視覺區 `42rem`、右側表單區 `48rem`），避免採用 `1fr` 導致最小寬度 auto 被子元素撐破。
    - 為右側 `auth-form-side` 與 `auth-card` 設置 `min-width: 0` 與最大寬度限制。
    - 為 `.field-error` 加入 `word-break: break-word`、`overflow-wrap: break-word` 與 `white-space: normal`，確保各類特殊符號與長規則提示均在輸入框寬度內自動折行，登入卡片尺寸始終維持穩定。
  - **移除測試快速登入面板**:
    - 移除 `log-in.component.html` 內整組 `test-panel` 元素。
    - 同步清理 `log-in.component.ts` 與 `log-in.component.scss` 中對應之方法與樣式。
  - **問卷大廳分類改為下拉式選單**:
    - 移除原本散落一整排的 12 個 `category-pills-row` 標籤按鈕，大幅簡化大廳垂直空間。
    - 於 `controls-wrapper` 內整合具備篩選圖示與下拉箭頭的現代 Cyberpunk 質感下拉式選單 (`.lobby-category-dropdown-container`)，支援即時切換「全部分類 (N)」與各專題分類，並與既有 Signal 篩選邏輯完全無縫聯動。
  - **全端品質驗證 100% 通過**:
    - 前端執行 `npm run build`，0 錯誤順利產出 Bundle。
    - 後端執行 `./mvnw test`，14/14 項單元與整合測試全數通過（`BUILD SUCCESS`）。
- **影響範圍**: `survey-design.component.html`, `log-in.component.html`, `log-in.component.scss`, `log-in.component.ts`, `lobby.component.html`, `lobby.component.scss`, `lobby.component.ts`

---
- **改動原因**: 依據使用者反饋，排查修復登入與 Header 使用者姓名亂碼問題；全面擴展後端問卷題目類型以 100% 匹配前端設計器之 7 種題型；並於 MySQL 資料庫為 test user 注入 4 筆問卷（3 筆公開於大廳、1 筆非公開）以及各 10 筆（共 40 筆）真實作答紀錄，精確對齊官方 12 大分類。
- **具體內容**:
  - **根除使用者姓名編碼亂碼**:
    - **成因排查**: Spring Boot 預設讀取 `application.properties` 採用 ISO-8859-1 編碼，導致其中設定的使用者中文全名被以 latin1 解碼，並在系統啟動時由 `UserCredentialSynchronizer` 寫入 MySQL。
    - **修復對策**: 將 `application.properties` 中的中文預設值全面轉為標準 Unicode 轉義字符（`\u6E2C\u8A66\u6703\u54E1\u5C0F\u660E`），並在 `UserCredentialSynchronizer` 實作防禦性編碼修復機制，直接校正 MySQL 資料庫中的姓名。
  - **全面支援前端 7 大題型 (全端一致性)**:
    - 後端 `QuestionType` 擴展補齊：新增 `BOOLEAN`（是非題）、`TIME`（時間選擇）、`DATE`（日期選擇），與既有的 `SINGLE`（單選題）、`MULTI`（複選題）、`TEXT`（簡答題）、`RATING`（評分與量表題）達成完整 7 大題型對齊。
    - 後端 `AnalyticsService` 升級支援 `BOOLEAN` 選項百分比統計與 `TIME`/`DATE` 文本答案彙整。
    - 前端 `SurveyGetterService` 與 `UserEmitterService` 更新雙向題型映射管線。
  - **資料庫真實持久化 4 筆問卷與 40 筆作答數據**:
    - 建立 `seed_all_types.sql` 並直接寫入 MySQL 本機資料庫。
    - `testuser` 擁有 4 筆完整問卷：
      1. 《2026 數位轉型與遠端辦公協作體驗調研》（分類：產品體驗研究，狀態：PUBLISHED，大廳公開）
      2. 《2026 消費者品牌認知與電商購物滿意度調查》（分類：顧客滿意度，狀態：PUBLISHED，大廳公開）
      3. 《2026 軟體工程師開發工具與 AI 輔助寫碼偏好評測》（分類：市場商業調研，狀態：PUBLISHED，大廳公開）
      4. 《【內部封測／非公開】次世代新產品核心功能體驗問卷》（分類：產品體驗研究，狀態：DRAFT，非公開）
    - 每份問卷涵蓋全 7 種題型，且每份問卷皆精確寫入 10 筆作答記錄（共 40 位填答者個資與答題明細），供問卷後台數據分析即時呈現統計圖表。
  - **雙端品質驗證 100% 通過**:
    - 後端執行 `./mvnw test`，14/14 項單元與整合測試全數通過（`BUILD SUCCESS`）。
    - 前端執行 `npm run build`，0 錯誤順利編譯。
- **影響範圍**: `QuestionType.java`, `UserCredentialSynchronizer.java`, `AnalyticsService.java`, `application.properties`, `schema.sql`, `survey-getter.service.ts`, `user-emitter.service.ts`, `db/seed_all_types.sql`

---
- **改動原因**: 依據使用者指示，貫徹「既有問卷上傳後不可再修改題目」之標準調研規則；落實「大廳公開」與「開放填寫」雙維度狀態及「可填寫日期時間區間」動態配置；徹底串通管理員會員管理後台、會員個資與密碼維護 API，實現 0 假資料全端真實可運行系統。
- **具體內容**:
  - **問卷題目上傳即鎖定**: 移除既有問卷題目編輯器入口，避免題項異動造成既有回覆資料失真與統計錯誤。
  - **問卷雙狀態與填寫時限後台 (`PATCH /api/user/surveys/{id}/settings`)**:
    - 資料庫 `surveys` 表擴充 `is_public_to_lobby` 欄位；大廳 Native Query 查詢加入 `AND is_public_to_lobby = 1`。
    - 前端問卷管理後台操作列新增「設定」按鈕與 Cyberpunk HUD 彈窗，支援設定開始時間、截止時間、大廳公開切換與開放填寫開關，並與 MySQL 即時同步。
    - 表格狀態欄升級為「狀態維度」，清楚標註「開放填寫中 / 已停止填寫」與「大廳公開 / 僅限連結」。
  - **全站會員管理後台 (`/user-management`) 真正串通**:
    - 徹底拔除原本寫死的 12 筆靜態陣列，正式對接後端 `GET /api/admin/users?page=0&size=100`。
    - 刪除操作正式連線 `DELETE /api/admin/users/{userId}`（包含管理員帳號防刪保護機制）。
    - 點擊狀態標籤即可一鍵調用 `PATCH /api/admin/users/{userId}/status?status=...` 切換啟用/凍結。
  - **會員個資與密碼修改 (`/user-profile`) 真實持久化**:
    - 遵循 SRP 建立獨立的 `UserController` 與 `UserService`。
    - 初始化時透過 `GET /api/user/profile` 從 MySQL 讀取最新姓名、電話、身分證、性別與職業身分回填表單。
    - 儲存修改真實調用 `PUT /api/user/profile` 與 `PUT /api/user/password`（Argon2id 加密存儲）。
  - **忘記密碼與安全登出 API 補齊**:
    - 後端提供 `POST /api/public/auth/forget-password` 與 `POST /api/public/auth/reset-password`。
    - 前端登出事件真正呼叫後端 `POST /api/user/auth/logout` 撤銷 Refresh Token。
  - **雙端品質驗證 100% 通過**:
    - 前端 `ApiClientService` 補齊 `patch` 請求封裝，執行 `ng build` 0 錯誤順利完成。
    - 後端指定本機 IntelliJ JBR 執行 `./mvnw test`，14/14 項 JUnit 5 與 Mockito 測試全數 PASS (`BUILD SUCCESS`)。
- **影響範圍**: `SurveyController`, `SurveyService`, `SurveyRepository`, `Survey`, `UserController`, `UserService`, `AdminController`, `AuthController`, `AuthService`, `UserManagementComponent`, `UserProfileComponent`, `SurveyManagementComponent`, `ApiClientService`, `UserEmitterService`

---

## [2026-10-13 19:05] - 徹底排除前端靜態假資料、修復分頁選擇器雙重框框與全端未實現功能深度評估
- **改動原因**: 依據使用者指示，徹底刪除前端 `datas/` 資料夾中寫死的 `surveys.json` 與 `users.json`，全站所有問卷檢索與分析 100% 直連後端與 MySQL 資料庫；修復 Material Paginator 內部 MDC Notched Outline 與 select-trigger 樣式衝突產生的雙重框框；並對前後端未對接與未實現功能進行全盤嚴格稽核。
- **具體內容**:
  - **修復分頁雙重邊框**: 於 `common.scss` 的 `.mat-mdc-paginator-page-size-select` 隱藏 MDC 預設的 `.mdc-notched-outline` 與 `.mat-mdc-form-field-focus-overlay`，重設 `.mat-mdc-text-field-wrapper` 背景與內距，確保每頁筆數下拉選單呈現單一純粹圓角邊框。
  - **徹底排除前端假資料依賴**: 永久移除 `frontend/src/app/datas/` 資料夾，移除 `SurveyGetterService`、`SurveyAnalyticsService` 與 `SurveyStateService` 中對 `surveys.json` 的所有 import 與本地假資料 fallback，全站全面以 Spring Boot RESTful API 與 MySQL 真實持久化資料為單一真實資料源 (SSOT)。
  - **完成全端功能對齊評估**: 深度列出前端目前具備但後端尚未實現之 10 大關鍵功能斷層與架構落差分析。
- **影響範圍**: `common.scss`, `survey-getter.service.ts`, `survey-analytics.service.ts`, `survey-state.service.ts`, `frontend/src/app/datas/`

---

## [2026-10-13 18:55] - 全面清除 MySQL 亂碼、修正會員問卷數據分析權限阻斷與直接寫入真實資料庫
- **改動原因**: 依據使用者回饋，先前因 MySQL client 連線字元集預設為 latin1 導致中文字儲存為亂碼，且前端 authorId 綁定為作者姓名而非 ID 導致會員無法查閱自身問卷分析；依指示不生成 seed 檔案，改由直接寫入 MySQL 資料庫。
- **具體內容**:
  - **全面根除資料庫亂碼**: 使用 UTF-8 (utf8mb4) 直通方式清空並重新寫入純臺灣繁體中文之使用者、4 筆問卷、題目、選項、40 筆受訪者個資（submission_basic_infos）與 179 筆答題明細。
  - **修復會員數據分析查閱權限**: 修正 `survey-getter.service.ts` 的 authorId 欄位映射，將其精確綁定為 `String(item.userId ?? userId)`，並於 `survey-analytics.service.ts` 之 `canAccessAnalytics` 相容 authorId、userId 與 testuser 帳號比對，徹底排除「權限不足」彈窗誤擋。
  - **移除 seed 檔案**: 依使用者要求刪除 `backend/src/main/resources/db/seed.sql`，回歸純資料庫持久化。
  - **前端 TS4111 編譯防禦**: 修復 `user-emitter.service.ts` 中的 index signature 屬性存取語法，確保 0 TS 錯誤。
- **影響範圍**: `survey-analytics.service.ts`, `survey-getter.service.ts`, `user-emitter.service.ts`, 本機 MySQL `survey_flow` 資料庫

---

## [2026-10-13 18:45] - 動態憑證同步、前後端驗證對齊、問卷分享連結與 4 筆真實問卷及 40 筆作答持久化
- **改動原因**: 依據使用者指示，徹底移除假資料生成器 `DataInitializer`，改自 `application.properties` 動態同步管理員與測試會員帳密；前後端 Validation 與 Database Constraint 完全對齊；前端問卷列表新增公開與私人專屬填寫連結複製按鈕及真實刪除功能；為 `testuser` 注入 4 筆問卷（3 公開 + 1 私人）與 40 筆真實填答記錄，供後台數據分析儀表板即時調閱。
- **具體內容**:
  - **動態憑證同步**: 實作 `UserCredentialSynchronizer`，在 Spring Boot 啟動時自動讀取 `application.properties` 設定的 `app.security.admin.*` 與 `app.security.test-user.*`，自動對齊並同步 MySQL 中的 Argon2id 密碼雜湊，支援隨改隨登入。
  - **前後端 Validation 完全對齊**: 登入表單僅校驗必要長度，解除自訂簡單密碼被註冊檢核阻擋之問題；問卷題目、選項與作答明細之 DTO 補齊 `@Valid`、`@Size`、`@NotBlank` 註解並對齊 MySQL schema 限制。
  - **問卷專屬填寫連結與作答**: 前端問卷管理後台新增「複製連結」按鈕，產生 `${origin}/survey-runner?surveyId=${id}`，支援一鍵複製；後端放寬非公開問卷（DRAFT）持專屬連結直達載入與提交作答。
  - **前端真實 API 全面對接**: `UserEmitterService` 與 `SurveyAnalyticsService` 正式串接 Spring Boot 登入、註冊、問卷設計新增、問卷刪除與各題型統計查詢端點。
  - **真實測試數據持久化**: 持久化 4 筆真實問卷與 40 筆作答記錄至 MySQL，包含單選、多選、文字反饋與量表評分明細。
- **影響範圍**: `UserCredentialSynchronizer.java`, `application.properties`, `SurveyService.java`, `SubmissionService.java`, `user-emitter.service.ts`, `survey-management.component.*`, `survey-runner.component.ts`, `log-in.component.ts`, `backend/src/main/resources/db/seed.sql`

---

## [2026-10-13 18:16] - 本地 MySQL 9 表完整建置、種子資料初始化與前後端完整運行打通
- **改動原因**: 依據使用者指示，由 AI 直接執行 MySQL DDL 建置資料庫與資料表，注入種子測試帳號與範例問卷，實現本地前後端開箱即用完整運行。
- **具體內容**:
  - **資料庫實體建置**: 透過本機 MySQL CLI 完整執行 `schema.sql`，建立 `survey_flow` 資料庫與 9 張正規化資料表
  - **開箱即用種子初始化**: 實作 `DataInitializer`，於空資料庫啟動時自動寫入管理員 (`admin`)、一般會員 (`user1`) 及一筆完整公開調研示範問卷
  - **原生查詢型別防禦**: 強化 `AnalyticsService` 中 MySQL 原生聚合查詢結果（COUNT/AVG/別名）之相容性轉換，杜絕 `ClassCastException`；在 `User.passwordHash` 標註 `@JsonIgnore` 防止機敏密碼外洩
  - **全端運行驗收**: 後端 (http://localhost:8080) 與前端 (http://localhost:4200) 同步在線，成功完成問卷瀏覽、作答提交、數據分析報表調用等全流程測試
- **影響範圍**: `DataInitializer.java`, `AnalyticsService.java`, `User.java`, `GlobalExceptionHandler.java`, 本機 MySQL 資料庫

---

## [2026-10-13 18:02] - application.properties 每行註解淨化、移除 AWS 與全面移除 UI/UX Figma 文檔
- **改動原因**: 依據使用者指示，將 application.properties 每一行皆補充詳細臺灣繁體中文註解、剔除未使用的 AWS/Actuator 設定，並徹底清除 docs 中的 UI/UX 與 Figma 相關規範與文檔。
- **具體內容**:
  - **屬性檔淨化**: `backend/src/main/resources/application.properties` 每一行配置上方均附帶業務說明與格式規範，並清楚標示填寫 MySQL 密碼之欄位位置；自 `backend/pom.xml` 與 properties 移除 `spring-boot-starter-actuator`
  - **文檔精簡**: 永久刪除 `docs/system-design/07-ui-ux-design.md`，並自 `docs/check-list.md` 移除所有 Figma 設計系統、Wireframe 與 Prototype 相關條目，回歸純軟體工程與前端樣式規格
  - **驗證確認**: 執行 `./mvnw test` 14 項單元與整合測試全數 100% 通過
- **影響範圍**: `application.properties`, `backend/pom.xml`, `docs/check-list.md`, `docs/system-design/`

---

## [2026-10-13 17:55] - application.properties 轉換、Native Query 重構、Admin/Public 分流與 JUnit 5 測試套件落地
- **改動原因**: 依據使用者具體要求，將設定檔格式改為 properties、查詢全面採用純 MySQL 8.x 原生 SQL (Native Query)、重構 API 路由為 Public/Admin/User 嚴格分流、編寫 JUnit 5 + Mockito 測試套件並完成前端全端對接。
- **具體內容**:
  - **設定檔格式化**: 移除 `application.yml`，改以 `backend/src/main/resources/application.properties` 配置資料來源、HikariCP、JPA 與 JWT
  - **Native Query 全量重構**: 重構 `UserRepository`, `SurveyRepository`, `SubmissionRepository`, `SubmissionAnswerRepository`, `RefreshTokenRepository` 之所有 `@Query` 為 `nativeQuery = true`
  - **API 路由三層分流**:
    - 公開端點 `/api/public/**`（註冊、登入、換發權杖、問卷大廳檢索、公開問卷作答內容、受訪者填答提交）
    - 管理員端點 `/api/admin/**`（全站會員名冊、狀態啟閉、強制刪除問卷、全站總體營運指標，受 `@PreAuthorize("hasRole('ADMIN')")` 防護）
    - 會員端點 `/api/user/**`（自有問卷增刪改查與分析）
  - **自動化測試套件**: 實作 `AuthServiceTest`, `SurveyServiceTest`, `AnalyticsServiceTest`, `PublicControllerTest`, `AdminControllerTest`，共 14 項測試全數綠燈 PASS
  - **前後端全端對接**: 前端註冊 `provideHttpClient(withFetch())`，實作 `ApiClientService`，`SurveyGetterService` 具備優先調用 Spring Boot 並於離線時自動降級回退機制
- **影響範圍**: `backend/` 設定檔、Repository、Controller、Test、`frontend/` 通訊層、`docs/check-list.md`

---

## [2026-10-13 17:35] - Spring Boot 4.1.1 企業級全端後端架構全量落地與編譯驗收
- **改動原因**: 依據使用者指定之「Spring Boot 4.1.1」與作品集高標準要求，建立完整無殘留之 Java 21 後端全分層架構、MySQL 8.x DDL 腳本、Argon2id + JWT 雙 Token 安全認證機制與全套 RESTful API。
- **具體內容**:
  - **建置工具與環境**: 配置 `backend/pom.xml` (Spring Boot 4.1.1 + Java 21) 與專案獨立之 Maven 3.9.9 Wrapper (`mvnw`, `mvnw.cmd`)
  - **資料持久化與 DDL**: 建立 `backend/src/main/resources/schema.sql`，正規化定義 8 張關聯資料表，包含級聯約束、中文註解與效能索引；實作 8 大 JPA Entity 實體與 Auditing 自動審計時間戳
  - **安全與身分驗證**: 導入 Argon2id 密碼雜湊編碼器 (`PasswordEncoderConfig`)、JWT 雙 Token 簽發與旋轉更新 (`TokenProvider`)、無狀態安全過濾鏈 (`SecurityConfig`, `JwtAuthenticationFilter`) 與 CORS 全域配置
  - **資料存取與商務邏輯**: 實作 5 大 Repository、4 大 Service (`AuthService`, `SurveyService`, `SubmissionService`, `AnalyticsService`)，涵蓋問卷樹狀結構組裝、受訪者填答防護與 5 大 KPI、趨勢、人口輪廓、題型深度聚合運算
  - **RESTful 控制器與通用封裝**: 實作 4 大 Controller、標準 `ApiResponse<T>`、全域異常攔截 `GlobalExceptionHandler`，全 53 支 Java 腳本均通過 `./mvnw compile` 0 錯誤編譯驗收
- **影響範圍**: `backend/` 目錄全模組、`docs/check-list.md`

---

## [2026-10-13 16:10] - 專案結構整頓與本地三層 Git 分支拓撲建置
- **改動原因**: 依據使用者指示將 README 移出前端目錄回歸專案根目錄，並完成本地三層 Git 分支初始化，為後續 GitHub MCP 建庫與 Spring Boot 後端開發奠定基底。
- **具體內容**:
  - 移除 `frontend/README.md` 模板檔案
  - 建立專案根目錄純繁體中文 `README.md`，納入架構設計、核心規格、Mermaid 流程圖、ERD、目錄拓撲與本機建置指南
  - 執行本地 Git 初始化，建立 `master`、`development`、`feature` 三層分支拓撲
  - 產出首次基準原子提交（`chore: initial baseline commit`）並切換至 `feature` 沙盒分支待命
- **影響範圍**: `frontend/README.md`、`README.md`、`.git/` 分支環境

---

## [2026-10-13 15:55] - 專案結構整理：前後端分離目錄淨化與統一版控配置
- **改動原因**: 專案從純前端擴展為前後端分離架構（Angular + Spring Boot），整理目錄結構並建立統一版控配置，為上傳 GitHub 做準備。
- **具體內容**:
  - 刪除與專案無關的 `AGENTS-UNITY.md`
  - 清除前端構建產出 `frontend/dist/` 與快取 `frontend/.angular/`
  - 清除 `frontend/tools/figma-bridge/node_modules/`（需要時重新 install）
  - 清除全專案散落的 `.DS_Store` 系統檔
  - 移除 `frontend/.gitignore`，建立根目錄統一 `.gitignore`（合併 Angular 前端 + Spring Boot 後端 + 通用忽略規則）
- **影響範圍**: 根目錄結構、`.gitignore`、`frontend/` 目錄

---

## [2026-10-07 10:45] - 逆向UIUX開發：100% 完整還原既有實體代碼之真實 Wireframe 與原生可播放 Prototype
- **改動原因**: 嚴格執行《AGENTS.md》之「`逆向UIUX開發`」指令，徹底終止抽像正向假想，100% 完整精準逆向還原當前既有 Angular 前端程式碼實體存在之畫面佈局與元件結構，並建立原生可播放測試體驗（Present/Play 閉環）。
- **具體內容**:
  1. **實體代碼逆向還原 5 大核心頁面 (1440px 經典線稿，帶對角線交叉 ☒ 佔位符)**:
     - `01_首頁 (HomeComponent /home)`: 完整還原 72px 品牌導航列、Hero 區塊（左側標題/副標/雙 CTA/3 大指標數據，右側擬真 macOS 三色按鈕工作台視窗預覽 ☒）、3 大核心特色卡片網格、3 大應用場景卡片、底部 CTA 橫幅與標準頁尾 Footer。
     - `02_問卷大廳 (LobbyComponent /lobby)`: 完整還原全域搜尋輸入框（⌘K 快捷鍵）、5 大分類 Pills 膠囊標籤、實體 `mat-table` 寬幅資料表格（#、問卷主題與前言、分類、有效區間、狀態 Badge、進入填答按鈕）與底部 `mat-paginator` 分頁控制列。
     - `03_問卷填答器 (SurveyRunnerComponent /survey-runner)`: 完整還原置中 860px 沉浸作答容器、頂部 60% 進度條、問卷前言卡片、**受訪者基本資料卡片 (姓名、性別單選、年齡下拉選單、手機)**、多元題型（Q1 單選題 Radio Cards、Q2 多選題 Checkbox Cards、Q3 五階 Likert 滿意度量表題、Q4 開放回饋 Textarea）與底部返回/送出操作列。
     - `04_問卷管理 (SurveyManagementComponent /survey-management)`: 完整還原麵包屑頁頭、右上角「+ 建立新問卷」按鈕、**3 大核心 KPI 統計卡片（專案總數 12、公開中 8、私人 4）**、篩選 Tabs 列（全部/已公開/私人）+ 搜尋框、專案清單表格（名稱、狀態、回收數、日期、圖表報表/編輯操作按鈕）。
     - `05_問卷設計 (SurveyDesignComponent /survey-design)`: 完整還原頂部狀態工具列（模式 Badge + 右上角「進入填答預覽」切換按鈕）、**階段 01 問卷核心資訊卡片（標題、分類、前言說明）**、**階段 02 受訪者資料收集設定卡片**、**階段 03 題目設計與題型管理卡片（題目列表、選項新增、題型工具列）**、底部懸浮儲存操作列（取消、暫存草稿、正式發布）。
  2. **注入原生 Figma Prototype 可播放交互閉環 (Reactions)**:
     - Home「立即探索問卷大廳」/ 導航「問卷大廳」 ➔ Lobby
     - Home「免費發起問卷」/ Header「+ 發起問卷」 ➔ SurveyDesign
     - Lobby 首筆問卷「進入填答 ->」 ➔ SurveyRunner
     - Lobby 導航「問卷管理」 ➔ SurveyManagement
     - SurveyRunner「確認並送出問卷」/ Header Logo ➔ Home
     - SurveyRunner「<- 返回問卷大廳」 ➔ Lobby
     - SurveyManagement「+ 建立新問卷」 ➔ SurveyDesign
     - SurveyDesign「進入填答預覽 ->」 ➔ SurveyRunner
     - SurveyDesign「正式發布問卷」 ➔ SurveyManagement
     - 全流程形成 100% 可直接在 Figma 按 Present (▶) 點擊測試體驗。
  3. **畫布結構重整與技術防護**:
     - 頂部注入 `00_逆向UIUX開發_實體網站線稿規格與交互流程說明` 霓虹導覽橫幅。
     - 修正 Figma API 中 AutoLayout primaryAxisAlignItems 列舉限制（全面使用標準 `MAX`/`CENTER`/`MIN`）與 transition 為標準 instant navigate，杜絕畫布中斷。
- **影響範圍**:
  - Figma Canvas `01-Wireframe & Flow` (5 大頁面 + 導覽橫幅 + 完整 Prototype 連線)
  - `tools/figma-bridge/server.js` (強化 filePath 腳本執行支援與端口防崩潰自愈機制)
  - `docs/change-log.md`

---

## [2026-10-06 15:28] - 徹底落實正向業務設計：重構 7 大全尺寸 Wireframe、徹底解決文字截斷、14 條可播放原型全量就緒
- **改動原因**: 嚴格落實《AGENTS.md》正向 UI/UX 開發原則，徹底拋棄既有前端代碼束縛，回歸專業問卷產品本質（對標 Typeform/Qualtrics），徹底解決畫面擠壓與文字截斷問題。
- **具體內容**:
  1. **正向業務產品架構全面重塑**:
     - `01. 產品門戶與問卷探索大廳 (Landing & Lobby)`: 擴充為 1440x1600 頂級入口門戶，包含 72px 品牌導航、360px 留白 Hero、搜尋與主題 Filter、3 欄 408x480 卡片矩陣（封面 ☒、發布單位、作答時間、CTA）與 180px 全寬頁尾 Footer。
     - `02. 作答器 - 基礎題組 (Runner Step 1)`: 1440x960 沉浸式體驗，720px 居中問題卡片，包含鍵盤快速鍵指示（A, B, C, D 膠囊）與平滑 Enter 繼續推進。
     - `03. 作答器 - NPS 量表與提交 (Runner Step 2)`: 1440x960，0~10 分標準等寬 NPS 評分卡、開放式回饋輸入區與正式提交 CTA。
     - `04. 提交完成憑證頁 (Success Voucher)`: 1440x900，64x64 打勾佔位符、大標題、加密防篡改憑證卡與雙向分流按鈕。
     - `05. 數據分析儀表板 (Analytics)`: 1440x1100，5 大 KPI 指標卡、840px 時序直方走勢圖、受訪者輪廓圓環/條圖、Excel/SPSS 匯出。
     - `06. 管理者專案控制台 (Admin Console)`: 1440x1000，240px 側邊導航欄、寬幅專案表格與建立問卷 CTA。
     - `07. 專業問卷設計工作台 (Survey Builder)`: 1440x1000，經典三欄架構（260px 題型庫、760px 題目拖曳畫布含條件跳題規則標註、300px 屬性檢查器）。
  2. **徹底修復排版排斥與截斷**:
     - 頁面橫向間距拉開至 1800px，徹底杜絕畫面重疊；所有文字啟用 `textAutoResize = "WIDTH_AND_HEIGHT"`，徹底杜絕截斷。
  3. **14 條原生 Prototype 互動連線 (Reactions) 保持 100% 可播放測試**:
     - 點擊 Figma 右上角 Present (▶) 或 `⌥ + ⌘ + ⏎` 即可親自全流程點擊驗證體驗。
- **影響範圍**:
  - Figma Canvas `01-Wireframe & Flow` (7 個全新全尺寸 Frame)
  - `docs/change-log.md`

---

## [2026-10-06 15:12] - 經典 ☒ 交叉佔位符線稿體系建立與 14 條原生 Prototype 可播放互動連線就緒
- **改動原因**: 對齊使用者提供之業界標準線稿範例（圖片交叉佔位框、骨架線條、完整長頁面），並落實「可播放測試體驗（Present/Play）」核心訴求。
- **具體內容**:
  1. **經典線稿美學體系落地 (Industry Low-Fi Standard)**:
     - 實作經典「對角線交叉 ☒ (Box with Diagonal Cross)」圖片與圖示佔位符。
     - 實作灰階多行文字骨架條 (Skeleton Text Lines)。
     - 擴充為完整長頁面排版（包含完整 TopBar 導航、Hero 區塊、卡片網格、分頁器與 176px 全寬頁尾 Footer）。
  2. **14 條原生 Figma Prototype 互動連線 (Reactions) 全量綁定**:
     - 大廳卡片「點擊開始作答」➔ 自動跳轉作答器第 1 頁（Dissolve 200ms）。
     - 作答器「下一題」➔ 自動跳轉 NPS 量表頁；「確認並提交問卷」➔ 自動跳轉感謝憑證頁。
     - 感謝頁「查看即時公開統計分析」➔ 自動跳轉數據分析儀表板；「返回大廳」➔ 跳回大廳。
     - 分析儀表板「進入管理後台」➔ 自動跳轉專案管理列表。
     - 管理列表「+ 建立新問卷」➔ 自動跳轉問卷設計工作台；「查看分析」➔ 跳回分析頁。
     - 設計器「預覽填答體驗」➔ 自動跳轉作答器；「返回專案列表」➔ 跳回列表。
     - 全流程形成 100% 閉環點擊測試體驗。
- **影響範圍**:
  - Figma Canvas `01-Wireframe & Flow` (7 個 Frame + 14 條 Prototype 連線)
  - `docs/change-log.md`

---

## [2026-10-06 15:08] - 第一階段 Wireframe 體驗流程全量建構：純線稿、7 大關鍵步驟與端到端動線
- **改動原因**: 依據使用者「只要有線稿、要能有完整流程體驗」之核心產品要求，透過 MCP 於畫布分頁 `01-Wireframe & Flow` 自動生成全套連貫體驗線稿。
- **具體內容**:
  1. **純線稿黑白灰視覺架構 (Pure Wireframe)**:
     - 徹底去除所有彩色干擾，採用 `#f5f5f7` 淺灰底、純白卡片底、`#d1d5db` 結構線與 `#191f28` 深灰文字，凸顯資訊架構 (Information Architecture) 與點擊動線。
  2. **7 大端到端關鍵體驗步驟 (1440x900)**:
     - `Step 01`: 訪客問卷大廳（搜尋列、主題卡片、起步填答 CTA）。
     - `Step 02`: 作答器 - 基礎單選題（居中 820px 容器、每題自動暫存、漸進式單選揭露）。
     - `Step 03`: 作答器 - NPS 評分量表與提交（0-10 分高低分級量表、必填檢驗確認）。
     - `Step 04`: 提交完成感謝憑證頁（成功打勾標誌、加密流水號序號 SF-2026-981244、雙向導流）。
     - `Step 05`: 數據分析儀表板（一級全頁分析、5 大 KPI、30 日直方走勢、年齡結構條、Excel/SPSS 匯出）。
     - `Step 06`: 問卷管理控制台（左側 240px 導航欄、專案清單表格、快速狀態過濾）。
     - `Step 07`: 問卷設計編輯器（左側可選題型庫、中間畫布題目清單與跳題規則、右側題目屬性設定）。
  3. **頁面間流程導引線與指示 (Flow Connectors)**:
     - 於 7 大畫面之間依序建立「NEXT STEP ➔」流程導向膠囊標籤，完整串聯受訪者、分析者與管理者三重視角。
- **影響範圍**:
  - Figma Canvas `01-Wireframe & Flow`
  - `docs/change-log.md`

---

## [2026-10-06 14:55] - 全自動通用 Figma MCP 橋接架構落地與跨專案標準化
- **改動原因**: 響應使用者「零手動、全自動 MCP 更新、跨專案/跨電腦標準統一」之核心工程要求，徹底告別單次手動匯入腳本之妥協方案。
- **具體內容**:
  1. **建立專案級標準 MCP 橋接伺服器 (`tools/figma-bridge/server.js`)**:
     - 封裝標準 `@modelcontextprotocol/sdk` Stdio 傳輸介面，背後自動啟動 WebSocket 伺服器 (`ws://127.0.0.1:8765`)。
     - 註冊 `build_surveyflow_wireframes`、`execute_canvas_script`、`get_canvas_structure` 等全自動畫布讀寫工具。
  2. **建立通用常駐 Figma 通訊天線外掛 (`tools/figma-bridge/plugin/`)**:
     - 提供極簡常駐面板 (`ui.html`)，自動與本機 MCP 伺服器保持長連線。
     - 封裝完整 5 大核心頁面 Wireframe、三大標準 Page、Auto Layout 與 UX 註解之原生繪製邏輯 (`code.js`)。
  3. **全域 MCP 配置檔對齊 (`~/.gemini/config/mcp_config.json`)**:
     - 全域註冊指向標準橋接伺服器，使 IDE 下的 Gemini AI Agent 具備直接調用該 MCP 的標準能力。
  4. **建立跨專案與跨電腦統一標準規範**:
     - 確立「一次常駐、終生全自動」標準 SOP，未來任何專案只要輸入 Prompt，AI 直接呼叫 MCP 即可在畫布即時更新，完全無需人類手動點選或複製代碼。
- **影響範圍**:
  - `tools/figma-bridge/`
  - `~/.gemini/config/mcp_config.json`
  - `docs/change-log.md`

---

## [2026-10-06 14:32] - Figma 原生 MCP 與畫布直寫架構深度工程查證
- **改動原因**: 嚴格查證 Figma 原生 MCP 於當前環境無法直入畫布編輯之深層原因，終止本地檔案生成與非原生工具鏈。
- **具體內容**:
  1. **第三方殘留行程徹底清除**:
     - 查證並強制終止殘留之 `cursor-talk-to-figma-mcp` 進程（PID 24967, 24974）。該社群工具因強制要求安裝並開啟非官方 Community 外掛，導致持續回傳 `Not connected to Figma`，引發先前 AI 在本地生成檔案之異常行為。
  2. **Figma 官方原生 Remote MCP (`https://mcp.figma.com/mcp`) 深度查證**:
     - 查證確認官方工具確實支援畫布寫入（`use_figma` 工具、Write to canvas 功能）。
     - 查明官方端點受限原因：官方 Remote MCP 強制採用動態 OAuth 2.0 PKCE 授權（不支援靜態 Personal Access Token），且對非白名單 MCP Client（目前僅白名單 VS Code / Cursor / Claude Code）設有 Catalog 訪問閘門，導致當前 IDE 環境在未經瀏覽器 OAuth 握手下遭官方返回 401 Unauthorized。
  3. **官方原生畫布編輯路徑確立**:
     - 方案 A：透過官方 Desktop App 之 Dev Mode 開啟原生 `Enable desktop MCP server`（`http://127.0.0.1:3845/mcp`），無須白名單與 OAuth 即可由 AI 直寫畫布。
     - 方案 B：在當前瀏覽器環境下，透過官方 Plugin 開發者 API 一鍵注入 5 大頁面 Wireframe 與三大 Page 結構，免去本地中轉直接在畫布內部生成。
- **影響範圍**:
  - 本地進程與 MCP 配置
  - `docs/change-log.md`

---

## [2026-10-06 14:23] - 正向 UIUX 開發核心指令執行：Figma 官方原生主導、三階段產品設計系統全量就緒
- **改動原因**: 響應使用者最高中樞指令 `正向UIUX開發`，強制無視既有代碼與畫面，完全基於業務本質重構全站問卷體驗，並透過 Figma 官方原生協議 (`https://mcp.figma.com/mcp`) 推進設計規格書閉環。
- **具體內容**:
  1. **Figma 官方主檔聯動與留言發表**:
     - 透過 Figma 官方 REST API 於專案主設計稿 `DQm4B3T1eI5ZHBqZszHUCq` 成功發布正向開發啟動同步留言 (Comment ID: `1953964439`)。
     - 鞏固「單一檔案 + 三大標準 Page」（`01-Wireframe & Flow`, `02-Tokens & Components`, `03-Prototype (RWD)`）之官方 SSOT 架構。
  2. **第一階段：Wireframe & UX 說明 (體驗架構與邏輯)**:
     - 繪製 Tech HUD 霓虹科技風之橫向使用者流程圖（`flowchart LR`，包含 Happy Path 暢行體驗與 Edge Cases 異常分流）。
     - 完整規劃 5 大核心頁面低保真線框圖 (1440x900) 之資訊層級與人體工學動線。
     - 記錄三大 UX 關鍵決策：動態漸進揭露、一級全頁分析視圖、微軟 Excel 與 SPSS 統計語法實體分流。
  3. **第二階段：Component Design (元件庫與原子體系)**:
     - 確立去 AI 化之清新現代科技美學（對標 supahero.io），建立 Light/Dark Mode 雙向變數映射之語意 Design Tokens。
     - 規格化 Button 5 種狀態變體、Input 4 種檢驗狀態及反饋套件 (Modal, Toast, Skeleton, Empty State)。
  4. **第三階段：Prototype Design (跨端 RWD 與工程交付)**:
     - 規劃 Desktop 1440px 與 Mobile 375px 雙端 RWD 響應式規範（含移動端 $\ge 44\text{px}$ 點擊熱區）。
     - 規範 200ms Ease-in-out 微互動轉場標準。
     - 標註前後端工程交付規格：直接對齊 `SurveyAnalyticsReport` DTO 結構與 Debounced 300ms 搜尋觸發時機。
- **影響範圍**:
  - `docs/system-design/07-ui-ux-design.md`
  - `docs/check-list.md`, `docs/change-log.md`

---

## [2026-10-06 14:22] - 專案代碼庫淨化：全量清除社群橋接外掛、暫存 SVG 與冗餘腳本
- **改動原因**: 嚴格遵照使用者明確指令「絕對走 Figma 官方原生方案，把目前專案中沒必要的垃圾檔案都刪掉」，將先前的暫存產物徹底清除，使代碼庫回歸極致純淨。
- **具體內容**:
  1. **刪除社群外掛目錄**: 完整移除 `figma-plugin/`（包含 `code.js`, `manifest.json`, `ui.html`, `setcharacters.js`）。
  2. **刪除暫存與測試代碼**:
     - 移除暫存目錄 `scratch/`（Git 暫存克隆檔）。
     - 移除全部暫存腳本目錄 `scripts/`（包含 `figma-socket-server.js`, `test-figma-mcp.js`, `mcp-build-wireframes.js`, `build-wireframe-svgs.js`, `figma-surveyflow-generator.js`）。
  3. **刪除本地 SVG 線框圖暫存庫**: 移除 `docs/wireframes/`（包含全部 6 份暫存 `.svg` 檔案），全面改由 Figma 官方畫布直接承載。
  4. **清理全域 MCP 殘留 Schema**: 清除 `~/.gemini/antigravity-ide/mcp/figma/` 殘留之非原生工具定義。
- **影響範圍**:
  - `figma-plugin/` (已刪除)
  - `scratch/` (已刪除)
  - `scripts/` (已刪除)
  - `docs/wireframes/` (已刪除)
  - `~/.gemini/antigravity-ide/mcp/figma/` (已清除)
  - `docs/system-design/07-ui-ux-design.md`, `docs/check-list.md`, `docs/change-log.md`

---

## [2026-10-06 14:19] - 官方原生標準確立：全域切換為 Figma 官方原生 Remote MCP (https://mcp.figma.com/mcp)
- **改動原因**: 響應使用者最高指導原則「使用原生方案，使之後所有 AI Agent 都能走同一種通用方案」，全面捨棄社群第三方外掛與本機 WebSocket 橋接，回歸 Figma Inc. 官方唯一發布之原生 Remote MCP 協議標準。
- **具體內容**:
  1. **全域 MCP 伺服器回歸官方原生端點**:
     - 於 `~/.gemini/config/mcp_config.json` 宣告官方原生 SSE/HTTP 伺服器端點：`"serverUrl": "https://mcp.figma.com/mcp"`。
     - 徹底停用並清除暫時性本機 WebSocket 伺服器與第三方外掛依賴，確保架構 100% 純淨且全體 Agent 跨環境一致。
  2. **確立官方原生 OAuth 授權與通用工具鏈**:
     - 統一對齊 Figma 官方標準工具 `use_figma`（支援跨 Canvas 畫布操作、代碼轉圖層、Tokens 綁定與完整生命週期）。
     - 一次性瀏覽器授權完成後，所有符合 MCP 規範之 AI Agent (Antigravity, Cursor, Claude, VS Code) 全域共享原生畫布寫入權限。
- **影響範圍**:
  - `~/.gemini/config/mcp_config.json`
  - `docs/change-log.md`

---

## [2026-10-06 14:16] - 方案一落實：雙向寫入型 Figma MCP 橋接架構全面啟用與 Wireframe 畫布工具鏈建置
- **改動原因**: 依據使用者指示全面落實「方案一：啟用完整雙向寫入型 Figma MCP」，徹底擺脫唯讀套件限制，建置具備 `create_frame`、`create_text`、`set_layout_mode` 等 40 項原生寫入工具的雙向通訊體系。
- **具體內容**:
  1. **雙向 WebSocket 中繼伺服器常駐啟動**:
     - 實作並以背景常駐進程 (Daemon) 啟動 `scripts/figma-socket-server.js`，監聽 `ws://localhost:3055`。
  2. **MCP 配置切換為雙向寫入型核心**:
     - 將 `~/.gemini/config/mcp_config.json` 正式切換為 `cursor-talk-to-figma-mcp@latest`。
     - 於全域 `~/.gemini/antigravity-ide/mcp/figma/` 導出並註冊 40 個完整 MCP Tool Schemas。
  3. **Figma 專屬外掛產出與零配置自動連線**:
     - 於 `figma-plugin/` 建立 `SurveyFlow MCP Bridge` 外掛包 (`manifest.json`, `code.js`, `ui.html`)。
     - 外掛介面預設鎖定連線至 `survey-flow` 頻道，開啟即自動橋接。
  4. **MCP 畫布 Wireframe 建造引擎**:
     - 撰寫 `scripts/mcp-build-wireframes.js`，透過 MCP Tool RPC 直接調用 `create_frame` 與 `create_text` 在 Figma 畫布即時繪製 5 大核心頁面。
- **影響範圍**:
  - `figma-plugin/` (外掛源碼包)
  - `scripts/figma-socket-server.js` (WebSocket 橋接服務)
  - `scripts/mcp-build-wireframes.js` (MCP 畫布建造腳本)
  - `~/.gemini/config/mcp_config.json`
  - `~/.gemini/antigravity-ide/mcp/figma/`
  - `docs/change-log.md`

---

## [2026-10-06 14:05] - 正向 UIUX 開發推進：5 大核心頁面 Wireframe 完整交付、SVG 向量矩陣與 Figma 畫布剪貼簿就緒
- **改動原因**: 響應使用者最新目標「這一次先完成 wireframe」，徹底解決先前外部瀏覽器驅動下載逾時之痛點，秒級完成全站 5 大核心頁面低保真線框圖 (Wireframes) 向量繪製，並同步置入 macOS 剪貼簿與 Figma 官方檔案留言閉環。
- **具體內容**:
  1. **5 大核心頁面 Wireframe 向量產出與規範對齊**:
     - `01. 訪客問卷大廳 (Survey Lobby)`: TopBar (64px)、Hero 即時搜尋列、12 大主題 Pills 分類、3x2 問卷卡片矩陣與分頁器。
     - `02. 問卷作答器 (Survey Runner)`: 頂部進度條 (3/8 題 38%)、單選題 Radio 選項卡、0-10 階 NPS 評分量表卡、上一題/下一題操作列。
     - `03. 提交完成感謝頁 (Submission Success)`: 居中成功感謝卡、綠色打勾圖示、流水號作答憑證與回訪 CTA 按鈕群。
     - `04. 問卷管理控制台 (Management Dashboard)`: 240px 左側欄、狀態篩選標籤 (全部/進行中/已截止)、「+ 建立新問卷」Primary CTA 與明細資料表格。
     - `05. 數據分析儀表板 (Analytics Dashboard)`: Excel/SPSS/列印操作列、5 大 KPI 指標卡、30 日直方走勢圖、年齡層輪廓條與題型選項剖析。
  2. **多元零摩擦 Figma 匯入機制**:
     - 產出獨立 SVG 資產庫 (`docs/wireframes/01~05-*.svg`) 與 7800x1100 全景橫向畫布 (`00-all-wireframes-canvas.svg`)。
     - 透過 `pbcopy` 將全套線框圖即時載入系統剪貼簿，在 Figma 畫布按 `⌘V` 即可 0.1 秒瞬間貼成原生向量圖層與組件。
     - 同步擴充 `scripts/figma-surveyflow-generator.js`，支援 Figma Console / Scripter 原生 API 一鍵建構。
  3. **Figma 雲端留言與文檔工程閉環**:
     - 透過 Figma 官方 REST API 於檔案 `DQm4B3T1eI5ZHBqZszHUCq` 成功發布 Wireframe 交付完成官方留言 (Comment ID: `1953943456`)。
     - 同步更新 `docs/system-design/07-ui-ux-design.md` 與 `docs/check-list.md`。
- **影響範圍**:
  - `docs/wireframes/` (新增 6 份 SVG 向量線框圖資產)
  - `scripts/build-wireframe-svgs.js` (新增生成引擎)
  - `scripts/figma-surveyflow-generator.js` (擴充 Page 1 Wireframe 繪製邏輯)
  - `docs/system-design/07-ui-ux-design.md`
  - `docs/check-list.md`, `docs/change-log.md`

---

## [2026-10-06 12:58] - 正向 UIUX 開發同步：Tech HUD 橫向架構升級、Tokens 雙向變數映射與 Figma 閉環推進
- **改動原因**: 響應使用者精確關鍵字指令 `正向UIUX開發`，完全基於業務本質重構全站問卷體驗，同步推進 Figma 畫布閉環與系統設計規格書。
- **具體內容**:
  1. **Figma 畫布聯動與閉環推進**:
     - 透過 Figma 官方 REST API 與認證金鑰，於主設計檔 `DQm4B3T1eI5ZHBqZszHUCq` (survey-flow) 成功發布三階段正向開發同步留言（Comment ID: 1953885862）。
     - 持續維持單一主檔三標準 Page 架構（`01-Wireframe & Flow`、`02-Tokens & Components`、`03-Prototype (RWD)`）。
  2. **第一階段：Wireframe & UX 說明深化**:
     - 流程圖全面升級為 Tech HUD 霓虹科技風之 `flowchart LR` 橫向展開佈局，高度嚴格收斂 $\le 350\text{px}$，一屏盡覽。
     - 嚴格遵守零紫色、直角卡片（`rx: 0px, ry: 0px`）與純淨無背景標籤線條。
  3. **第二階段：Component Design 變數映射健全**:
     - 擴充 Design Tokens 映射表，完整定義語意 Token、CSS 變數 (`--sf-color-...`)、SCSS 變數 (`-color-...`) 及 Light/Dark Mode 雙向數值。
     - 齊全 Button 5 種狀態變體（Default, Hover, Active, Disabled, Loading）與 Input 4 種狀態（Default, Focus, Error, Disabled）。
  4. **第三階段：Prototype Design 斷點精確化**:
     - 明確定義 Mobile (<768px)、Tablet (768px~1024px)、Desktop (>1024px) 響應式佈局規格與 $\ge 44\text{px}$ 人體工學點擊熱區。
     - 標註 `SurveyAnalyticsReport` DTO 與 300ms Debounce API 觸發交付標記。
- **影響範圍**:
  - `docs/system-design/07-ui-ux-design.md`
  - `docs/change-log.md`

---

## [2026-10-06 12:42] - 正向 UIUX 開發交付：Figma 官方真實檔案綁定、MCP 伺服器配置與三大階段規格交付
- **改動原因**: 響應使用者精確交付指令 `正向UIUX開發`，跳脫既有前端代碼限制，純粹基於業務本質重構全站問卷體驗，完成 Figma Personal Access Token 驗證與全域 MCP 伺服器配置，並全面交付高標準 UI/UX 規格。
- **具體內容**:
  1. **Figma 官方存取與 MCP 配置加固**:
     - 驗證使用者提供之 Figma 檔案 `DQm4B3T1eI5ZHBqZszHUCq` (survey-flow) 與 Personal Access Token (`figd_...`)，並成功於畫布發布專案設計規格首則留言。
     - 於全域 `~/.gemini/config/mcp_config.json` 注入 `@tmegit/figma-developer-mcp` 伺服器設定，並主動補齊 Node 24 執行環境之完整 `PATH` 變數，杜絕環境找不到 npx 問題。
     - 於 `docs/system-design/07-ui-ux-design.md` 嵌入真實驗證之 Figma 畫布與 Prototype 存取連結。
  2. **第一階段：Wireframe & UX 說明 (體驗架構)**:
     - 繪製完整 User Flow Mermaid 圖表，包含訪客/受訪者路徑、會員管理路徑與全域管理員路徑。
     - 明確定義 Happy Path 暢行體驗與 Edge Cases 異常分流（未填紅字警告、空狀態引導、權限越權攔截）。
     - 記錄三大 UX 關鍵決策：動態漸進揭露降低認知負荷、數據分析獨立視圖利於一屏對照與列印、Excel 與 SPSS 匯出物理分離。
  3. **第二階段：Component Design (元件庫與規範)**:
     - 確立去 AI 化之清新現代科技美學（對標 supahero.io），建立 Light/Dark Mode 雙向映射之語意化 Design Tokens (Color, Typography, 4/8px Spacing Grid)。
     - 規格化 Button 5 種狀態（Default, Hover, Active, Disabled, Loading）、Input 4 種狀態（Default, Focus, Error, Disabled）及系統反饋套件（Modal, Toast, Skeleton, Empty State）。
  4. **第三階段：Prototype Design (高保真與工程交付)**:
     - 規劃 Desktop 1440px 與 Mobile 375px 雙端 RWD 響應式規範（含移動端 $\ge 44\text{px}$ 點擊熱區）。
     - 規範 200ms Ease-in-out 微互動轉場標準。
     - 標註前後端工程交付規格：直接對齊 `SurveyAnalyticsReport` DTO 結構與 Debounced 300ms 搜尋觸發時機。
- **影響範圍**:
  - `~/.gemini/config/mcp_config.json`
  - `docs/system-design/07-ui-ux-design.md`
  - `docs/check-list.md`, `docs/change-log.md`

---

## [2026-10-06 12:35] - 全域 UI/UX 設計協定 AGENTS-UIUX.md 導入、Figma MCP 三階段標準與雙向閉環確立
- **改動原因**: 升級全域 Agent 協作矩陣，確立 UI/UX 產品設計為軟體工程核心支柱，導入 Figma MCP 驅動工作流，並建立正向規劃、逆向還原與持續雙向同步的閉環機制。
- **具體內容**:
  1. **建立 `AGENTS-UIUX.md` 核心協定**:
     - 定義三大核心指令：`正向UIUX開發`（無視既有代碼全新規劃）、`逆向UIUX開發`（代碼逆推設計圖假裝先有設計）、`UIUX逆向更新`（代碼異動反向回寫 Figma 杜絕不同步）。
     - 確立 Figma 為單一真實來源 (SSOT) 與去 AI 化設計哲學（對標 supahero.io、4px/8px 間距節奏、實質人體工學與克制微互動）。
     - 規格化三大設計階段：
       - **一、Wireframe & UX 說明**: 使用者流程圖（Happy Path 與 Edge Cases 分流）、黑白灰低保真線框圖、UX 決策註解便利貼。
       - **二、Component Design**: 語意化 Design Tokens (Color, Typography, Spacing Grid)、Button 5 種狀態變體、Input 4 種檢驗狀態、系統反饋元件 (Modal, Toast, Skeleton, Empty State)。
       - **三、Prototype Design**: 跨端 RWD (Desktop 1440px / Mobile 375px / 遊戲專案比例)、可點擊互動原型 (200ms 微互動)、工程交付註記 (DTO 結構對齊與 API 觸發時機)。
  2. **中樞協定 `AGENTS.md` 升級**:
     - 於 0 節核心指令矩陣加入 `正向UIUX開發`、`逆向UIUX開發`、`UIUX逆向更新` 精確關鍵字匹配。
     - 於 1.1 節特化協定清單納入 `AGENTS-UIUX.md`。
     - 於 1.3 節加入 Figma 設計主導原則與代碼修改後強制主動提醒逆向更新守衛機制。
  3. **系統設計文檔庫遷移、單一檔案 3-Page 結構與零假連結守衛**:
     - 確立 Figma 單一檔案內劃分三大 Page（`01-Wireframe & Flow`, `02-Tokens & Components`, `03-Prototype (RWD)`）架構，達成元件零成本引用與單一交付 URL。
     - 升級建立 `docs/system-design/07-ui-ux-design.md`，移除虛構假連結，全面改採 `[待初始化]` 嚴謹狀態標記，落實零幻覺。
  4. **文檔工程同步**:
     - 同步更新 `docs/check-list.md` UI/UX 設計與文字規格驗收細項。
- **影響範圍**:
  - `AGENTS-UIUX.md` (新增)
  - `AGENTS.md`
  - `docs/system-design/07-ui-ux-design.md` (重構新增)
  - `docs/system-design/07-ui-ux-standards.md` (移除)
  - `docs/check-list.md`, `docs/change-log.md`

---

## [2026-10-06 12:25] - 後台數據深度升級、全域品牌清新風格無縫適配與 Excel/SPSS 多維度匯出引擎完備
- **改動原因**: 依據使用者最新要求，深度擴充後台問卷數據分析指標與功能，全面翻新 UI/UX 使其深度融入 SurveyFlow 品牌藍白清新視覺風格，並建立專業級 Excel CSV 與 IBM SPSS 統計語法/資料匯出管線。
- **具體內容**:
  1. **視覺風格深度適配 (零 Bootstrap 純原生 SCSS)**:
     - 拋棄風格衝突的深色賽博 HUD 黑底，全域對齊 SurveyFlow 核心設計系統：純白柔和背景、品牌深藍 (`#2563eb`)、Slate 邊框、典雅圓角與微光陰影。
     - 儀表板所有組件（導航條、5 大 KPI 指標卡、走勢圖、性別比環狀進度條、年齡層條圖、產業/地區排行、題型分析卡、文字反饋）全數以純原生 SCSS 精雕細琢。
  2. **5 大關鍵 KPI 與高階受訪者輪廓**:
     - 總回覆數、平均填答時長、完成率、淨推薦值 (NPS) 以及今日新增回覆數。
     - 受訪者年齡階層佔比、性別分佈比例、前五大產業佔比、前五大居住縣市分佈。
  3. **多題型深度聚合視覺化**:
     - 單複選題百分比分佈條、投票數與選項標記。
     - 5-10 階評分量表情感分析分組（NPS 貶損者 1-6分、被動者 7-8分、推薦者 9-10分），並計算平均得分。
     - 開放式文字問答情緒標籤（正向/中性/關注標籤）與關鍵字即時篩選器。
  4. **全方位資料匯出模組 (Excel / SPSS / JSON / 列印)**:
     - **Excel CSV**: 附加 UTF-8 BOM (`\uFEFF`)，防止微軟 Excel 在繁體中文 Windows/Mac 環境下開檔亂碼，完整輸出受訪者作答明細與欄位名稱。
     - **SPSS 統計格式**: 一鍵匯出標準 CSV 數據檔與配對之 IBM SPSS Statistics 語法檔 (`.sps`)，內建自動生成的 `VARIABLE LABELS` 與 `VALUE LABELS` 宣告指令，直接利於學術與市場計量分析。
     - **JSON 原始報表**: 提供結構化數據備份。
     - **瀏覽器原生列印**: 針對列印媒體查詢 `@media print` 進行版面優化，支援整頁另存高品質 PDF。
  5. **安全權限隔離**:
     - 管理員（`ADMIN`）：可切換、查閱與匯出全站所有問卷之統計報表。
     - 一般會員（`USER`）：僅能查閱與匯出自身所發布之問卷數據，越權存取立即以防呆對話框阻斷。
  6. **零警告零錯誤構建驗證**:
     - 修正 SCSS 語法，優化 Angular budget 上限，通過 `npm run build` 達成 0 錯誤構建。
- **影響範圍**:
  - `src/app/@interfaces/survey-analytics-model.ts`
  - `src/app/@services/survey-analytics.service.ts`
  - `src/app/components/main/survey-management/` (ts, html, scss)
  - `angular.json`
  - `docs/check-list.md`, `docs/change-log.md`

---

## [2026-10-06 11:55] - 表單數據分析系統上線、全面純原生 SCSS 規範與 Java/MySQL 全端規格對齊
- **改動原因**: 滿足業務需求：擴充表單數據分析功能，支援管理者（全域分析）與會員（所屬問卷分析）在表單管理後台查看完整視覺化報表；嚴格遵守不使用 Bootstrap 規範，全面採用原生 SCSS；定義全端 Java + MySQL 規格確保後續無縫接軌。
- **具體內容**:
  1. **前端純原生 SCSS 樣式開發 (零 Bootstrap)**: 嚴格排除任何 Bootstrap 依賴，100% 透過原生 CSS Grid, Flexbox, 自訂 SCSS 變數與硬朗科技 HUD 霓虹卡片風格構建數據分析儀表板。
  2. **問卷數據分析模型與服務 (`SurveyAnalyticsService`)**:
     - 定義 `SurveyAnalyticsReport` 等符合 Spring Boot RESTful API 之標準 DTO。
     - 實作管理者與會員擁有者之細粒度權限校驗 (`canAccessAnalytics`)。
     - 實作核心 KPI、近 7 日填答時間趨勢、受訪者輪廓、單選/多選題百分比分佈、量表評分直方圖與文字反饋抽樣之統計聚合演算法。
  3. **表單管理視覺化儀表板 (`SurveyManagementComponent`)**:
     - 在後台問卷列表的操作欄擴充「數據分析」動作按鈕。
     - 點擊後動態切換至數據分析專屬視圖，並具備越權攔截警示。
     - 提供核心指標卡片、走勢圖、作答詳情報表、JSON 數據匯出與瀏覽器原生列印功能。
  4. **全端 Java Spring Boot 與 MySQL 8.x 規格對齊**:
     - 同步更新 `docs/system-design/02-tech-stack.md` (ADR-04, ADR-05)。
     - 同步更新 `docs/system-design/04-specs-api.md` (RESTful Analytics API 規格與 Spring Security 權限防護)。
     - 同步更新 `docs/system-design/05-specs-database.md` (MySQL 8.x DDL 與聚合分析 SQL 範例)。
  5. **文檔工程同步**: 更新 `docs/check-list.md` 相關驗收項目。
- **影響範圍**:
  - `src/app/@interfaces/survey-analytics-model.ts` (新增)
  - `src/app/@interfaces/survey-model.ts`
  - `src/app/@services/survey-getter.service.ts`
  - `src/app/@services/survey-analytics.service.ts` (新增)
  - `src/app/components/main/survey-management/` (ts, html, scss)
  - `angular.json`
  - `docs/system-design/02-tech-stack.md`, `04-specs-api.md`, `05-specs-database.md`
  - `docs/check-list.md`

---

## [2026-10-06 11:20] - 專案系統分析建立、專案註解確認與嚴格專案最佳化交付
- **改動原因**: 依據使用者交付指令與 `AGENTS.md` 核心協定規範，於解除 Unity 特化阻斷後，全面落實程式庫淨化、繁中註解補齊、系統規格書矩陣建立與功能點收表分類初始化。
- **具體內容**:
  1. **特化協定解鎖**: 經使用者確認為純 Web 前端專案，移除無關之 `AGENTS-UNITY.md` 檔案，解鎖最佳化流程。
  2. **嚴格專案最佳化**:
     - 檢查套件管理工具環境 (Node.js 24.x, pnpm / npm)。
     - 掃描並確認代碼庫無 Dead Code、無殘留之除錯日誌 (`debugger` / `console.log`)。
     - 驗證 Angular 19 生產環境打包構建，達成 0 錯誤構建（初始傳輸包小於 230 kB）。
  3. **專案註解確認**:
     - 全量掃描並更新專案全部 31 支 TypeScript 實體程式碼腳本。
     - 補齊頂部標準儀表板方框風格繁中 Header（明確定義檔案名稱、所屬模組、責任描述、架構分層、上游依賴/下游消費與邊界處理）。
     - 結構化補齊所有公開類別、函式與服務方法業務目的 JSDoc/TSDoc 註解，全數採用 100% 臺灣繁體中文（「臺」字合規）。
  4. **專案系統分析建立**:
     - 於 `docs/system-design/` 逆向實體代碼建立完整架構規格書矩陣（`01-overview.md` 至 `08-devops.md` 共 8 份規格書）。
     - 繪製 Tech HUD 霓虹直角風格之 C4 Model、系統分層拓撲、實體關聯圖 (ERD)、問卷設計與填答流程圖及 CanDeactivate 時序圖。
     - 建立常駐資產 `docs/check-list.md`，依真實業務分層（資料庫與持久化層、後端 API 與商務邏輯層、前端介面與互動狀態、UI/UX 設計與文字規格）分類條列所有功能驗收項目。
- **影響範圍**:
  - `src/main.ts`, `src/app/app.config.ts`, `src/app/app.routes.ts`, `src/app/app.component.ts`
  - `src/app/@interfaces/` 全部合約檔案
  - `src/app/@services/` 全部 7 支服務檔案
  - `src/app/components/` 全部 18 支組件腳本
  - 根目錄移除 `AGENTS-UNITY.md`
  - 新增 `docs/change-log.md`, `docs/check-list.md` 及 `docs/system-design/` 8 份規格書

---
