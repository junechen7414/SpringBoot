## AI 工具與設定檔總覽

本專案同時被多套 AI 工具使用。本文件說明 repo 提供哪些設定、如何同步，以及版控策略。多數設定屬於**專案層級**，clone 會取得檔案，但仍須提供環境變數、安裝必要工具，並確認目前客戶端實際載入了哪些來源；檔案存在不代表設定已生效。

> 新增 / 修改任一工具的設定（指令、MCP、skill、agent/mode）時，須同步其他工具的對應檔，並依各工具格式調整。可委派 `ai-doc-sync` subagent 以 dry-run 檢查一致性。

### 工具 ↔ 設定檔對應表

| 工具 | 指令 / 規則 | MCP | Skills | Agents / Modes | 其他 |
|------|------------|-----|--------|----------------|------|
| **Claude Code** | `AGENTS.md`（原生讀取，`@`-import `docs/agents/*`）+ `.claude/rules/*` | `.mcp.json` | `.claude/skills/` | `.claude/agents/*.md` | `.claude/settings.json`（hooks）、`.claude/settings.local.json`（`enabledMcpjsonServers` 控制啟用哪些 MCP） |
| **GitHub Copilot** | repo 提供 `AGENTS.md` 與 `.github/instructions/Global.instructions.md`；載入與引用展開依 harness 驗證 | legacy Local harness 使用 `.vscode/mcp.json`；其他 harness 的來源另查；目前以 env 引用金鑰 | — | — | — |
| **Cline** | repo 提供 `AGENTS.md`；不可假設會讀 GitHub instruction 檔或展開裸 `@` imports | 依客戶端實際來源驗證，不把 `.vscode/mcp.json` 當成已確認來源 | — | — | — |
| **Bob (IBM BOB)** | repo 提供 `AGENTS.md`；`@` imports 是否展開須核對客戶端 | `.bob/mcp.json` | `.bob/skills/` | `.bob/custom_modes.yaml` | `.bob/settings.json`（追蹤的 `autoAccept` 偏好，不是存放憑證的位置） |
| **agy CLI** | repo 提供 `AGENTS.md`；`@` imports 是否展開須核對客戶端 | `.agents/mcp_config.json` | `.agents/skills/` | — | — |

> **入口與載入是兩件事**：repo 維護 `AGENTS.md` 與 GitHub instruction 兩份入口，引用相同文件與 pointer 清單，只有 `AGENTS.md` 多了 plugin 的 `## Agent skills`。Claude Code 支援這裡的原生 `@` imports；其他工具不能只因檔案存在、`description: Every conversation` 或有一條相對路徑，就當成全文已載入。Copilot 的不同 harness 也可能使用不同的 MCP 與指令來源，應在實際客戶端查驗。本專案**刻意不放 `CLAUDE.md`**，見下方「文件真相源」。

### 哪些設定跨 repo 共用

| 層級 | 範例與範圍 |
|---|---|
| **使用者層** | `~/.claude/settings.json` 的 provider、通知 hooks、user-scope plugins，通常供採用同一設定來源的本機 sessions 共用；不同 host／profile／cloud 不保證相同 |
| **Repo 版控層** | `AGENTS.md`、共用 docs、repo MCP、skills、agents、shared settings；各 repo 獨立，clone 會取得，仍須由客戶端載入 |
| **專案個人層** | `.claude/settings.local.json`，以及 `~/.claude.json` 中 `projects[專案路徑].mcpServers`；只屬於該專案。設定檔在 home 不等於內容是 user scope；同名 local MCP entry 可整筆覆蓋 repo entry，不是逐欄合併 |
| **Git hooks 安裝層** | 每 repo 的 effective `core.hooksPath`；未設定時為 `.git/hooks`。ignored hook 仍可執行，且不是只有原安裝工具執行 Git 時才觸發 |
| **Host 注入** | SDK／IDE 可以另行提供 MCP、skills、permissions、hooks；repo 檔案不是目前 session 全部能力的清單 |

同名 skill、相同內容或相同遠端來源都不表示跨 repo 自動同步；沒有明確連結或共用機制時，應視為獨立副本。全域基線可在同機同 harness 查一次，但不能用「SpringBoot 已整理」代表其他 repo 也已整理。

**其他 repo 的最小差異檢查**：

