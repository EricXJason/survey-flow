# SurveyFlow | 現代實證調研與學術結構化數據採集系統

> **專案作者**: 許哲誠 (HSU, CHE-CHENG)  
> **專案定位**: 專業級全端動態線上問卷調研與多維數據分析平台。支援 7 大型態動態題目編排、條件跳題動態解鎖、原生 DOM 雙向防誤判、即時統計圖表聚合、4 大專業格式匯出與三層身分授權治理。  
> **語言與執行環境版本**:
> - **前端 SPA**: Node.js 24.18.0 / Angular 19.0.0 / TypeScript 5.6.2 / Angular CDK 19.2.19 (Cloudflare Pages 託管，100% 純原生 SCSS/CSS，無外部樣式表)
> - **後端 Web API**: Java 21 / Spring Boot 4.1.1 / Spring Security / JJWT 0.12.6 / Argon2id (Render Docker 容器化)
> - **持久化資料庫**: TiDB Cloud Serverless (MySQL 8.0.36 協定相容分散式關聯式資料庫)

---

## 1. 架構設計思路 (Architecture Mindset)

SurveyFlow 採**前後端完全解耦之現代雲端雙軌架構**，前端與後端分別專注於極致的互動體驗與嚴謹的資料安全：

- **最少實體檔案原則 (Minimal Physical Files Principle under SOLID)**:
  - 專案在符合 SOLID 規範下，以最精簡的實體檔案數量達成架構責任。
  - 前端徹底移除多餘的 `environments/` 目錄與多餘攔截器，改以純原生主機名動態解析後端網址（本地開發自動連線 `http://localhost:8080`，雲端生產環境自動對接 Render 雲端後端）。
- **前端 100% 純原生 SCSS/CSS 樣式規範 (Strict No External Stylesheets)**:
  - 全站樣式 100% 自研採用純原生 SCSS/CSS 與 CSS 自訂變數，**嚴格禁止引用 Bootstrap、Tailwind CSS 等任何第三方外部樣式表**，確保零樣式污染與極致載入效能。
- **全域環境變數外部化與 OCP (Zero-Hardcoding via Environment Variables)**:
  - 系統所有連線字串、金鑰、發信帳號（Email / SMTP 密碼）、外部端點 100% 外部化為環境變數管理，落實開放封閉原則，代碼與設定檔中嚴格杜絕硬編碼。
- **單一真實資料源 (SSOT) 與分層隔離**:
  - 後端嚴格貫徹 `Controller ➔ Service ➔ Repository ➔ Entity` 三層架構單向調用，Controller 絕對嚴禁直接存取 Repository。
  - 前端基於 Angular Signals 反應式狀態流，搭配依賴注入 (DI) 與模組化 Service 負責資料檢索與防呆檢核。
- **零信任資安架構 (Zero-Trust Security)**:
  - 短效 15 分鐘 Access Token + 長效 7 天 Refresh Token 輪替 (RTR) 吊銷機制。
  - 密碼採用國際資安標準 **Argon2id** 雜湊演算法加密。
- **雲端生產雙軌部署**:
  - 前端部署於 **Cloudflare Pages** (`https://survey-flow.pages.dev`)，享有 Edge CDN 全球邊緣加速。
  - 後端以 Docker 容器化部署於 **Render Web Service** (`https://survey-flow-9gt6.onrender.com`)，配置 `/actuator/health` 探針維持常駐熱機，消除冷啟動延遲。
  - 持久化儲存於 **TiDB Cloud Serverless** 分散式資料庫，保證 ACID 交易隔離與外鍵約束。

---

## 2. 核心功能規格 (Specifications)

