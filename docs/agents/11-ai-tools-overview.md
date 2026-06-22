## AI 工具與設定檔總覽

本專案同時被多套 AI 工具使用。本文件說明**每個工具各自讀哪些設定檔**、彼此如何同步，以及版控策略。設定檔多為**專案層級**並納入版控，clone 後即可使用（金鑰仍須自行以環境變數 / `.env` 提供）。

> 新增 / 修改任一工具的設定（指令、MCP、skill、agent/mode）時，須同步其他工具的對應檔，並依各工具格式調整。可委派 `ai-doc-sync` subagent 以 dry-run 檢查一致性。

### 工具 ↔ 設定檔對應表

| 工具 | 指令 / 規則 | MCP | Skills | Agents / Modes | 其他 |
|------|------------|-----|--------|----------------|------|
| **Claude Code** | `CLAUDE.md`（手寫摘要）+ `AGENTS.md`（`@`-import `docs/agents/*`） | `.mcp.json` | `.claude/skills/` | `.claude/agents/*.md` | `.claude/settings.json`（hooks）、`.claude/settings.local.json`（`enabledMcpjsonServers` 控制啟用哪些 MCP） |
| **GitHub Copilot** | `.github/instructions/Global.instructions.md`（`@`-import `docs/agents/*`） | `.vscode/mcp.json`（含 `inputs` 區塊提示輸入金鑰） | — | — | — |
| **Cline** | `.github/instructions/Global.instructions.md`（本專案無 `.clinerules`） | `.vscode/mcp.json` | — | — | — |
| **Bob (IBM BOB)** | `AGENTS.md`（`@`-import `docs/agents/*`） | `.bob/mcp.json` | `.bob/skills/` | `.bob/custom_modes.yaml` | `.bob/settings.json`（`autoAccept` 等本機偏好） |
| **agy CLI** | `AGENTS.md`（`@`-import `docs/agents/*`） | `.agents/mcp_config.json` | `.agents/skills/` | — | — |

> **指令檔分工**：Claude Code、Bob、agy CLI 三者皆讀根目錄 `AGENTS.md`；Cline / Copilot 讀 `.github/instructions/Global.instructions.md`。兩份都以 `@`-import 拉入同一組 `docs/agents/*` 真相源，差別在 `Global.instructions.md` 檔頭另含語言 / podman / pnpm / shell 偏好區塊。`CLAUDE.md` 則是 Claude Code 額外讀的手寫摘要。

### 文件真相源（source of truth）

- **唯一真相源**：`docs/agents/01-overview.md` ~ `11-ai-tools-overview.md`。
- **下游（靠 `@`-import，不該重複貼內容）**：
  - `AGENTS.md` — `@./docs/agents/*`，Claude Code 讀。
  - `.github/instructions/Global.instructions.md` — `@../../docs/agents/*`，Cline / Copilot 讀；檔頭另有語言 / podman / pnpm / shell 偏好區塊。
- **`CLAUDE.md`** — 手寫 fast-start 摘要，**非** import，最容易 drift。改動 `docs/agents/*` 的慣例時要回頭檢查它。

兩份 `@`-import 清單（`AGENTS.md` 與 `Global.instructions.md`）須與 `docs/agents/*` 檔案清單保持一一對應，新增 / 刪除文件時兩邊 import 行都要同步增刪。

### MCP server 現況

| Server | 用途 | command | 所需環境變數 |
|--------|------|---------|-------------|
| `github` | GitHub 操作（PR / issue / label 等） | 見下方差異 | `GITHUB_PAT` 或 `GITHUB_PERSONAL_ACCESS_TOKEN` |
| `browser-use` | 瀏覽器自動化 | `uvx --from browser-use[cli] browser-use --mcp` | 見下方差異 |
| `awslabs.document-loader-mcp-server` | 文件載入 / 解析 | `uvx awslabs.document-loader-mcp-server@latest` | `FASTMCP_LOG_LEVEL`（無金鑰） |

四份 MCP 設定檔須同步維護：`.mcp.json`（Claude，`type: http`/`stdio`）、`.bob/mcp.json`（同格式）、`.vscode/mcp.json`（`servers` + `inputs`）、`.agents/mcp_config.json`（額外帶 `disabled` / `autoApprove`）。

#### 跨工具的刻意差異（非 drift，維護時勿盲目對齊）

- **`github`**
  - Claude / BOB / VS Code：遠端 HTTP endpoint `https://api.githubcopilot.com/mcp/`，金鑰 `GITHUB_PAT`（VS Code 以 `${env:GITHUB_PAT}` 讀環境變數，須將 `GITHUB_PAT` 設到 Windows User 層級，VS Code 才吃得到）。
  - agy CLI：本地 npm 套件 `npx -y @modelcontextprotocol/server-github`（stdio），金鑰變數名 `GITHUB_PERSONAL_ACCESS_TOKEN`。
- **`browser-use`**
  - Claude / BOB / VS Code：Anthropic API（`ANTHROPIC_BASE_URL` / `ANTHROPIC_AUTH_TOKEN` / `ANTHROPIC_MODEL`）。
  - agy CLI：Gemini（`GEMINI_API_KEY`）。

### Skills 現況

各工具的 skills **並非完全一致**：

- `.claude/skills/` 與 `.bob/skills/`：互為鏡像 —— caveman、find-skills、github-actions-docs、skill-creator、skills-cli（5 個）。
- `.agents/skills/`（agy CLI）：另一套 —— cavecrew、caveman 全家族（commit / compress / help / init / review / stats）、compress、find-skills。
- 真相源：`skills-lock.json`（記錄每個 skill 的 GitHub 來源與 hash）。

### Agents ↔ Modes

`.claude/agents/*.md`（frontmatter: name/description/tools/model + system prompt）與 `.bob/custom_modes.yaml`（`customModes:` 陣列）是平行概念，新增「跨工具都該有」的角色時兩邊都要建立對應條目（格式不同、語意對齊）。工具專屬角色（如 Claude 的 `ai-doc-sync` meta agent、Bob 的 `mcp-builder-agent-utils`）不必互相硬搬。

### 版控與 .gitignore 策略

AI 工具的**專案層級設定與 skills 已納入版控**，僅忽略暫存 / 個人產物：

- **追蹤**：`.vscode/`（`mcp.json` / `launch.json` / `settings.json`）、`.bob/`（`mcp.json` / `custom_modes.yaml` / `settings.json` / `skills/`）、`.claude/skills/`、`.agents/`、以及根目錄 `.mcp.json`、文件群。
- **忽略**：`.bob/.bob-errors/`（錯誤 log）、`.bob/notes/`（待辦筆記）、`.env`（金鑰）。

> git 規則：**不可整目錄忽略（如 `.bob`）後再用 `!` 救回子檔**。需要部分追蹤時，只忽略要排除的子路徑（如 `.bob/.bob-errors/`）。

### 環境變數

金鑰一律透過環境變數 / `.env` 注入，**禁止寫死在設定檔**。引用語法：`${VAR}`（Claude / BOB / agy）；VS Code 支援 `${env:VAR}`（讀 OS 環境變數，須在 Windows User/Machine 層級設定，VS Code 從 GUI 啟動才吃得到）或 `${input:id}`（啟動時跳出輸入框、輸入一次後快取）。本專案 VS Code 的 `github` 用 `${env:GITHUB_PAT}`，`browser-use` 用 `${input:...}`。
