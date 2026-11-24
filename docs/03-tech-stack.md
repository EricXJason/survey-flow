# 03. 技術選型矩陣與版本理由 (Technology Stack & Decisions)

> **系統環境**: Node.js 24.18.0, TypeScript 5.6.2, Angular 19.0.0, Java 21.0.12, Spring Boot 4.1.1, MySQL 8.0.36 協定 (TiDB)  
> **文檔定位**: 專案全域技術選型與架構決策紀錄 (ADR)。接續身分權限，闡述支撐系統運作之軟體工程技術棧。

---

## 1. 核心技術選型矩陣 (絕對明確版本號)

| 類別 / 領域 | 選定技術 / 工具 | 絕對明確版本號 | 選型權衡與架構優勢 |
| :--- | :--- | :--- | :--- |
| **前端核心框架** | Angular | `19.0.0` | 採用 Standalone Components，不使用 NgModule；整合 Angular Signals 實現細粒度高性能響應式更新。 |
| **語言與型別系統** | TypeScript | `5.6.2` | 以 strict 模式在編譯期攔截 undefined 存取等型別錯誤，支援進階型別推導與條件介面。 |
| **狀態管理機制** | Angular Signals & RxJS | Signals + `7.8.0` | 區域與共享狀態採用 Signals 原生追蹤，複雜事件串流與非同步彈窗交互採用 RxJS Observable，兼顧效能與開發彈性。 |
| **UI 元件與佈局庫** | Angular Material & CDK | `19.2.19` | 採用 Material Table、Paginator、Sort 提供具備在地化語言支援的後臺資料表；導入 `@angular/cdk/drag-drop` 實現問卷題型流暢拖曳排序。 |
| **表單核心驅動** | Angular Reactive Forms | `19.0.0` | 支援高階動態 FormArray 與 FormGroup 巢狀陣列動態擴充，精準監聽 valueChanges 達成條件跳題與即時防呆。 |
| **樣式與設計系統** | SCSS + CSS 自訂變數 | 純原生 SCSS | 導入純原生 SCSS 體系 (`common.scss`, `variables.scss`, `styles.scss`)，100% 純原生 SCSS/CSS，嚴格禁用 Bootstrap, Tailwind 等任何外部樣式庫與外部樣式表。 |
| **建置與打包工具** | Angular CLI (Vite + esbuild) | `19.0.5` | 底層基於 esbuild 與 Vite 核心，提供極速熱重載 (HMR) 與體積高度壓縮之現代化 Bundle 產物。 |
| **套件管理器** | pnpm (唯一，鎖檔 `pnpm-lock.yaml`) | `pnpm 11` / `Node.js ≥ 22.13` (本機 24.18.0、CI 22.x) | 嚴格依賴鎖定，CI 以 `--frozen-lockfile` 安裝；`pnpm-workspace.yaml` 白名單控管安裝期建置腳本。 |
| **後端核心框架** | Java / Spring Boot | `Java 21.0.12` / `Spring Boot 4.1.1` | 業界常用的 Java 後端框架，提供高併發處理能力、靜態型別安全、Spring Security 嚴密權限防護與 Spring Data JPA 交易隔離。 |
| **持久化關聯資料庫** | TiDB Cloud / MySQL | `MySQL 8.0.36 協定` | 業界主流 ACID 分散式關聯資料庫，具備優異的二級索引查詢、聚合作答統計效能 (COUNT, AVG, GROUP BY) 與外鍵完整性約束。 |
| **後端單元測試** | JUnit 5 + Mockito + Spring MockMvc + JaCoCo | Spring Boot Test 內建 / JaCoCo `0.8.12` | 360 項單元測試，不需資料庫即可執行；JaCoCo 門檻行 ≥ 95%、分支 ≥ 90%。 |
| **後端整合測試** | Testcontainers + MySQL 容器 + Maven Failsafe | Testcontainers `2.0.5` / `mysql:8.0.36` | 57 項整合測試在真實 MySQL 上驗證原生 SQL、完整 Security 過濾鏈與併發鎖；`./mvnw verify` 執行，需 Docker。 |
| **前端單元測試** | Karma + Jasmine (ChromeHeadless) + karma-coverage | Karma `6.4` / Jasmine `5.4` | 291 項服務、守衛與元件測試；`karma.conf.js` 門檻敘述 / 函式 / 行 ≥ 95%、分支 ≥ 85%。 |
| **瀏覽器端到端測試** | Playwright (Chromium) + MySQL / Mailpit 容器 | `@playwright/test 1.57.0` / Mailpit `v1.27` | 20 項以真實瀏覽器操作完整前後端流程 (註冊收信、建立與填答問卷、樂觀鎖衝突、Token 自動換發)；`pnpm e2e` 一行啟動整套環境。 |

