# AGENTS-GIT.md | 全域 AI Agent Git 版本控制協定

> **專案作者**: 許哲誠 (HSU, CHE-CHENG)  
> **更新日期**: 2026-09-30  
> **協定定位**: 全域 AI Agent 執行版本控制之唯一法典。規範預設無 Git 邊界、全 CLI 純指令執行、GitHub MCP 同名存放庫建立、三層沙盒拓撲、防撞 Rebase 與發布防禦。

---

## 0. 版控專屬指令矩陣 (精確關鍵字匹配，全純 Git CLI 執行)

所有版控行為**一律強制使用終端機 Git CLI 指令**執行，嚴禁呼叫黑箱外掛：

| 指令名稱 | 性質與防護等級 | 執行機制摘要 |
| :--- | :--- | :--- |
| **`初始化 git`** | 遠端連線與拓撲建置 | 終端檢查 SSH ➔ MCP 建立同名存放庫 (或 SSH 降級引導) ➔ CLI 建立三層分支並推送基準 ➔ 切入 `feature` 沙盒待命 |
| **`切回 feature`** | ⚡ 極速執行 (免確認) | 本地工作區自動 CLI Commit ➔ CLI 切換至 `feature` 沙盒分支 |
| **`切回 dev`** | ⚡ 極速執行 (免確認) | 本地工作區自動 CLI Commit ➔ CLI 切換至 `development` 分支檢視 |
| **`切回 master`** | ⚡ 極速執行 (免確認) | 本地工作區自動 CLI Commit ➔ CLI 切換至 `master` 分支檢視 |
| **`push到feature`** | ⚡ 極速執行 (防撞保護) | 本地原子 CLI Commit ➔ 遠端存在則 `pull --rebase`，否則直接推送 ➔ `git push origin feature` |
| **`push到dev`** | ⚠️ 單行批次確認 | 單行確認 ➔ CLI Squash 合併至 `development` ➔ 推送遠端 ➔ CLI 重置沙盒對齊 dev |
| **`push到master`** | ⚠️ 單行批次確認 | 二次確認 ➔ 繁中對齊 `README.md` ➔ CLI 同步 `development` ➔ 合併 `master` ➔ 推送遠端 ➔ CLI 重置沙盒對齊 master |
| **`倒回上一個 commit`**| 🛑 高危強制攔截 | CLI 檢驗歷史邊界（若相對於 master 起點無提交則阻斷）➔ 執行 `git reset --hard HEAD~1 && git clean -fd` |
| **`git 重置化`** | 🛑 高危強制攔截 | 二次強制確認 ➔ 移除 `.git` ➔ CLI 重建乾淨三層分支基底 |

---

## 1. 預設無 Git 與「初始化 git」標準 SOP

### 1.1 預設無 Git 邊界
專案在未收到人類明確指令「`初始化 git`」前，**嚴禁執行 `git init` 或建立任何版控檔案**。

### 1.2 初始化 git 執行步驟 (SSH 驗證、MCP 同名建庫與 CLI 分支拓撲)
收到指令：「**`初始化 git`**」時，強制依序執行：

1. **SSH 通道檢驗 (CLI 指令)**:
   - 終端執行：`ssh -T git@github.com`
   - 若回傳包含 `successfully authenticated`，代表連線正常。
   - 若連線失敗，立即以繁體中文提示生成 SSH Key 並於 GitHub Settings 設定，確認連線通過後才進入下一步。
2. **存放庫建立與綁定 (GitHub MCP 優先建立同名庫)**:
   - **優先嘗試 GitHub MCP 通道**:
     - 檢查當前 Agent 環境是否具備 GitHub MCP 工具。
     - 若具備，**強制以當前專案根目錄「資料夾名稱」為 Repository Name**，調用 MCP 建立遠端同名存放庫，並取得 SSH Remote URL（`git@github.com:<owner>/<folder-name>.git`）。建立時必須確保遠端為完全空白庫（無初始 README/License）。
     - 若環境支援 MCP 但尚未授權 Token，**優先以繁體中文引導使用者配置 GitHub MCP 連線**以啟用全自動建立。
   - **第二順位：平滑降級為手動 SSH 綁定**:
     - 若無 MCP 工具或呼叫失敗，立即降級詢問使用者已手動建好之 GitHub 存放庫 SSH URL（**強制 SSH 格式，嚴禁 HTTPS**）。
3. **三層分支拓撲建置與上游鎖定 (全純 Git CLI 指令)**:
   ```bash
   git init
   git checkout -b master
   git add .
   git commit -m "chore: initial baseline commit"
   git remote add origin <SSH-URL>
   git push -u origin master
   git checkout -b development
   git push -u origin development
   git checkout -b feature
   git push -u origin feature
   ```
4. **完成回報**: 使用繁體中文回報三層分支建立與遠端綁定成功，當前於 `feature` 沙盒待命。

---

## 2. 三層分支日常流動與 AI 自動 Commit 機制

