## AI 工具與設定檔總覽

本專案同時被多套 AI 工具使用。本文件說明**每個工具各自讀哪些設定檔**、彼此如何同步，以及版控策略。設定檔多為**專案層級**並納入版控，clone 後即可使用（金鑰仍須自行以環境變數 / `.env` 提供）。

> 新增 / 修改任一工具的設定（指令、MCP、skill、agent/mode）時，須同步其他工具的對應檔，並依各工具格式調整。可委派 `ai-doc-sync` subagent 以 dry-run 檢查一致性。

### 工具 ↔ 設定檔對應表

| 工具 | 指令 / 規則 | MCP | Skills | Agents / Modes | 其他 |
|------|------------|-----|--------|----------------|------|
| **Claude Code** | `AGENTS.md`（原生讀取，`@`-import `docs/agents/*`）+ `.claude/rules/*` | `.mcp.json` | `.claude/skills/` | `.claude/agents/*.md` | `.claude/settings.json`（hooks）、`.claude/settings.local.json`（`enabledMcpjsonServers` 控制啟用哪些 MCP） |
| **GitHub Copilot** | `.github/instructions/Global.instructions.md`（`@`-import `docs/agents/*`） | `.vscode/mcp.json`（含 `inputs` 區塊提示輸入金鑰） | — | — | — |
| **Cline** | `.github/instructions/Global.instructions.md`（本專案無 `.clinerules`） | `.vscode/mcp.json` | — | — | — |
| **Bob (IBM BOB)** | `AGENTS.md`（`@`-import `docs/agents/*`） | `.bob/mcp.json` | `.bob/skills/` | `.bob/custom_modes.yaml` | `.bob/settings.json`（`autoAccept` 等本機偏好） |
| **agy CLI** | `AGENTS.md`（`@`-import `docs/agents/*`） | `.agents/mcp_config.json` | `.agents/skills/` | — | — |

> **指令檔分工**：Claude Code、Bob、agy CLI 三者皆讀根目錄 `AGENTS.md`；Cline / Copilot 讀 `.github/instructions/Global.instructions.md`。兩份內容相同（同一組 `@`-import 加同一份 pointer 清單），只差 `AGENTS.md` 多了 plugin 用的 `## Agent skills` 區塊。本專案**刻意不放 `CLAUDE.md`**，見下方「文件真相源」。

### 文件真相源（source of truth）

`docs/agents/*` 是唯一真相源，分三層：

| 層級 | 檔案 | 怎麼被讀到 |
|---|---|---|
| **常駐** | `01`、`03`、`05`、`06`、`08` | 兩個入口檔 `@`-import，每個 session 都載入 |
| **pointer-only** | `02`、`09`、`10`、`11` | 不 import；入口檔的「何時讀哪份文件」清單寫明觸發情境，agent 遇到時自己讀 |
| **plugin 管理** | 未編號的 `issue-tracker.md`、`triage-labels.md`、`domain.md` | 由 `/setup-matt-pocock-skills` 產生，plugin 的 skills 依固定路徑讀取；`AGENTS.md` 的 `## Agent skills` 區塊指向它們 |

**放哪一層的判準**：每個 session 都可能用到 → 常駐；只在動到特定檔案或遇到特定症狀時才需要 → pointer-only。常駐內容越長，每條規則越容易被淹沒，所以新增內容前先問「刪掉它，agent 會不會出錯？」。

- **入口檔（下游，不重複貼內容）**：`AGENTS.md` 用 `@./docs/agents/*`；`Global.instructions.md` 用 `@../../docs/agents/*`。兩份的 import 清單與 pointer 清單必須逐行一致；新增、刪除文件或調整層級時兩邊同步改。
- **不放 `CLAUDE.md` / `CLAUDE.local.md` / `.claude/CLAUDE.md`**：Claude Code（v2.1.277+）原生讀 `AGENTS.md`，但預設只在 repo 裡**沒有**這三個檔案時才讀。多放任何一個（包括個人的 `CLAUDE.local.md`），Claude Code 就只讀它、不再載入 `AGENTS.md` 與 `docs/agents/*`。
  - Claude 專屬、只在改特定檔案時才需要的細節放 `.claude/rules/*`（以 `paths:` 限定載入時機）。
  - 個人非版控的指示放 `~/.claude/CLAUDE.md`（使用者層級，不會擋住 `AGENTS.md`）。若一定要用 `CLAUDE.local.md`，須在使用者設定把 `agents-md@builtin` 的 `instructionFiles` 設成 `claude-md-and-agents-md`。

### MCP server 現況

| Server | 用途 | command | 所需環境變數 |
|--------|------|---------|-------------|
| `github` | GitHub 操作（PR / issue / label 等） | 見下方差異 | `GITHUB_PAT` 或 `GITHUB_PERSONAL_ACCESS_TOKEN` |
| `playwright` | 瀏覽器自動化（extension 模式：接管已開啟的 Chrome / Edge，沿用既有登入狀態） | `npx @playwright/mcp@latest --extension`（Claude 見下方差異） | 無 |