| 模組分類 | 核心功能特點 | 規格邊界與實作說明 |
| :--- | :--- | :--- |
| **視覺化問卷設計** | 7 大型態動態編排與條件跳題 | 支援是非、單選、多選、評分量表 (1-5/1-10/NPS)、簡答、時間、日期題型；支援拖曳排序、單選動態跳題 (選 A 開 B)、封面附圖上傳與即時擬真預覽。發布後鎖定結構以確保作答真實性。 |
| **問卷填答運行器** | 雙軌填答與原生 DOM 雙向防誤判 | 支援大廳公開或專屬直達連結填寫；全題型原生 DOM 狀態深度掃描同步，徹底根除誤判；條件跳題隱藏題自動豁免必填驗證；整合身分證 Modulo 10 與手機 10 碼防呆。 |
| **問卷專案管理** | 生命週期與開放時效管控 | 問卷列表關鍵字搜尋、預覽問卷、複製專屬直達連結、動態切換大廳公開狀態與連結填答狀態、設定有效起訖時限及專案軟刪除。 |
| **深度數據分析** | 多維聚合圖表與專業報表匯出 | 5 大營運 KPI 指標、近 30 日填答趨勢走勢圖、受訪者人口輪廓金字塔、題型深度聚合 (單複選百分比/最高票皇冠微徽章/評分量表情感分析)、真實最新回覆時間 (非虛假時間) 及 4 大專業格式 (CSV/SPSS/JSON/PDF) 數據匯出。 |
| **會員帳號認證** | 實名信箱啟用與密碼安全 | 真實 Gmail SMTP 寄送 6 碼註冊啟用信與 15 分鐘時效密碼重設信；個人資料維護 (身分證字號唯讀保護)；舊密碼驗證與 Argon2id 安全重設。 |
| **系統營運治理** | 管理員全站會員名冊治理 | 系統管理員 (`ROLE_ADMIN`) 專屬全站會員清單審查、會員狀態即時啟用/凍結、違規帳號安全刪除 (內建 admin 保護機制)。 |

---

## 3. 系統業務架構與資料流圖 (Mermaid Tech HUD)

```mermaid
%%{init: {'theme': 'dark', 'themeVariables': { 'darkMode': true, 'background': '#030712', 'primaryColor': '#0b0f19', 'primaryTextColor': '#f8fafc', 'primaryBorderColor': '#00f0ff', 'lineColor': '#00f0ff', 'edgeLabelBackground': 'transparent', 'fontFamily': 'system-ui, sans-serif' }, 'flowchart': { 'curve': 'linear' } }}%%
flowchart LR
    classDef hudCard fill:#0b0f19,stroke:#00f0ff,stroke-width:1.5px,color:#f8fafc,rx:0px,ry:0px;

    Client["使用者瀏覽器<br/>(Desktop / Mobile)"]:::hudCard
    FE["前端 SPA (Cloudflare Pages)<br/>(Angular 19 / 純原生 SCSS)"]:::hudCard
    BE["後端 API (Render Web Service)<br/>(Java 21 / Spring Boot 4.1.1)"]:::hudCard
    DB[("持久化 DB (TiDB Cloud)<br/>(MySQL 8.0 分散式關聯庫)")]:::hudCard
    SMTP["Gmail SMTP 服務<br/>(TLS 587 / OTP 驗證信)"]:::hudCard

    Client -->|"HTTPS / Edge CDN"| FE
    FE -->|"RESTful API / JSON"| BE
    BE -->|"TLS / JPA 交易"| DB
    BE -->|"JavaMailSender"| SMTP
```

---

## 4. 資料庫實體關聯圖 (Database ERD)

