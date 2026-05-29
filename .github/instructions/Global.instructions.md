---
name: instructions
description: Every conversation
---
## 回應語言偏好

所有回應必須使用繁體中文（正體中文）。絕對不可使用簡體中文。若遇到使用英文更好理解的情況下，才改以英文回應；回應優先順序如下：
1. 繁體中文（首選）
2. 英文（使用英文更好理解的情況，一般是某些不容易翻譯的詞彙如hash等等）

在任何情況下，不得使用簡體中文。請確保未來的所有建議、說明與程式碼註解回應都遵循此語言偏好。

## 程式設計中心思想
1. 程式碼應以可讀性為優先考量，即使程式簡短又可以運作甚至運作速度再快都沒有意義。
2. 考量現代化的做法，盡量使用最新的語法與方法，避免使用過時的語法與方法。
3. 考量實務上的做法，如果理論上很好但是實務上可能in the case不是最佳解的話也沒有意義。

## 程式碼修改影響範圍檢查

**核心原則**: 修改任何層級的程式碼時，必須檢查所有相依層級是否需要同步更新。

### 專案分層架構

```
Client (跨模組呼叫介面)
    ↓
Controller (HTTP 端點)
    ↓
Service (業務邏輯)
    ↓
Repository (資料存取)
    ↓
Entity (資料模型)
```

### 跨層相依性檢查清單

**修改 Service 層時**，必須檢查：
1. ✅ **Controller 層**: 方法簽名、參數傳遞、import 語句
2. ✅ **Client 層**: 若 Service 被其他模組呼叫，檢查 Client 介面與實作
3. ✅ **DTO 層**: 新增或修改的 DTO 是否在所有使用處都正確 import
4. ✅ **測試層**: 單元測試與整合測試是否需要更新

**修改 Controller 層時**，必須檢查：
1. ✅ **Service 層**: 方法簽名是否匹配
2. ✅ **DTO 層**: Request/Response DTO 是否正確使用與 import
3. ✅ **API 文件**: OpenAPI 註解是否需要更新

**修改 Client 層時**，必須檢查：
1. ✅ **Service 層**: 呼叫 Client 的 Service 是否需要更新
2. ✅ **DTO 層**: 跨模組傳遞的 DTO 是否正確 import
3. ✅ **RestClient 配置**: 是否需要更新 URL 或錯誤處理

**修改 Repository 層時**，必須檢查：
1. ✅ **Service 層**: 查詢方法的呼叫是否需要調整
2. ✅ **Entity 層**: 實體欄位變更是否影響查詢

**修改 Entity 層時**，必須檢查：
1. ✅ **Repository 層**: 自定義查詢是否需要更新
2. ✅ **DTO 層**: 映射邏輯是否需要調整
3. ✅ **資料庫遷移**: 是否需要新增 Flyway 腳本

**修改 DTO 層時**，必須檢查：
1. ✅ **Controller 層**: Request/Response 使用處
2. ✅ **Service 層**: DTO 轉換邏輯
3. ✅ **Client 層**: 跨模組呼叫的參數與回傳值

### Agent 執行流程

**核心原則**: 發現問題或多步驟任務時，**立即建立待辦清單**，避免因上下文限制而遺漏。

**工作流程**: 發現問題/任務 → 立即建立待辦清單 → 逐步完成 → 更新狀態 → 驗證完成

**何時建立待辦清單**:
- ✅ 發現編譯錯誤需要修復多個檔案
- ✅ 任務涉及 3 個以上步驟
- ✅ 需要跨層級修改程式碼
- ✅ 需要同步更新多個配置檔案
- ✅ 複雜的重構或功能開發

### 常見遺漏場景
- ❌ 修改 Service 方法參數，忘記更新 Controller 的 import
- ❌ 新增 DTO 類別，忘記在 Controller 或 Client 中 import
- ❌ 修改 Repository 查詢方法，忘記更新 Service 的呼叫
- ❌ 變更 Entity 欄位，忘記更新 Flyway 遷移腳本
- ❌ 修改 Service 介面，忘記更新對應的 Client 實作

### 預防措施
1. 修改前搜尋所有引用該類別的檔案
2. 修改後執行編譯確認無錯誤
3. 執行相關測試確認功能正常
4. Code Review 時特別注意跨層相依性

## 指令執行偏好
1. 容器管理工具：一律使用 podman 而非 docker。
2. 套件管理器：一律使用 pnpm。嚴禁使用 npm 或 yarn。
3. Shell 環境適配：執行指令前須先偵測當前 Shell（PowerShell 或 CMD），並使用對應語法：
   - PowerShell (`pwsh`)：指令串接用 `;`，執行當前目錄腳本用 `./script`（例如 `./gradlew`）
   - CMD (`cmd.exe`)：指令串接用 `&&`，執行當前目錄腳本直接用名稱（例如 `gradlew`，不加 `./`）
   - 不可假設預設 Shell 環境，須透過環境資訊判斷後再決定指令語法

