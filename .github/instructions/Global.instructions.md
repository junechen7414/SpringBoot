---
name: instructions
description: Every conversation
---

## 回應語言偏好

所有回應必須使用繁體中文（正體中文）。絕對不可使用簡體中文。若遇到使用英文更好理解的情況下，才改以英文回應；回應優先順序如下：
1. 繁體中文（首選）
2. 英文（使用英文更好理解的情況，一般是某些不容易翻譯的詞彙如hash等等）

在任何情況下，不得使用簡體中文。請確保未來的所有建議、說明與程式碼註解回應都遵循此語言偏好。

## 指令執行偏好

1. 容器管理工具：一律使用 podman 而非 docker。
2. 套件管理器：一律使用 pnpm。嚴禁使用 npm 或 yarn。
3. Shell 環境適配：執行指令前須先偵測當前 Shell（PowerShell 或 CMD），並使用對應語法：
   - PowerShell (`pwsh`)：指令串接用 `;`，執行當前目錄腳本用 `./script`（例如 `./gradlew`）
   - CMD (`cmd.exe`)：指令串接用 `&&`，執行當前目錄腳本直接用名稱（例如 `gradlew`，不加 `./`）
   - 不可假設預設 Shell 環境，須透過環境資訊判斷後再決定指令語法

<!-- 專案基礎 -->
@../../docs/agents/01-overview.md
@../../docs/agents/02-setup.md

<!-- 開發流程 -->
@../../docs/agents/03-git-workflow.md
@../../docs/agents/04-git-branch-cleanup.md
@../../docs/agents/05-code-standards.md

<!-- 架構與設計 -->
@../../docs/agents/06-architecture.md
@../../docs/agents/07-dependency-check.md

<!-- 測試與維運 -->
@../../docs/agents/08-testing.md
@../../docs/agents/09-monitoring.md
@../../docs/agents/10-troubleshooting.md

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