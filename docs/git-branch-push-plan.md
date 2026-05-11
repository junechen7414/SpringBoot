# Git 分支創建與推送計劃

## 📋 概述

本計劃將指導您如何將本地修改適當地組織成兩個獨立的分支並推送到 GitHub。

**目前狀態：** 您在 `docs/e2e-profile-validation` 分支上

### 修改內容分類

**分支 1: `docs/add-rebase-guides`** (文檔類)
- `docs/git-branch-push-plan.md` - 完整的分支推送計劃
- `docs/git-branch-push-quick-guide.md` - 快速參考指南
- `docs/git-branch-strategy-diagram.md` - 視覺化流程圖
- `conflict-resolution-strategy.md` - 衝突解決策略文檔
- `rebase-workflow.md` - Rebase 工作流程圖
- `rebase-plan.md` - Rebase 執行計劃
- `rebase-quick-reference.md` - Rebase 快速參考
- `.github/instructions/Global.instructions.md` - 更新文檔管理規範

**分支 2: `chore/update-config-comments`** (配置類)
- `.gitignore` - 新增 `.bob` 目錄忽略規則
- `src/main/resources/application.yml` - 新增 e2e profile 說明
- `src/test/resources/application-test.yml` - 新增密碼欄位註解

---

## 🎯 執行步驟

### 步驟 1: 檢查當前狀態

```bash
# 查看當前分支
git branch

# 查看當前狀態
git status

# 查看遠端連接
git remote -v
```

**預期結果：**
- 確認當前在哪個分支（可能是 `main` 或其他）
- 看到所有未提交的修改
- 確認遠端倉庫連接正常

---

### 步驟 2: 創建並切換到文檔分支

```bash
# 從當前分支（docs/e2e-profile-validation）創建新分支
git checkout -b docs/add-rebase-guides
```

**說明：**
- `-b` 參數表示創建新分支並立即切換過去
- 分支名稱使用 `docs/` 前綴表示這是文檔相關的修改
- 新分支會基於當前的 `docs/e2e-profile-validation` 分支創建

---

### 步驟 3: 提交文檔文件

```bash
# 添加所有文檔相關文件
git add docs/git-branch-push-plan.md
git add docs/git-branch-push-quick-guide.md
git add docs/git-branch-strategy-diagram.md
git add conflict-resolution-strategy.md
git add rebase-workflow.md
git add rebase-plan.md
git add rebase-quick-reference.md
git add .github/instructions/Global.instructions.md

# 查看暫存的文件
git status

# 提交這些文件
git commit -m "docs: add comprehensive Git workflow and rebase guides

- Add Git branch push planning documents in docs/
- Add rebase workflow diagrams with Mermaid
- Add conflict resolution strategy guide
- Add quick reference guides for common scenarios
- Update Global.instructions.md with documentation standards"
```

**Commit Message 說明：**
- 使用 `docs:` 前綴表示文檔類型的提交
- 第一行是簡短摘要
- 空一行後列出詳細變更內容

---

### 步驟 4: 推送文檔分支到 GitHub

```bash
# 推送新分支到遠端
git push -u origin docs/add-rebase-guides
```

**說明：**
- `-u` 參數設置上游追蹤，之後可以直接使用 `git push`
- `origin` 是遠端倉庫的名稱
- 推送後會在 GitHub 上創建這個新分支

**預期輸出：**
```
Enumerating objects: X, done.
Counting objects: 100% (X/X), done.
...
To https://github.com/your-username/SpringBoot.git
 * [new branch]      docs/add-rebase-guides -> docs/add-rebase-guides
Branch 'docs/add-rebase-guides' set up to track remote branch 'docs/add-rebase-guides' from 'origin'.
```

---

### 步驟 5: 切換回原分支並創建配置分支

```bash
# 查看當前在哪個分支
git branch

# 切換回原始分支（docs/e2e-profile-validation）
git checkout docs/e2e-profile-validation

# 創建並切換到配置更新分支
git checkout -b chore/update-config-comments
```

**注意：**
- 原始分支是 `docs/e2e-profile-validation`
- 確保切換回包含所有未提交修改的原始分支
- 配置文件的修改應該還在這個分支上

---

### 步驟 6: 提交配置文件修改

```bash
# 添加配置文件
git add .gitignore
git add src/main/resources/application.yml
git add src/test/resources/application-test.yml

# 查看暫存的文件
git status

# 提交這些文件
git commit -m "chore: update configuration files with better documentation

- Add .bob directory to .gitignore for IBM BOB tool files
- Add e2e profile documentation in application.yml
- Add clarifying comment for H2 password field in test config"
```

**Commit Message 說明：**
- 使用 `chore:` 前綴表示維護性質的修改
- 清楚說明每個配置文件的變更目的

---

### 步驟 7: 推送配置分支到 GitHub

```bash
# 推送新分支到遠端
git push -u origin chore/update-config-comments
```

**預期輸出：**
```
Enumerating objects: X, done.
Counting objects: 100% (X/X), done.
...
To https://github.com/your-username/SpringBoot.git
 * [new branch]      chore/update-config-comments -> chore/update-config-comments
Branch 'chore/update-config-comments' set up to track remote branch 'chore/update-config-comments' from 'origin'.
```

---

## ✅ 驗證步驟

### 在本地驗證

```bash
# 查看所有分支（包括遠端）
git branch -a

# 應該看到：
# * chore/update-config-comments
#   docs/add-rebase-guides
#   main (或其他原始分支)
#   remotes/origin/chore/update-config-comments
#   remotes/origin/docs/add-rebase-guides
```