---

## 2. 核心架構決策紀錄 (ADR)

### ADR-01: 採用 Standalone Components 與 Signals
- **決策背景**: 傳統 Angular 採用 NgModule 造成模組間依賴難以拆分與樹搖 (Tree-shaking) 不完全。
- **解決方案**: 專案所有組件 100% 採用 `standalone: true`，狀態採用 `signal()` 與 `computed()`，大幅降低記憶體開銷並消除不必要的全域變更檢測 (Change Detection Cycle)。

### ADR-02: 雙軌狀態儲存 (In-Memory + SessionStorage)
- **決策背景**: SPA 頁面重新整理 (F5) 容易遺失當前登入者身分與工作區脈絡。
- **解決方案**: `UserStateService` 於背景將身分狀態序列化至瀏覽器 `sessionStorage`，頁面初始化時自動還原；登出時主動清除。Refresh Token 與密碼一律不寫入瀏覽器儲存區 (見 `09-security.md`)。

### ADR-03: 題目唯一識別符 (Crypto UUID) 綁定條件跳題
- **決策背景**: 若以陣列索引 (Index) 綁定題目跳題解鎖，使用者執行「拖曳重新排序」或刪除題目時，會導致跳題目標錯位失聯。
- **解決方案**: 題目與選項解鎖目標以 `crypto.randomUUID()` 唯一字串綁定，無論順序如何調動，邏輯依賴永遠精準指向目標題型。

### ADR-04: 嚴禁 Bootstrap 與外部樣式庫，貫徹 100% 純原生 CSS / SCSS
- **決策背景**: 外部樣式表（如 Bootstrap、Tailwind）容易帶來冗餘樣式、全域類別污染且不利於精準把控品牌調性。
- **解決方案**: 專案 100% 排除 Bootstrap 與任何外部樣式表，採用純原生 SCSS 變數體系 (`variables.scss`) 與流動網格，元件樣式完全自主可控，且不載入用不到的樣式。

### ADR-05: 面向 Java Spring Boot + TiDB MySQL 之全端 RESTful 數據分析架構
- **決策背景**: 前端數據分析必須無縫切換至生產環境後端服務，不可造成重構成本。
- **解決方案**: 前端 DTO 與 API 介面規格嚴格對齊 Spring Boot REST Controller 與 TiDB MySQL Schema，成功回應統一以 `ApiResponse<T>` 封裝、錯誤回應統一為 RFC 9457 Problem Details (見 ADR-13)，實現平滑無痛接軌。

### ADR-06: 嚴禁 Hibernate 自動建表，強制採用 `ddl-auto=none`
- **決策背景**: 若開啟 `ddl-auto=update`，Hibernate 會在啟動期自動發出未經審核的 DDL，更名時產生孤立廢棄欄位、遺失註解與精確約束，甚至在高並發或生產資料庫引發鎖表與服務中斷。
- **解決方案**: 全專案 ORM 強制設定 `spring.jpa.hibernate.ddl-auto=none`，資料庫結構 100% 由 `backend/src/main/resources/database/schema.sql` 唯一真實來源 (SSOT) 主導，避免程式與資料庫結構不一致 (Schema Drift) 或資料表被非預期變更。

