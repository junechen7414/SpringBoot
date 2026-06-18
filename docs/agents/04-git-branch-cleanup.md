# Git 分支清理指南（Windows PowerShell）

本指南說明如何在 Windows 環境下使用 Git alias 定期清理本地分支。

> **定位**：本專案採[主幹開發](./03-git-workflow.md)，多數改動直接在 `main` 上進行，**分支是例外**（僅高風險改動才開）。因此分支清理的需求遠低於以往；但只要你開過分支、合併後留下 `[gone]` 殘餘，本指南依然適用。

## 📋 目錄

- [概述](#概述)
- [一次性設定](#一次性設定)
- [定期清理操作](#定期清理操作)
- [進階用法](#進階用法)
- [常見問題](#常見問題)

---

## 概述

### 什麼是 `[gone]` 分支？

當遠端分支被刪除（通常是 PR 合併後），對應的本地分支會標記為 `[gone]`：

```
* main                                  f319b2d [origin/main]
  feature/add-payment                   abc1234 [origin/feature/add-payment: gone]
  docs/update-readme                    def5678 [origin/docs/update-readme: gone]
```

這些 `[gone]` 分支：
- ✅ 遠端分支已刪除（PR 已合併）
- ❌ 本地分支仍然存在
- 💾 佔用磁碟空間
- 🔍 造成 `git branch` 輸出混亂

### 清理策略

| 操作 | 頻率 | 說明 |
|------|------|------|
| **設定 Alias** | 一次性 | 設定後永久有效 |
| **查看 [gone] 分支** | 每週 | 了解有哪些分支可清理 |
| **清理 [gone] 分支** | 每週或每月 | 刪除已合併的本地分支 |
| **清理已合併分支** | 每月 | 刪除已合併到 main 的分支 |

---

## 一次性設定

### 步驟 1: 開啟終端機

開啟您常用的終端機：
- **Git Bash**（推薦，Windows 安裝 Git 時自帶）
- **PowerShell**
- **CMD**

**注意**：本指南使用 **Git Bash** 語法（適用於 Linux/macOS/Git Bash）。

### 步驟 2: 設定 Git Alias

複製並執行以下指令（**僅需執行一次**）：

```bash
# Alias 1: 顯示 [gone] 分支（安全，僅查看）
git config --global alias.show-gone "!git fetch --prune && git branch -vv | grep ': gone]'"

# Alias 2: 刪除 [gone] 分支（常用）
git config --global alias.prune-local "!git fetch --prune && git branch -vv | grep ': gone]' | awk '{print \$1}' | xargs -r git branch -D"

# Alias 3: 清理已合併分支（安全）
git config --global alias.cleanup "!git branch --merged main | grep -v '\\*\\|main\\|master' | xargs -r git branch -d"
```

**PowerShell 使用者請改用以下指令**：

```powershell
# Alias 1: 顯示 [gone] 分支
git config --global alias.show-gone "!git fetch --prune; git branch -vv | Select-String ': gone]'"

# Alias 2: 刪除 [gone] 分支
git config --global alias.prune-local "!git fetch --prune; git branch -vv | Select-String ': gone]' | ForEach-Object { `$_.Line.Trim().Split()[0] } | ForEach-Object { git branch -D `$_ }"

# Alias 3: 清理已合併分支
git config --global alias.cleanup "!git branch --merged main | Select-String -NotMatch '\\*|main|master' | ForEach-Object { `$_.Line.Trim() } | ForEach-Object { git branch -d `$_ }"
```

### 步驟 3: 驗證設定

```bash
# 查看已設定的 alias
git config --global --get-regexp alias

# 應該會看到類似輸出：
# alias.show-gone !git fetch --prune && git branch -vv | grep ': gone]'
# alias.prune-local !git fetch --prune && git branch -vv | grep ': gone]' | awk '{print $1}' | xargs -r git branch -D
# alias.cleanup !git branch --merged main | grep -v '\\*\\|main\\|master' | xargs -r git branch -d
```

✅ **設定完成！** 這些 alias 會永久保存在 `~/.gitconfig` 中，不需要重複設定。

---

## 定期清理操作

### 基本清理流程（推薦每週執行）

#### 1️⃣ 查看有哪些 [gone] 分支

```bash
git show-gone
```

**輸出範例**：
```
  feature/add-payment                   abc1234 [origin/feature/add-payment: gone]
  docs/update-readme                    def5678 [origin/docs/update-readme: gone]
  fix/bug-123                           ghi9012 [origin/fix/bug-123: gone]
```

#### 2️⃣ 確認後刪除 [gone] 分支

```bash
git prune-local
```

**輸出範例**：
```
Deleted branch feature/add-payment (was abc1234).
Deleted branch docs/update-readme (was def5678).
Deleted branch fix/bug-123 (was ghi9012).
```

#### 3️⃣ 驗證清理結果

```bash
git branch -vv
```

應該只會看到：
- `main` 分支
- 尚未合併的工作分支（如果有）

---

### 完整清理流程（推薦每月執行）

```bash
# 1. 切換到 main 分支
git checkout main

# 2. 更新 main 到最新版本
git pull origin main

# 3. 清理遠端分支參考
git fetch --prune

# 4. 刪除 [gone] 分支
git prune-local

# 5. 清理已合併到 main 的分支
git cleanup

# 6. 查看最終狀態
git branch -vv
```

---

## 進階用法

### 手動清理（不使用 alias）

如果您想手動執行清理，可以使用以下指令：

#### 查看 [gone] 分支

```bash
git fetch --prune
git branch -vv | grep ': gone]'
```

#### 刪除單個 [gone] 分支

```bash
git branch -D <branch-name>
```

#### 批次刪除所有 [gone] 分支

```bash
git fetch --prune
git branch -vv | grep ': gone]' | awk '{print $1}' | xargs -r git branch -D
```

### 互動式清理（逐一確認）

```bash
# 查看 [gone] 分支並逐一詢問
git fetch --prune
git branch -vv | grep ': gone]' | while read -r line; do
    branch=$(echo "$line" | awk '{print $1}')
    read -p "Delete branch '$branch'? (y/N) " confirm
    if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
        git branch -D "$branch"
    fi
done
```

---

## 常見問題

### Q1: 為什麼要定期清理分支？

**答**：
- 減少 `git branch` 輸出的混亂
- 釋放磁碟空間（雖然通常不多）
- 避免誤操作舊分支
- 保持工作區整潔

### Q2: 刪除本地分支會影響遠端嗎？

**答**：不會。刪除本地分支不會影響遠端 repository。如果需要，您隨時可以從遠端重新 checkout。

### Q3: 如果誤刪分支怎麼辦？

**答**：
1. 使用 `git reflog` 找到分支的最後一次 commit hash
2. 使用 `git checkout -b <branch-name> <commit-hash>` 恢復分支

範例：
```powershell
# 查看最近的操作
git reflog

# 恢復分支（假設 commit hash 是 abc1234）
git checkout -b feature/recovered abc1234
```

### Q4: `git branch -d` 和 `git branch -D` 的差異？

**答**：
- `-d`（小寫）：安全刪除，僅刪除已合併的分支
- `-D`（大寫）：強制刪除，刪除任何分支（包括未合併的）

`prune-local` 使用 `-D` 是因為 `[gone]` 分支通常已經合併到遠端。

### Q5: 可以自動化清理嗎？

**答**：可以，但不建議。建議手動執行以避免誤刪重要分支。如果確實需要自動化，可以：

1. 建立 Bash 腳本：
   ```bash
   #!/bin/bash
   # cleanup-branches.sh
   cd /path/to/your/repo
   git fetch --prune
   git branch -vv | grep ': gone]' | awk '{print $1}' | xargs -r git branch -D
   ```

2. 使用 cron 或 Windows 工作排程器定期執行（不推薦）

### Q6: 如何查看 alias 的完整定義？

**答**：
```bash
# 查看特定 alias
git config --global alias.prune-local

# 查看所有 alias
git config --global --get-regexp alias
```

### Q7: 如何移除已設定的 alias？

**答**：
```bash
git config --global --unset alias.prune-local
git config --global --unset alias.show-gone
git config --global --unset alias.cleanup
```

---

## 建議的清理時程表

| 時機 | 操作 | 指令 |
|------|------|------|
| **每週一** | 查看 [gone] 分支 | `git show-gone` |
| **每週一** | 清理 [gone] 分支 | `git prune-local` |
| **每月初** | 完整清理 | 執行「完整清理流程」 |
| **PR 合併後** | 立即清理 | `git fetch --prune` |

---

## 相關資源

- **Git Alias 官方文件**: https://git-scm.com/book/en/v2/Git-Basics-Git-Aliases
- **Git 分支管理**: https://git-scm.com/book/en/v2/Git-Branching-Branch-Management
- **專案 Git 工作流程**: 參考 `AGENTS.md` 的 Git 工作流程章節

---

## 附錄：Alias 定義參考

完整的 alias 定義保存在專案根目錄的 `.gitconfig-aliases` 檔案中，供參考使用。

**最後更新**: 2026-05-28
**維護者**: Bobby