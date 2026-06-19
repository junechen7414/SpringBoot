# Git 工作流程

> 本文件說明本專案的 Git 工作模式（主幹開發）、分支命名、Commit 訊息規範，以及何時才需要走分支 / Pull Request。

## 工作模式：主幹開發（Trunk-Based）

本專案是**一人 side project**，採用**主幹開發**：預設直接在 `main` 上小步快跑，只有在「高風險改動」時才另開分支走 PR。

### 為什麼這樣做

- CI（`.github/workflows/image-publish.yml`）在 **push 到 `main`** 與 **開 PR** 時都會觸發測試關卡 —— 不需要靠 PR 才能跑測試。
- 一人專案沒有多人協作要同步，長命分支只會增加 rebase 成本。
- `main` **不設 branch protection**，可直接 push；壞了就 fix-forward 或 revert，自己負責。

### 核心原則

1. **預設直接在 `main` 開發**：小修正、文件、低風險改動 → 直接 commit + push。
2. **push 前一定先過測試**：用 pre-push hook 自動跑（見下方），補回「合併前關卡」這層保險。
3. **高風險改動才開分支 + PR**（清單見下方）。
4. **`main` 紅了最優先處理**：立刻 fix-forward 或 `git revert`，不要累積。

### ⚠️ 重要：push `main` 是有副作用的

`main` 不是只跑個測試而已。push 到 `main` 會連帶觸發：

- 推 `latest` tag 的 image 到 `ghcr.io`
- `repository-dispatch` 觸發**下游 repo 的 E2E**
- 重新產 `swagger.json` 並推到下游 repo

CI 的 `Run Unit Tests (Gate)` 跑在 build image **之前**，所以**測試抓得到的錯**不會產出壞 image。風險落在**測試抓不到的錯**（執行期 / 整合問題）—— 這正是 pre-push hook 與「高風險走 PR」要補的洞。

## push 前的保險：pre-push hook

專案提供 `.githooks/pre-push`，在你 push **含 `main`** 的內容時，自動跑與 CI gate **完全相同**的指令，做到「**本地綠 = CI 綠**」。

### 一次性啟用（每台機器設定一次）

```bash
git config core.hooksPath .githooks
```

> Windows 上 Git 會用內附的 `sh` 執行 hook，無需額外設定；確認 `.githooks/pre-push` 具執行權限（`git update-index --chmod=+x .githooks/pre-push` 已隨檔提交）。

啟用後，每次 `git push` 若推送目標包含 `main`，會先在本地跑：

```bash
./gradlew test -Djunit.platform.exclude.tags=SanityTest
```

測試掛掉就**中止 push**，把問題擋在進 `main` 之前。推送非 `main` 分支則略過（交給 PR 的 CI 跑）。

> 趕時間想跳過（自負風險）：`git push --no-verify`。

## 何時才開分支 + PR

以下情況**建議**另開分支走 PR，讓 CI 在合併前先跑完整 build，並留下可回顧的變更紀錄：

- 改 **CI 本身**（`.github/workflows/*`）—— 壞了會影響所有後續流程。
- 改 **DB migration**（`src/main/resources/db/migration`）。
- **跨 domain 的重構**或大型功能（多 commit、難以一次驗證）。
- 任何「測試抓不到、但壞了影響大」、想在合併前多跑一輪 image build 的改動。

其餘日常改動，直接在 `main` 上做。

## 分支命名規範

> 僅在「開分支」時適用。

### 格式

```
類別/任務簡述
```

### 常用前綴

- `feature/` - 新功能開發
- `fix/` - Bug 修復（通用）
- `hotfix/` - 緊急修復（生產環境）
- `refactor/` - 程式碼重構
- `config/` - 設定檔變更
- `docs/` - 文件更新
- `test/` - 測試相關
- `chore/` - 建置/工具更新

### 命名規則

- ✅ 全小寫
- ✅ 使用 `-` 分隔單字
- ✅ 使用 `/` 分層
- ❌ 避免使用空格或特殊字元

### 範例

```bash
feature/add-payment-module
fix/order-creation-transient-entity-bug
hotfix/security-patch
refactor/improve-openapi-annotations
config/update-resilience4j-config
docs/update-api-guide
test/add-integration-tests
chore/upgrade-spring-boot
```

## Commit 訊息規範

遵循 **Conventional Commits** 規範（無論直接 commit 到 `main` 或在分支上都適用）。

### 格式

```
<type>(<scope>): <subject>
```

### 常用類型

| Type | 說明 | 範例 |
|------|------|------|
| `feat` | 新功能 | `feat(order): add bulk order creation endpoint` |
| `fix` | 錯誤修復 | `fix(product): resolve stock deduction race condition` |
| `docs` | 文件更新 | `docs: update API documentation for account module` |
| `style` | 程式碼格式 | `style: format code with spotless` |
| `refactor` | 重構 | `refactor(service): replace WebClient with RestClient` |
| `test` | 測試 | `test(order): add integration tests for order creation` |
| `chore` | 雜項 | `chore(deps): upgrade Spring Boot to 4.0.7` |

### 撰寫原則

- ✅ 使用祈使句（如 `add` 而非 `added`）
- ✅ 第一個字母小寫
- ✅ 不要在結尾加句號
- ✅ 簡潔明瞭，說明「做了什麼」而非「為什麼」

### 範例

```bash
# 好的範例
feat(order): add bulk order creation endpoint
fix(product): resolve stock deduction race condition
docs(agents): split into modular files

# 避免的範例
Added payment feature  # 非祈使句
Fix bug.  # 不夠具體
updated readme  # 缺少 type
```