## Git 工作流程規範

### 改動前的分支檢查流程

在進行任何程式碼改動前，必須遵循以下工作流程確保改動在正確的分支上進行：

#### 1. 檢查當前分支狀態

```bash
# 查看當前分支
git branch --show-current

# 查看工作區狀態
git status

# 列出所有本地分支
git branch -a
```

#### 2. 判斷改動類型並選擇分支策略

根據改動性質決定分支類型：

| 改動類型 | 分支前綴 | 說明 | 範例 |
|---------|---------|------|------|
| 新功能開發 | `feature/` | 新增業務功能或模組 | `feature/add-payment-module` |
| Bug 修復 | `bugfix/` | 修復非緊急的程式錯誤 | `bugfix/fix-order-calculation` |
| 緊急修復 | `hotfix/` | 修復生產環境的緊急問題 | `hotfix/security-patch` |
| 程式碼重構 | `refactor/` | 改善程式結構但不改變功能 | `refactor/optimize-query-performance` |
| 文件更新 | `docs/` | 僅更新文件內容 | `docs/update-api-guide` |
| 測試相關 | `test/` | 新增或修改測試案例 | `test/add-integration-tests` |
| 建置/工具 | `chore/` | 更新建置腳本、依賴版本等 | `chore/upgrade-spring-boot` |

#### 3. 分支選擇決策流程

**步驟 A: 檢查是否存在相關分支**

```bash
# PowerShell 環境
git branch -a | Select-String -Pattern "關鍵字"

# 範例：搜尋與訂單相關的分支（PowerShell）
git branch -a | Select-String -Pattern "order"

# Bash/Linux 環境
git branch -a | grep -i "關鍵字"
git branch -a | grep -i "order"
```

**步驟 B: 決策邏輯**

```
IF 存在相關且未合併的分支 THEN
    → 切換到該分支繼續開發
ELSE IF 當前在 main/master 分支 THEN
    → 必須建立新分支
ELSE IF 當前分支與改動類型不符 THEN
    → 建議建立新分支或切換到適當分支
ELSE
    → 可在當前分支繼續開發
END IF
```

#### 4. 分支操作指令

**情境 1: 切換到已存在的分支**

```bash
# 切換到本地分支
git checkout feature/add-payment-module

# 切換到遠端分支（首次）
git checkout -b feature/add-payment-module origin/feature/add-payment-module

# 更新分支到最新狀態
git pull origin feature/add-payment-module
```

**情境 2: 建立新分支**

**基底分支選擇策略**：
1. **優先選擇相關的功能分支作為基底**：如果新功能依賴於尚未合併的分支
2. **否則從 main 分支建立**：獨立功能或修復

```bash
# 策略 A: 從 main 建立新分支（獨立功能）
git checkout main
git pull origin main
git checkout -b feature/add-payment-module

# 策略 B: 從現有功能分支建立（依賴關係）
# 範例：新功能依賴於 feature/add-user-auth 的改動
git checkout feature/add-user-auth
git pull origin feature/add-user-auth
git checkout -b feature/add-payment-with-auth

# 策略 C: 從當前分支建立（延續當前工作）
git checkout -b feature/extend-current-work
```

**何時使用策略 B（從功能分支建立）**：
- 新功能需要使用尚未合併到 main 的程式碼
- 修復依賴於另一個未合併分支的 bug
- 在大型功能的子任務間建立依賴關係

**範例情境**：
```
main
 └─ feature/user-authentication (尚未合併)
     └─ feature/payment-with-auth (依賴 user-authentication)
```

**情境 3: 有未提交的改動需要切換分支**

```bash
# 方法 1: 暫存改動（推薦使用具名 stash）
git stash push -m "WIP: 描述當前工作"
git checkout target-branch
git stash list  # 查看 stash 列表
git stash pop   # 彈出最新的 stash
# 或指定特定的 stash
git stash apply stash@{0}

# 方法 2: 提交到臨時分支
git checkout -b temp/wip-backup
git add .
git commit -m "WIP: temporary backup"
git checkout target-branch
```

#### 5. 改動提交前的最終檢查

在提交前必須確認：

```bash
# 1. 確認當前分支正確
git branch --show-current

# 2. 檢查改動內容
git status
git diff

# 3. 確認沒有意外的檔案被追蹤
git ls-files --others --exclude-standard

# 4. 執行測試（依專案需求）
./gradlew test

# 5. 提交改動
git add <files>
git commit -m "type(scope): description"
```

