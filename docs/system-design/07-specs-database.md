# 07. 實體資料模型與資料庫架構 (Database ERD & Storage Models)

> **專案作者**: 許哲誠 (HSU, CHE-CHENG)  
> **持久化架構**: TiDB Cloud (Serverless) / MySQL 8.0 分散式關聯式資料庫 + Spring Data JPA  
> **更新日期**: 2026-10-22  
> **規格標準**: 100% 嚴格對齊後端 9 大實體資料表 (`users`, `verification_tokens`, `surveys`, `survey_basic_info_configs`, `survey_questions`, `survey_options`, `submissions`, `submission_answers`, `submission_basic_infos`)

---

## 1. 系統實體關聯圖 (Database ERD - Tech HUD Neon Style)

```mermaid
%%{init: {'theme': 'dark', 'themeVariables': { 'darkMode': true, 'background': '#030712', 'primaryColor': '#0b0f19', 'primaryTextColor': '#f8fafc', 'primaryBorderColor': '#00f0ff', 'lineColor': '#00f0ff', 'textColor': '#f8fafc', 'attributeBackgroundColorOdd': '#060a14', 'attributeBackgroundColorEven': '#0b0f19' }, 'themeCSS': '.er.relationshipLabelBox { fill: none !important; stroke: none !important; display: none !important; } rect, .node rect, rect.actor { rx: 0px !important; ry: 0px !important; }' }}%%
erDiagram
    USERS ||--o{ SURVEYS : "建立者與問卷"
    USERS ||--o{ VERIFICATION_TOKENS : "電子郵件驗證"
    SURVEYS ||--|| SURVEY_BASIC_INFO_CONFIGS : "基本資料收集設定"
    SURVEYS ||--o{ SURVEY_QUESTIONS : "問卷包含題目"
    SURVEY_QUESTIONS ||--o{ SURVEY_OPTIONS : "題目包含選項"
    SURVEYS ||--o{ SUBMISSIONS : "問卷填答紀錄"
    SUBMISSIONS ||--|| SUBMISSION_BASIC_INFOS : "受訪者填報輪廓"
    SUBMISSIONS ||--o{ SUBMISSION_ANSWERS : "填答作答明細"
    SURVEY_QUESTIONS ||--o{ SUBMISSION_ANSWERS : "關聯題目"
    SURVEY_OPTIONS ||--o{ SUBMISSION_ANSWERS : "關聯選項"

    USERS {
        bigint id PK "會員唯一流水號"
        varchar username UK "帳號名稱(50)"
        varchar email UK "電子郵件(100)"
        varchar password_hash "Argon2id密碼雜湊(255)"
        varchar full_name "真實姓名(50)"
        varchar phone "聯絡手機(20)"
        varchar identity_card "身分證字號(20)"
        varchar role "權限角色(ROLE_ADMIN/ROLE_USER/ROLE_GUEST)"
        varchar status "帳號狀態(ACTIVE/DISABLED)"
        datetime created_at "建立時間"
        datetime updated_at "最後更新時間"
        datetime deleted_at "軟刪除時間"
    }

    VERIFICATION_TOKENS {
        bigint id PK "驗證碼流水號"
        varchar email "驗證信箱(100)"
        varchar code "時效性驗證碼(10)"
        varchar token_type "驗證類型(REGISTER/FORGOT_PASSWORD)"
        datetime expires_at "過期時效"
        boolean is_used "是否已核銷使用"
        datetime created_at "生成時間"
    }

    SURVEYS {
        bigint id PK "問卷專案流水號"
        bigint user_id FK "發起人會員編號"
        varchar title "問卷標題(150)"
        text description "問卷前言說明"
        varchar category "12大主題分類(50)"
        boolean is_public_to_lobby "是否公開於問卷大廳"
        boolean is_open "是否開放填答中"
        longtext cover_image_url "封面圖片URL或Base64"
        datetime start_date "開放填答起始日期"
        datetime end_date "填答截止日期"
        int view_count "瀏覽閱覽次數"
        datetime created_at "建立時間"
        datetime updated_at "更新時間"
        datetime deleted_at "軟刪除時間"
    }

    SURVEY_BASIC_INFO_CONFIGS {
        bigint id PK "設定流水號"
        bigint survey_id UK_FK "關聯問卷編號"
        boolean collect_basic_info "是否啟用受訪者資料收集"
        boolean require_name "是否收集姓名"
        boolean require_gender "是否收集生理性別"
        boolean require_age "是否收集年齡層"
        boolean require_birthday "是否收集生日"
        boolean require_national_id "是否收集身分證字號"
        boolean require_phone "是否收集行動電話"
        boolean require_home_phone "是否收集市內電話"
        boolean require_email "是否收集電子信箱"
        boolean require_industry "是否收集產業類別"
        boolean require_job_title "是否收集職稱"
        boolean require_department "是否收集任職部門"
        boolean require_employee_id "是否收集員工編號"
        boolean require_academic_major "是否收集科系所"
        boolean require_class_name "是否收集班級名稱"
        boolean require_student_id "是否收集學號"
        boolean require_height "是否收集身高"
        boolean require_weight "是否收集體重"
        boolean require_residence "是否收集居住城市"
        boolean require_registered_address "是否收集戶籍地址"
    }

    SURVEY_QUESTIONS {
        bigint id PK "題目唯一流水號"
        bigint survey_id FK "所屬問卷編號"
        varchar question_code "題目標識代碼(Q1/UUID)"
        varchar type "題型(SINGLE/MULTI/BOOLEAN/RATING/TEXT/TIME/DATE)"
        varchar question_text "題目主體敘述(500)"
        longtext image_url "題目附加附圖"
        boolean is_required "是否為必填題目"
        int sort_order "題目排列順序"
        int min_rating "評分量表最小值(0或1)"
        int max_rating "評分量表最大值(5或10)"
        datetime created_at "建立時間"
        datetime updated_at "更新時間"
    }

    SURVEY_OPTIONS {
        bigint id PK "選項唯一流水號"
        bigint question_id FK "所屬題目編號"
        varchar option_text "選項文字內容(255)"
        int sort_order "選項排列順序"
        varchar trigger_question_uid "條件跳題解鎖目標題目代碼"
    }

    SUBMISSIONS {
        bigint id PK "填答主紀錄流水號"
        bigint survey_id FK "填答之問卷編號"
        varchar ip_address "受訪者IP位址(45)"
        int duration_seconds "作答耗時秒數"
        datetime submittedAt "提交作答時間"
    }

    SUBMISSION_BASIC_INFOS {
        bigint id PK "基本輪廓流水號"
        bigint submission_id UK_FK "關聯填答主紀錄"
        varchar name "填報姓名(50)"
        varchar gender "性別(10)"
        varchar age_group "年齡級距(30)"
        varchar birthday "生日(20)"
        varchar national_id "身分證號(20)"
        varchar phone "手機門號(20)"
        varchar home_phone "市話(30)"
        varchar email "電子郵件(100)"
        varchar industry "產業別(50)"
        varchar job_title "職稱(50)"
        varchar department "部門(50)"
        varchar employee_id "員工代號(50)"
        varchar academic_major "系所(50)"
        varchar class_name "班別(50)"
        varchar student_id "學號(50)"
        double height "身高cm"
        double weight "體重kg"
        varchar residence "居住縣市(50)"
        varchar registered_address "戶籍地址(200)"
    }

    SUBMISSION_ANSWERS {
        bigint id PK "作答明細流水號"
        bigint submission_id FK "所屬填答紀錄"
        bigint question_id FK "作答之題目編號"
        bigint option_id FK "選取之選項編號(單選/多選/是非)"
        text text_answer "簡答題內容或時間日期字串"
        int rating_value "量表評分分值(1至10)"
    }
```