1. 確認實際 instruction／MCP 來源與 project-local overrides，不只看一份 JSON；記下客戶端版本與載入畫面，未做 runtime trace 就標明未驗證。
2. 查 repo instructions、skills、agents 的引用是否存在、是否需要額外 runtime；區分工具專屬設計與真正的過時副本，不整批搬入 SpringBoot 的 skills。
3. 查 shared／local／ignored 邊界及 effective Git hooks；特別看自動安裝、外部呼叫、Git notes fetch／push。不要執行被稽核的命令來證明它有副作用。
4. 以白名單方式解析設定，只回報 transport、公開 package／flags、env／header 名稱；不輸出 token、私人 endpoint、notes 或完整 payload。

### 文件真相源（source of truth）

`docs/agents/*` 是唯一真相源，分三層：

| 層級 | 檔案 | 怎麼被讀到 |
|---|---|---|
| **常駐** | `01`、`03`、`05`、`06`、`08` | 設計上每個 session 需要；Claude 由 `AGENTS.md` 的原生 imports 載入，其他工具須核對 loader 與引用展開結果 |
| **pointer-only** | `02`、`09`、`10`、`11` | 不 import；入口檔的「何時讀哪份文件」清單寫明觸發情境，agent 遇到時自己讀 |
| **plugin 管理** | 未編號的 `issue-tracker.md`、`triage-labels.md`、`domain.md` | 由 `/setup-matt-pocock-skills` 產生，plugin 的 skills 依固定路徑讀取；`AGENTS.md` 的 `## Agent skills` 區塊指向它們 |

**放哪一層的判準**：每個 session 都可能用到 → 常駐；只在動到特定檔案或遇到特定症狀時才需要 → pointer-only。常駐內容越長，每條規則越容易被淹沒，所以新增內容前先問「刪掉它，agent 會不會出錯？」。

- **入口檔（下游，不重複貼內容）**：`AGENTS.md` 用 `@./docs/agents/*`；`Global.instructions.md` 用 `@../../docs/agents/*`。兩份的 import 清單與 pointer 清單必須逐行一致；新增、刪除文件或調整層級時兩邊同步改。這是檔案維護契約，不保證所有工具在執行期都會展開 imports。
- **不放 `CLAUDE.md` / `CLAUDE.local.md` / `.claude/CLAUDE.md`**：Claude Code（v2.1.277+）原生讀 `AGENTS.md`，但預設只在 repo 裡**沒有**這三個檔案時才讀。多放任何一個（包括個人的 `CLAUDE.local.md`），Claude Code 就只讀它、不再載入 `AGENTS.md` 與 `docs/agents/*`。
  - Claude 專屬、只在改特定檔案時才需要的細節放 `.claude/rules/*`（以 `paths:` 限定載入時機）。
  - 個人非版控的指示放 `~/.claude/CLAUDE.md`（使用者層級，不會擋住 `AGENTS.md`）。若一定要用 `CLAUDE.local.md`，須在使用者設定把 `agents-md@builtin` 的 `instructionFiles` 設成 `claude-md-and-agents-md`。

### MCP server 現況

| Server | 用途 | command | 所需環境變數 |
|--------|------|---------|-------------|
| `github` | GitHub 操作（PR / issue / label 等） | 見下方差異 | `GITHUB_PAT`（agy 映射為 child `GITHUB_PERSONAL_ACCESS_TOKEN`） |
| `playwright` | 瀏覽器自動化（CDP 模式：連到本機專用 Edge，沿用該 profile 的登入狀態；啟用 annotation／錄影） | `npx -y @playwright/mcp@latest --cdp-endpoint http://localhost:9222 --caps=devtools`（Claude 見下方差異） | 無 |

四份 MCP 設定檔須維護等價功能，不是逐字相同：`.mcp.json`（Claude，`type: http`/`stdio`）、`.bob/mcp.json`（同格式）、`.vscode/mcp.json`（`servers`，格式可另設 `inputs`）、`.agents/mcp_config.json`（agy，格式可另設 `disabled` / `autoApprove`）。可用欄位不代表目前已設定；目前 VS Code 使用環境變數，沒有 `inputs` 區塊。

#### 跨工具的刻意差異（非 drift，維護時勿盲目對齊）

