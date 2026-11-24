# 05. 核心業務流程與狀態機圖表 (Business Flowcharts & State Machines)

> **圖表風格**: Sci-Fi HUD 霓虹科技風 (`flowchart LR`、純白無背景連線文字)  

---

## 1. 問卷設計發布生命週期流程 (Survey Design & Publishing)

```mermaid
%%{init: {'theme': 'dark', 'themeVariables': { 'darkMode': true, 'background': '#030712', 'primaryColor': '#0b0f19', 'primaryTextColor': '#f8fafc', 'primaryBorderColor': '#00f0ff', 'lineColor': '#00f0ff', 'textColor': '#f8fafc', 'edgeLabelBackground': 'transparent' }, 'themeCSS': '.edgeLabel, .edgeLabel rect, .edgeLabel span, .labelBkg { background: transparent !important; background-color: transparent !important; fill: transparent !important; border: none !important; } .edgeLabel, .edgeLabel span { color: #ffffff !important; fill: #ffffff !important; font-weight: 500 !important; } rect, .node rect, polygon { rx: 0px !important; ry: 0px !important; }', 'flowchart': { 'curve': 'linear' } }}%%
flowchart LR
    classDef hudCard fill:#0b0f19,stroke:#00f0ff,stroke-width:1.5px,color:#f8fafc,rx:0px,ry:0px;

    Start(["進入設計工作臺"]):::hudCard
    ConfigMeta["設定基本資訊<br/>(標題/分類/時限)"]:::hudCard
    AddQuestions["編排題型與選項<br/>(單選/多選/量表)"]:::hudCard
    SetUnlockLogic["配置條件跳題<br/>(UID 連動解鎖)"]:::hudCard
    ValidateForm{"表單完整性核驗"}:::hudCard
    ConfirmDialog["彈出二次確認視窗"]:::hudCard
    PublishSuccess(["發布成功至大廳"]):::hudCard

    Start --> ConfigMeta
    ConfigMeta --> AddQuestions
    AddQuestions --> SetUnlockLogic
    SetUnlockLogic --> ValidateForm
    ValidateForm -->|"有缺漏項目"| AddQuestions
    ValidateForm -->|"通過"| ConfirmDialog
    ConfirmDialog -->|"確認發布"| PublishSuccess
```

---

## 2. 受訪者填答與動態條件跳題流程 (Survey Runner & Dynamic Branching)

進入方式有兩種：由大廳點選 (`?surveyId=`) 或專屬填答連結 (`?share=<分享代碼>`)。未公開於大廳之問卷只能走分享連結，存取規則見 `09-security.md` §3.1。