四份 MCP 設定檔須同步維護：`.mcp.json`（Claude，`type: http`/`stdio`）、`.bob/mcp.json`（同格式）、`.vscode/mcp.json`（`servers` + `inputs`）、`.agents/mcp_config.json`（額外帶 `disabled` / `autoApprove`）。

#### 跨工具的刻意差異（非 drift，維護時勿盲目對齊）

- **`github`**
  - Claude / BOB / VS Code：遠端 HTTP endpoint `https://api.githubcopilot.com/mcp/`，金鑰 `GITHUB_PAT`（VS Code 以 `${env:GITHUB_PAT}` 讀環境變數，須將 `GITHUB_PAT` 設到 Windows User 層級，VS Code 才吃得到）。
  - agy CLI：本地 npm 套件 `npx -y @modelcontextprotocol/server-github`（stdio），金鑰變數名 `GITHUB_PERSONAL_ACCESS_TOKEN`。
- **`playwright`**
  - Claude：`cmd /c npx ...` —— Claude Code 在原生 Windows 無法直接 spawn `npx`（它是 `.cmd`），須以 `cmd /c` 包住。
  - BOB / VS Code / agy：直接 `npx ...`。
  - 使用前須在 Chrome / Edge 安裝 Playwright 擴充（Playwright MCP Bridge）；agent 首次連線時由擴充跳窗讓使用者選擇要交出控制的分頁。它以**使用者本人的登入身分**操作瀏覽器，建議用只登入必要網站的專用 profile（可加 `--profile-dir-name` 指定）。

### Skills 現況

各工具的 skills **並非完全一致**：

- `.claude/skills/` 與 `.bob/skills/`：互為鏡像 —— caveman、find-skills、github-actions-docs、high-risk-pr-workflow、integration-test-runner、new-domain-scaffold、openapi-doc-gen、skill-creator、skills-cli（9 個）。
- `.agents/skills/`（agy CLI）：另一套 —— cavecrew、caveman 全家族（commit / compress / help / init / review / stats）、compress、find-skills。
- 真相源：`skills-lock.json`（記錄每個 skill 的 GitHub 來源與 hash）。

#### Claude Code plugin：mattpocock-skills（刻意不鏡像）

以 `/plugin install mattpocock-skills@claude-plugins-official` 安裝在**使用者層級**，會自動更新，不進 repo、不記入 `skills-lock.json`，也**不鏡像**到 Bob / agy。原因是它屬於個人工作習慣，不是專案契約；換機器時要自己重裝。

- 在 repo 留下的只有 setup 產物：`docs/agents/issue-tracker.md`（local markdown，issue 放 `.scratch/<feature>/` 並納入版控）、`triage-labels.md`、`domain.md`，以及 `AGENTS.md` 的 `## Agent skills` 區塊。
- skill 名稱帶 `mattpocock-skills:` 前綴，所以它的 `code-review` 和內建的 `/code-review` 是兩個不同的 skill。
- 想把某個 skill 改寫成繁中或客製時，再用 `npx skills add mattpocock/skills` 把**那一個**複製進 repo，並照上面的鏡像規則同步。

### Agents ↔ Modes

`.claude/agents/*.md`（frontmatter: name/description/tools/model + system prompt）與 `.bob/custom_modes.yaml`（`customModes:` 陣列）是平行概念，新增「跨工具都該有」的角色時兩邊都要建立對應條目（格式不同、語意對齊）。工具專屬角色（如 Claude 的 `ai-doc-sync` meta agent、Bob 的 `mcp-builder-agent-utils`）不必互相硬搬。

### 版控與 .gitignore 策略

AI 工具的**專案層級設定與 skills 已納入版控**，僅忽略暫存 / 個人產物：

- **追蹤**：`.vscode/`（`mcp.json` / `launch.json` / `settings.json`）、`.bob/`（`mcp.json` / `custom_modes.yaml` / `settings.json` / `skills/`）、`.claude/skills/`、`.agents/`、以及根目錄 `.mcp.json`、文件群。
- **忽略**：`.bob/.bob-errors/`（錯誤 log）、`.bob/notes/`（待辦筆記）、`.env`（金鑰）。

> git 規則：**不可整目錄忽略（如 `.bob`）後再用 `!` 救回子檔**。需要部分追蹤時，只忽略要排除的子路徑（如 `.bob/.bob-errors/`）。

### 環境變數

金鑰一律透過環境變數 / `.env` 注入，**禁止寫死在設定檔**。引用語法：`${VAR}`（Claude / BOB / agy）；VS Code 支援 `${env:VAR}`（讀 OS 環境變數，須在 Windows User/Machine 層級設定，VS Code 從 GUI 啟動才吃得到）或 `${input:id}`（啟動時跳出輸入框、輸入一次後快取）。本專案 VS Code 的 `github` 用 `${env:GITHUB_PAT}`。