---

## 2. TiDB / MySQL 8.0 實體資料表結構 (DDL) 規格書

### 2.1 會員帳號資料表 (`users`)
```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '會員唯一流水號',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '登入帳號名稱',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT '會員電子郵件',
    password_hash VARCHAR(255) NOT NULL COMMENT 'Argon2id 密碼雜湊值',
    full_name VARCHAR(50) NOT NULL COMMENT '真實姓名',
    phone VARCHAR(20) DEFAULT NULL COMMENT '聯絡行動電話',
    identity_card VARCHAR(20) DEFAULT NULL COMMENT '身分證字號',
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER' COMMENT '系統權限角色 (ROLE_ADMIN, ROLE_USER, ROLE_GUEST)',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '帳號狀態 (ACTIVE, DISABLED)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最後異動時間',
    deleted_at DATETIME DEFAULT NULL COMMENT '軟刪除時間戳'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系統會員核心資料表';
```

### 2.2 帳號時效性驗證碼表 (`verification_tokens`)
```sql
CREATE TABLE verification_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '驗證碼流水號',
    email VARCHAR(100) NOT NULL COMMENT '驗證目標電子郵件',
    code VARCHAR(10) NOT NULL COMMENT '6位數字或字母驗證碼',
    token_type VARCHAR(30) NOT NULL COMMENT '權杖業務類型 (REGISTER, FORGOT_PASSWORD)',
    expires_at DATETIME NOT NULL COMMENT '過期時限 (通常發出後15分鐘)',
    is_used BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已被使用核銷',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成時間',
    INDEX idx_vt_email_type (email, token_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='時效性 OTP 驗證碼記錄表';
```