### 在 GitHub 上驗證

1. 訪問您的 GitHub 倉庫
2. 點擊分支下拉選單
3. 應該看到兩個新分支：
   - `docs/add-rebase-guides`
   - `chore/update-config-comments`

---

## 🔄 後續步驟：創建 Pull Request

### 為文檔分支創建 PR

1. 在 GitHub 上，切換到 `docs/add-rebase-guides` 分支
2. 點擊 "Compare & pull request" 按鈕
3. 填寫 PR 資訊：

**標題：**
```
docs: Add comprehensive Git rebase guides
```

**描述範例：**
```markdown
## 📝 變更說明

新增三個 Git rebase 相關的指導文檔，幫助團隊成員更好地理解和執行 rebase 操作。

## 📄 新增文件

- `conflict-resolution-strategy.md` - 詳細的衝突解決策略和流程
- `rebase-workflow.md` - 包含 Mermaid 圖表的工作流程視覺化
- `rebase-plan.md` - 完整的 rebase 執行計劃和檢查清單

## 🎯 目的

- 標準化團隊的 rebase 流程
- 提供清晰的衝突解決指引
- 減少 rebase 操作中的錯誤

## ✅ 檢查清單

- [x] 文檔內容完整且準確
- [x] 包含實用的範例和命令
- [x] 使用 Mermaid 圖表增強可讀性
- [x] 提供快速參考指南
```

4. 選擇審查者（如果需要）
5. 點擊 "Create pull request"

### 為配置分支創建 PR

1. 在 GitHub 上，切換到 `chore/update-config-comments` 分支
2. 點擊 "Compare & pull request" 按鈕
3. 填寫 PR 資訊：

**標題：**
```
chore: Update configuration files with better documentation
```

**描述範例：**
```markdown
## 🔧 變更說明

改進配置文件的文檔註解，提高可讀性和可維護性。

## 📝 變更內容

### `.gitignore`
- 新增 `.bob` 目錄的忽略規則（IBM BOB 工具生成的文件）

### `application.yml`
- 新增 `e2e` profile 的說明註解
- 完善 profile 使用指南

### `application-test.yml`
- 為 H2 資料庫的空密碼欄位新增說明註解
- 澄清這是 H2 預設配置的有意設計

## 🎯 目的

- 提高配置文件的自我說明性
- 幫助新成員快速理解配置用途
- 避免對空密碼欄位的誤解

## ✅ 檢查清單

- [x] 註解清晰且準確
- [x] 不影響現有功能
- [x] 遵循專案的註解風格
```

4. 選擇審查者（如果需要）
5. 點擊 "Create pull request"

---

## 🔙 如果需要回滾

### 刪除本地分支

```bash
# 切換到其他分支
git checkout main

# 刪除本地分支
git branch -D docs/add-rebase-guides
git branch -D chore/update-config-comments
```

### 刪除遠端分支

```bash
# 刪除遠端分支
git push origin --delete docs/add-rebase-guides
git push origin --delete chore/update-config-comments
```

---

## 📚 Git 分支命名慣例

本專案使用以下分支命名慣例：

- `feature/` - 新功能開發
- `bugfix/` - Bug 修復
- `hotfix/` - 緊急修復
- `docs/` - 文檔更新
- `chore/` - 維護性工作（配置、依賴更新等）
- `refactor/` - 代碼重構
- `test/` - 測試相關

---

## 💡 最佳實踐提醒

1. **Commit Message 格式**
   - 使用類型前綴（feat, fix, docs, chore 等）
   - 第一行簡短摘要（50 字元內）
   - 空一行後詳細說明

2. **分支管理**
   - 保持分支專注於單一目的
   - 使用描述性的分支名稱
   - 定期同步主分支的更新

3. **Pull Request**
   - 提供清晰的描述和上下文
   - 包含變更的目的和影響
   - 添加相關的檢查清單

4. **推送前檢查**
   - 確認只包含相關的修改
   - 檢查 commit message 是否清晰
   - 驗證沒有包含敏感資訊

---

## 🆘 常見問題

### Q: 如果推送時出現權限錯誤？

**A:** 確認您的 GitHub 認證設定：

```bash
# 檢查遠端 URL
git remote -v

# 如果使用 HTTPS，可能需要更新認證
# 如果使用 SSH，確認 SSH key 已添加到 GitHub
```

### Q: 如果不小心在錯誤的分支上提交了？

**A:** 使用 cherry-pick 移動 commit：

```bash
# 記下 commit hash
git log --oneline

# 切換到正確的分支
git checkout correct-branch

# 應用該 commit
git cherry-pick <commit-hash>

# 回到錯誤的分支並重置
git checkout wrong-branch
git reset --hard HEAD~1
```

### Q: 如何查看兩個分支的差異？

**A:** 使用 diff 命令：

```bash
# 查看兩個分支的差異
git diff docs/add-rebase-guides..chore/update-config-comments

# 查看分支與 main 的差異
git diff main..docs/add-rebase-guides
```

---

## 📞 需要幫助？

如果在執行過程中遇到問題：

1. 使用 `git status` 查看當前狀態
2. 使用 `git log --oneline --graph --all` 查看分支結構
3. 查閱 Git 官方文檔：https://git-scm.com/doc
4. 或尋求團隊成員協助

---

**祝您順利完成分支創建和推送！** 🎉