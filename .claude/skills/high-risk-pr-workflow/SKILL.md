---
name: high-risk-pr-workflow
description: >
  高風險變更時的完整 branch + PR 流程，涵蓋建分支、跑測試、開 PR、
  加 label、清理分支。觸發時機：改 CI workflow、DB migration、
  跨 domain 重構、大型功能，或任何使用者明確要開 PR 的情境。
---

# High-Risk PR Workflow

## 何時用這個流程

以下情境**必須**走 branch + PR，不要直接 push `main`：

- 改 `.github/workflows/*`（CI 本身）
- 改 `src/main/resources/db/migration/`（DB migration）
- 跨 domain 重構或大型功能（多個 commit、難一次驗證）
- 任何「測試抓不到但壞了影響大」的變更

其餘日常改動 → 直接在 `main` 上做。

---

## Step 1：建立分支

```bash
git checkout main
git pull origin main
git checkout -b <prefix>/<task-description>
```

**Branch 命名**（小寫、`-` 分隔）：

| 前綴 | 用途 |
|------|------|
| `feature/` | 新功能 |
| `fix/` | Bug 修復 |
| `hotfix/` | 緊急生產修復 |
| `refactor/` | 重構 |
| `config/` | 設定檔變更 |
| `docs/` | 文件更新 |
| `test/` | 測試相關 |
| `chore/` | 建置/工具更新 |

範例：`feature/add-payment-module`、`fix/order-creation-bug`

---

## Step 2：開發與提交

```bash
# 開發完成後
git add <files>
git commit -m "<type>(<scope>): <subject>"
```

**Commit 格式（Conventional Commits）**：
- `type(scope): subject`（祈使句、小寫、不加句點）
- 常用 type：`feat` `fix` `docs` `refactor` `test` `chore` `style`
- 範例：`feat(order): add bulk order creation endpoint`
- ❌ 不加 AI 協作者署名（`Co-Authored-By: Claude`、`🤖 Generated with Claude Code` 等）；PR body（Step 3）亦同。`.githooks/commit-msg` hook 會擋下含這些署名的 commit。

---

## Step 3：Push 並開 PR

```bash
git push origin <branch-name>
```

Push 後 Git 會輸出建立 PR 的連結，直接點擊。

若用 **GitHub MCP** 工具開 PR：

```
create_pull_request(
  title: "<type>(<scope>): <subject>",
  body: "變更目的、背景、主要功能說明",
  base: "main",
  head: "<branch-name>"
)
```

> `create_pull_request` **沒有 labels 欄位**，需另外加（見 Step 4）。

---

## Step 4：加 PR Labels

**`create_pull_request`/`update_pull_request` 無法加 label**，改用：

```
issue_write(
  method: "update",
  issue_number: <PR 號>,
  labels: ["<label1>", "<label2>"]
)
```

> PR 在 GitHub API 中也是 issue，所以 `issue_write` 可以操作 PR。

**可用 Labels 對照表**：

| 變更類型 | Label |
|---------|-------|
| 文件更新 | `documentation` |
| 新功能 | `enhancement` |
| Bug 修復 | `bugfix` |
| 重構 | `refactor` |
| 測試相關 | `test` |
| 依賴更新 | `dependencies` |
| 設定變更 | `config` |
| 破壞性變更 | `breaking-change` |
| E2E 測試 | `e2e-test` |
| 建置/工具 | `chore` |

> Label 必須已存在於 repo，MCP 無法新建 label。

---

## Step 5：合併後清理分支

PR 合併到 `main` 後：

```bash
# 切回 main 並更新
git checkout main
git pull origin main

# 刪除遠端分支（若 GitHub 設定未自動刪除）
git push origin --delete <branch-name>

# 刪除本地分支
git branch -d <branch-name>
```

詳細清理步驟見 `docs/agents/04-git-branch-cleanup.md`。

---

## push main 的副作用提醒

PR 合併到 `main` 後，CI 會連帶：
- 推 `latest` image 到 `ghcr.io`
- 觸發下游 E2E
- 重新產生並推送 `swagger.json`

這是預期行為，確保 PR 的測試通過即可。