```mermaid
%%{init: {
  'theme': 'dark',
  'themeVariables': {
    'darkMode': true,
    'background': '#030712',
    'primaryColor': '#0b0f19',
    'primaryTextColor': '#f8fafc',
    'primaryBorderColor': '#00f0ff',
    'lineColor': '#00f0ff',
    'textColor': '#f8fafc',
    'edgeLabelBackground': '#0b0f19',
    'tertiaryColor': '#0b0f19'
  },
  'themeCSS': '.edgeLabel, .edgeLabel rect, .edgeLabel span, .labelBkg { background: #0b0f19 !important; background-color: #0b0f19 !important; fill: #0b0f19 !important; border: 1px solid #1e293b !important; } .edgeLabel, .edgeLabel span { color: #38bdf8 !important; fill: #38bdf8 !important; font-weight: 600 !important; font-size: 11px !important; } .cluster rect { stroke: #00f0ff !important; stroke-width: 1.5px !important; stroke-dasharray: 4 2 !important; fill: #060a14 !important; rx: 4px !important; ry: 4px !important; } .cluster text, .cluster span.nodeLabel { fill: #00f0ff !important; color: #00f0ff !important; font-weight: 700 !important; font-size: 13px !important; } rect, .node rect, rect.actor { rx: 2px !important; ry: 2px !important; }',
  'flowchart': { 'curve': 'linear' }
}}%%
flowchart LR
    classDef hudCard fill:#0b0f19,stroke:#00f0ff,stroke-width:1.5px,color:#f8fafc,rx:2px,ry:2px;

    EnterRunner["開啟 /survey-runner"]:::hudCard
    ByShare["GET /api/public/surveys/shared/{代碼}"]:::hudCard
    ById["GET /api/public/surveys/{id}"]:::hudCard
    CheckOpen{"可存取且開放中？"}:::hudCard
    Unavailable["顯示不可填答原因<br/>(不存在 / 已關閉 / 未開始 / 已截止)"]:::hudCard
    FillBasicInfo["填寫背景資料"]:::hudCard
    AnswerQuestions["回答主體題目"]:::hudCard
    TriggerCondition{"選項命中解鎖條件？"}:::hudCard
    UnlockBranch["即時顯示隱藏題目"]:::hudCard
    KeepLocked["維持隱藏且免填"]:::hudCard
    CheckRequired{"必填完整性檢查"}:::hudCard
    SubmitFinish["POST /api/public/submissions<br/>(分享連結進入者帶 shareToken)"]:::hudCard
    ToLobby["提交成功並導向大廳"]:::hudCard

    EnterRunner -->|"?share="| ByShare
    EnterRunner -->|"?surveyId="| ById
    ByShare --> CheckOpen
    ById --> CheckOpen
    CheckOpen -->|"否"| Unavailable
    CheckOpen -->|"是"| FillBasicInfo
    FillBasicInfo --> AnswerQuestions
    AnswerQuestions --> TriggerCondition
    TriggerCondition -->|"命中"| UnlockBranch
    TriggerCondition -->|"未命中"| KeepLocked
    UnlockBranch --> CheckRequired
    KeepLocked --> CheckRequired
    CheckRequired -->|"缺漏"| AnswerQuestions
    CheckRequired -->|"通過"| SubmitFinish
    SubmitFinish --> ToLobby
```

---

## 3. 路由跳轉未儲存防呆守衛時序 (CanDeactivate Guard Sequence)

```mermaid
%%{init: {'theme': 'dark', 'themeVariables': { 'darkMode': true, 'background': '#030712', 'primaryColor': '#0b0f19', 'primaryTextColor': '#f8fafc', 'primaryBorderColor': '#00f0ff', 'lineColor': '#00f0ff', 'textColor': '#f8fafc' }, 'themeCSS': 'rect, .node rect, rect.actor { rx: 0px !important; ry: 0px !important; } .actor { font-weight: 600 !important; }' }}%%
sequenceDiagram
    autonumber
    actor User as 使用者
    participant Router as Angular Router
    participant Guard as canDeactivateGuard
    participant Page as 設計/個人資料頁面
    participant Dialog as DialogService

    User->>Router: 點擊導航連結 (如切換至大廳)
    Router->>Guard: 觸發路由離開守衛檢查
    Guard->>Page: 呼叫 canDeactivate()
    alt 表單處於 dirty 且未正式提交
        Page->>Dialog: showDialog(確認跳出彈窗)
        Dialog-->>User: 顯示「尚未儲存將遺失」確認窗
        alt 使用者點選「返回」或按 ESC
            Dialog-->>Guard: 回傳 false
            Guard-->>Router: 阻斷跳轉，維持原地狀態
        else 使用者點選「是」
            Dialog-->>Guard: 回傳 true
            Guard-->>Router: 放行跳轉，執行頁面卸載
        end
    else 表單乾淨或已正常提交
        Page-->>Guard: 回傳 true
        Guard-->>Router: 放行導航至目標路由
    end
```

---

## 4. Access Token 過期自動換發與重放偵測時序 (Token Refresh Rotation)

Access Token 15 分鐘、Refresh Token 7 天；黑名單與整批作廢存於資料庫，後端重啟不遺失 (細節見 `09-security.md` §2.2、§2.3)。