* **`master` 分支**: 生產環境穩定分支。嚴禁日常直接 Commit。
* **`development` 分支**: 整合測試主幹。由發布指令將 `feature` 壓平匯入。
* **`feature` 分支**: 日常開發沙盒。
* **⚠️ AI 回應前強制原子 Commit 守衛 (Mandatory In-Sandbox Commit Guard)**:
  - **前置防禦守衛**: **若專案尚未執行 `初始化 git`（本地無 `.git` 目錄），嚴禁執行任何 Git 指令。**
  - **絕對執行時機**: 專案已完成 Git 初始化且當前處於 `feature` 分支時，**Agent 每次完成任何程式碼生成、修改、文檔任務後，在向人類輸出最終回覆文字前，強制必須自動於背景執行 CLI 原子提交**：
    ```bash
    git add .
    git commit -m "<type>(<scope>): <short imperative description in pure English>"
    ```
  - **Commit 訊息語言強制純英文規範 (Pure English Only)**:
    - **所有 `git commit -m "..."` 的提交訊息，一律強制 100% 僅能使用英文撰寫**。
    - 嚴格遵守業界 Conventional Commits 標準格式（例如 `feat: ...`, `fix: ...`, `refactor: ...`, `docs: ...`, `chore: ...`）。
    - **嚴禁在 Git Commit 訊息中出現任何中文字元**，維持國際化乾淨版控歷程。
  - **工作區零殘留底線**: 嚴禁在結束對話或回報時遺留未提交變更（Uncommitted changes）於本地工作區交差；工作區必須永遠維持乾淨已提交狀態，進度 100% 入庫。

---

## 3. 核心指令執行詳細 SOP (全純 Git CLI 指令)

### 3.1 push到feature (日常備份與防撞保護)

1. 確保沙盒當前代碼已完成原子提交且工作區乾淨。
2. **防撞保護與推送**:
   ```bash
   # 若遠端已存在 feature 分支則先防撞 rebase，否則直接鎖定上游推送
   git pull --rebase origin feature 2>/dev/null || true
   git push -u origin feature
   ```
   若發生語意衝突立即執行 `git rebase --abort` 並以臺灣繁體中文回報衝突清單由人類裁決。

### 3.2 倒回上一個 commit (邊界阻斷保護)

1. 查詢相對於 `master` 基準點之總提交數：
   ```bash
   git rev-list --count master..HEAD
   ```
2. **阻斷機制**: 若數值為 `0`（代表已達 master 起點，無本地提交），強制終止並使用繁體中文回報：「⚠️ 阻斷提示：當前分支已達 master 基準點，前方無可撤回之獨立提交，操作自動攔截。」
3. **安全抹殺**: 數值 `>= 1` 時執行：
   ```bash
   git reset --hard HEAD~1
   git clean -fd
   ```

### 3.3 push到dev (Squash Merge 發布至測試環境)

1. 確保沙盒代碼全數完成原子提交。
2. 執行發布與沙盒重置指令序列：
   ```bash
   git checkout development
   git pull origin development
   git merge --squash feature
   ```
   - **衝突防護**: 若 `git merge --squash` 發生衝突，強制立即中斷並回滾：
     ```bash
     git merge --abort
     git checkout feature
     ```
     以臺灣繁體中文列出衝突檔案清單，嚴禁私自解決語意衝突。
   - **無衝突時完成整合**:
     ```bash
     git commit -m "feat: integrate updates into development"
     git push origin development
     git checkout feature
     git reset --hard development
     git push origin feature --force-with-lease
     ```

### 3.4 push到master (生產發布一條龍：自動串聯 dev 與 README 對齊)

當人類下達 `push到master` 時，AI 自動執行一條龍發布管線：

1. 單行確認發布授權：「即將同步 dev、更新繁中 README 並發布至 master，請確認？[Y/N]」。
2. **第一階段：自動串聯同步至 dev**:
   ```bash
   git checkout development
   git pull origin development
   git merge --squash feature
   ```
   若發生衝突強制執行 `git merge --abort && git checkout feature` 並通報人類；無衝突則繼續：
   ```bash
   git commit -m "feat: integrate updates into development before master release"
   git push origin development
   ```
3. **第二階段：繁體中文 README 逆向全量對齊**:
   - 依據 `AGENTS.md` 第 4.1 節規範，提取真實代碼與架構全量生成/更新根目錄純繁體中文 `README.md`。
   - 提交並推送文檔：
     ```bash
     git add README.md
     git commit -m "docs: update production readme with latest architecture"
     git push origin development
     ```
4. **第三階段：發布至生產 master 並重置沙盒**:
   ```bash
   git checkout master
   git pull origin master
   git merge development
   git push origin master
   git checkout feature
   git reset --hard master
   git push origin feature --force-with-lease
   ```

---

## 4. 提交訊息規範 (Conventional Commits)

Commit 訊息為全協定中**唯一強制 100% 使用純英文**之項目，格式：

```text
<type>(<scope>): <short imperative description>
```

* 允許類型：`feat`, `fix`, `refactor`, `perf`, `chore`, `docs`, `style`, `test`。

---

## 5. GitHub 服務 MCP 賦能建議

當人類使用者提及或要求對接遠端儲存庫管理、PR 審查、Issue 追蹤或 Release 發布時，若環境具備 GitHub MCP 支援，Agent **必須主動建議啟用 GitHub MCP 工具**，並說明其加速能力（例如：直接透過 API 建立同名空白遠端庫、自動提取遠端分支狀態、建立 Pull Request 與發布 Release 標籤，省去使用者手動開啟瀏覽器設定權限與複製網址的繁瑣步驟）。