## 日常開發流程（直接在 main）

```bash
# 1. 確認在 main 且為最新
git checkout main
git pull origin main

# 2. 進行改動

# 3. 提交前檢查
git status
git diff
./gradlew test          # 也可直接交給 pre-push hook

# 4. 提交（遵循 Conventional Commits）
git add <files>
git commit -m "type(scope): description"

# 5. 推送（pre-push hook 會先跑 CI gate 相同的測試）
git push origin main
```

### Agent 自動化建議流程

當 Agent 準備進行程式碼改動時：

1. **偵測當前分支**：`git branch --show-current`。
2. **判斷改動風險**：
   - 低風險（小修正 / 文件 / 單一 domain 的小改動）→ **直接在 `main` 上進行**。
   - 高風險（改 CI、DB migration、跨 domain 重構、大型功能）→ **建議另開分支走 PR**（前綴見上）。
3. **若已在某條工作分支**：延續該分支即可，不需特地切回 `main`。
4. **改動完成後**：依 Conventional Commits 提交，push 前確保測試通過（pre-push hook 會把關）。

> 注意：與舊流程相反 —— 在 `main` 上**不需要**為了一般改動而強制建立新分支。

## 開分支時的策略：短期分支原則

一旦決定開分支（高風險場景），仍遵循短命分支原則：

- ✅ **所有分支都從最新的 `main` 建立**，避免分支間依賴。
- ✅ **盡快合併回 `main`**，減少長命分支的維護成本。
- ✅ **拆分大功能**為多個小 PR，每個獨立可測試。

```
# ❌ 避免：建立依賴鏈
main
 └─ feature/user-auth
     └─ feature/payment (依賴 user-auth)

# ✅ 推薦：拆分獨立 PR，依序從最新 main 建立
main
 ├─ feature/user-auth-api (先合併)
 ├─ feature/user-auth-ui (等 API 合併後建立)
 └─ feature/payment (等前面都合併後建立)
```

建立新分支：

```bash
git checkout main
git pull origin main
git checkout -b feature/add-payment-module
# ... 開發 & 提交 ...
git push origin feature/add-payment-module
```

## 建立 Pull Request（高風險改動才需要）

### 透過 Git 推送訊息中的連結

推送分支後，Git 會在終端機輸出中提供建立 PR 的連結：

```
remote: Create a pull request for 'feature/your-branch' on GitHub by visiting:
remote:      https://github.com/junechen7414/SpringBoot/pull/new/feature/your-branch
```

直接點擊或複製該連結到瀏覽器即可建立 PR。

### PR 標題和描述建議

**標題**遵循 Conventional Commits 格式：

- 範例：`feat(order): add bulk order creation endpoint`
- 範例：`docs(agents): 更新 Git 工作流程說明`

**描述應該包含：**

- ✅ 變更的目的和背景
- ✅ 主要功能或改進說明
- ✅ 相關 issue 連結（使用 `Closes #123`）
- ✅ 破壞性變更說明（使用 `BREAKING CHANGE`）

**不需要包含：**

- ❌ 修改的檔案列表（GitHub 會自動顯示）
- ❌ 測試結果（CI/CD 會自動執行並顯示）
- ❌ 程式碼細節（可在 Files changed 中查看）

### Labels 選擇

為 PR 加上適當的 labels，根據變更性質選擇 1-2 個最相關的：

| 變更類型 | 建議 Label |
|---------|-----------|
| 文件更新 | `documentation` |
| 新功能 | `enhancement` |
| 錯誤修復 | `bugfix` |
| 程式碼重構 | `refactor` |
| 測試相關 | `test` |
| 依賴更新 | `dependencies` |
| 配置變更 | `config` |
| 破壞性變更 | `breaking-change` |
| 建置/工具 | `chore` |

查詢目前 repo 上的所有 labels（PowerShell）：

```powershell
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

#### 為 PR 加上 Labels（工具方式）

PR 在 GitHub API 中本質上也是 issue，但各工具加 label 的方式不同：

- **`gh` CLI**（最直接）：

  ```bash
  gh pr edit <PR 號> --add-label "dependencies,breaking-change"
  # 或建立 PR 時一併指定
  gh pr create --label dependencies --label breaking-change ...
  ```

- **GitHub MCP**：`create_pull_request` / `update_pull_request` **沒有 labels 欄位**，無法直接加。改用 **`issue_write`**（`method: "update"`、`issue_number` 填 **PR 號**、`labels: [...]`）——因為 PR 在 API 中也是 issue。流程：先 `create_pull_request` 取得 PR 號 → 再 `issue_write` 補上 labels。
  > 注意：label 必須**已存在於 repo**（MCP 工具集只有 `get_label`，無法新建 label）。確認某 PR 目前的 labels 用 `pull_request_read`（`get_labels` 只適用純 issue，傳 PR 號會回 "Could not resolve to an Issue"）。

- **GitHub 網頁**：PR 頁面右側 Labels 區塊手動勾選。

合併 PR 後，依 [Git 分支清理指南](./04-git-branch-cleanup.md) 清理分支。

## main 紅了怎麼辦

直接 push `main` 的代價是壞 commit 已經在 `main` 上，處理原則：

1. **能快速修好** → 立刻補一個 `fix:` commit 推上去（fix-forward）。
2. **一時修不好** → `git revert <bad-sha>` 先讓 `main` 恢復綠，再從容處理。
3. **避免累積**：`main` 紅了就優先處理，不要疊新的改動上去。

## 相關文件

- [Git 分支清理指南](./04-git-branch-cleanup.md)
- [程式碼規範](./05-code-standards.md)
- [架構設計](./06-architecture.md)