### 2.3 問卷主檔表 (`surveys`)
```sql
CREATE TABLE surveys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '問卷唯一流水號',
    user_id BIGINT NOT NULL COMMENT '發起人會員 ID (關聯 users.id)',
    title VARCHAR(150) NOT NULL COMMENT '問卷標題',
    description TEXT DEFAULT NULL COMMENT '問卷前言介紹與作答說明',
    category VARCHAR(50) NOT NULL DEFAULT '綜合調查' COMMENT '問卷 12 大領域分類',
    is_public_to_lobby BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否公開呈現於大廳廣場',
    is_open BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否處於開放填答狀態',
    cover_image_url LONGTEXT DEFAULT NULL COMMENT '封面宣傳圖片 (Base64 或 CDN 連結)',
    start_date DATETIME DEFAULT NULL COMMENT '開放填答起始日期',
    end_date DATETIME DEFAULT NULL COMMENT '開放填答截止日期',
    view_count INT NOT NULL DEFAULT 0 COMMENT '問卷大廳點閱次數',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',
    deleted_at DATETIME DEFAULT NULL COMMENT '軟刪除時間',
    CONSTRAINT fk_surveys_creator FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='問卷專案主檔資料表';
```

### 2.4 問卷基本資料配置表 (`survey_basic_info_configs`)
```sql
CREATE TABLE survey_basic_info_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置記錄編號',
    survey_id BIGINT NOT NULL UNIQUE COMMENT '所屬問卷 ID (一對一強制唯一)',
    collect_basic_info BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否啟用受訪者資料收集',
    require_name BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否收集受訪者姓名',
    require_gender BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否收集生理性別',
    require_age BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否收集年齡層',
    require_birthday BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集生日',
    require_national_id BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集身分證字號',
    require_phone BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集行動電話',
    require_home_phone BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集市話',
    require_email BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集電子郵件',
    require_industry BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集所屬產業',
    require_job_title BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集職位稱謂',
    require_department BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集服務部門',
    require_employee_id BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集員工編號',
    require_academic_major BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集就讀科系',
    require_class_name BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集班級編號',
    require_student_id BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集學生學號',
    require_height BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集身高',
    require_weight BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集體重',
    require_residence BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集居住縣市',
    require_registered_address BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否收集戶籍地址',
    CONSTRAINT fk_basic_info_survey FOREIGN KEY (survey_id) REFERENCES surveys(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='問卷受訪者個人輪廓收集欄位配置表';
```

### 2.5 問卷題目表 (`survey_questions`)
```sql
CREATE TABLE survey_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '題目流水號',
    survey_id BIGINT NOT NULL COMMENT '所屬問卷 ID',
    question_code VARCHAR(50) DEFAULT NULL COMMENT '前端題號識別代碼 (如 Q1, Q2)',
    type VARCHAR(20) NOT NULL COMMENT '題目類型 (SINGLE, MULTI, BOOLEAN, RATING, TEXT, TIME, DATE)',
    question_text VARCHAR(500) NOT NULL COMMENT '題目主體敘述文字',
    image_url LONGTEXT DEFAULT NULL COMMENT '題目附加圖片',
    is_required BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否為必填題目',
    sort_order INT NOT NULL DEFAULT 1 COMMENT '題目呈現順序',
    min_rating INT DEFAULT 1 COMMENT '評分題最小值 (預設 1 或 0)',
    max_rating INT DEFAULT 5 COMMENT '評分題最大值 (預設 5 或 10)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新時間',
    CONSTRAINT fk_questions_survey FOREIGN KEY (survey_id) REFERENCES surveys(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='問卷題目清單資料表';
```

### 2.6 問卷選項表 (`survey_options`)
```sql
CREATE TABLE survey_options (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '選項唯一流水號',
    question_id BIGINT NOT NULL COMMENT '所屬題目 ID',
    option_text VARCHAR(255) NOT NULL COMMENT '選項文字標籤',
    sort_order INT NOT NULL DEFAULT 1 COMMENT '選項排列順序',
    trigger_question_uid VARCHAR(50) DEFAULT NULL COMMENT '條件跳題解鎖之目標題目 UID',
    CONSTRAINT fk_options_question FOREIGN KEY (question_id) REFERENCES survey_questions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='單選/多選/是非題目選項資料表';
```