```mermaid
%%{init: {'theme': 'dark', 'themeVariables': { 'darkMode': true, 'background': '#030712', 'primaryColor': '#0b0f19', 'primaryTextColor': '#f8fafc', 'primaryBorderColor': '#00f0ff', 'lineColor': '#00f0ff', 'textColor': '#f8fafc', 'attributeBackgroundColorOdd': '#060a14', 'attributeBackgroundColorEven': '#0b0f19' }, 'themeCSS': '.er.relationshipLabelBox { fill: none !important; stroke: none !important; display: none !important; } rect, .node rect, rect.actor { rx: 0px !important; ry: 0px !important; }' }}%%
erDiagram
    users ||--o{ surveys : " "
    surveys ||--o{ survey_questions : " "
    survey_questions ||--o{ survey_options : " "
    surveys ||--o{ submissions : " "
    submissions ||--o{ submission_answers : " "
    users ||--o{ refresh_tokens : " "
    users ||--o{ verification_tokens : " "

    users {
        BIGINT id PK
        VARCHAR username UK
        VARCHAR email UK
        VARCHAR password_hash
        VARCHAR full_name
        VARCHAR role
        TINYINT is_active
        DATETIME created_at
    }

    surveys {
        BIGINT id PK
        BIGINT creator_id FK
        VARCHAR title
        VARCHAR category
        TINYINT is_public_to_lobby
        TINYINT is_open
        DATETIME start_date
        DATETIME end_date
    }

    survey_questions {
        BIGINT id PK
        BIGINT survey_id FK
        VARCHAR uid
        VARCHAR type
        VARCHAR title
        TINYINT is_required
        INT sort_order
    }

    survey_options {
        BIGINT id PK
        BIGINT question_id FK
        VARCHAR option_text
        VARCHAR trigger_question_uid
        INT sort_order
    }

    submissions {
        BIGINT id PK
        BIGINT survey_id FK
        VARCHAR respondent_name
        DATETIME submitted_at
    }

    submission_answers {
        BIGINT id PK
        BIGINT submission_id FK
        VARCHAR question_uid
        BIGINT option_id
        INT rating_value
        TEXT text_answer
    }
```

---

## 5. 檔案目錄結構拓撲

```text
survey-flow/
├── Dockerfile                         # 後端多階段 Alpine 容器映像檔建置規格
├── .dockerignore                      # Docker 排除建置資產清單
├── README.md                          # 專案生產發布全量規格書 (純臺灣繁體中文)
├── docs/                              # 專案文檔庫
│   ├── change-log.md                  # 【常駐】修訂歷程庫 (最新異動置頂)
│   ├── check-list.md                  # 【常駐】業務功能點收清單
│   └── system-design/                 # 系統架構規格書 (01-10 循序合理編號、分則明確)
│       ├── 01-overview.md             # 系統願景、核心定位與模組總覽
│       ├── 02-role-type.md            # 使用者身分型別與功能權限矩陣 (18項純符號)
│       ├── 03-tech-stack.md           # 技術選型矩陣與 ADR 架構決策
│       ├── 04-architecture.md         # 分層架構與檔案物理索引字典
│       ├── 05-flowcharts.md           # 核心業務流程圖與狀態機
│       ├── 06-specs-api.md            # RESTful API 通訊協定規格書
│       ├── 07-specs-database.md       # TiDB / MySQL 資料庫模型與 ERD
│       ├── 08-ui-ux-standards.md      # 前端 UI/UX 美學、純原生 SCSS/CSS 規範
│       ├── 09-security.md             # 全端資安防護、認證授權與資料脫敏
│       └── 10-deployment.md           # 雲端雙軌部署拓撲與維運規範書
├── frontend/                          # Angular 19 前端 SPA 專案 (純原生 SCSS)
│   ├── angular.json                   # Angular 建置配置 (無 fileReplacements)
│   ├── package.json                   # 前端依賴清單 (Node.js 24.18.0)
│   ├── tsconfig.json                  # TypeScript 嚴格編譯設定 (具繁中註解)
│   └── src/
│       ├── main.ts                    # 應用程式啟動引導 (無 Bootstrap 依賴)
│       ├── app/                       # 核心組件與服務模組
│       │   ├── @interfaces/           # 強型別資料契約 DTO
│       │   ├── @services/             # 單一職責業務服務 (ApiClient, Getter, Emitter...)
│       │   └── components/            # 獨立 Standalone 視圖組件 (Hero, Lobby, Design...)
│       ├── styles.scss                # 全域原生樣式
│       └── variables.scss             # 品牌色票與中性 Token
└── backend/                           # Java 21 / Spring Boot 4.1.1 後端專案
    ├── pom.xml                        # Maven 依賴中樞 (具繁中結構化註解)
    └── src/
        ├── main/
        │   ├── java/com/surveyflow/   # Clean Architecture 乾淨分層
        │   │   ├── controller/        # RESTful API 端點控制器 (6 個 Controller)
        │   │   ├── service/           # 核心商務邏輯層 (6 個 Service)
        │   │   ├── repository/        # 原生 SQL 資料存取 (5 個 Repository)
        │   │   ├── entity/            # JPA 實體映射 (9 個 Entity)
        │   │   ├── security/          # Argon2id + 雙 Token 認證過濾器
        │   │   └── dto/               # 嚴格分層對外資料傳輸物件
        │   └── resources/
        │       ├── application.properties # 系統組態 (100% 繁中註解、環境變數 OCP)
        │       └── database/          # schema.sql (DDL) 與 data.sql (DML)
        └── test/                      # 16/16 全套 JUnit 5 單元測試套件
```

