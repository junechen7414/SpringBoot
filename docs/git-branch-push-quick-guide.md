# Git 分支推送快速指南 ⚡

## 🎯 目標

將本地修改分成兩個分支推送到 GitHub：
1. **`docs/add-rebase-guides`** - rebase 相關文檔（包含根目錄的三個文件）
2. **`chore/update-config-comments`** - 配置文件註解更新

---

## 📋 快速執行命令

### 第一部分：文檔分支

```bash
# 1. 從當前分支創建新的文檔分支
git checkout -b docs/add-rebase-guides

# 2. 添加所有文檔文件（包含 docs/ 目錄和根目錄的文件）
git add docs/git-branch-push-plan.md docs/git-branch-push-quick-guide.md docs/git-branch-strategy-diagram.md
git add conflict-resolution-strategy.md rebase-workflow.md rebase-plan.md rebase-quick-reference.md
git add .github/instructions/Global.instructions.md

# 3. 提交
git commit -m "docs: add comprehensive Git workflow and rebase guides

- Add Git branch push planning documents in docs/
- Add rebase workflow diagrams with Mermaid
- Add conflict resolution strategy guide
- Add quick reference guides for common scenarios
- Update Global.instructions.md with documentation standards"

# 4. 推送到 GitHub
git push -u origin docs/add-rebase-guides
```

### 第二部分：配置分支

```bash
# 5. 切換回原分支（目前是 docs/e2e-profile-validation）
git checkout docs/e2e-profile-validation

# 6. 創建並切換到配置分支
git checkout -b chore/update-config-comments

# 7. 添加配置文件
git add .gitignore src/main/resources/application.yml src/test/resources/application-test.yml

# 8. 提交
git commit -m "chore: update configuration files with better documentation

- Add .bob directory to .gitignore for IBM BOB tool files
- Add e2e profile documentation in application.yml
- Add clarifying comment for H2 password field in test config"

# 9. 推送到 GitHub
git push -u origin chore/update-config-comments
```

**注意：** 目前您在 `docs/e2e-profile-validation` 分支上，所以步驟 5 要切換回這個分支。

---

## ✅ 驗證命令

```bash
# 查看所有分支
git branch -a

# 查看遠端分支
git ls-remote --heads origin
```

---

## 🔄 後續步驟

1. 前往 GitHub 倉庫
2. 為每個分支創建 Pull Request
3. 填寫 PR 描述（參考完整計劃文檔）
4. 等待審查和合併

---

## 📚 詳細說明

完整的步驟說明、PR 範本和故障排除，請參閱：
[`git-branch-push-plan.md`](git-branch-push-plan.md)

---

## ⚠️ 注意事項

- 確認當前在正確的分支上再執行命令
- 如果原始分支不是 `main`，請在步驟 5 中調整
- 推送前使用 `git status` 確認暫存的文件正確
- 如果遇到問題，使用 `git status` 和 `git branch` 檢查狀態

---

**準備好了嗎？開始執行吧！** 🚀