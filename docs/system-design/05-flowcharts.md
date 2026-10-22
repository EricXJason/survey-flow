# 05. 核心業務流程與狀態機圖表 (Business Flowcharts & State Machines)

> **圖表風格**: Tech HUD 深黑風格 (直線直角、無灰塊)  
> **更新日期**: 2026-10-22  

---

## 1. 問卷設計發布生命週期流程 (Survey Design & Publishing)

```mermaid
%%{init: {'theme': 'dark', 'themeVariables': { 'darkMode': true, 'background': '#030712', 'primaryColor': '#0b0f19', 'primaryTextColor': '#f8fafc', 'primaryBorderColor': '#00f0ff', 'lineColor': '#00f0ff', 'textColor': '#f8fafc' }, 'flowchart': { 'curve': 'linear' } }}%%
flowchart LR
    classDef hudCard fill:#0b0f19,stroke:#00f0ff,stroke-width:1.5px,color:#f8fafc,rx:0px,ry:0px;

    Start(["進入設計工作台"]):::hudCard
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

```mermaid
%%{init: {'theme': 'dark', 'themeVariables': { 'darkMode': true, 'background': '#030712', 'primaryColor': '#0b0f19', 'primaryTextColor': '#f8fafc', 'primaryBorderColor': '#00f0ff', 'lineColor': '#00f0ff', 'textColor': '#f8fafc' }, 'flowchart': { 'curve': 'linear' } }}%%
flowchart LR
    classDef hudCard fill:#0b0f19,stroke:#00f0ff,stroke-width:1.5px,color:#f8fafc,rx:0px,ry:0px;

    EnterRunner(["載入問卷填答頁面"]):::hudCard
    FillBasicInfo["填寫背景資料<br/>(身分證/手機號碼)"]:::hudCard
    AnswerQuestions["回答主體題目"]:::hudCard
    TriggerCondition{"單選是否命中解鎖選項"}:::hudCard
    UnlockBranch["即時解鎖隱藏題目"]:::hudCard
    KeepLocked["維持鎖定並跳過"]:::hudCard
    CheckRequired{"必填完整性檢查"}:::hudCard
    SubmitFinish(["提交完成並導向首頁"]):::hudCard

    EnterRunner --> FillBasicInfo
    FillBasicInfo --> AnswerQuestions
    AnswerQuestions --> TriggerCondition
    TriggerCondition -->|"命中"| UnlockBranch
    TriggerCondition -->|"未命中"| KeepLocked
    UnlockBranch --> CheckRequired
    KeepLocked --> CheckRequired
    CheckRequired -->|"通過"| SubmitFinish
```

---

## 3. 路由跳轉未儲存防呆守衛時序 (CanDeactivate Guard Sequence)

```mermaid
%%{init: {'theme': 'dark', 'themeVariables': { 'darkMode': true, 'background': '#030712', 'primaryColor': '#0b0f19', 'primaryTextColor': '#f8fafc', 'primaryBorderColor': '#00f0ff', 'lineColor': '#00f0ff', 'textColor': '#f8fafc' }, 'themeCSS': 'rect, .node rect, rect.actor { rx: 0px !important; ry: 0px !important; }' }}%%
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