#### 6. Agent 自動化建議流程

當 Agent 準備進行程式碼改動時，應自動執行以下檢查：

1. **偵測當前分支**: 執行 `git branch --show-current`
2. **分析改動性質**: 根據任務描述判斷改動類型（feature/bugfix/refactor 等）
3. **搜尋相關分支**: 執行 `git branch -a | grep -i "關鍵字"` 尋找相關分支
4. **提供建議**:
   - 若在 main 分支 → **必須**建議建立新分支
   - 若存在相關分支 → 建議切換到該分支
   - 若當前分支類型不符 → 建議建立新分支或切換分支
   - 若當前分支適合 → 確認後繼續
5. **等待使用者確認**: 詢問使用者是否同意建議的分支操作
6. **執行分支操作**: 獲得確認後執行 Git 指令
7. **進行程式碼改動**: 分支確認無誤後才開始修改程式碼

**範例對話流程**:

```
Agent: 偵測到您當前在 main 分支，準備進行新功能開發。
建議建立新分支: feature/add-payment-module

是否要我執行以下指令？
git checkout -b feature/add-payment-module

User: 是

Agent: [執行 git checkout -b feature/add-payment-module]
分支建立成功，現在開始進行程式碼改動...
```

### 分支命名規範

```
<類別>/<任務簡述>

類別前綴:
- feature/  : 新功能開發
- bugfix/   : Bug 修復
- hotfix/   : 緊急修復
- refactor/ : 程式碼重構
- docs/     : 文件更新
- test/     : 測試相關
- chore/    : 建置/工具更新

範例:
feature/add-payment-module
bugfix/fix-order-calculation
hotfix/security-patch
refactor/optimize-query-performance
docs/update-api-guide
test/add-integration-tests
chore/upgrade-spring-boot
```

**關鍵原則：** 全小寫、使用連字符分隔、使用 `/` 分層。

### Commit 訊息規範 (Conventional Commits)

```
<type>(<scope>): <subject>

type: feat, fix, docs, style, refactor, test, chore
scope: 可選，如 account, order, product
subject: 使用祈使句，如 "add" 而非 "added"

範例:
feat(order): add bulk order creation endpoint
fix(product): resolve stock deduction race condition
docs: update API documentation for account module
refactor(service): replace WebClient with RestClient
test(order): add integration tests for order creation
chore(deps): upgrade Spring Boot to 3.5.14
```

**關鍵原則：** 使用祈使句、標題與內容空一行、內容著重「為什麼」而非「怎麼做」。

### 分支合併與清理

**推薦方式：使用 Pull Request**

```bash
# 1. 推送分支到遠端
git push origin feature/add-payment-module

# 2. 建立 Pull Request
# 方式 A: 使用 Bob Agent 的 /create-pr 指令（推薦）
#   - 自動切換到 Advanced 模式
#   - 生成 PR 描述
#   - 詢問目標分支
#   - 建立 Pull Request

# 方式 B: 使用 GitHub CLI
gh pr create --base main --head feature/add-payment-module --title "feat: add payment module" --body "詳細說明..."

# 3. 等待 Code Review 與 CI/CD 通過後，在 GitHub 上合併 PR

# 4. 合併後清理本地分支
git checkout main
git pull origin main
git branch -d feature/add-payment-module

# 5. 清理已刪除的遠端分支參考
git fetch --prune
**分支策略：短期分支原則**

**核心原則**：
- **所有功能分支都從 main 建立**，避免分支間依賴
- **盡快合併**，減少長期分支的維護成本
- **拆分大功能**為多個小 PR，每個獨立可測試

**實務做法**：

```
# ❌ 避免：建立依賴鏈
main
 └─ feature/user-auth
     └─ feature/payment (依賴 user-auth)

# ✅ 推薦：拆分獨立 PR
main
 ├─ feature/user-auth-api (先合併)
 ├─ feature/user-auth-ui (等 API 合併後建立)
 └─ feature/payment (等前面都合併後建立)
```

**如何處理功能依賴**：

1. **拆分功能**：將大功能分解為可獨立交付的小單元
2. **順序開發**：等前置功能合併後，再從最新的 main 建立新分支
3. **使用 Feature Flag**：未完成的功能用開關控制，允許提早合併

**範例：開發支付功能（依賴使用者認證）**

```bash
# 步驟 1: 開發並合併使用者認證 API
git checkout main
git pull origin main
git checkout -b feature/user-auth-api
# ... 開發 & 提交 ...
# 使用 /create-pr 合併到 main

# 步驟 2: 等 PR 合併後，開發使用者認證 UI
git checkout main
git pull origin main  # 取得最新的 user-auth-api
git checkout -b feature/user-auth-ui
# ... 開發 & 提交 ...
# 使用 /create-pr 合併到 main