### 2.7 填答紀錄主檔表 (`submissions`)
```sql
CREATE TABLE submissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '填答事件流水號',
    survey_id BIGINT NOT NULL COMMENT '作答之問卷 ID',
    ip_address VARCHAR(45) DEFAULT NULL COMMENT '受訪者 IPv4 / IPv6 位址',
    duration_seconds INT DEFAULT NULL COMMENT '填答作答總耗時 (秒)',
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交作答時間戳',
    CONSTRAINT fk_submissions_survey FOREIGN KEY (survey_id) REFERENCES surveys(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='問卷填答主檔紀錄表';
```

### 2.8 受訪者輪廓明細表 (`submission_basic_infos`)
```sql
CREATE TABLE submission_basic_infos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '受訪者基本資料流水號',
    submission_id BIGINT NOT NULL UNIQUE COMMENT '關聯之填答紀錄 ID (一對一唯一定位)',
    name VARCHAR(50) DEFAULT NULL COMMENT '填報姓名',
    gender VARCHAR(10) DEFAULT NULL COMMENT '生理性別',
    age_group VARCHAR(30) DEFAULT NULL COMMENT '年齡區間',
    birthday VARCHAR(20) DEFAULT NULL COMMENT '出生年月日',
    national_id VARCHAR(20) DEFAULT NULL COMMENT '身分證統一編號',
    phone VARCHAR(20) DEFAULT NULL COMMENT '聯絡電話',
    home_phone VARCHAR(30) DEFAULT NULL COMMENT '住家市話',
    email VARCHAR(100) DEFAULT NULL COMMENT '電子信箱',
    industry VARCHAR(50) DEFAULT NULL COMMENT '就業產業',
    job_title VARCHAR(50) DEFAULT NULL COMMENT '擔任職位',
    department VARCHAR(50) DEFAULT NULL COMMENT '所屬部門',
    employee_id VARCHAR(50) DEFAULT NULL COMMENT '公司工號',
    academic_major VARCHAR(50) DEFAULT NULL COMMENT '修讀系所',
    class_name VARCHAR(50) DEFAULT NULL COMMENT '所屬班級',
    student_id VARCHAR(50) DEFAULT NULL COMMENT '在校學號',
    height DOUBLE DEFAULT NULL COMMENT '身高數據',
    weight DOUBLE DEFAULT NULL COMMENT '體重數據',
    residence VARCHAR(50) DEFAULT NULL COMMENT '居住城市',
    registered_address VARCHAR(200) DEFAULT NULL COMMENT '戶籍完整地址',
    CONSTRAINT fk_sub_info_submission FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='受訪者個資輪廓真實填報明細表';
```

### 2.9 題目作答明細表 (`submission_answers`)
```sql
CREATE TABLE submission_answers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '單題作答流水號',
    submission_id BIGINT NOT NULL COMMENT '所屬填答紀錄 ID',
    question_id BIGINT NOT NULL COMMENT '作答之題目 ID',
    option_id BIGINT DEFAULT NULL COMMENT '選取之選項 ID (單選、多選、是非題填入)',
    text_answer TEXT DEFAULT NULL COMMENT '簡答題文字內容或日期/時間字串',
    rating_value INT DEFAULT NULL COMMENT '評分題數值 (1 至 10)',
    CONSTRAINT fk_answers_submission FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE,
    CONSTRAINT fk_answers_question FOREIGN KEY (question_id) REFERENCES survey_questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_answers_option FOREIGN KEY (option_id) REFERENCES survey_options(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='受訪者各題詳細作答答案表';
```

---

## 3. 雙軌儲存與快取策略說明

1. **持久化層 (TiDB Cloud / MySQL 8.0)**:
   - 全面落實關聯外鍵約束與 `ON DELETE CASCADE` 級聯防護。
   - 會員刪除其問卷時，該問卷底下的基本資料配置、題目、選項、填答主檔、答案明細與輪廓個資均透過外鍵約束一併級聯清理，杜絕資料庫孤兒數據產生。
2. **會話層 (SessionStorage - `SURVEY_USER_STATE_SESSION`)**:
   - 瀏覽器分頁關閉即銷毀，儲存當前使用者的 JWT Token、帳號角色與授權資訊，確保 F5 重新整理時認證狀態不遺失。
