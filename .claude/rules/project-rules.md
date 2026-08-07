# Project Rules

以下規則適用於本儲存庫的所有任務。

---

## 語言偏好

- **以繁體中文回應**；技術術語保留英文。

---

## 工具偏好

- **容器一律使用 `podman`，不用 `docker`。** 所有 compose/build 指令都以 `podman compose` 為準。

---

## Shell 偵測規則

執行 CLI 指令前先偵測 shell：

- **PowerShell**：以 `;` 串接指令，呼叫 `./gradlew`
- **CMD**：以 `&&` 串接指令，呼叫 `gradlew`
- **Git Bash（Windows）**：使用 `./gradlew`

不要混用不同 shell 的語法。

---

## Git Workflow 規則

- **主線開發（trunk-based）**：小步驟直接 commit 到 `main` 並 push；不需要為了觸發測試而開 PR。
- **Push 前測試必須通過。** pre-push hook（`.githooks/pre-push`）會在 push 含 `main` 時執行 `./gradlew test -Djunit.platform.exclude.tags=SanityTest`。
- **只在高風險變更時才開 branch + PR**：CI workflow 編輯、DB migrations、跨 domain 重構／大型功能。
- **Branch 命名**：使用 `feature/ fix/ hotfix/ refactor/ config/ docs/ test/ chore/` 前綴（小寫、以 `-` 分隔），從最新的 `main` 分出，保持短命。
- **Commit 格式**：遵循 Conventional Commits — `type(scope): subject`（祈使句、小寫、結尾不加句點）。
- **禁止 AI 協作者署名**：commit message **不得**包含 `Co-Authored-By: Claude`（或任何指向 claude/anthropic 的 co-author）、`noreply@anthropic.com`、`🤖 Generated with Claude Code` 這類 AI 署名行。PR body 亦同。`.githooks/commit-msg` 會擋下含這些署名的 commit，作為第二道防線。
- **新增 PR label 時**：使用 GitHub MCP 的 `issue_write`（method `update`）帶入 PR 編號 — `create_pull_request`/`update_pull_request` 沒有 labels 欄位。

---

## 專案環境慣例

- 單模組 Gradle 專案（`settings.gradle` 定義單一 project）。
- Java 25 toolchain。
- Base package：`com.ibm.demo`。
- App 監聽於 **http://localhost:8787**。
- 需要一份包含 `ORACLE_DEV_USERNAME` / `ORACLE_DEV_PASSWORD` 的 `.env`（見 `.env.example`）。

---

## 文件同步要求

當專案慣例變更時，請保持以下檔案同步：

- `AGENTS.md`
- `docs/agents/*`
- `.github/instructions/Global.instructions.md`
