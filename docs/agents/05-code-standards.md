## 程式碼與文件慣例

### 程式碼

- **可讀性優先於精簡或微幅效能**：較短或較快、但難以理解的寫法不採用。
- **優先使用 Java 25 新特性**（Virtual Threads、pattern matching、records 等）。

### 文件放置與命名

專案文件一律放在 `docs/` 下，使用 kebab-case，依類型加後綴：`*-guide.md`、`*-plan.md`、`*-explanation.md`、`*-workflow.md` / `*-diagram.md`、`*-quick-*.md`。根目錄只放必要的設定檔。

### Agent 文件維護

- **跨工具同步**：改動 `docs/agents/*`、`AGENTS.md`、skills、MCP 設定或 agents 時，要讓其他 AI 工具的對應檔保持一致。對應表見 `11-ai-tools-overview.md`；可委派 `ai-doc-sync` subagent 做 dry-run 比對。
- **入口檔只放常駐內容**：`AGENTS.md` 與 `.github/instructions/Global.instructions.md` 只 `@`-import 每個 session 都需要的文件；只在動到特定檔案時才需要的參考資料，改成「何時讀哪份文件」的一行 pointer。
- **過時就修**：發現文件與實際情況不符時，立即提出並在確認後修正。
- **驗證過的做法要寫進 repo**：查出或驗證了某個正確做法、環境修法或踩坑解法，就寫進最貼近的既有文件（例如 `08-testing.md`、`10-troubleshooting.md`），附上「為何」與「如何套用」。不要只留在個人筆記、本機路徑或對話裡。沒有合適的章節時，加到 `10-troubleshooting.md`。