---

## 6. 本機建置與快速啟動指南

### 前置需求
- **Node.js**: `24.x` (或相容之 LTS 版本)
- **Java JDK**: `21` (建議 Eclipse Temurin 21)
- **MySQL 資料庫**: `8.0+` 或可存取之 TiDB Cloud 執行個體

### 步驟 1: 後端 Spring Boot 啟動
```bash
cd backend
# 執行全套單元測試驗證 (16/16 PASS)
./mvnw test

# 啟動後端 Web API (預設監聽 http://localhost:8080)
./mvnw spring-boot:run
```

### 步驟 2: 前端 Angular 啟動
```bash
cd frontend
# 安裝依賴
npm install

# 啟動本機開發伺服器 (自動連線 http://localhost:8080，支援熱重載)
npm start
# 瀏覽器開啟 http://localhost:4200 即可進入 SurveyFlow
```

---

## 7. 雲端生產部署與環境變數設定指南

本專案部署於 **Cloudflare Pages** (前端) 與 **Render** (後端)，在 Render 儀表板的 **Environment Variables** 需填入以下環境變數以保證完整運作：

| 環境變數名稱 | 範例 / 設定說明 | 必要性 |
| :--- | :--- | :---: |
| `SPRING_DATASOURCE_URL` | TiDB Cloud 之 JDBC 連線字串 (含 TLS 參數) | **必填** |
| `SPRING_DATASOURCE_USERNAME` | TiDB Cloud 分配之使用者帳號 | **必填** |
| `SPRING_DATASOURCE_PASSWORD` | TiDB Cloud 資料庫密碼 | **必填** |
| `JWT_SECRET` | 至少 256 位元之安全密碼簽章字串 | **必填** |
| `CORS_ALLOWED_ORIGINS` | `https://survey-flow.pages.dev` | **必填** |
| `FRONTEND_BASE_URL` | `https://survey-flow.pages.dev` | **必填** |
| `MAIL_HOST` | `smtp.gmail.com` | 選填 (預設 gmail) |
| `MAIL_PORT` | `587` | 選填 (預設 587) |
| `MAIL_USERNAME` | 用於發送驗證信之 Gmail 信箱地址 | **必填 (若需發信)** |
| `MAIL_PASSWORD` | Google 帳戶之 16 碼「應用程式密碼」 | **必填 (若需發信)** |
| `MAIL_FROM` | 寄件人顯示信箱 (如 `no-reply@surveyflow.tw`) | 選填 |

---

## 8. 版權與開源宣告

Copyright © 2026 許哲誠 (HSU, CHE-CHENG). All Rights Reserved.