### ADR-07: 實體與 DTO 以顯式轉換取代反射映射
- **決策背景**: ModelMapper 等執行期反射映射在欄位改名時靜默失效，且需開放 Entity Setter，破壞封裝。
- **解決方案**: 移除 ModelMapper 依賴；Entity 不開放 Setter，狀態變更一律透過具業務語意之方法 (如 `Survey.patchSettings()`、`User.softDelete()`)；DTO 轉換集中於各 Service 底部之私有 `convertTo...` / `to...Entity` 方法，編譯期即可發現欄位不一致。

### ADR-08: 單元測試 + 真實資料庫整合測試兩層，並以覆蓋率門檻防止倒退
- **決策背景**: 單元測試快速且不依賴外部服務，但 Mock 掉 Repository 就驗證不到原生 SQL 寫錯，Controller standalone 測試也不會經過 Security 過濾鏈。
- **解決方案**:
  - **單元測試** (`./mvnw test`)：Service / Security / Entity 以 JUnit 5 + Mockito 撰寫，Controller 以 MockMvc standalone 驗證 HTTP 契約；外部依賴 (HttpClient、JavaMailSender) 皆以建構子注入以便 Mock。
  - **整合測試** (`./mvnw verify`，類別以 `IT` 結尾)：Testcontainers 啟動與正式環境同版本的 MySQL 8 容器，載入 `schema.sql` + `seed/01~05` 種子資料，以 `@SpringBootTest` 啟動完整應用程式。只 Mock 寄信 (`JavaMailSender`)，其餘 (SQL、過濾鏈、Argon2id、JWT、鎖) 皆為真實元件。
  - 兩層各有 JaCoCo 報告，單元測試另設建置門檻。
- **取捨**: 整合測試需要 Docker、單次約多 1 分鐘；不選 H2 記憶體資料庫，因為 `FOR UPDATE`、`LIMIT`、`ON UPDATE` 等 MySQL 語法與行為在 H2 上不一致，測過也不代表正式環境正確。

### ADR-09: 併發控制採「依情境選鎖」，不引入分散式鎖或 Redis
- **決策背景**: 同一份問卷可能被擁有者在兩個分頁、或擁有者與管理員同時修改；重設密碼驗證碼可能被重複送出；熱門問卷的瀏覽數會被大量同時遞增。單一 Render 實例、單一 MySQL，不需要跨服務協調。
- **解決方案**:

  | 情境 | 做法 | 理由 |
  | :--- | :--- | :--- |
  | 修改問卷內容與開放設定 | **樂觀鎖**：`surveys.version` + JPA `@Version`，前端送回讀取時的版本號，不符回 409 | 衝突少、修改時間長 (使用者在畫面上編輯)，不能長時間鎖住資料列 |
  | 核銷信箱驗證碼 / 重設密碼驗證碼 | **悲觀鎖**：`SELECT … FOR UPDATE` 鎖住該筆驗證碼直到交易結束 | 交易很短，且一組驗證碼絕對只能用一次 (安全需求) |
  | 瀏覽數 +1 | **原子更新**：`UPDATE … SET view_count = view_count + 1` | 不需要讀出來再寫回，也不應讓瀏覽數衝突造成使用者看到錯誤 |
  | 同時註冊相同帳號或信箱 | **唯一索引**：資料庫 UNIQUE KEY 為最後防線，衝突轉為 409 | 應用程式先查再寫之間有空窗，只有資料庫約束能保證唯一 |

- **取捨**: 樂觀鎖衝突時由使用者重新整理後再改 (前端顯示後端訊息)，不自動合併；Token 黑名單與整批作廢存於資料庫 (`revoked_tokens`、`users.tokens_revoked_before`)，重啟不遺失；登入失敗鎖定仍存於單一實例記憶體 (見 `09-security.md` §2.5)，若改為多實例部署需改用共享儲存。

