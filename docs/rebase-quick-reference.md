# Git Rebase 快速參考指南

## 🚀 快速執行步驟

```bash
# 1. 確認當前在 add-flyway 分支
git branch

# 2. 檢查工作目錄狀態
git status

# 3. 如有未提交變更，先處理
git stash  # 或 git commit -m "WIP"

# 4. 獲取最新的 main
git fetch origin main

# 5. 執行 rebase
git rebase main

# 6. 如有衝突，解決後繼續
git add <resolved-files>
git rebase --continue

# 7. 驗證結果
git log --oneline --graph -10
git diff main

# 8. 推送（如需要）
git push origin add-flyway --force-with-lease
```

---

## 📋 執行前檢查清單

- [ ] 確認當前在 `add-flyway` 分支
- [ ] 工作目錄乾淨（無未提交變更）
- [ ] 已備份重要變更
- [ ] 了解可能的衝突檔案
- [ ] 準備好解決衝突的策略

---

## 🔧 常用命令

### 查看狀態
```bash
# 查看當前分支
git branch

# 查看工作目錄狀態
git status

# 查看 commit 歷史
git log --oneline --graph --decorate -10

# 查看分支差異
git log --oneline main..add-flyway  # add-flyway 有但 main 沒有的
git log --oneline add-flyway..main  # main 有但 add-flyway 沒有的
```

### Rebase 操作
```bash
# 開始 rebase
git rebase main

# 繼續 rebase（解決衝突後）
git rebase --continue

# 跳過當前 commit
git rebase --skip

# 中止 rebase
git rebase --abort
```

### 衝突處理（用戶主導）
```bash
# 查看衝突檔案
git status

# 查看衝突內容
git diff

# 【重要】遇到衝突時：
# 1. 暫停操作
# 2. 向用戶報告衝突情況
# 3. 等待用戶指示如何處理

# 用戶解決後，標記為已解決
git add <file>

# 繼續 rebase
git rebase --continue
```

### 驗證與推送
```bash
# 查看與 main 的差異
git diff main

# 查看特定檔案的差異
git diff main -- build.gradle

# 推送到遠端（首次）
git push origin add-flyway

# 強制推送（rebase 後）
git push origin add-flyway --force-with-lease
```

---

## ⚠️ 衝突解決策略

**重要：遇到衝突時，立即暫停並詢問用戶如何處理**

### 衝突處理流程
1. 執行 `git status` 查看衝突檔案
2. 執行 `git diff` 查看衝突內容
3. **暫停並向用戶報告**：
   - 哪些檔案有衝突
   - 衝突的具體內容
   - 兩個版本的差異
4. **等待用戶指示**下一步操作

### 用戶可選方案
- **A)** 手動編輯解決衝突
- **B)** 保留 add-flyway 版本：`git checkout --ours <file>`
- **C)** 保留 main 版本：`git checkout --theirs <file>`
- **D)** 使用合併工具：`git mergetool`
- **E)** 中止 rebase：`git rebase --abort`

### 詳細說明
請參閱 [`conflict-resolution-strategy.md`](conflict-resolution-strategy.md) 獲取：
- 完整的衝突處理流程
- 每個可能衝突檔案的詳細分析
- 實際操作範例

---

## 🔄 回滾方案

### 方法 1: 使用 reflog
```bash
# 查看操作歷史
git reflog

# 回到 rebase 前（假設是 HEAD@{1}）
git reset --hard HEAD@{1}
```

### 方法 2: 使用備份分支
```bash
# rebase 前創建備份
git branch add-flyway-backup

# 如需回滾
git reset --hard add-flyway-backup

# 刪除備份
git branch -D add-flyway-backup
```

---

## ✅ 驗證檢查清單

### Git 歷史驗證
- [ ] `git log --oneline --graph` 顯示線性歷史
- [ ] add-flyway 的 commit 在 main 的最新 commit 之後
- [ ] 沒有 merge commit

### 檔案完整性驗證
- [ ] `build.gradle` 包含 Flyway 依賴
- [ ] `application.yml` 的 `ddl-auto` 是 `validate`
- [ ] `application.yml` 包含完整 Flyway 配置
- [ ] `src/main/resources/db/migration/h2/V1__initial_schema.sql` 存在
- [ ] `src/main/resources/db/migration/oracle/V1__initial_schema.sql` 存在
- [ ] `src/test/java/com/ibm/demo/FlywayMigrationTests.java` 存在
- [ ] `src/test/resources/application-test.yml` 包含 Flyway 配置
- [ ] `筆記.md` 包含 Flyway 章節

### 功能驗證
```bash
# 執行測試
./gradlew test

# 啟動應用（檢查 Flyway migration）
./gradlew bootRun

# 查看 Flyway 執行日誌
# 應該看到 "Migrating schema..." 相關訊息
```

---

## 🎯 關鍵檔案路徑

```
SpringBoot/
├── build.gradle                                          # Flyway 依賴
├── src/main/resources/
│   ├── application.yml                                   # Flyway 配置
│   └── db/migration/
│       ├── h2/V1__initial_schema.sql                    # H2 migration
│       └── oracle/V1__initial_schema.sql                # Oracle migration
├── src/test/
│   ├── java/com/ibm/demo/FlywayMigrationTests.java     # 測試
│   └── resources/application-test.yml                   # 測試配置
└── 筆記.md                                               # 文檔
```

---

## 💡 最佳實踐提醒

1. **備份優先**: 執行 rebase 前創建備份分支
2. **小步前進**: 一次解決一個衝突，不要急
3. **驗證充分**: 每個步驟都要驗證結果
4. **測試保證**: Rebase 後執行完整測試
5. **溝通清楚**: 如果分支是共享的，通知團隊成員

---

## 🆘 常見問題

### Q: Rebase 過程中出錯怎麼辦？
A: 使用 `git rebase --abort` 回到初始狀態，然後重新開始。

### Q: 忘記創建備份怎麼辦？
A: 使用 `git reflog` 查看歷史，可以回到任何之前的狀態。

### Q: 衝突太複雜無法解決？
A: 考慮使用 `git merge main` 代替 rebase，或尋求團隊協助。

### Q: Push 被拒絕怎麼辦？
A: 如果是因為 rebase 改寫了歷史，使用 `--force-with-lease` 強制推送。

### Q: 如何確認 rebase 成功？
A: 執行 `git diff main` 應該只顯示 Flyway 相關的變更。

---

## 📞 需要幫助？

如果遇到問題：
1. 先使用 `git status` 查看當前狀態
2. 使用 `git reflog` 查看操作歷史
3. 如果不確定，使用 `git rebase --abort` 安全退出
4. 查閱詳細文檔：`rebase-plan.md`
5. 查看流程圖：`rebase-workflow.md`

---

## 🎓 學習資源

- Git Rebase 官方文檔: https://git-scm.com/docs/git-rebase
- Git Reflog 官方文檔: https://git-scm.com/docs/git-reflog
- 互動式 Rebase: `git rebase -i main`