- **`github`**
  - Claude / BOB / VS Code：遠端 HTTP endpoint `https://api.githubcopilot.com/mcp/`，金鑰 `GITHUB_PAT`（VS Code 以 `${env:GITHUB_PAT}` 讀環境變數，須將 `GITHUB_PAT` 設到 Windows User 層級，VS Code 才吃得到）。
  - agy CLI：以 Podman 執行官方 `ghcr.io/github/github-mcp-server`（stdio）；child 的 `GITHUB_PERSONAL_ACCESS_TOKEN` 由 parent `GITHUB_PAT` 提供。使用前須確認 Podman 可用並完成 agy 初始化驗證，不再執行已封存的 `@modelcontextprotocol/server-github`。目前未釘 image tag／digest；靜態設定檢查不代表已驗證 image 取得與 server 啟動。
- **`playwright`**
  - Claude：`cmd /c npx ...` —— Claude Code 在原生 Windows 無法直接 spawn `npx`（它是 `.cmd`），須以 `cmd /c` 包住。
  - BOB / VS Code / agy：直接 `npx ...`。
  - 四份設定都連到 `http://localhost:9222` 的 CDP endpoint，不使用 `--extension`，因此不需要安裝 Playwright 擴充。使用前先啟動帶 `--remote-debugging-port=9222` 與獨立 `--user-data-dir` 的專用 Edge；Chromium 136+ 不允許對預設 profile 開啟 remote debugging。以 `http://localhost:9222/json/version` 確認 endpoint 可達。
  - `--caps=devtools` 開啟 `browser_annotate`（圈選與註解回饋）、`browser_start_video`／`browser_stop_video`（WebM 錄影）、`browser_video_show_actions`（操作標註）、trace 與操作錄製；`browser_start_recording` 產生的是 Playwright code，並非影片。瀏覽器工具會沿用**該專用 profile 的登入身分**，截圖與錄影可能包含敏感資訊，未經確認不要上傳。
  - **保留開新頁的操作流程**：開始任務先用 `browser_tabs(action: "new")` 建立專用分頁，不在原有分頁直接導覽；annotation 只對本次新頁操作。`--caps=devtools` 不會改變 CDP 連線或自動替任務開新分頁；使用完只關本次建立的分頁，不呼叫會影響整個 browser 的關閉操作。
  - **錄影範圍不是只有目前頁**：MCP 0.0.86 的 `browser_start_video` 會錄下該連線已納管的所有分頁，以及錄影中新增的頁。若要保證不錄到原有頁面，須使用只含測試頁的暫時隔離 context／連線；不要把 `--isolated` 寫成既有工作流的預設，否則會失去專用 profile 的登入狀態。單純切到新 tab 不足以限制錄影範圍。
  - **FFmpeg 是另外下載的錄影依賴**：新版 MCP 可能要求比 E2E 套件更新的 FFmpeg revision；缺少時照錯誤指定版本安裝即可，無須安裝 `@playwright/cli`。執行 `@playwright/mcp install-browser ffmpeg` 前，先在該次安裝的 process 設 `PLAYWRIGHT_SKIP_BROWSER_GC=1`，結束後還原環境變數，避免 Playwright installer 自動清除其他版本仍使用的 browser cache（2026-11-19 實測會移除舊 Chromium、Headless Shell 與 FFmpeg）。
  - `npx -y` 避免首次安裝確認卡住 stdio 初始化。Claude 的 local scope 設定（`~/.claude.json` 的專案項目）可能覆寫 repo 的 `.mcp.json`；切換 CDP／extension 或 capabilities 時要一併核對，重新連線 MCP，必要時新開 session。Windows 工作目錄路徑的磁碟機字母大小寫可能形成不同專案項目，勿只憑另一個 terminal 的 `claude mcp get playwright` 認定目前 session 使用同一份設定。

### Skills 現況

各工具的 skills **並非完全一致**：

