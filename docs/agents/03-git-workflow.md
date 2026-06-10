## Git 工作流程

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
| Bug 修復 | `fix/` | 修復程式錯誤（通用） | `fix/order-creation-transient-entity-bug` |
| 緊急修復 | `hotfix/` | 修復生產環境的緊急問題 | `hotfix/security-patch` |
| 程式碼重構 | `refactor/` | 改善程式結構但不改變功能 | `refactor/improve-openapi-annotations` |
| 設定檔變更 | `config/` | 更新應用程式或基礎設施配置 | `config/update-resilience4j-config` |
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

1. **偵測當前分支**: 使用 `execute_command` 執行 `git branch --show-current`
2. **分析改動性質**: 根據任務描述判斷改動類型（feature/bugfix/refactor 等）
3. **搜尋相關分支**: 執行 `git branch -a | grep -i "關鍵字"` 尋找相關分支
4. **提供建議**:
   - 若在 main 分支 → **必須**建議建立新分支
   - 若存在相關分支 → 建議切換到該分支
   - 若當前分支類型不符 → 建議建立新分支或切換分支
   - 若當前分支適合 → 確認後繼續
5. **等待使用者確認**: 使用 `ask_followup_question` 詢問使用者是否同意建議的分支操作
6. **執行分支操作**: 獲得確認後使用 `execute_command` 執行 Git 指令
7. **進行程式碼改動**: 分支確認無誤後才開始使用 `apply_diff` 或 `write_to_file`

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
- fix/      : Bug 修復（通用）
- hotfix/   : 緊急修復（生產環境）
- refactor/ : 程式碼重構
- config/   : 設定檔變更
- docs/     : 文件更新
- test/     : 測試相關
- chore/    : 建置/工具更新

範例:
feature/add-payment-module
fix/order-creation-transient-entity-bug
hotfix/security-patch
refactor/improve-openapi-annotations
config/update-resilience4j-config
docs/update-api-guide
test/add-integration-tests
chore/upgrade-spring-boot
```

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

### 分支合併與清理

**推薦方式：使用 Pull Request（手動在 GitHub 網頁建立）**

```bash
# 1. 推送分支到遠端
git push origin feature/add-payment-module
# push 成功後，終端會顯示 PR 建立連結：
# https://github.com/junechen7414/SpringBoot/pull/new/feature/add-payment-module

# 2. 在 GitHub 網頁上手動建立 Pull Request
#    - 點擊上方連結或前往 GitHub repo 頁面
#    - 填寫 PR Title（遵循 Conventional Commits 格式）
#    - 撰寫 PR Description（說明變更內容與原因）
#    - 選擇適當的 Label（參考下方 Labels 表格）
#    - 確認 base branch 為 main

# 3. 等待 Code Review 與 CI/CD 通過後，在 GitHub 上合併 PR

# 4. 合併後清理本地分支
git checkout main
git pull origin main
git branch -d feature/add-payment-module

# 5. 清理已刪除的遠端分支參考
git fetch --prune
```

### 分支策略：短期分支原則

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
# 推送後在 GitHub 網頁建立 PR 合併到 main

# 步驟 2: 等 PR 合併後，開發使用者認證 UI
git checkout main
git pull origin main  # 取得最新的 user-auth-api
git checkout -b feature/user-auth-ui
# ... 開發 & 提交 ...
# 推送後在 GitHub 網頁建立 PR 合併到 main

# 步驟 3: 等 PR 合併後，開發支付功能
git checkout main
git pull origin main  # 取得完整的 user-auth
git checkout -b feature/payment
# ... 開發 & 提交 ...
# 推送後在 GitHub 網頁建立 PR 合併到 main
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
# 推送後在 GitHub 網頁建立 PR 合併到 main
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
4. 提供 GitHub PR 建立連結：`https://github.com/junechen7414/SpringBoot/pull/new/<branch-name>`
5. 建議 PR 的 Title、Description 和 Label
6. 提醒使用者在 GitHub 網頁上手動建立 PR
7. 提醒使用者等待 Code Review 與 CI/CD 檢查
8. 合併後才建議清理本地分支

### 查詢 GitHub Labels

在建立 PR 時需要選擇正確的 label。可透過 PowerShell 查詢目前 repo 上的所有 labels：

```powershell
# 查詢 GitHub repo 的 labels（PowerShell）
(Invoke-RestMethod -Uri "https://api.github.com/repos/junechen7414/SpringBoot/labels").name
```

**目前可用的 Labels：**

| Label | 用途 |
|-------|------|
| `breaking-change` | API 或行為的破壞性變更 |
| `bugfix` | 修復程式錯誤 |
| `chore` | 建置/工具/CI 配置調整 |
| `config` | 設定檔變更 |
| `dependencies` | 依賴版本更新 |
| `documentation` | 文件更新 |
| `e2e-test` | E2E 測試相關 |
| `enhancement` | 功能增強 |
| `refactor` | 程式碼重構 |
| `test` | 測試相關 |

**Agent 建議流程**：建立 PR 時應根據改動性質選擇 1-2 個最相關的 labels。