```mermaid
%%{init: {'theme': 'dark', 'themeVariables': { 'darkMode': true, 'background': '#030712', 'primaryColor': '#0b0f19', 'primaryTextColor': '#f8fafc', 'primaryBorderColor': '#00f0ff', 'lineColor': '#00f0ff', 'textColor': '#f8fafc' }, 'themeCSS': 'rect, .node rect, rect.actor { rx: 2px !important; ry: 2px !important; } .actor { font-weight: 600 !important; }' }}%%
sequenceDiagram
    autonumber
    participant Page as 前端頁面
    participant Api as ApiClientService
    participant Filter as JwtAuthenticationFilter
    participant Auth as AuthService
    participant Revoke as TokenRevocationService
    participant DB as revoked_tokens / users

    Page->>Api: 呼叫業務 API
    Api->>Filter: Authorization: Bearer (已過期的 Access Token)
    Filter-->>Api: 401
    Api->>Auth: POST /api/public/auth/refresh (瀏覽器自動夾帶 refreshToken Cookie)
    Auth->>Revoke: rotateRefreshToken(舊 jti)
    Revoke->>DB: 簽發時間早於 tokens_revoked_before？
    Revoke->>DB: INSERT IGNORE 舊 jti
    alt 寫入 1 筆 (第一次使用)
        Revoke-->>Auth: 輪替成功
        Auth-->>Api: 新 Access Token (Body) + 新 Refresh Token (Set-Cookie)
        Api->>Filter: 以新 Access Token 重送原請求
        Filter-->>Page: 200 回應
    else 寫入 0 筆 (同一顆 Token 第二次使用 = 重放)
        Revoke->>DB: UPDATE users.tokens_revoked_before = 現在
        Revoke-->>Auth: 拒絕
        Auth-->>Api: 401 (整批作廢不回滾)
        Api-->>Page: 清除登入狀態，回到訪客
    end
```

---

## 5. 請求處理管線與稽核寫入時序 (Request Pipeline & Audit Trail)

以「管理員凍結會員」為例，說明一個請求經過的元件、錯誤回應產生的位置，以及稽核紀錄何時寫入 (細節見 `09-security.md` §5~§7)。

```mermaid
%%{init: {'theme': 'dark', 'themeVariables': { 'darkMode': true, 'background': '#030712', 'primaryColor': '#0b0f19', 'primaryTextColor': '#f8fafc', 'primaryBorderColor': '#00f0ff', 'lineColor': '#00f0ff', 'textColor': '#f8fafc' }, 'themeCSS': 'rect, .node rect, rect.actor { rx: 2px !important; ry: 2px !important; } .actor { font-weight: 600 !important; }' }}%%
sequenceDiagram
    autonumber
    participant Browser as 前端 (管理員)
    participant Trace as RequestTraceFilter
    participant Limit as RateLimitFilter
    participant Jwt as JwtAuthenticationFilter
    participant Svc as AdminService / UserService
    participant Audit as AuditLogService
    participant DB as MySQL / TiDB

    Browser->>Trace: PATCH /api/admin/users/3/status
    Trace->>Trace: 產生 traceId、解析 clientIp 寫入 MDC
    Trace->>Limit: 繼續
    alt 同一 IP 超過上限
        Limit-->>Browser: 429 Problem Details + Retry-After
    else 未超量
        Limit->>Jwt: 繼續
        Jwt->>DB: 黑名單 / 整批作廢比對、載入帳號狀態
        Jwt->>Svc: 已驗證之管理員
        Svc->>DB: 更新 users.status (業務交易)
        Svc->>Svc: publishEvent(USER_STATUS_CHANGED)
        Svc-->>DB: 提交業務交易
        Note over Audit: AFTER_COMMIT 才觸發；業務回滾則不寫
        Audit->>DB: 新交易：鎖定 audit_chain_head → 計算 HMAC → 新增紀錄 → 推進鏈頂端
        Svc-->>Browser: 200 ApiResponse (標頭 X-Trace-Id)
    end
```