- `.claude/skills/` 與 `.bob/skills/`：互為鏡像 —— caveman、find-skills、github-actions-docs、high-risk-pr-workflow、integration-test-runner、new-domain-scaffold、openapi-doc-gen、skill-creator、skills-cli（9 個）。
- `.agents/skills/`（agy CLI）：另一套 —— cavecrew、caveman 全家族（commit / compress / help / init / review / stats）、compress、find-skills。
- `caveman-init`、`caveman-stats`、`cavecrew` 是條件式入口：repo 未提供各自的 companion runtime／presets。缺少時回報不可用，不自動下載、建立或安裝；`cavecrew` 可改用目前 host 已提供的 agent 或直接處理，不虛構 agent type。
- `skills-lock.json` 記錄已匯入外部 skills 的來源與 hash，不是所有本地技能內容的真相源。本地客製內容以 repo 內定義與 Git 歷史為準；四個自製 workflows（high-risk-pr-workflow、integration-test-runner、new-domain-scaffold、openapi-doc-gen）不建立虛構 upstream／hash。更新外部來源時另查 provenance，不用 lock metadata 蓋掉本地修改。
- `compress` 與 `caveman-compress` 保留各自入口；前者已補入相同的敏感路徑啟發式、空輸入／首次回覆防護與 backup 回讀檢查。這不是內容 secret scanner，也不表示兩套 runtime 完全等價；傳送普通檔名的內容到模型前仍須確認授權與敏感資料。

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

### 本機 Bob Git notes hook（按需維護）

這次檢查的本機 `post-commit` 原有遠端探測與 notes fetch／push；本機修補在同步函式入口加上 `BOB_NOTES_REMOTE_SYNC=1` 檢查，未設或不是 `1` 時不進入同步流程，保留本機 notes 行為。**這是本機修補，不是 Bob 原生已移除同步功能的保證**，也不表示已證實 notes 曾發布。

這份 hook 不進版控，clone 不會取得修補。Bob 重新安裝或改寫 hooks 後，用 `git config --show-origin --get core.hooksPath` 確認生效目錄（未設定時為 `.git/hooks`），再檢查來源並執行下方的副本測試；不需要每次 commit 重查。ignored 不代表停用，任何工具執行 Git 都可能觸發 hook；既有 `.githooks/pre-push` 測試 gate 不受此次修補影響。

若確實需要發布，須另行審查目的地與**整段 `refs/notes/bob` 歷史**，處理絕對路徑及敏感資料，並取得發布授權；opt-in 本身不是遮罩或發布授權。

### 離線驗證與未涵蓋的界線

AI infra 的回歸測試不需啟動 Spring／Oracle，也不該為了驗證而把文件內容送到模型、執行真 commit／push，或連到已登入的瀏覽器。

從 repo 根目錄執行：

```bash
# 合成 PostToolUse payload：監看路徑、Windows 分隔符號、無關路徑、空／壞 JSON、fail-open
node --test .claude/hooks/ai-doc-sync-check.test.cjs

# 暫存檔 + mock model backend：敏感路徑、空輸入／首次回覆、backup、驗證與還原
python -I -B -m unittest discover -s .agents/skills/compress/scripts/tests -p 'test_*.py' -v

# 已檢視的本機 Bob hook：只執行暫存副本，使用合成 notes 與替身 Git
python -I -B .githooks/tests/test_bob_notes_hook.py -v

git diff --check
```

- **同步提醒 hook 是 best-effort**：只對 host 交給 `PostToolUse` 的 `Edit|Write|MultiEdit` 事件提醒，不自動同步，也不阻擋；Bash、其他編輯器或沒有載入該 hook 的 host 不保證觸發。測試通過不代表所有 harness 都會執行它。
- **靜態設定**：MCP 用 JSON parser 只檢查預期 server／transport／公開參數與 env 引用；不要把整份設定當 log。Bob YAML 用現有 parser 的安全模式（例如 SnakeYAML `SafeConstructor`，禁止 duplicate keys）解析，確認 `customModes` 是非空陣列，不執行其中指令。
- **鏡像與引用**：Claude／Bob 檔案清單應一致，內容比對可正規化 UTF-8 BOM 與換行；不要把既有 encoding-only 差異當功能缺陷。兩份指令入口依各自位置解析 imports 後再比對，不能因相對路徑字串不同就判 drift。
- **本機 Bob hook 副本測試**：8 個案例涵蓋 pending 不存在／空檔／無 matching note／有 matching note，以及 opt-in 未設／設為 `0`；未驗證 `opt-in=1` 的發布流程。缺少本機 hook 或必要 Bash 工具會明確 skip，**skip 不算安全檢查通過**。可用 `--hook PATH` 指定另一份經檢視的副本；替身 Git 不是執行任意不可信 hook 的安全沙箱。
- **尚須實際客戶端查驗**：instruction imports 的 runtime 展開、user／project-local／host 注入的生效來源、agy 的 Podman stdio 初始化，以及 MCP 連線都不在上述離線測試的證明範圍。需要使用它們時，另依授權確認啟動、下載與登入身分。