### ADR-10: 前端測試採 Angular 內建 Karma + Jasmine，不額外導入 Jest
- **決策背景**: Angular CLI 19 預設即為 Karma + Jasmine，`ng test` 不需額外設定即可編譯 Standalone 元件與範本。
- **解決方案**: 服務以 `jasmine.createSpyObj` 隔離依賴，`ApiClientService` 以 `HttpTestingController` 攔截真實 HTTP 請求驗證 Token 換發流程；元件以 `TestBed` 渲染範本測試互動；共用替身集中於 `src/testing/test-doubles.ts`。CI 以 ChromeHeadless 執行並檢查覆蓋率門檻。
- **取捨**: 測試在真實 Chrome 中執行，比 Jest (jsdom) 慢，但 DOM、`Blob`、`canvas` 行為與正式瀏覽器一致；真實瀏覽器跨頁流程另由 Playwright E2E 涵蓋 (ADR-11)。

### ADR-11: 瀏覽器端到端測試採 Playwright，並以容器建立一次性的完整環境
- **決策背景**: 單元與整合測試各自驗證前端或後端，但「前端送出的欄位後端是否真的接受」、「Cookie 是否真的被瀏覽器保存並在換發時夾帶」、「兩個分頁同時修改的實際畫面」只有真實瀏覽器連上真實後端才能驗證。註冊流程還需要收到驗證信。
- **解決方案**: Playwright `webServer` 依序啟動 `e2e/scripts/start-e2e-backend.sh` (MySQL 容器 + Mailpit 收信容器 + 後端 JAR) 與 `ng serve`；測試透過 Mailpit API 讀取真正寄出的驗證碼。所有連接埠與日常開發錯開，資料庫密碼與 JWT 金鑰每次隨機產生，結束後移除容器。
- **取捨**: 完整一輪約 1~2 分鐘 (含打包後端)，因此日常開發只跑單元測試，E2E 交給 CI 在單元測試通過後執行；測試共用同一個資料庫，故依序執行 (`workers: 1`)，每個測試自行建立需要的會員與問卷以避免互相影響。選 Playwright 而非 Cypress：原生支援多個瀏覽器內容 (模擬兩位使用者同時操作) 與 HTTP 請求 API (快速準備資料)。


### ADR-12: CI 由 GitHub Actions 負責測試，部署交給平臺的推送即部署
- **決策背景**: 部署可以由 GitHub Actions 在測試通過後呼叫平臺 API 執行，也可以直接使用 Render 與 Cloudflare Pages 內建的「推送即部署」。前者需要把平臺 API 金鑰存放在 GitHub。
- **解決方案**: `.github/workflows/ci.yml` 只執行測試 (後端單元與整合、前端型別與單元、E2E)；master 推送後 Render 依 Dockerfile、Pages 依 `pnpm run build` 各自自動部署。GitHub 不存放任何部署金鑰。
- **取捨**: 設定最少、沒有金鑰外流風險；代價是部署不等待測試結果、前後端各自上線、沒有自動冒煙測試。單人維護下以「推送前本機跑完測試、推送後看 Actions 結果、依 `10-deployment.md` §7.3 人工檢查」補足。若 Render 提供「等待 CI 檢查通過才部署」選項，可改用以降低風險。正式資料庫的結構升級刻意不自動化，於推送前人工執行。