# 步驟 3: 等 PR 合併後，開發支付功能
git checkout main
git pull origin main  # 取得完整的 user-auth
git checkout -b feature/payment
# ... 開發 & 提交 ...
# 使用 /create-pr 合併到 main
```

**特殊情況：必須並行開發時**

如果確實需要在功能分支上建立子分支（不推薦，但有時無法避免）：

```bash
# 從功能分支建立
git checkout feature/user-auth
git checkout -b feature/payment-on-auth

# 當 feature/user-auth 合併到 main 後
git checkout feature/payment-on-auth
git rebase main  # 將基底改為 main
git push origin feature/payment-on-auth --force-with-lease
# 使用 /create-pr 合併到 main
```

**Agent 偵測與建議**

當 Agent 偵測到使用者想從功能分支建立新分支時：

```
Agent: 偵測到您想從 feature/user-auth 建立新分支。

建議採用短期分支策略：
1. 將 feature/user-auth 拆分為更小的 PR 並先合併
2. 或等待 feature/user-auth 合併後，從 main 建立新分支

這樣可以：
- 避免複雜的合併順序
- 減少合併衝突
- 加快 Code Review 速度

是否要繼續從 feature/user-auth 建立？（不推薦）
```

```

**緊急情況：本地直接合併（不推薦）**

僅在緊急情況或個人專案使用：

```bash
git checkout main
git pull origin main
git merge --no-ff feature/add-payment-module
git push origin main
git branch -d feature/add-payment-module
```

**Agent 建議流程**

當改動完成準備合併時，Agent 應：
1. 確認所有改動已提交
2. **檢查是否從功能分支建立**：如果是，提醒考慮重新從 main 建立
3. 建議推送分支到遠端：`git push origin <branch-name>`
4. **優先建議使用者執行 `/create-pr` 指令**（Bob Agent）或使用 GitHub CLI
5. 提醒使用者等待 Code Review 與 CI/CD 檢查
6. 合併後才建議清理本地分支

**Agent 建議流程**

當改動完成準備合併時，Agent 應：
1. 確認所有改動已提交
2. 建議推送分支到遠端：`git push origin <branch-name>`
3. **優先建議使用者執行 `/create-pr` 指令**（Bob Agent）或使用 GitHub CLI
4. 提醒使用者等待 Code Review 與 CI/CD 檢查
5. 合併後才建議清理本地分支
```

## 文檔管理規範

1. **文檔放置位置**
   - 所有專案相關的文檔必須放在 `docs/` 目錄下
   - 包括但不限於：指南文件、說明文件、流程圖、計劃文件等
   - 根目錄只保留必要的配置文件（如 README.md、.gitignore 等）

2. **文檔命名規範**
   - 使用小寫字母和連字符（kebab-case）
   - 範例：`git-branch-push-plan.md`、`test-profile-configuration-guide.md`
   - 避免使用空格或特殊字元

3. **文檔類型分類**
   - 指南類：`*-guide.md`（如 `test-profile-configuration-guide.md`）
   - 計劃類：`*-plan.md`（如 `git-branch-push-plan.md`、`migration-plan.md`）
   - 說明類：`*-explanation.md`（如 `docker-compose-test-explanation.md`）
   - 流程圖：`*-workflow.md` 或 `*-diagram.md`
   - 快速參考：`*-quick-*.md`（如 `playwright-quick-fix.md`）

## Agent 文件維護規範

### 文件同步更新原則

1. **跨 Agent 文件同步**: 當對話過程中更新本檔案或其他 Agent 相關文件時，必須確保所有類型的 Agent 文件都同步更新，包括：
   - `.github/instructions/Global.instructions.md` (本檔案，GitHub Copilot 使用)
   - `AGENTS.md` (Cline、Bob、Gemini CLI 等通用 Agent 使用)
   - 其他專案特定的 Agent 配置檔案

2. **文件過時檢測**: 在對話開始前、進行中或結束後，若發現文件內容已經過時或不符合實際情況，應：
   - 立即標記過時的內容
   - 提出更新建議
   - 在獲得確認後同步更新所有相關 Agent 文件
   - 記錄更新日期與變更原因

3. **一致性驗證**: 定期檢查各 Agent 文件間的規範是否一致，特別是：
   - 語言偏好設定
   - 工具使用規範（如 podman、pnpm）
   - Git 命名與訊息規範
   - 文檔管理規範

4. **更新觸發時機**:
   - 專案架構或技術棧變更時
   - 開發規範或最佳實踐更新時
   - 發現文件與實際情況不符時
   - 新增或移除開發工具時