### ADR-13: 錯誤回應採 RFC 9457 Problem Details，成功回應維持 `ApiResponse`
- **決策背景**: 錯誤若各自回傳不同形狀，前端得逐一猜欄位，錯誤也無法對應到伺服器日誌。過濾鏈 (未登入、權限不足、限流) 與 Controller 例外原本由不同地方產生回應，格式容易分歧。
- **解決方案**:
  - 所有錯誤一律由 `GlobalExceptionHandler` 回傳 `application/problem+json`：標準欄位 `type`、`title`、`status`、`detail`、`instance`；擴充欄位 `code`、`traceId`、`timestamp`。
  - Spring Security 之 entry point / accessDeniedHandler 與 `RateLimitFilter` 都透過 `HandlerExceptionResolver` 轉交同一個處理器。
  - 前端只讀 `detail` 顯示、只看 `code` 判斷流程。
- **取捨**: 成功回應仍使用 `ApiResponse` 封裝，與現有前端資料讀取一致；RFC 9457 只規範錯誤，兩者不衝突。`type` 使用不可解析之 URN (RFC 允許)，不另架設錯誤說明網頁。

### ADR-14: 稽核日誌以 HMAC 雜湊鏈防竄改，存於同一資料庫
- **決策背景**: 稽核紀錄若可被任意修改就失去證明力。一般的資料表誰有資料庫權限都能改；單純 SHA-256 雜湊鏈則可被重新計算整條鏈而偽造。
- **解決方案**:
  - 每筆 `hash = HMAC-SHA256(AUDIT_HMAC_SECRET, 前一筆 hash + 欄位)`，以鏈頂端單列 `FOR UPDATE` 序列化寫入。
  - 業務 Service 以 Spring 事件發布，`@TransactionalEventListener` 於交易提交後寫入；失敗事件則於交易結束後必寫。
  - 管理員可隨時驗證整條鏈；每筆同時寫入結構化日誌作為資料庫外副本。
- **取捨**:
  - 不使用外部 WORM 儲存或區塊鏈服務：成本與複雜度不符作品規模。持有金鑰且能同時改資料庫與日誌的人，理論上仍能偽造；金鑰與資料庫權限分開保管即可大幅降低風險。
  - 寫入稽核失敗時只記錄 ERROR 而不讓業務操作失敗：可用性優先，並以日誌告警補足。

### ADR-15: 結構化 JSON 日誌採 Spring Boot 內建格式，不另引入 Logstash Encoder
- **決策背景**: 雲端平臺 (Render Logs) 以文字搜尋為主，純文字日誌無法以欄位過濾；同一請求的多筆日誌也串不起來。
- **解決方案**: 使用 Spring Boot 內建 `logging.structured.format.console=logstash`，以設定重新命名 `timestamp`、加上 `service` 欄位；`RequestTraceFilter` 將 `traceId`、`clientIp` 寫入 MDC，自動成為 JSON 欄位，並以 `X-Trace-Id` 回應標頭讓前端錯誤訊息附上追蹤碼。
- **取捨**: 內建格式免額外相依套件且由 Spring Boot 維護；本機開發時主控臺也是 JSON，可讀性略降，但與正式環境一致，除錯時看到的就是線上會看到的格式。

### ADR-16: 速率限制以 Servlet 過濾器 + 記憶體固定時間窗實作，不引入 Bucket4j 或 Redis
- **決策背景**: 登入已有「以帳號為鍵」的失敗鎖定，但同一 IP 仍可大量註冊灌信、換帳號猜密碼或灌票送出問卷。
- **解決方案**:
  - `RateLimitFilter` 依端點等級 (登入註冊類 / 送出問卷 / 其他) 與用戶端 IP 計數，超量回 429 與 `Retry-After`。
  - 掛在 Security 過濾鏈 `CorsFilter` 之後，429 也讀得到原因。
  - 用戶端 IP 由 `X-Forwarded-For` 右側依可信任代理層數取得，避免被偽造。
- **取捨**: 單一 Render 實例下記憶體計數最簡單；重啟只會提早解除限制，不影響正確性。固定時間窗在窗邊界可能短暫放行至多兩倍上限，對本專案之防濫用目的可接受；若改為多實例部署，須換成共享計數。

